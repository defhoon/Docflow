package com.docflow.analysis.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisStatus;
import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.dto.AnalysisResponse;
import com.docflow.analysis.repository.AnalysisRepository;
import com.docflow.analysis.repository.ProjectAiSettingsRepository;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;

/**
 * 버전 분석 조회·요청 (SPEC-11 §3, SPEC-14). 모든 작업은 프로젝트 접근 → 문서 소속 → 버전 소속 순으로 확인한다. 결과는
 * 문서·버전 데이터를 바꾸지 않는 파생 데이터이므로 FINALIZED 문서에서도 조회·요청할 수 있다.
 */
@Service
public class DocumentAnalysisService {

	private final ProjectService projectService;

	private final ProjectPermissionService permissionService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository versionRepository;

	private final AnalysisRepository repository;

	private final ProjectAiSettingsRepository settingsRepository;

	private final JsonMapper jsonMapper;

	public DocumentAnalysisService(ProjectService projectService, ProjectPermissionService permissionService,
			DocumentRepository documentRepository, DocumentVersionRepository versionRepository,
			AnalysisRepository repository, ProjectAiSettingsRepository settingsRepository, JsonMapper jsonMapper) {
		this.projectService = projectService;
		this.permissionService = permissionService;
		this.documentRepository = documentRepository;
		this.versionRepository = versionRepository;
		this.repository = repository;
		this.settingsRepository = settingsRepository;
		this.jsonMapper = jsonMapper;
	}

	/** 접근 확인을 마친 대상 버전(들). {@code base}는 SUMMARY에서 null. */
	public record Target(Project project, Document document, DocumentVersion version, DocumentVersion base) {

		public boolean pdf() {
			return isPdf(this.version) && (this.base == null || isPdf(this.base));
		}

		private static boolean isPdf(DocumentVersion v) {
			return DocumentFileType.fromContentType(v.getContentType()).orElse(null) == DocumentFileType.PDF;
		}

	}

	@Transactional(readOnly = true)
	public Target resolve(Long projectId, Long documentId, int versionNumber, Integer baseVersionNumber, Long userId) {
		Project project = this.projectService.getAccessibleProject(projectId, userId);
		Document document = this.documentRepository.findByIdAndProjectId(documentId, projectId)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
		DocumentVersion version = version(documentId, versionNumber);
		DocumentVersion base = (baseVersionNumber != null) ? version(documentId, baseVersionNumber) : null;
		return new Target(project, document, version, base);
	}

	private DocumentVersion version(Long documentId, int number) {
		return this.versionRepository.findByDocumentIdAndVersionNumber(documentId, number)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
	}

	@Transactional(readOnly = true)
	public AnalysisResponse get(Target target, AnalysisType type) {
		return response(target, type);
	}

	/**
	 * 분석을 요청한다. 작업이 없으면 만들고, 다시 시도할 수 있는 실패면 대기로 되돌린다. 대기·진행·완료 중이면 그대로 둔다(중복
	 * 호출 없음).
	 */
	@Transactional
	public AnalysisResponse request(Target target, AnalysisType type, Long userId) {
		this.permissionService.requireFeedbackWriter(target.project(), userId);
		if (!this.settingsRepository.find(target.project().getId()).enabled()) {
			throw new BusinessException(ErrorCode.AI_DISABLED_FOR_PROJECT);
		}
		if (!target.pdf()) {
			throw new BusinessException(ErrorCode.ANALYSIS_UNSUPPORTED_TYPE);
		}
		Long baseId = (target.base() != null) ? target.base().getId() : null;
		AnalysisRecord existing = this.repository.find(target.version().getId(), type, baseId).orElse(null);
		if (existing == null) {
			this.repository.insertPending(target.project().getId(), target.document().getId(), target.version().getId(),
					baseId, type, userId);
		}
		else if (existing.status() == AnalysisStatus.FAILED) {
			if (!existing.retryable()) {
				throw new BusinessException(ErrorCode.ANALYSIS_NOT_RETRYABLE);
			}
			this.repository.resetFailed(existing.id(), userId);
		}
		return response(target, type);
	}

	private AnalysisResponse response(Target target, AnalysisType type) {
		boolean aiEnabled = this.settingsRepository.find(target.project().getId()).enabled();
		Long baseId = (target.base() != null) ? target.base().getId() : null;
		AnalysisRecord record = this.repository.find(target.version().getId(), type, baseId).orElse(null);
		if (record == null) {
			return new AnalysisResponse("NONE", null, false, aiEnabled, target.pdf(), null, null, null, null);
		}
		JsonNode result = (record.resultJson() != null) ? this.jsonMapper.readTree(record.resultJson()) : null;
		// 대기 중 재시도 예정이면 마지막 오류 코드를 숨긴다(진행 중으로 표시).
		String errorCode = (record.status() == AnalysisStatus.FAILED) ? record.errorCode() : null;
		return new AnalysisResponse(record.status().name(), errorCode, record.retryable(), aiEnabled, target.pdf(), result,
				record.model(), record.updatedAt(), record.completedAt());
	}

}
