package com.telusko.airline.service;

import com.telusko.airline.model.KnowledgeArticle;
import com.telusko.airline.repository.KnowledgeArticleRepository;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Puts the knowledge base into the vector store. The writing half of RAG.
 * <p>
 * Kept apart from {@code RagConfig}, which only reads. Indexing runs on startup and when an
 * admin edits an article, and it is the operation that costs money in embedding calls, so it
 * is worth being able to see and trigger on its own.
 */
@Service
public class KnowledgeIndexService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexService.class);

    /**
     * Chunk size in tokens, and how much each chunk repeats of the one before.
     * <p>
     * 300 with 40 of overlap, chosen for the shape of this content. Airline policy is written
     * in short clauses, and a chunk that ends mid clause retrieves as nonsense: "checked
     * baggage over 15 kg" is useless without the sentence that says what happens then. The
     * overlap is what stops a rule being split across two chunks so that neither one answers
     * the question.
     * <p>
     * Bigger chunks are not free. Five chunks of 1000 tokens is 5000 tokens of prompt on
     * every single question, and most of it is text nobody asked about.
     */
    private static final int CHUNK_TOKENS = 300;
    private static final int CHUNK_OVERLAP_TOKENS = 40;

    private final KnowledgeArticleRepository articles;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public KnowledgeIndexService(KnowledgeArticleRepository articles,
                                 EmbeddingStore<TextSegment> embeddingStore,
                                 EmbeddingModel embeddingModel) {
        this.articles = articles;
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    /**
     * Indexes every article. Returns how many chunks were written.
     * <p>
     * Removes the previous chunks for each article first. Without that, editing an article
     * leaves the old wording in the store next to the new, and the assistant retrieves both
     * and quotes whichever scored higher. A knowledge base that grows a second copy of a
     * policy every time somebody fixes a typo is worse than no knowledge base.
     */
    @Transactional(readOnly = true)
    public int reindexAll() {
        List<KnowledgeArticle> all = articles.findAll();
        int chunks = 0;

        for (KnowledgeArticle article : all) {
            chunks += index(article);
        }

        log.info("Indexed {} articles into {} chunks", all.size(), chunks);
        return chunks;
    }

    /**
     * Indexes one article, replacing whatever was there before.
     *
     * @return the number of chunks written
     */
    public int index(KnowledgeArticle article) {
        removeExisting(article);

        // The metadata is what makes a citation possible. Without the slug and title on
        // every chunk, the assistant can quote the policy but cannot say which policy, and
        // a passenger who wants to check it has nowhere to look.
        Document document = Document.from(
                article.getTitle() + "\n\n" + article.getBody(),
                Metadata.from(java.util.Map.of(
                        "slug", article.getSlug(),
                        "title", article.getTitle(),
                        "topic", article.getTopic())));

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(CHUNK_TOKENS, CHUNK_OVERLAP_TOKENS))
                .embeddingModel(embeddingModel)
                .embeddingStore(embeddingStore)
                .build();

        ingestor.ingest(document);

        // The ingestor does not report a count, so estimate from the body length. Only used
        // for the log line and the admin response, never for anything that has to be exact.
        return Math.max(1, article.getBody().length() / (CHUNK_TOKENS * 3));
    }

    /**
     * Drops the chunks belonging to one article.
     * <p>
     * Uses the store's own metadata filter rather than a hand written delete, so this keeps
     * working if the table layout changes underneath us.
     */
    private void removeExisting(KnowledgeArticle article) {
        try {
            embeddingStore.removeAll(
                    dev.langchain4j.store.embedding.filter.MetadataFilterBuilder
                            .metadataKey("slug").isEqualTo(article.getSlug()));
        } catch (RuntimeException ex) {
            // First run, when the table exists but holds nothing matching. Not worth
            // failing a startup over, and the ingest below is what actually matters.
            log.debug("Nothing to remove for slug {}: {}", article.getSlug(), ex.getMessage());
        }
    }
}
