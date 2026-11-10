package com.docflow.feedback.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.service.AuditLogService;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.feedback.domain.Feedback;
import com.docflow.feedback.dto.FeedbackResponse;
import com.docflow.feedback.repository.FeedbackRepository;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.service.UserAccountService;

/**
 * 버전별 피드백 작성·조회 (SPEC-08).
 * <p>
 * 작성은 문서 행을 {@code FOR UPDATE}로 잠근 뒤 ACTIVE를 확인하므로 최종 확정(SPEC-07)과 직렬화된다(FR-3). 피드백 내용은
 * 로그에 남기지 않는다.
 */
@Service
public class FeedbackService {

	private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final UserAccountService userAccountService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final FeedbackRepository feedbackRepository;

	private final AuditLogService auditLogService;

	public FeedbackService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			UserAccountService userAccountService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, FeedbackRepository feedbackRepository,
			AuditLogService auditLogService) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.userAccountService = userAccountService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.feedbackRepository = feedbackRepository;
		this.auditLogService = auditLogService;
	}

	@Transactional
	public FeedbackResponse create(Long projectId, Long documentId, int versionNumber, Long sessionUserId,
			String rawContent) {
		String content = normalize(rawContent);
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireFeedbackWriter(project, sessionUserId);
		Document document = this.documentRepository.findByIdAndProjectIdForUpdate(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		if (!document.getStatus().isCollaborationOpen()) {
			throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_FINALIZED);
		}
		DocumentVersion version = findVersion(documentId, versionNumber);
		Feedback feedback = this.feedbackRepository
			.save(Feedback.create(version, this.userAccountService.getReference(sessionUserId), content));
		this.auditLogService.record(projectId, sessionUserId, AuditAction.FEEDBACK_CREATED, AuditTargetType.FEEDBACK,
				feedback.getId(), documentId, versionNumber);
		this.feedbackRepository.flush();
		log.info("Feedback created: projectId={}, documentId={}, versionNumber={}, feedbackId={}", projectId,
				documentId, versionNumber, feedback.getId());
		return FeedbackResponse.from(feedback);
	}

	@Transactional(readOnly = true)
	public List<FeedbackResponse> findByVersion(Long projectId, Long documentId, int versionNumber,
			Long sessionUserId) {
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.documentRepository.findByIdAndProjectId(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		DocumentVersion version = findVersion(documentId, versionNumber);
		return this.feedbackRepository.findAllByVersionId(version.getId()).stream().map(FeedbackResponse::from).toList();
	}

	private DocumentVersion findVersion(Long documentId, int versionNumber) {
		return this.documentVersionRepository.findByDocumentIdAndVersionNumber(documentId, versionNumber)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
	}

	/** 앞뒤 공백 제거 후 1~2,000자(코드 포인트, PostgreSQL VARCHAR 길이와 같은 기준) (FR-2). */
	static String normalize(String rawContent) {
		String content = (rawContent == null) ? "" : rawContent.strip();
		int length = content.codePointCount(0, content.length());
		if (length == 0 || length > Feedback.MAX_CONTENT_LENGTH) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED);
		}
		return content;
	}

}
