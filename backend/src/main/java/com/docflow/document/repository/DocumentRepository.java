package com.docflow.document.repository;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.home.dto.HomeDocumentResponse;

/**
 * 문서 조회. 단건은 항상 경로의 {@code projectId}와 {@code documentId}를 함께 조건으로 쓴다 (SPEC-03 §6.2, IDOR 방어).
 */
public interface DocumentRepository extends JpaRepository<Document, Long> {

	/**
	 * 프로젝트 문서 목록: {@code created_at DESC, id DESC}. 작성자, 최신 버전(번호·업로더·형식), 최종 버전을 한 번의 쿼리로
	 * 가져온다 (결정 Q13·SPEC-18, N+1 방지). 최신 버전은 {@code (document_id, version_number)} 유일 인덱스로 찾는다.
	 */
	@Query("""
			select new com.docflow.document.dto.DocumentSummaryResponse(
			    d.id, d.title, d.description, d.status, u.id, u.displayName, d.createdAt,
			    lv.versionNumber, fv.versionNumber, d.updatedAt, lu.id, lu.displayName, lv.contentType)
			from Document d join d.createdBy u left join d.finalVersion fv
			join DocumentVersion lv on lv.document = d
			    and lv.versionNumber = (select max(v.versionNumber) from DocumentVersion v where v.document = d)
			join lv.uploadedBy lu
			where d.project.id = :projectId
			order by d.createdAt desc, d.id desc""")
	List<DocumentSummaryResponse> findSummariesByProjectId(@Param("projectId") Long projectId);

	/**
	 * 홈: 사용자가 접근할 수 있는 프로젝트의 최근 변경 문서 (SPEC-18). {@code updated_at DESC, id DESC}, 개수는 호출자가 정한다.
	 * 접근 조건은 프로젝트 목록과 같다(Owner이거나 MEMBER).
	 */
	@Query("""
			select new com.docflow.home.dto.HomeDocumentResponse(
			    d.id, d.title, d.status, p.id, p.name, lv.versionNumber, fv.versionNumber, d.updatedAt,
			    lu.id, lu.displayName, lv.contentType)
			from Document d join d.project p left join d.finalVersion fv
			join DocumentVersion lv on lv.document = d
			    and lv.versionNumber = (select max(v.versionNumber) from DocumentVersion v where v.document = d)
			join lv.uploadedBy lu
			where p.owner.id = :userId
			   or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId)
			order by d.updatedAt desc, d.id desc""")
	List<HomeDocumentResponse> findRecentAccessible(@Param("userId") Long userId, Limit limit);

	/** 홈: 접근 가능한 프로젝트의 문서 수를 상태별로 센다 (SPEC-18). 집계 쿼리 한 번. */
	@Query("""
			select d.status as status, count(d) as count
			from Document d join d.project p
			where p.owner.id = :userId
			   or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId)
			group by d.status""")
	List<StatusCount> countAccessibleByStatus(@Param("userId") Long userId);

	/** {@link #countAccessibleByStatus} 결과. */
	interface StatusCount {

		DocumentStatus getStatus();

		Long getCount();

	}

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
