package com.docflow.document.repository;

import java.util.List;
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

	/** 버전 히스토리: 최신 먼저, 업로더를 함께 조회한다 (SPEC-05 §6.3, N+1 방지). */
	@Query("""
			select v from DocumentVersion v join fetch v.uploadedBy
			where v.document.id = :documentId
			order by v.versionNumber desc""")
	List<DocumentVersion> findAllWithUploaderByDocumentId(@Param("documentId") Long documentId);

	/** 문서 안의 버전 번호로 단건 조회한다 (SPEC-05 §5.2). 문서 소속은 호출 측이 먼저 확인한다. */
	Optional<DocumentVersion> findByDocumentIdAndVersionNumber(Long documentId, int versionNumber);

}
