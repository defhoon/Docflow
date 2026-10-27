package com.docflow.project.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.project.domain.ProjectMember;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, Long> {

	boolean existsByProjectIdAndUserId(Long projectId, Long userId);

	/** 구성원 목록: 추가 순({@code created_at ASC, id ASC}). 사용자를 함께 조회해 N+1을 막는다. */
	@Query("""
			select m from ProjectMember m join fetch m.user
			where m.project.id = :projectId
			order by m.createdAt asc, m.id asc""")
	List<ProjectMember> findAllWithUserByProjectId(@Param("projectId") Long projectId);

	/** 지정한 프로젝트의 지정한 사용자 행만 삭제한다. 삭제 건수를 반환한다. */
	@Modifying
	@Query("delete from ProjectMember m where m.project.id = :projectId and m.user.id = :userId")
	int deleteByProjectIdAndUserId(@Param("projectId") Long projectId, @Param("userId") Long userId);

}
