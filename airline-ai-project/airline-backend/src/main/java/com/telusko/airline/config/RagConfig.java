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
 * Shared RAG retrieval pipeline: vector store, content retriever, and retrieval augmentor.
 */
@Configuration
public class RagConfig {

    /**
     * Vector store using the application DataSource so embeddings and business data share
     * the configured PostgreSQL instance.
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
     * Vector search with a configurable score floor. Low-scoring matches are omitted to
     * avoid supplying unrelated policy text; {@link #policyOnlyRouter} limits retrieval to
     * requests covered by the corpus.
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
     * Rewrites conversational follow-ups into standalone queries before retrieval. A plain
     * {@code ChatModel} keeps query transformation outside passenger-facing agent memory.
     */
    @Bean
    public RetrievalAugmentor retrievalAugmentor(ContentRetriever contentRetriever, ChatModel chatModel) {
        return DefaultRetrievalAugmentor.builder()
                .queryTransformer(new CompressingQueryTransformer(chatModel))
                .queryRouter(policyOnlyRouter(contentRetriever, chatModel))
                .build();
    }

    /**
     * Routes only policy and destination-guide requests to retrieval. On routing failure,
     * {@code DO_NOT_ROUTE} allows a narrower tool-based answer without unrelated sources.
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
