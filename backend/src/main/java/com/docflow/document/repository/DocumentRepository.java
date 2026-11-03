package com.docflow.document.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
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
			    (select max(v.versionNumber) from DocumentVersion v where v.document = d))
			from Document d join d.createdBy u
			where d.project.id = :projectId
			order by d.createdAt desc, d.id desc""")
	List<DocumentSummaryResponse> findSummariesByProjectId(@Param("projectId") Long projectId);

	@Query("""
			select d from Document d join fetch d.createdBy
			where d.id = :documentId and d.project.id = :projectId""")
	Optional<Document> findByIdAndProjectId(@Param("documentId") Long documentId, @Param("projectId") Long projectId);

}
