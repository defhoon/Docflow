package com.docflow.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.dto.DocumentVersionResponse;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.repository.DocumentVersionRepository.VersionFeedbackCount;
import com.docflow.document.storage.FileStorage;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.service.ProjectService;

/**
 * 버전 조회와 원본 파일 열기 (SPEC-05 §6). 모든 작업은 프로젝트 접근 → 문서 소속 → 버전 소속 순으로 확인한다.
 * <p>
 * 파일은 메타데이터 조회 트랜잭션이 끝난 뒤 연다. 파일 전송 중에는 DB 연결을 잡지 않는다 (SPEC-05 §6.4). 원본 파일명은 로그에
 * 남기지 않는다.
 */
@Service
public class DocumentVersionQueryService {

	private static final Logger log = LoggerFactory.getLogger(DocumentVersionQueryService.class);

	private final ProjectService projectService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final FileStorage fileStorage;

	private final TransactionTemplate readOnlyTransaction;

	public DocumentVersionQueryService(ProjectService projectService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, FileStorage fileStorage,
			PlatformTransactionManager transactionManager) {
		this.projectService = projectService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.fileStorage = fileStorage;
		this.readOnlyTransaction = new TransactionTemplate(transactionManager);
		this.readOnlyTransaction.setReadOnly(true);
	}

	/**
	 * 버전 히스토리: 최신 먼저, 첫 항목만 최신 표시 (SPEC-05 §6.3). 버전별 피드백 수는 집계 쿼리 한 번으로 붙인다 (SPEC-16).
	 */
	@Transactional(readOnly = true)
	public List<DocumentVersionResponse> findVersions(Long projectId, Long documentId, Long sessionUserId) {
		requireDocument(projectId, documentId, sessionUserId);
		List<DocumentVersion> versions = this.documentVersionRepository.findAllWithUploaderByDocumentId(documentId);
		Map<Integer, Long> feedbackCounts = new HashMap<>();
		for (VersionFeedbackCount count : this.documentVersionRepository.countFeedbacksByVersion(documentId)) {
			feedbackCounts.put(count.getVersionNumber(), count.getFeedbackCount());
		}
		List<DocumentVersionResponse> responses = new ArrayList<>(versions.size());
		for (int i = 0; i < versions.size(); i++) {
			DocumentVersion version = versions.get(i);
			responses.add(DocumentVersionResponse.from(version, i == 0,
					feedbackCounts.getOrDefault(version.getVersionNumber(), 0L)));
		}
		return responses;
	}

	/** 원본 다운로드용으로 파일을 연다 (SPEC-05 §6.4). */
	public VersionFile openOriginal(Long projectId, Long documentId, int versionNumber, Long sessionUserId) {
		VersionFileInfo info = resolve(projectId, documentId, versionNumber, sessionUserId);
		return new VersionFile(info.originalFilename(), info.contentType(), info.sizeBytes(), openStored(info));
	}

	/** 접근·문서·버전을 확인하고 파일 메타데이터를 돌려준다. 트랜잭션은 반환 전에 끝난다. */
	public VersionFileInfo resolve(Long projectId, Long documentId, int versionNumber, Long sessionUserId) {
		return this.readOnlyTransaction.execute((status) -> {
			requireDocument(projectId, documentId, sessionUserId);
			DocumentVersion version = this.documentVersionRepository
				.findByDocumentIdAndVersionNumber(documentId, versionNumber)
				.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
			DocumentFileType type = DocumentFileType.fromContentType(version.getContentType())
				.orElseThrow(() -> new IllegalStateException("Unknown stored content type: documentId=" + documentId
						+ ", versionNumber=" + versionNumber));
			return new VersionFileInfo(projectId, documentId, versionNumber, version.getOriginalFilename(),
					version.getContentType(), version.getSizeBytes(), version.getStorageKey(), type);
		});
	}

	/**
	 * 저장된 원본을 연다. 응답 헤더를 보내기 전에 호출되므로 누락·크기 불일치는 JSON 오류로 응답된다 (SPEC-05 §7.3).
	 */
	public InputStream openStored(VersionFileInfo info) {
		long actual;
		try {
			actual = this.fileStorage.size(info.storageKey());
		}
		catch (NoSuchFileException ex) {
			throw unavailable(info, "missing", null);
		}
		catch (IOException | RuntimeException ex) {
			throw unavailable(info, "size check failed", ex);
		}
		if (actual != info.sizeBytes()) {
			throw unavailable(info, "size mismatch (expected=" + info.sizeBytes() + ", actual=" + actual + ")", null);
		}
		try {
			return this.fileStorage.openForRead(info.storageKey());
		}
		catch (IOException | RuntimeException ex) {
			throw unavailable(info, "open failed", ex);
		}
	}

	private void requireDocument(Long projectId, Long documentId, Long sessionUserId) {
		this.projectService.getAccessibleProject(projectId, sessionUserId);
		if (this.documentRepository.findByIdAndProjectId(documentId, projectId).isEmpty()) {
			throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
		}
	}

	private static BusinessException unavailable(VersionFileInfo info, String reason, Throwable cause) {
		log.error("Stored document file unavailable: projectId={}, documentId={}, versionNumber={}, storageKey={}, reason={}",
				info.projectId(), info.documentId(), info.versionNumber(), info.storageKey(), reason, cause);
		return new BusinessException(ErrorCode.DOCUMENT_FILE_UNAVAILABLE);
	}

}
