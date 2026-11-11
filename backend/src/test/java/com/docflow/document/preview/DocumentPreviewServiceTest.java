package com.docflow.document.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import com.docflow.document.preview.PreviewCacheTest.MutableClock;
import com.docflow.document.service.DocumentVersionQueryService;
import com.docflow.document.service.VersionFileInfo;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/**
 * SPEC-05 §8.2·§8.5 미리보기 작업 (테스트 대역 변환기): PDF 원본 통과, single-flight, 동시 실행·대기열 제한, 결과 검증, 실패 기록과
 * 재시도 간격, 엔진 없음, 작업 디렉터리 정리와 재시작 복구 (AC-VDL-10, 13, 14, 16).
 */
class DocumentPreviewServiceTest {

	private static final byte[] PDF = "%PDF-1.4\nfake\n%%EOF\n".getBytes(StandardCharsets.US_ASCII);

	@TempDir
	private Path root;

	private final DocumentVersionQueryService queryService = mock(DocumentVersionQueryService.class);

	private final MutableClock clock = new MutableClock(Instant.parse("2026-11-04T00:00:00Z"));

	private FakeConverter converter;

	private DocumentPreviewService service;

	@BeforeEach
	void setUp() throws IOException {
		this.converter = new FakeConverter();
		given(this.queryService.openStored(any())).willAnswer((invocation) -> new ByteArrayInputStream(new byte[] { 1 }));
	}

	@AfterEach
	void tearDown() {
		if (this.service != null) {
			this.service.shutdown();
		}
		this.converter.release.countDown();
	}

	@Test
	void pdfOriginalIsReturnedWithoutConversion() throws Exception {
		this.service = service(properties(1, 10));
		VersionFileInfo info = info(1, DocumentFileType.PDF);
		given(this.queryService.openStored(info)).willReturn(new ByteArrayInputStream(PDF));

		DocumentPreviewService.PreviewResult result = this.service.preview(1L, 1L, 1, 1L);

		assertThat(result).isInstanceOf(DocumentPreviewService.Ready.class);
		assertThat(((DocumentPreviewService.Ready) result).file().content().readAllBytes()).isEqualTo(PDF);
		assertThat(this.converter.calls.get()).isZero();
	}

	@Test
	void officeIsConvertedOnceAndThenServedFromCache() throws Exception {
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.DOCX);
		this.converter.release.countDown();

		assertThat(this.service.preview(1L, 1L, 1, 1L)).isEqualTo(DocumentPreviewService.Pending.INSTANCE);
		DocumentPreviewService.Ready ready = awaitReady(1);

