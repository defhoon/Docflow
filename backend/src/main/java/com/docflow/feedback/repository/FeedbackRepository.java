package com.docflow.feedback.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.docflow.feedback.domain.Feedback;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

	/** 버전의 피드백을 작성 순으로, 작성자·버전과 함께 한 번에 조회한다 (N+1 방지). */
	@Query("""
			select f from Feedback f join fetch f.author join fetch f.documentVersion
			where f.documentVersion.id = :versionId
			order by f.id asc""")
	List<Feedback> findAllByVersionId(@Param("versionId") Long versionId);

}
