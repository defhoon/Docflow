package com.docflow.audit.service;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditLog;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.dto.ActivityResponse;
import com.docflow.audit.repository.AuditLogRepository;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.service.ProjectService;

/**
 * 활동 이력 기록·조회 (SPEC-06).
 * <p>
 * 기록({@link #record})은 업무 트랜잭션 안에서만 호출한다({@link Propagation#MANDATORY}, FR-1). 같은 트랜잭션이므로 업무가
 * 롤백되면 이력도 저장되지 않고, 이력 저장이 실패하면 업무도 롤백된다(FR-2).
 */
@Service
public class AuditLogService {

	public static final int DEFAULT_LIMIT = 20;

	public static final int MAX_LIMIT = 100;

	private final AuditLogRepository auditLogRepository;

	private final ProjectService projectService;

	private final DocumentRepository documentRepository;

	public AuditLogService(AuditLogRepository auditLogRepository, ProjectService projectService,
			DocumentRepository documentRepository) {
		this.auditLogRepository = auditLogRepository;
		this.projectService = projectService;
		this.documentRepository = documentRepository;
	}

	@Transactional(propagation = Propagation.MANDATORY)
	public void record(Long projectId, Long actorId, AuditAction action, AuditTargetType targetType, Long targetId,
			Long documentId, Integer versionNumber) {
		this.auditLogRepository
			.save(AuditLog.of(projectId, actorId, action, targetType, targetId, documentId, versionNumber));
	}

	@Transactional(readOnly = true)
	public List<ActivityResponse> findMine(Long sessionUserId, Integer limit, Long beforeId) {
		return this.auditLogRepository.findAccessibleBy(sessionUserId, cursor(beforeId), limit(limit));
	}

	@Transactional(readOnly = true)
	public List<ActivityResponse> findByProject(Long projectId, Long sessionUserId, Integer limit, Long beforeId) {
		Limit pageLimit = limit(limit);
		long cursor = cursor(beforeId);
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		return this.auditLogRepository.findByProject(projectId, cursor, pageLimit);
	}

	@Transactional(readOnly = true)
	public List<ActivityResponse> findByDocument(Long projectId, Long documentId, Long sessionUserId, Integer limit,
			Long beforeId) {
		Limit pageLimit = limit(limit);
		long cursor = cursor(beforeId);
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.documentRepository.findByIdAndProjectId(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		return this.auditLogRepository.findByDocument(projectId, documentId, cursor, pageLimit);
	}

	static Limit limit(Integer limit) {
		int value = (limit == null) ? DEFAULT_LIMIT : limit;
		if (value < 1 || value > MAX_LIMIT) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED);
		}
		return Limit.of(value);
	}

	static long cursor(Long beforeId) {
		if (beforeId == null) {
			return Long.MAX_VALUE;
		}
		if (beforeId < 1) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED);
		}
		return beforeId;
	}

}
