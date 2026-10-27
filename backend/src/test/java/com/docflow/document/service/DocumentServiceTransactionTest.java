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
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.docflow.document.DocumentTestFiles;
import com.docflow.document.domain.Document;
import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.dto.CreateDocumentRequest;
import com.docflow.document.repository.DocumentRepository;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.storage.LocalFileStorage;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.project.domain.Project;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-03 §7.2 트랜잭션 결과별 파일 보상 (AC-DOC-12, 13, 14). 실제 Spring 트랜잭션 동기화 흐름
 * ({@link AbstractPlatformTransactionManager})에 시작·커밋·롤백 실패를 주입한다. 실제 DB를 쓰는 시나리오는
 * {@code DocumentStorageIntegrationTest}에서 확인한다.
 */
class DocumentServiceTransactionTest {

	@TempDir
	private Path root;

	private final FakeTransactionManager transactionManager = new FakeTransactionManager();

	private final DocumentRepository documentRepository = mock(DocumentRepository.class);

	private final DocumentVersionRepository versionRepository = mock(DocumentVersionRepository.class);

	private LocalFileStorage fileStorage;

	private DocumentService service;

	@BeforeEach
	void setUp() {
		User user = User.create("a@test.local", "hash", "Author");
		ReflectionTestUtils.setField(user, "id", 1L);
		Project project = Project.create("P", null, user);
		ProjectService projectService = mock(ProjectService.class);
		given(projectService.getAccessibleProject(10L, 1L)).willReturn(project);
		UserAccountService userAccountService = mock(UserAccountService.class);
		given(userAccountService.getReference(1L)).willReturn(user);
		given(this.documentRepository.save(any(Document.class))).willAnswer((invocation) -> {
			Document document = invocation.getArgument(0);
			ReflectionTestUtils.setField(document, "id", 100L);
			return document;
		});
		given(this.versionRepository.save(any(DocumentVersion.class))).willAnswer((invocation) -> invocation.getArgument(0));
		this.fileStorage = spy(new LocalFileStorage(this.root.toString(), () -> "documents/fixed"));
		this.service = new DocumentService(projectService, new ProjectPermissionService(), userAccountService,
				this.documentRepository, this.versionRepository, this.fileStorage, new DocumentFileValidator(),
				this.transactionManager);
	}

	@Test
	void commitKeepsFileAndRemovesTemp() throws IOException {
		var response = this.service.create(10L, 1L, request());

		assertThat(response.id()).isEqualTo(100L);
		assertThat(response.latestVersion().versionNumber()).isEqualTo(1);
		assertThat(storedFiles()).containsExactly("fixed");
		assertThat(tempFiles()).isEmpty();
	}

	// AC-DOC-12 A(단위): INSERT 실패 → 롤백 확정 → 이 요청이 만든 파일 삭제
	@Test
	void rollbackDeletesFileCreatedByThisRequest() throws IOException {
		given(this.versionRepository.save(any(DocumentVersion.class)))
			.willThrow(new DataIntegrityViolationException("uk_document_versions_storage_key"));

		assertThatThrownBy(() -> this.service.create(10L, 1L, request()))
			.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(this.transactionManager.rolledBack).isTrue();
		assertThat(storedFiles()).isEmpty();
		assertThat(tempFiles()).isEmpty();
	}

	// AC-DOC-13: 커밋 결과 불명확 → 파일 유지
	@Test
	void commitOutcomeUnknownKeepsFile() throws IOException {
		this.transactionManager.commitFailure = new TransactionSystemException("connection lost during commit");

		assertThatThrownBy(() -> this.service.create(10L, 1L, request()))
			.isInstanceOf(TransactionSystemException.class);

		assertThat(storedFiles()).containsExactly("fixed");
		verify(this.fileStorage, never()).delete(anyString());
	}

	// 롤백 자체가 실패하면 결과를 알 수 없으므로 파일 유지
	@Test
	void rollbackFailureKeepsFile() throws IOException {
		given(this.versionRepository.save(any(DocumentVersion.class)))
			.willThrow(new DataIntegrityViolationException("violation"));
		this.transactionManager.rollbackFailure = new TransactionSystemException("rollback failed");

		assertThatThrownBy(() -> this.service.create(10L, 1L, request())).isInstanceOf(RuntimeException.class);

		assertThat(storedFiles()).containsExactly("fixed");
		verify(this.fileStorage, never()).delete(anyString());
	}

	// 트랜잭션 시작 실패(연결 획득 실패) → DB에 쓴 것이 없으므로 이 요청이 만든 파일 삭제
	@Test
	void transactionBeginFailureDeletesFile() throws IOException {
		this.transactionManager.beginFailure = new CannotCreateTransactionException("no connection");

		assertThatThrownBy(() -> this.service.create(10L, 1L, request()))
			.isInstanceOf(CannotCreateTransactionException.class);

		assertThat(storedFiles()).isEmpty();
	}

	// AC-DOC-14: 보상 삭제 실패는 원래 오류를 덮지 않는다
	@Test
	void compensationFailureKeepsOriginalError() throws IOException {
		DataIntegrityViolationException original = new DataIntegrityViolationException("original");
		given(this.versionRepository.save(any(DocumentVersion.class))).willThrow(original);
		willThrow(new IOException("disk gone")).given(this.fileStorage).delete(anyString());

		assertThatThrownBy(() -> this.service.create(10L, 1L, request())).isSameAs(original);

		assertThat(storedFiles()).containsExactly("fixed");
	}

	@Test
	void compensationFailureBeforeTransactionIsSuppressedIntoOriginal() throws IOException {
		CannotCreateTransactionException original = new CannotCreateTransactionException("no connection");
		this.transactionManager.beginFailure = original;
		willThrow(new IOException("disk gone")).given(this.fileStorage).delete(anyString());

		assertThatThrownBy(() -> this.service.create(10L, 1L, request())).isSameAs(original);

		assertThat(original.getSuppressed()).hasSize(1);
	}

	private static CreateDocumentRequest request() {
		return new CreateDocumentRequest("문서", null,
				new MockMultipartFile("file", "a.pdf", "application/pdf", DocumentTestFiles.pdf()));
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

	static class FakeTransactionManager extends AbstractPlatformTransactionManager {

		RuntimeException beginFailure;

		RuntimeException commitFailure;

		RuntimeException rollbackFailure;

		boolean rolledBack;

		@Override
		protected Object doGetTransaction() {
			return new Object();
		}

		@Override
		protected void doBegin(Object transaction, TransactionDefinition definition) {
			if (this.beginFailure != null) {
				throw this.beginFailure;
			}
		}

		@Override
		protected void doCommit(DefaultTransactionStatus status) {
			if (this.commitFailure != null) {
				throw this.commitFailure;
			}
		}

		@Override
		protected void doRollback(DefaultTransactionStatus status) {
			this.rolledBack = true;
			if (this.rollbackFailure != null) {
				throw this.rollbackFailure;
			}
		}

	}

}
