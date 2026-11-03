package com.docflow.document.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

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
import com.docflow.document.validation.DocumentFileType;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.document.validation.InvalidDocumentFileException;
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

	/** 20MiB (SPEC-03 §5.6, 결정 Q5). */
	public static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	static final String EMPTY_FILE = "빈 파일은 업로드할 수 없습니다.";

	private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

	private static final int BUFFER_SIZE = 8192;

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final UserAccountService userAccountService;

	private final DocumentRepository documentRepository;

	private final DocumentVersionRepository documentVersionRepository;

	private final FileStorage fileStorage;

	private final DocumentFileValidator documentFileValidator;

	private final TransactionTemplate transactionTemplate;

	public DocumentService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			UserAccountService userAccountService, DocumentRepository documentRepository,
			DocumentVersionRepository documentVersionRepository, FileStorage fileStorage,
			DocumentFileValidator documentFileValidator, PlatformTransactionManager transactionManager) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.userAccountService = userAccountService;
		this.documentRepository = documentRepository;
		this.documentVersionRepository = documentVersionRepository;
		this.fileStorage = fileStorage;
		this.documentFileValidator = documentFileValidator;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 문서와 v1을 등록한다 (SPEC-03 §7.2).
	 * <p>
	 * 이 메서드에는 트랜잭션을 두지 않는다. 파일을 최종 위치에 배치(복사·flush·close 완료)한 뒤에만
	 * {@link TransactionTemplate}으로 DB 트랜잭션을 시작해 Document와 v1을 함께 저장한다. 롤백이 확정되면 이 요청이
	 * 생성한 파일을 삭제하고, 커밋 결과가 불명확하면 파일을 남긴다 ({@link StoredFileCompensation}).
	 */
	public DocumentResponse create(Long projectId, Long sessionUserId, CreateDocumentRequest request) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireDocumentCreator(project, sessionUserId);
		DocumentFileName fileName = DocumentFileName.of(request.file().getOriginalFilename());

		StoredDocumentFile storedFile = storeValidatedFile(request.file(), fileName);

		boolean[] compensationRegistered = { false };
		try {
			DocumentResponse response = this.transactionTemplate.execute((status) -> {
				TransactionSynchronizationManager
					.registerSynchronization(new StoredFileCompensation(this.fileStorage, storedFile.storageKey()));
				compensationRegistered[0] = true;
				User user = this.userAccountService.getReference(sessionUserId);
				Document document = this.documentRepository
					.save(Document.create(project, request.title(), request.description(), user));
				DocumentVersion version = this.documentVersionRepository
					.save(DocumentVersion.first(document, storedFile, user));
				return DocumentResponse.from(document, version);
			});
			log.info("Document created: projectId={}, documentId={}, storageKey={}", projectId, response.id(),
					storedFile.storageKey());
			return response;
		}
		catch (RuntimeException | Error ex) {
			// 트랜잭션 시작(연결 획득) 전 실패는 DB에 아무것도 쓰지 않았으므로 여기서 직접 정리한다.
			if (!compensationRegistered[0]) {
				StoredFileCompensation.deleteOwnedFile(this.fileStorage, storedFile.storageKey(), ex);
			}
			throw ex;
		}
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

	/**
	 * 업로드 스트림을 임시 파일로 복사하며 크기·SHA-256을 계산하고, 형식을 검증한 뒤 최종 위치에 배치한다. 임시 파일은
	 * 성공·실패와 관계없이 삭제를 시도한다. 반환 시점에 최종 파일은 이 요청이 생성한 것이며 아직 DB가 참조하지 않는다.
	 */
	private StoredDocumentFile storeValidatedFile(MultipartFile file, DocumentFileName fileName) {
		Path tempFile;
		try {
			tempFile = this.fileStorage.createTempFile();
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to create upload temp file", ex);
		}
		try {
			CopyResult copy = copyToTemp(file, tempFile);
			DocumentFileType type = fileName.type();
			this.documentFileValidator.validate(tempFile, type);
			String storageKey = this.fileStorage.store(tempFile);
			return new StoredDocumentFile(fileName.value(), type.contentType(), copy.sizeBytes(), storageKey,
					copy.sha256());
		}
		catch (IOException ex) {
			throw new UncheckedIOException("Failed to store uploaded file", ex);
		}
		finally {
			try {
				this.fileStorage.deleteTemp(tempFile);
			}
			catch (IOException | RuntimeException ex) {
				log.warn("Failed to delete upload temp file", ex);
			}
		}
	}

	private static CopyResult copyToTemp(MultipartFile file, Path tempFile) throws IOException {
		MessageDigest digest = sha256();
		long total = 0;
		byte[] buffer = new byte[BUFFER_SIZE];
		try (InputStream in = file.getInputStream();
				OutputStream out = Files.newOutputStream(tempFile, StandardOpenOption.WRITE,
						StandardOpenOption.TRUNCATE_EXISTING)) {
			int read;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > MAX_FILE_BYTES) {
					throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
				}
				digest.update(buffer, 0, read);
				out.write(buffer, 0, read);
			}
		}
		if (total == 0) {
			throw new InvalidDocumentFileException(EMPTY_FILE);
		}
		return new CopyResult(total, HexFormat.of().formatHex(digest.digest()));
	}

	private static MessageDigest sha256() {
		try {
			return MessageDigest.getInstance("SHA-256");
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record CopyResult(long sizeBytes, String sha256) {
	}

}
