package com.documenttaskrunner.repository;

import com.documenttaskrunner.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentRepository extends JpaRepository<Document, Long> {
}
