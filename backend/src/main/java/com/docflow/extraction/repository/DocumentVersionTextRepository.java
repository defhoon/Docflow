package com.docflow.extraction.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docflow.extraction.domain.DocumentVersionText;

public interface DocumentVersionTextRepository extends JpaRepository<DocumentVersionText, Long> {

}
