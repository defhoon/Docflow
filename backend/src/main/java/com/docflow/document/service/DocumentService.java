package com.docflow.document.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.audit.domain.AuditAction;
import com.docflow.audit.domain.AuditTargetType;
import com.docflow.audit.service.AuditLogService;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.domain.StoredDocumentFile;
import com.docflow.document.dto.CreateDocumentRequest;
import com.docflow.document.dto.DocumentResponse;
import com.docflow.document.dto.DocumentSummaryResponse;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.storage.FileStorage;
import com.docflow.document.validation.DocumentFileName;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 문서 등록·조회 (SPEC-03 §6, §7). 모든 작업은 프로젝트 접근 확인(404)을 먼저 수행한다.
 * <p>
 * 원본 파일명은 로그에 남기지 않는다. 로그에는 {@code projectId}, {@code documentId}, {@code storageKey}만 남긴다.
 */
@Service
public class DocumentService {

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final UserAccountService userAccountService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final DocumentFileIntake fileIntake;

	private final StoredFileTransaction storedFileTransaction;

	private final AuditLogService auditLogService;

	public DocumentService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			UserAccountService userAccountService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, FileStorage fileStorage,
			DocumentFileValidator documentFileValidator, PlatformTransactionManager transactionManager,
			AuditLogService auditLogService) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.userAccountService = userAccountService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.fileIntake = new DocumentFileIntake(fileStorage, documentFileValidator);
		this.storedFileTransaction = new StoredFileTransaction(transactionManager, fileStorage);
		this.auditLogService = auditLogService;
	}

	/**
	 * 문서와 v1을 등록한다 (SPEC-03 §7.2).
	 * <p>
	 * 이 메서드에는 트랜잭션을 두지 않는다. 파일을 최종 위치에 배치(복사·flush·close 완료)한 뒤에만
	 * {@link StoredFileTransaction}으로 DB 트랜잭션을 시작해 Document와 v1을 함께 저장한다. 롤백이 확정되면 이 요청이
	 * 생성한 파일을 삭제하고, 커밋 결과가 불명확하면 파일을 남긴다 ({@link StoredFileCompensation}).
	 */
	public DocumentResponse create(Long projectId, Long sessionUserId, CreateDocumentRequest request) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireDocumentCreator(project, sessionUserId);
		DocumentFileName fileName = DocumentFileName.of(request.file().getOriginalFilename());

		StoredDocumentFile storedFile = this.fileIntake.store(request.file(), fileName);

		DocumentResponse response = this.storedFileTransaction.execute(storedFile, (status) -> {
			User user = this.userAccountService.getReference(sessionUserId);
			Document document = this.documentRepository
				.save(Document.create(project, request.title(), request.description(), user));
			DocumentVersion version = this.documentVersionRepository
				.save(DocumentVersion.first(document, storedFile, user));
			// 활동 이력은 같은 트랜잭션에서 기록한다 (SPEC-06 FR-2, AC-AUD-01).
			this.auditLogService.record(projectId, sessionUserId, AuditAction.DOCUMENT_CREATED, AuditTargetType.DOCUMENT,
					document.getId(), document.getId(), version.getVersionNumber());
			return DocumentResponse.from(document, version);
		});
		log.info("Document created: projectId={}, documentId={}, storageKey={}", projectId, response.id(),
				storedFile.storageKey());
		return response;
	}

	@Transactional(readOnly = true)
	public List<DocumentSummaryResponse> findDocuments(Long projectId, Long sessionUserId) {
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		return this.documentRepository.findSummariesByProjectId(projectId);
	}

	@Transactional(readOnly = true)
	public DocumentResponse getDocument(Long projectId, Long documentId, Long sessionUserId) {
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		Document document = this.documentRepository.findByIdAndProjectId(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		DocumentVersion latest = this.documentVersionRepository
			.findFirstByDocumentIdOrderByVersionNumberDesc(document.getId())
			.orElseThrow(() -> new IllegalStateException("Document has no version: documentId=" + document.getId()));
		return DocumentResponse.from(document, latest);
	}

}
