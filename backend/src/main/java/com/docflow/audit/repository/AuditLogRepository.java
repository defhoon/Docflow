package com.docflow.audit.repository;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.audit.domain.AuditLog;
import com.docflow.audit.dto.ActivityResponse;

/**
 * 활동 이력 조회 (SPEC-06 §6). 모든 목록은 {@code id DESC}이며 {@code beforeId}보다 작은 ID만 반환한다(커서 페이지).
 * {@code beforeId}가 없으면 호출자가 {@link Long#MAX_VALUE}를 넘긴다.
 */
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

	String SELECT_ACTIVITY = """
			select new com.docflow.audit.dto.ActivityResponse(
			    a.id, a.action, u.id, u.displayName, p.id, p.name, d.id, d.title, a.versionNumber, a.createdAt)
			from AuditLog a
			join User u on u.id = a.actorId
			join Project p on p.id = a.projectId
			left join Document d on d.id = a.documentId
			""";

	@Query(SELECT_ACTIVITY + """
			where a.projectId = :projectId and a.id < :beforeId
			order by a.id desc""")
	List<ActivityResponse> findByProject(@Param("projectId") Long projectId, @Param("beforeId") long beforeId,
			Limit limit);

	@Query(SELECT_ACTIVITY + """
			where a.projectId = :projectId and a.documentId = :documentId and a.id < :beforeId
			order by a.id desc""")
	List<ActivityResponse> findByDocument(@Param("projectId") Long projectId, @Param("documentId") Long documentId,
			@Param("beforeId") long beforeId, Limit limit);

	/** 사용자가 Owner이거나 구성원인 프로젝트의 이력만 (SPEC-06 §4, AC-AUD-06). */
	@Query(SELECT_ACTIVITY + """
			where a.id < :beforeId
			  and (p.owner.id = :userId
			       or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId))
			order by a.id desc""")
	List<ActivityResponse> findAccessibleBy(@Param("userId") Long userId, @Param("beforeId") long beforeId,
			Limit limit);

}
