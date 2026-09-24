package com.telusko.airline.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * The readable copy of everything that gets indexed for RAG.
 * <p>
 * The vector table holds chunks and embeddings, which nobody can review. Keeping the source
 * article in a normal table means an admin can read what the assistant is answering from,
 * edit it, and re-index. Without this the knowledge base is a black box you can only add to.
 */
@Entity
@Table(name = "knowledge_article")
@Getter
@Setter
@NoArgsConstructor
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String title;

    /** BAGGAGE, REFUND, CHECK_IN, DESTINATION and so on. Used as a retrieval filter. */
    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, length = 20000)
    private String body;

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    public KnowledgeArticle(String slug, String title, String topic, String body) {
        this.slug = slug;
        this.title = title;
        this.topic = topic;
        this.body = body;
    }
}
