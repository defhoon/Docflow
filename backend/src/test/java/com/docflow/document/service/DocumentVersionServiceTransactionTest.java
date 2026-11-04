package com.docflow.document.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionSystemException;

import com.docflow.document.DocumentTestFiles;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentStatus;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.service.DocumentServiceTransactionTest.FakeTransactionManager;
import com.docflow.document.storage.LocalFileStorage;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-04 §7.3 새 버전 등록의 트랜잭션 결과별 파일 보상 (AC-VER-05·06·11, 테스트 대역). 실제 Spring 트랜잭션 동기화 흐름에
 * 시작·커밋·롤백 실패를 주입한다. 실제 PostgreSQL 시나리오는 {@code DocumentVersionStorageIntegrationTest}·
 * {@code DocumentVersionConcurrencyIntegrationTest}에서 확인한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class DocumentVersionServiceTransactionTest {

	private static final Long PROJECT_ID = 10L;

	private static final Long DOCUMENT_ID = 100L;

	private static final Long USER_ID = 1L;

	@TempDir
	private Path root;

	private final FakeTransactionManager transactionManager = new FakeTransactionManager();

	private final DocumentRepository documentRepository = mock(DocumentRepository.class);

	private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);

	private LocalFileStorage fileStorage;

	private DocumentVersionService service;

	private User user;

	private Project project;

	@BeforeEach
	void setUp() {
		this.user = User.create("a@test.local", "hash", "Author");
		ReflectionTestUtils.setField(this.user, "id", USER_ID);
		this.project = Project.create("P", null, this.user);
		ProjectService projectService = mock(ProjectService.class);
		given(projectService.getAccessibleProject(PROJECT_ID, USER_ID)).willReturn(this.project);
		UserAccountService userAccountService = mock(UserAccountService.class);
		given(userAccountService.getReference(USER_ID)).willReturn(this.user);
		given(this.documentRepository.findByIdAndProjectId(DOCUMENT_ID, PROJECT_ID))
			.willReturn(Optional.of(document(DocumentStatus.DRAFT)));
		given(this.documentRepository.findByIdAndProjectIdForUpdate(DOCUMENT_ID, PROJECT_ID))
			.willReturn(Optional.of(document(DocumentStatus.DRAFT)));
		given(this.versionRepository.findMaxVersionNumber(DOCUMENT_ID)).willReturn(1);
		given(this.versionRepository.save(any(DocumentVersion.class))).willAnswer((invocation) -> invocation.getArgument(0));
		this.fileStorage = spy(new LocalFileStorage(this.root.toString(), () -> "documents/fixed"));
		this.service = new DocumentVersionService(projectService, new ProjectPermissionService(), userAccountService,
				this.documentRepository, this.versionRepository, this.fileStorage, new DocumentFileValidator(),
				this.transactionManager);
	}

	@Test
	void commitAssignsMaxPlusOneKeepsFileAndRemovesTemp() throws IOException {
		var response = this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file());

		assertThat(response.id()).isEqualTo(DOCUMENT_ID);
		assertThat(response.latestVersion().versionNumber()).isEqualTo(2);
		assertThat(response.status()).isEqualTo(DocumentStatus.DRAFT);
		assertThat(storedFiles()).containsExactly("fixed");
		assertThat(tempFiles()).isEmpty();
		verify(this.documentRepository).flush();
	}

	// AC-VER-05: 사전 확인에서 거부되면 파일을 쓰지 않는다
	@Test
	void statusRejectedBeforeUploadStoresNothing() throws IOException {
		given(this.documentRepository.findByIdAndProjectId(DOCUMENT_ID, PROJECT_ID))
			.willReturn(Optional.of(document(DocumentStatus.IN_REVIEW)));

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOfSatisfying(BusinessException.class,
					(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_STATUS_CONFLICT));

		verify(this.fileStorage, never()).createTempFile();
		assertThat(storedFiles()).isEmpty();
	}

	// AC-VER-06: 잠금 후 재검증에서 거부되면 롤백 확정 후 이 요청이 만든 파일을 삭제한다
	@Test
	void statusRejectedAfterLockRollsBackAndDeletesFile() throws IOException {
		given(this.documentRepository.findByIdAndProjectIdForUpdate(DOCUMENT_ID, PROJECT_ID))
			.willReturn(Optional.of(document(DocumentStatus.APPROVED)));

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOfSatisfying(BusinessException.class,
					(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_STATUS_CONFLICT));

		assertThat(this.transactionManager.rolledBack).isTrue();
		verify(this.versionRepository, never()).save(any(DocumentVersion.class));
		assertThat(storedFiles()).isEmpty();
		assertThat(tempFiles()).isEmpty();
	}

	@Test
	void documentMissingAfterLockRollsBackAndDeletesFile() throws IOException {
		given(this.documentRepository.findByIdAndProjectIdForUpdate(DOCUMENT_ID, PROJECT_ID))
			.willReturn(Optional.empty());

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOfSatisfying(BusinessException.class,
					(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_NOT_FOUND));

		assertThat(storedFiles()).isEmpty();
	}

	// AC-VER-09(단위): INSERT 실패 → 롤백 확정 → 이 요청이 만든 파일 삭제
	@Test
	void insertFailureRollsBackAndDeletesFile() throws IOException {
		given(this.versionRepository.save(any(DocumentVersion.class)))
			.willThrow(new DataIntegrityViolationException("uk_document_versions_storage_key"));

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(this.transactionManager.rolledBack).isTrue();
		assertThat(storedFiles()).isEmpty();
	}

	// AC-VER-10(단위): 버전 번호 UNIQUE 위반만 원인 로그를 남긴다. 원본 파일명은 남기지 않는다
	@Test
	void versionNumberConflictLogsCauseAndRethrows(CapturedOutput output) throws IOException {
		DataIntegrityViolationException violation = new DataIntegrityViolationException("duplicate",
				new ConstraintViolationException("duplicate key", new SQLException("23505"),
						DocumentVersionService.VERSION_NUMBER_CONSTRAINT));
		given(this.versionRepository.save(any(DocumentVersion.class))).willThrow(violation);

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file())).isSameAs(violation);

		assertThat(output.getOut()).contains("Unexpected version number conflict: projectId=10, documentId=100, "
				+ "versionNumber=2, constraint=uk_document_versions_document_version, storageKey=documents/fixed")
			.doesNotContain("secret-name");
		assertThat(storedFiles()).isEmpty();
	}

	@Test
	void otherConstraintViolationDoesNotLogVersionConflict(CapturedOutput output) {
		given(this.versionRepository.save(any(DocumentVersion.class)))
			.willThrow(new DataIntegrityViolationException("other", new ConstraintViolationException("dup",
					new SQLException("23505"), "uk_document_versions_storage_key")));

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(output.getOut()).doesNotContain("Unexpected version number conflict");
	}

	// AC-VER-11: 커밋 결과 불명확 → 파일 유지
	@Test
	void commitOutcomeUnknownKeepsFile() throws IOException {
		this.transactionManager.commitFailure = new TransactionSystemException("connection lost during commit");

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOf(TransactionSystemException.class);

		assertThat(storedFiles()).containsExactly("fixed");
		verify(this.fileStorage, never()).delete(anyString());
	}

	@Test
	void rollbackFailureKeepsFile() throws IOException {
		given(this.versionRepository.save(any(DocumentVersion.class)))
			.willThrow(new DataIntegrityViolationException("violation"));
		this.transactionManager.rollbackFailure = new TransactionSystemException("rollback failed");

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOf(RuntimeException.class);

		assertThat(storedFiles()).containsExactly("fixed");
		verify(this.fileStorage, never()).delete(anyString());
	}

	@Test
	void transactionBeginFailureDeletesFile() {
		this.transactionManager.beginFailure = new CannotCreateTransactionException("no connection");

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file()))
			.isInstanceOf(CannotCreateTransactionException.class);

		assertThat(this.root.resolve("documents/fixed")).doesNotExist();
	}

	// AC-VER-11: 보상 삭제 실패는 원래 오류를 덮지 않는다
	@Test
	void compensationFailureKeepsOriginalError() throws IOException {
		DataIntegrityViolationException original = new DataIntegrityViolationException("original");
		given(this.versionRepository.save(any(DocumentVersion.class))).willThrow(original);
		willThrow(new IOException("disk gone")).given(this.fileStorage).delete(anyString());

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file())).isSameAs(original);

		assertThat(storedFiles()).containsExactly("fixed");
	}

	@Test
	void compensationFailureBeforeTransactionIsSuppressedIntoOriginal() throws IOException {
		CannotCreateTransactionException original = new CannotCreateTransactionException("no connection");
		this.transactionManager.beginFailure = original;
		willThrow(new IOException("disk gone")).given(this.fileStorage).delete(anyString());

		assertThatThrownBy(() -> this.service.create(PROJECT_ID, DOCUMENT_ID, USER_ID, file())).isSameAs(original);

		assertThat(original.getSuppressed()).hasSize(1);
	}

	private Document document(DocumentStatus status) {
		Document document = Document.create(this.project, "문서", null, this.user);
		ReflectionTestUtils.setField(document, "id", DOCUMENT_ID);
		ReflectionTestUtils.setField(document, "status", status);
		return document;
	}

	private static MockMultipartFile file() {
		return new MockMultipartFile("file", "secret-name.pdf", "application/pdf", DocumentTestFiles.pdf());
	}

	private List<String> storedFiles() throws IOException {
		Path dir = this.root.resolve("documents");
		if (!Files.exists(dir)) {
			return List.of();
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.map((path) -> path.getFileName().toString()).toList();
		}
	}

	private List<Path> tempFiles() throws IOException {
		try (Stream<Path> files = Files.list(this.root.resolve("tmp"))) {
			return files.toList();
		}
	}

}