		assertThat(ready.file().downloadFilename()).isEqualTo("report.pdf");
		assertThat(ready.file().contentType()).isEqualTo("application/pdf");
		try (InputStream in = ready.file().content()) {
			assertThat(in.readAllBytes()).isEqualTo(PDF);
		}
		assertThat(this.service.preview(1L, 1L, 1, 1L)).isInstanceOf(DocumentPreviewService.Ready.class);
		assertThat(this.converter.calls.get()).isEqualTo(1);
		assertThat(workDirs()).isEmpty();
	}

	// SPEC-05 §18.7: 결과(캐시·실패)는 작업 디렉터리를 지운 뒤에 공개된다. 결과를 본 즉시 작업 루트가 비어 있어야 한다.
	@Test
	void resultIsVisibleOnlyAfterWorkDirectoryIsRemoved() throws Exception {
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.DOCX);
		info(2, DocumentFileType.DOCX);
		this.converter.release.countDown();

		this.service.preview(1L, 1L, 1, 1L);
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (!(this.service.preview(1L, 1L, 1, 1L) instanceof DocumentPreviewService.Ready)) {
			assertThat(System.nanoTime()).isLessThan(deadline);
			Thread.onSpinWait();
		}
		assertThat(workDirs()).isEmpty();

		this.converter.failure = new PreviewConversionException(PreviewConversionException.Kind.FAILED, "no output");
		this.service.preview(1L, 1L, 2, 1L);
		while (true) {
			try {
				this.service.preview(1L, 1L, 2, 1L);
				assertThat(System.nanoTime()).isLessThan(deadline);
				Thread.onSpinWait();
			}
			catch (BusinessException ex) {
				break;
			}
		}
		assertThat(workDirs()).isEmpty();
	}

	@Test
	void concurrentRequestsForSameVersionShareOneConversion() throws Exception {
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.XLSX);

		for (int i = 0; i < 5; i++) {
			assertThat(this.service.preview(1L, 1L, 1, 1L)).isEqualTo(DocumentPreviewService.Pending.INSTANCE);
		}
		this.converter.release.countDown();
		awaitReady(1);

		assertThat(this.converter.calls.get()).isEqualTo(1);
	}

	@Test
	void queueOverflowIsUnavailableAndRunsOneConversionAtATime() throws Exception {
		this.service = service(properties(1, 1));
		info(1, DocumentFileType.DOCX);
		info(2, DocumentFileType.DOCX);
		info(3, DocumentFileType.DOCX);

		this.service.preview(1L, 1L, 1, 1L); // 실행 중
		assertThat(this.converter.started.await(5, TimeUnit.SECONDS)).isTrue();
		this.service.preview(1L, 1L, 2, 1L); // 대기열 1칸

		assertThatThrownBy(() -> this.service.preview(1L, 1L, 3, 1L)).isInstanceOf(PreviewBusyException.class)
			.satisfies((ex) -> assertThat(((BusinessException) ex).errorCode())
				.isEqualTo(ErrorCode.DOCUMENT_PREVIEW_UNAVAILABLE));
		assertThat(this.converter.maxConcurrent.get()).isEqualTo(1);
		this.converter.release.countDown();
		awaitReady(1);
		awaitReady(2);
		assertThat(this.converter.maxConcurrent.get()).isEqualTo(1);
	}

	@Test
	void permanentFailureIsReportedUntilRestartAndWorkDirIsCleaned() throws Exception {
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.DOCX);
		this.converter.failure = new PreviewConversionException(PreviewConversionException.Kind.FAILED, "no output");
		this.converter.release.countDown();

		this.service.preview(1L, 1L, 1, 1L);
		awaitFailure(1);
		this.clock.advance(Duration.ofDays(1));

		assertThatThrownBy(() -> this.service.preview(1L, 1L, 1, 1L)).isInstanceOfSatisfying(BusinessException.class,
				(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_PREVIEW_FAILED));
		assertThat(this.converter.calls.get()).isEqualTo(1);
		assertThat(workDirs()).isEmpty();
	}

	@Test
	void timeoutCanBeRetriedAfterConfiguredInterval() throws Exception {
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.PPTX);
		this.converter.failure = new PreviewConversionException(PreviewConversionException.Kind.TIMEOUT, "timed out");
		this.converter.release.countDown();

		this.service.preview(1L, 1L, 1, 1L);
		awaitFailure(1);
		this.clock.advance(Duration.ofMinutes(9));
		assertThatThrownBy(() -> this.service.preview(1L, 1L, 1, 1L)).isInstanceOf(BusinessException.class);

		this.clock.advance(Duration.ofMinutes(2));
		this.converter.failure = null;
		assertThat(this.service.preview(1L, 1L, 1, 1L)).isEqualTo(DocumentPreviewService.Pending.INSTANCE);
		awaitReady(1);
		assertThat(this.converter.calls.get()).isEqualTo(2);
	}

	@Test
	void outputThatIsNotPdfOrTooLargeIsRejected() throws Exception {
		this.service = service(new PreviewProperties(Duration.ofSeconds(5), 1, 10, DataSize.ofBytes(10), null, List.of(),
				Duration.ofMinutes(10), null));
		info(1, DocumentFileType.DOCX);
		info(2, DocumentFileType.DOCX);
		this.converter.release.countDown();

		this.converter.output = "not a pdf".getBytes(StandardCharsets.US_ASCII);
		this.service.preview(1L, 1L, 1, 1L);
		awaitFailure(1);
		this.converter.output = PDF; // 10바이트 상한 초과
		this.service.preview(1L, 1L, 2, 1L);
		awaitFailure(2);

		assertThat(this.service.cache().totalBytes()).isZero();
	}

	@Test
	void unavailableEngineIsReportedWithoutCreatingAJob() throws Exception {
		this.converter.status = DocumentPreviewConverter.EngineStatus.unavailable("not installed");
		this.service = service(properties(1, 10));
		info(1, DocumentFileType.DOCX);

		assertThatThrownBy(() -> this.service.preview(1L, 1L, 1, 1L)).isInstanceOfSatisfying(BusinessException.class,
				(ex) -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_PREVIEW_UNAVAILABLE));
		assertThat(this.converter.calls.get()).isZero();
	}

	@Test
	void startupRemovesLeftoverWorkDirectoriesAndKeepsCache() throws Exception {
		Path leftover = Files.createDirectories(this.root.resolve("previews/work/stale-job/profile"));
		Files.writeString(leftover.resolve("x"), "x");
		Path cached = Files.createDirectories(this.root.resolve("previews/cache"))
			.resolve(PreviewCache.fileName("documents/kept"));
		Files.write(cached, PDF);

		this.service = service(properties(1, 10));

		assertThat(this.root.resolve("previews/work/stale-job")).doesNotExist();
		assertThat(this.service.cache().contains("documents/kept")).isTrue();
	}

	private DocumentPreviewService.Ready awaitReady(int versionNumber) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			DocumentPreviewService.PreviewResult result = this.service.preview(1L, 1L, versionNumber, 1L);
			if (result instanceof DocumentPreviewService.Ready ready) {
				return ready;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("preview not ready");
	}

	private void awaitFailure(int versionNumber) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (System.nanoTime() < deadline) {
			try {
				this.service.preview(1L, 1L, versionNumber, 1L);
			}
			catch (BusinessException ex) {
				assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOCUMENT_PREVIEW_FAILED);
				assertThat(workDirs()).isEmpty();
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("preview did not fail");
	}

	private List<Path> workDirs() throws IOException {
		try (Stream<Path> dirs = Files.list(this.root.resolve("previews/work"))) {
			return dirs.toList();
		}
	}

	private VersionFileInfo info(int versionNumber, DocumentFileType type) {
		VersionFileInfo info = new VersionFileInfo(1L, 1L, versionNumber, "report." + type.extension(),
				type.contentType(), 1, "documents/key-" + versionNumber, type);
		given(this.queryService.resolve(1L, 1L, versionNumber, 1L)).willReturn(info);
		return info;
	}

	private DocumentPreviewService service(PreviewProperties properties) throws IOException {
		return new DocumentPreviewService(this.queryService, this.converter, properties, this.root, this.clock);
	}

	private static PreviewProperties properties(int concurrency, int queue) {
		return new PreviewProperties(Duration.ofSeconds(5), concurrency, queue, null, null, List.of(),
				Duration.ofMinutes(10), null);
	}

	/** 테스트 대역: release 전까지 변환을 막고, 동시 실행 수를 기록한다. */
	static final class FakeConverter implements DocumentPreviewConverter {

		final CountDownLatch release = new CountDownLatch(1);

		final CountDownLatch started = new CountDownLatch(1);

		final AtomicInteger calls = new AtomicInteger();

		final AtomicInteger running = new AtomicInteger();

		final AtomicInteger maxConcurrent = new AtomicInteger();

		volatile EngineStatus status = EngineStatus.ok();

		volatile PreviewConversionException failure;

		volatile byte[] output = PDF;

		@Override
		public EngineStatus status() {
			return this.status;
		}

		@Override
		public Path convertToPdf(Path input, DocumentFileType type, Path workDir, Duration timeout)
				throws PreviewConversionException {
			this.calls.incrementAndGet();
			this.maxConcurrent.accumulateAndGet(this.running.incrementAndGet(), Math::max);
			this.started.countDown();
			try {
				assertThat(input.getFileName().toString()).isEqualTo("input." + type.extension());
				assertThat(input.startsWith(workDir)).isTrue();
				if (!this.release.await(10, TimeUnit.SECONDS)) {
					throw new PreviewConversionException(PreviewConversionException.Kind.TIMEOUT, "test timeout");
				}
				if (this.failure != null) {
					throw this.failure;
				}
				// Windows의 LibreOffice처럼 작업 디렉터리 안에 읽기 전용 알려진 폴더를 남긴다.
				Path documents = Files.createDirectories(workDir.resolve("Documents"));
				PreviewWorkspaceTest.markReadOnly(documents);
				Path out = Files.createDirectories(workDir.resolve("out")).resolve("input.pdf");
				Files.write(out, this.output);
				return out;
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "interrupted");
			}
			catch (IOException ex) {
				throw new PreviewConversionException(PreviewConversionException.Kind.ENGINE, "io", ex);
			}
			finally {
				this.running.decrementAndGet();
			}
		}

	}

}
