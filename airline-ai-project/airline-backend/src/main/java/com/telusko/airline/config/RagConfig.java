package com.telusko.airline.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;
import dev.langchain4j.rag.query.transformer.CompressingQueryTransformer;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Map;

/**
 * The retrieval half of RAG, wired once so every feature searches the same way.
 * <p>
 * Three beans, and the order they are listed in is the order a question travels through them:
 * the store holds the vectors, the retriever searches it, and the augmentor is what an
 * {@code @AiService} plugs into.
 */
@Configuration
public class RagConfig {

    /**
     * The vector store, built on the application's own DataSource.
     * <p>
     * {@code datasourceBuilder()} is the reason this is three lines rather than a second set
     * of host, port, user and password properties. It borrows the pooled connection Spring
     * already manages, so the vectors live in the same Postgres as the bookings, in the same
     * transaction manager, with one thing to back up.
     * <p>
     * {@code createTable(true)} means the table appears on first boot. The vector extension
     * itself cannot be created this way, which is what init/schema.sql is for.
     */
    @Bean
    public EmbeddingStore<TextSegment> embeddingStore(DataSource dataSource,
                                                      @Value("${airline.rag.table}") String table,
                                                      @Value("${airline.rag.dimension}") int dimension) {
        return PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table(table)
                .dimension(dimension)
                .createTable(true)
                // An index only pays for itself once there are a lot of rows, and it makes
                // the seeded knowledge base slower to build. Turn it on when the corpus grows.
                .useIndex(false)
                .build();
    }

    /**
     * Vector search with a score floor.
     * <p>
     * The floor is the part people skip, and it is the part that stops the assistant lying.
     * A store with no floor always returns its {@code maxResults} nearest rows, so "what is
     * the wifi password" pulls back the baggage policy and the model is invited to answer
     * from it. Below the floor the retriever returns nothing, and the system message tells
     * the assistant to say it does not know.
     * <p>
     * 0.70 was arrived at by measuring, and it started at 0.55. At 0.55 a question with
     * nothing to do with policy, "what are my upcoming flights", still came back citing
     * three articles: the baggage allowance, the refund rules and the check in times. At
     * 0.70 that fell to one, and genuine policy questions still retrieve the right article
     * and answer correctly. Anyone tuning this should do the same thing: ask a question the
     * corpus does not cover and see what it drags back.
     * <p>
     * The floor is not the only defence. {@link #policyOnlyRouter} decides whether to
     * retrieve at all, which is the cheaper lever of the two.
     */
    @Bean
    public ContentRetriever contentRetriever(EmbeddingStore<TextSegment> embeddingStore,
                                             EmbeddingModel embeddingModel,
                                             @Value("${airline.rag.max-results}") int maxResults,
                                             @Value("${airline.rag.min-score}") double minScore) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(maxResults)
                .minScore(minScore)
                .displayName("airline-knowledge")
                .build();
    }

    /**
     * What an {@code @AiService} actually uses. It rewrites the question, then retrieves.
     * <p>
     * The rewrite is not decoration. Embeddings have no memory of the conversation, so a
     * follow up like "and for a cabin bag?" is close to meaningless on its own and retrieves
     * noise. {@link CompressingQueryTransformer} turns it back into a standalone question
     * using the chat history, which is what makes multi turn RAG work at all. Short follow
     * ups are exactly what passengers type.
     * <p>
     * Note which model it gets. This has to be a plain {@code ChatModel} and not one of our
     * agents: the rewrite is a background call, and routing it through an agent would put
     * machine written text into the passenger's own chat memory.
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ChatModel chatModel) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(new CompressingQueryTransformer(chatModel))
                .queryRouter(policyOnlyRouter(contentRetriever, chatModel))
                .build();
    }

    /**
     * Decides whether to retrieve at all.
     * <p>
     * This was added after measuring, not on principle. Without a router the augmentor
     * retrieves on every single request, so "what are my upcoming flights" came back at
     * 3693 tokens with three policy articles listed as its sources: the baggage allowance,
     * the refund rules and the check in times, none of which had anything to do with the
     * question. The score floor did not catch them, because the compressing transformer
     * rewrites a short question into a longer one that scores respectably against almost
     * any policy text.
     * <p>
     * The router costs one small model call and saves a few thousand tokens of prompt on
     * every question that is about the passenger's own data rather than the rules. It also
     * fixes the sources list, which is worth as much: an answer that claims to be based on
     * three policies it never used is worse than one that cites nothing.
     * <p>
     * {@code DO_NOT_ROUTE} on failure is the safe default of the three. If the router itself
     * errors, the assistant answers from the tools with no retrieved text, which is a
     * narrower answer. {@code ROUTE_TO_ALL} would put us back where we started, and
     * {@code FAIL} would turn a routing hiccup into a failed passenger request.
     */
    private static QueryRouter policyOnlyRouter(ContentRetriever contentRetriever, ChatModel chatModel) {
        return LanguageModelQueryRouter.builder()
                .chatModel(chatModel)
                .retrieverToDescription(Map.of(contentRetriever,
                        // Written for the model to read, so it says what is in the corpus
                        // and, just as importantly, what is not.
                        """
                        The written policies of the airline and its destination guides:
                        baggage allowances and fees, cancellation and refund rules,
                        compensation for delays, check in and boarding times, seat charges,
                        special assistance, and what particular destinations are like and
                        when to visit them.
                        Not useful for questions about a specific booking, a specific flight,
                        a fare, a seat number or a PNR. Those come from tools.
                        """))
                .fallbackStrategy(LanguageModelQueryRouter.FallbackStrategy.DO_NOT_ROUTE)
                .build();
    }
}
