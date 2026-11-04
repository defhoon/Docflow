package com.docflow.document.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.document.domain.DocumentVersion;

public interface DocumentVersionRepository extends JpaRepository<DocumentVersion, Long> {

	/** 최신 버전: {@code version_number}가 가장 큰 버전 (결정 Q13). */
	Optional<DocumentVersion> findFirstByDocumentIdOrderByVersionNumberDesc(Long documentId);

	/** 문서의 최대 버전 번호. 문서 행 잠금을 잡은 트랜잭션 안에서 다음 번호 계산에 쓴다 (SPEC-04 §5.2, 결정 V1). */
	@Query("select coalesce(max(v.versionNumber), 0) from DocumentVersion v where v.document.id = :documentId")
	int findMaxVersionNumber(@Param("documentId") Long documentId);

}
