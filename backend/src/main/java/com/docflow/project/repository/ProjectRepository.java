package com.docflow.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.project.domain.Project;

/**
 * 프로젝트 조회. 목록과 단건 접근 확인은 같은 조건(Owner이거나 MEMBER)을 사용한다 (SPEC-02 §7.2, SPEC-01 §7.8 원칙 4).
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

	/** 내가 접근할 수 있는 프로젝트 목록: {@code created_at DESC, id DESC}. */
	@Query("""
			select p from Project p
			where p.owner.id = :userId
			   or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId)
			order by p.createdAt desc, p.id desc""")
	List<Project> findAllAccessible(@Param("userId") Long userId);

	/** 접근 확인용 단건 조회. 존재하지 않는 프로젝트와 구성원이 아닌 프로젝트를 구분하지 않는다. */
	@Query("""
			select p from Project p
			where p.id = :projectId
			  and (p.owner.id = :userId
			       or exists (select 1 from ProjectMember m where m.project = p and m.user.id = :userId))""")
	Optional<Project> findAccessible(@Param("projectId") Long projectId, @Param("userId") Long userId);

}
