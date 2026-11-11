package com.docflow.document.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.document.domain.Document;
import com.docflow.document.dto.DocumentSummaryResponse;

/**
 * 문서 조회. 단건은 항상 경로의 {@code projectId}와 {@code documentId}를 함께 조건으로 쓴다 (SPEC-03 §6.2, IDOR 방어).
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

	/**
	 * 프로젝트 문서 목록: {@code created_at DESC, id DESC}. 작성자와 최신 버전 번호({@code MAX(version_number)})를 한
	 * 번의 쿼리로 가져온다 (결정 Q13, N+1 방지).
	 */
	@Query("""
			select new com.docflow.document.dto.DocumentSummaryResponse(
			    d.id, d.title, d.status, u.id, u.displayName, d.createdAt,
			    (select max(v.versionNumber) from DocumentVersion v where v.document = d), fv.versionNumber)
			from Document d join d.createdBy u left join d.finalVersion fv
			where d.project.id = :projectId
			order by d.createdAt desc, d.id desc""")
	List<DocumentSummaryResponse> findSummariesByProjectId(@Param("projectId") Long projectId);

	@Query("""
			select d from Document d join fetch d.createdBy
			where d.id = :documentId and d.project.id = :projectId""")
	Optional<Document> findByIdAndProjectId(@Param("documentId") Long documentId, @Param("projectId") Long projectId);

	/**
	 * 새 버전 등록용 문서 행 잠금 (SPEC-04 §7.4, 결정 V1·I3). 다른 테이블을 join하지 않아 {@code documents} 행 하나만
	 * 잠근다({@code join fetch}를 쓰면 PostgreSQL이 {@code users} 행까지 잠근다). 파일 배치 후 시작한 트랜잭션 안에서만 호출한다.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
			select d from Document d
			where d.id = :documentId and d.project.id = :projectId""")
	Optional<Document> findByIdAndProjectIdForUpdate(@Param("documentId") Long documentId,
			@Param("projectId") Long projectId);

}
