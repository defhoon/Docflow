package com.docflow.home.service;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.audit.repository.AuditLogRepository;
import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentRepository.StatusCount;
import com.docflow.home.dto.HomeResponse;
import com.docflow.project.repository.ProjectRepository;

/**
 * 홈 통합 조회 (SPEC-18). 읽기 전용이며 쿼리 4회(프로젝트 수, 상태별 문서 수, 최근 문서, 최근 활동)로 끝난다. 프로젝트·문서
 * 수와 무관하게 반복 조회하지 않는다.
 */
@Service
public class HomeService {

	private final ProjectRepository projectRepository;

	private final DocumentRepository documentRepository;

	private final AuditLogRepository auditLogRepository;

	public HomeService(ProjectRepository projectRepository, DocumentRepository documentRepository,
			AuditLogRepository auditLogRepository) {
		this.projectRepository = projectRepository;
		this.documentRepository = documentRepository;
		this.auditLogRepository = auditLogRepository;
	}

	@Transactional(readOnly = true)
	public HomeResponse getHome(Long sessionUserId) {
		long projectCount = this.projectRepository.countAccessible(sessionUserId);
		long active = 0;
		long finalized = 0;
		for (StatusCount count : this.documentRepository.countAccessibleByStatus(sessionUserId)) {
			if (count.getStatus() == DocumentStatus.ACTIVE) {
				active = count.getCount();
			}
			else if (count.getStatus() == DocumentStatus.FINALIZED) {
				finalized = count.getCount();
			}
		}
		return new HomeResponse(projectCount, active, finalized,
				this.documentRepository.findRecentAccessible(sessionUserId, Limit.of(HomeResponse.RECENT_DOCUMENT_LIMIT)),
				this.auditLogRepository.findAccessibleBy(sessionUserId, Long.MAX_VALUE,
						Limit.of(HomeResponse.RECENT_ACTIVITY_LIMIT)));
	}

}
