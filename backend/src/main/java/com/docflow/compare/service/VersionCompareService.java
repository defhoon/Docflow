package com.docflow.compare.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.compare.diff.CompareResult;
import com.docflow.compare.diff.VersionDiffer;
import com.docflow.compare.dto.CompareResponse;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.extraction.pdf.ExtractedText;
import com.docflow.extraction.service.DocumentTextService;
import com.docflow.extraction.service.VersionText;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.service.ProjectService;

/**
 * 버전 비교 (SPEC-12 §4). 접근 확인(프로젝트 → 문서 → 두 버전 모두 같은 문서) 뒤 SPEC-10 추출 결과로 결정론적 Diff를 계산한다.
 * 버전은 수정되지 않으므로 결과를 버전 조합별로 메모리에 잠시 보관해 재사용한다(최근 {@value #CACHE_SIZE}개). 추출·비교 중에는
 * DB 트랜잭션을 잡지 않는다.
 */
@Service
public class VersionCompareService {

	static final int CACHE_SIZE = 16;

	private final ProjectService projectService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository versionRepository;

	private final DocumentTextService textService;

	private final TransactionTemplate readOnly;

	private final Map<String, CompareResult> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, CompareResult> eldest) {
			return size() > CACHE_SIZE;
		}
	};

	public VersionCompareService(ProjectService projectService, DocumentRepository documentRepository,
			DocumentVersionRepository versionRepository, DocumentTextService textService,
			PlatformTransactionManager transactionManager) {
		this.projectService = projectService;
		this.documentRepository = documentRepository;
		this.versionRepository = versionRepository;
		this.textService = textService;
		this.readOnly = new TransactionTemplate(transactionManager);
		this.readOnly.setReadOnly(true);
	}

	private record Pair(DocumentVersion base, DocumentVersion target) {
	}

	public CompareResponse compare(Long projectId, Long documentId, int baseNumber, int targetNumber, Long userId) {
		if (baseNumber == targetNumber) {
			throw new BusinessException(ErrorCode.COMPARE_SAME_VERSION);
		}
		Pair pair = this.readOnly.execute((status) -> {
			this.projectService.getAccessibleProject(projectId, userId);
			if (this.documentRepository.findByIdAndProjectId(documentId, projectId).isEmpty()) {
				throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
			}
			return new Pair(version(documentId, baseNumber), version(documentId, targetNumber));
		});
		if (!isPdf(pair.base()) || !isPdf(pair.target())) {
			throw new BusinessException(ErrorCode.COMPARE_UNSUPPORTED_TYPE);
		}
		VersionText base = text(pair.base(), "base");
		VersionText target = text(pair.target(), "target");
		CompareResult result = compareTexts(pair.base().getId(), base.text(), pair.target().getId(), target.text());
		return new CompareResponse(info(pair.base(), base.text()), info(pair.target(), target.text()), result);
	}

	/**
	 * 권한 확인 없이 버전 ID로 비교한다(SPEC-14 작업자 전용: 작업 생성 시 이미 확인함). 텍스트가 없으면
	 * {@link CompareTextUnavailableException}.
	 */
	public CompareResult compareVersions(Long baseVersionId, Long targetVersionId) {
		VersionText base = this.textService.getOrExtract(baseVersionId);
		if (!base.hasText()) {
			throw new CompareTextUnavailableException("base", base.status());
		}
		VersionText target = this.textService.getOrExtract(targetVersionId);
		if (!target.hasText()) {
			throw new CompareTextUnavailableException("target", target.status());
		}
		return compareTexts(baseVersionId, base.text(), targetVersionId, target.text());
	}

	private CompareResult compareTexts(Long baseId, ExtractedText base, Long targetId, ExtractedText target) {
		String key = baseId + ":" + targetId + ":" + DocumentTextService.EXTRACTOR_VERSION;
		synchronized (this.cache) {
			CompareResult cached = this.cache.get(key);
			if (cached != null) {
				return cached;
			}
		}
		CompareResult result = VersionDiffer.compare(base, target);
		synchronized (this.cache) {
			this.cache.put(key, result);
		}
		return result;
	}

	private DocumentVersion version(Long documentId, int number) {
		return this.versionRepository.findByDocumentIdAndVersionNumber(documentId, number)
			.orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_VERSION_NOT_FOUND));
	}

	private VersionText text(DocumentVersion version, String side) {
		VersionText text = this.textService.getOrExtract(version.getId());
		if (!text.hasText()) {
			throw new CompareTextUnavailableException(side, text.status());
		}
		return text;
	}

	private static boolean isPdf(DocumentVersion version) {
		return DocumentFileType.fromContentType(version.getContentType()).orElse(null) == DocumentFileType.PDF;
	}

	private static CompareResponse.VersionInfo info(DocumentVersion version, ExtractedText text) {
		return new CompareResponse.VersionInfo(version.getVersionNumber(), version.getOriginalFilename(),
				text.pageCount(), text.pages()
					.stream()
					.map((p) -> new CompareResponse.Page(p.width(), p.height(), p.rotation()))
					.toList());
	}

}
