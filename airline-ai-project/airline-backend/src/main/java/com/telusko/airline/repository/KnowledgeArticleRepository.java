package com.telusko.airline.repository;

import com.telusko.airline.model.KnowledgeArticle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeArticleRepository extends JpaRepository<KnowledgeArticle, Long> {

    Optional<KnowledgeArticle> findBySlug(String slug);

    List<KnowledgeArticle> findByTopic(String topic);
}
