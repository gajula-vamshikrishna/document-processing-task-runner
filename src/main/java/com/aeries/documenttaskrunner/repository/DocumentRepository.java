package com.aeries.documenttaskrunner.repository;

import com.aeries.documenttaskrunner.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
