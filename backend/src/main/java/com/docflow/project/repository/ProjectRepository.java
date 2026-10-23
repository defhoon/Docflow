package com.docflow.project.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docflow.project.domain.Project;

public interface ProjectRepository extends JpaRepository<Project, Long> {

	/** 내 프로젝트 목록: {@code created_at DESC, id DESC}. */
	List<Project> findAllByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);

	/** 접근 확인용 단건 조회. 다른 사용자의 프로젝트와 존재하지 않는 프로젝트를 구분하지 않는다. */
	Optional<Project> findByIdAndOwnerId(Long id, Long ownerId);

}
