package com.docflow.document.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docflow.document.domain.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

	/** 최신 버전: {@code version_number}가 가장 큰 버전 (결정 Q13). */
	Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(Long documentId);

}
