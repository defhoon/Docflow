package com.docflow.document.preview;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.docflow.document.service.DocumentVersionQueryService;
import com.docflow.document.service.VersionFile;
import com.docflow.document.service.VersionFileInfo;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/**
 * 버전 미리보기 (SPEC-05 §6.5, §8). PDF 원본은 그대로, Office 문서는 최초 요청 시 비동기로 PDF로 변환해 캐시한다.
 * <ul>
 * <li>같은 버전의 작업은 하나만 만든다(single-flight).</li>
 * <li>동시 변환 {@code concurrency}개, 대기열 {@code queueCapacity}개. 대기열이 가득 차면 503.</li>
 * <li>결과는 종료 코드·파일 존재·{@code %PDF-} 헤더·크기 상한을 확인한 뒤에만 캐시에 등록한다.</li>
 * <li>작업 디렉터리({@code previews/work/<UUID>})는 성공·실패와 관계없이 지우고, 시작 시 남은 것을 지운다.</li>
 * <li>실패 기록은 메모리에 둔다. 시간 초과·엔진 오류는 일정 시간 뒤 다시 시도하고, 변환 불가는 재시작 전까지 유지한다.</li>
 * </ul>
 * 원본 파일과 DB는 바꾸지 않는다. 원본 파일명과 엔진 출력은 로그에 남기지 않는다.
 */
@Service
public class DocumentPreviewService {

	private static final Logger log = LoggerFactory.getLogger(DocumentPreviewService.class);

	private static final byte[] PDF_HEADER = "%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII);

	private final DocumentVersionQueryService queryService;

	private final DocumentPreviewConverter converter;

	private final PreviewProperties properties;

	private final PreviewCache cache;

	private final Path workRoot;

	private final Clock clock;

	private final ThreadPoolExecutor executor;

	private final ScheduledExecutorService cleaner;

	private final Map<String, Boolean> jobs = new ConcurrentHashMap<>();

	private final Map<String, Failure> failures = new ConcurrentHashMap<>();

	private final AtomicInteger conversions = new AtomicInteger();

	@Autowired
	public DocumentPreviewService(DocumentVersionQueryService queryService, DocumentPreviewConverter converter,
			PreviewProperties properties, @Value("${docflow.storage.dir}") String storageRoot) throws IOException {
		this(queryService, converter, properties, Path.of(storageRoot), Clock.systemUTC());
	}

	DocumentPreviewService(DocumentVersionQueryService queryService, DocumentPreviewConverter converter,
			PreviewProperties properties, Path storageRoot, Clock clock) throws IOException {
		this.queryService = queryService;
		this.converter = converter;
		this.properties = properties;
		this.clock = clock;
		Path previews = storageRoot.toAbsolutePath().normalize().resolve("previews");
		this.workRoot = previews.resolve("work");
		this.cache = new PreviewCache(previews.resolve("cache"), properties.cache().maxBytes().toBytes(), clock);
		// 재시작 복구: 이전 실행에서 남은 작업 디렉터리를 지우고 캐시 색인을 다시 만든다.
		Files.createDirectories(this.workRoot);
		PreviewWorkspace.deleteContents(this.workRoot);
		this.cache.rebuild();
		this.executor = new ThreadPoolExecutor(properties.concurrency(), properties.concurrency(), 0L,
				TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(Math.max(1, properties.queueCapacity())),
				namedThreads("docflow-preview-"));
		this.cleaner = Executors.newSingleThreadScheduledExecutor(namedThreads("docflow-preview-cleaner-"));
		long interval = properties.cache().cleanupInterval().toMillis();
		this.cleaner.scheduleWithFixedDelay(this::evictIdle, interval, interval, TimeUnit.MILLISECONDS);
	}

	/**
	 * 미리보기를 요청한다. 접근·문서·버전 확인을 먼저 하고, 준비된 PDF가 있으면 {@link Ready}, 변환 중이거나 방금 작업을 등록했으면
	 * {@link Pending}을 돌려준다.
	 */
	public PreviewResult preview(Long projectId, Long documentId, int versionNumber, Long sessionUserId) {
		VersionFileInfo info = this.queryService.resolve(projectId, documentId, versionNumber, sessionUserId);
		String pdfName = pdfName(info.originalFilename());
		if (info.fileType() == DocumentFileType.PDF) {
			return new Ready(
					new VersionFile(pdfName, DocumentFileType.PDF.contentType(), info.sizeBytes(), this.queryService.openStored(info)));
		}
		String key = info.storageKey();
		Optional<PreviewCache.Lease> lease = this.cache.acquire(key);
		if (lease.isPresent()) {
			return new Ready(new VersionFile(pdfName, DocumentFileType.PDF.contentType(), lease.get().sizeBytes(),
					lease.get().content()));
		}
		if (this.jobs.containsKey(key)) {
			return Pending.INSTANCE;
		}
		Failure failure = this.failures.get(key);
		if (failure != null) {
			if (!failure.kind().retryable()
					|| failure.at().plus(this.properties.retryAfterFailure()).isAfter(this.clock.instant())) {
				throw new BusinessException(ErrorCode.DOCUMENT_PREVIEW_FAILED);
			}
			this.failures.remove(key, failure);
		}
		DocumentPreviewConverter.EngineStatus status = this.converter.status();
		if (!status.available()) {
			throw new BusinessException(ErrorCode.DOCUMENT_PREVIEW_UNAVAILABLE);
		}
		// 원본이 없거나 크기가 다르면 작업을 만들지 않고 500으로 응답한다.
		closeQuietly(this.queryService.openStored(info));
		if (this.jobs.putIfAbsent(key, Boolean.TRUE) == null) {
			// 확인 사이에 다른 작업이 끝났으면 다시 변환하지 않는다.
			Optional<PreviewCache.Lease> finished = this.cache.acquire(key);
			if (finished.isPresent()) {
				this.jobs.remove(key);
				return new Ready(new VersionFile(pdfName, DocumentFileType.PDF.contentType(),
						finished.get().sizeBytes(), finished.get().content()));
			}
			try {
				this.executor.execute(() -> convert(info));
			}
			catch (RejectedExecutionException ex) {
				this.jobs.remove(key);
				log.warn("Preview queue full: projectId={}, documentId={}, versionNumber={}", info.projectId(),
						info.documentId(), info.versionNumber());
				throw new PreviewBusyException();
			}
		}
		return Pending.INSTANCE;
	}

	/** 주기적 정리: {@code cache.max-idle} 동안 사용하지 않은 캐시. */
	void evictIdle() {
		try {
			int removed = this.cache.evictIdle(this.properties.cache().maxIdle());
			if (removed > 0) {
				log.info("Evicted idle preview cache entries: {}", removed);
			}
		}
		catch (RuntimeException ex) {
			log.warn("Preview cache idle cleanup failed", ex);
		}
	}

	PreviewCache cache() {
		return this.cache;
	}

	int conversions() {
		return this.conversions.get();
	}

	Path workRoot() {
		return this.workRoot;
	}

	@PreDestroy
	void shutdown() {
		this.cleaner.shutdownNow();
		this.executor.shutdownNow();
		try {
			this.executor.awaitTermination(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
		try {
			PreviewWorkspace.deleteContents(this.workRoot);
		}
		catch (IOException ex) {
			log.warn("Failed to delete preview work directory on shutdown", ex);
		}
	}

	/**
	 * 변환 작업. 결과(캐시 등록 또는 실패 기록)는 작업 디렉터리를 지운 뒤에 공개한다. 요청 측이 결과를 확인한 시점에는 이 작업의
	 * 임시 파일과 엔진 프로세스가 남아 있지 않다(Windows에서는 열린 파일·읽기 전용 디렉터리가 삭제를 막는다, SPEC-05 §18.7).
	 */
	private void convert(VersionFileInfo info) {
		String key = info.storageKey();
		String jobId = UUID.randomUUID().toString();
		Path workDir = this.workRoot.resolve(jobId);
		// 결과 PDF는 작업 디렉터리 밖(작업 루트)에 잠시 두었다가 캐시로 옮긴다.
		Path staged = this.workRoot.resolve(jobId + ".pdf");
		long started = System.nanoTime();
		Failure failure = null;
		try {
			Files.createDirectories(workDir.resolve("in"));
			Path input = workDir.resolve("in").resolve("input." + info.fileType().extension());
			try (InputStream original = this.queryService.openStored(info)) {
				Files.copy(original, input, StandardCopyOption.REPLACE_EXISTING);
			}
			this.conversions.incrementAndGet();
			Path pdf = this.converter.convertToPdf(input, info.fileType(), workDir, this.properties.timeout());
			validate(pdf);
			Files.move(pdf, staged, StandardCopyOption.REPLACE_EXISTING);
		}
		catch (PreviewConversionException ex) {
			failure = new Failure(ex.kind(), this.clock.instant());
			log.warn("Preview conversion failed: projectId={}, documentId={}, versionNumber={}, storageKey={}, kind={}, "
					+ "reason={}, elapsedMs={}", info.projectId(), info.documentId(), info.versionNumber(), key,
					ex.kind(), ex.getMessage(), elapsedMillis(started));
		}
		catch (IOException | RuntimeException ex) {
			failure = new Failure(PreviewConversionException.Kind.ENGINE, this.clock.instant());
			log.error("Preview conversion error: projectId={}, documentId={}, versionNumber={}, storageKey={}",
					info.projectId(), info.documentId(), info.versionNumber(), key, ex);
		}
		try {
			deleteWorkDirectory(workDir);
			if (failure == null) {
				this.cache.put(key, staged);
				this.failures.remove(key);
				log.info("Preview converted: projectId={}, documentId={}, versionNumber={}, storageKey={}, elapsedMs={}",
						info.projectId(), info.documentId(), info.versionNumber(), key, elapsedMillis(started));
			}
			else {
				this.failures.put(key, failure);
			}
		}
		catch (IOException | RuntimeException ex) {
			this.failures.put(key, new Failure(PreviewConversionException.Kind.ENGINE, this.clock.instant()));
			log.error("Preview cache registration failed: projectId={}, documentId={}, versionNumber={}, storageKey={}",
					info.projectId(), info.documentId(), info.versionNumber(), key, ex);
			deleteWorkDirectory(staged);
		}
		finally {
			this.jobs.remove(key);
		}
	}

	/** 작업 디렉터리 삭제. 실패는 원인과 함께 WARN으로 남기고(다음 시작 때 다시 정리), 결과 공개는 계속한다. */
	private void deleteWorkDirectory(Path path) {
		try {
			PreviewWorkspace.deleteRecursively(this.workRoot, path);
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Failed to delete preview work directory: {}", path.getFileName(), ex);
		}
	}

	private void validate(Path pdf) throws IOException, PreviewConversionException {
		long size = Files.size(pdf);
		if (size > this.properties.maxOutputBytes().toBytes()) {
			throw new PreviewConversionException(PreviewConversionException.Kind.FAILED, "output too large: " + size);
		}
		byte[] header = new byte[PDF_HEADER.length];
		try (InputStream in = Files.newInputStream(pdf)) {
			if (in.readNBytes(header, 0, header.length) != header.length || !java.util.Arrays.equals(header, PDF_HEADER)) {
				throw new PreviewConversionException(PreviewConversionException.Kind.FAILED, "output is not a PDF");
			}
		}
	}

	/** 미리보기 PDF 파일명: 원본 확장자를 {@code .pdf}로 바꾼다. */
	static String pdfName(String originalFilename) {
		int dot = originalFilename.lastIndexOf('.');
		String base = (dot > 0) ? originalFilename.substring(0, dot) : originalFilename;
		return base + ".pdf";
	}

	private static long elapsedMillis(long started) {
		return Duration.ofNanos(System.nanoTime() - started).toMillis();
	}

	private static void closeQuietly(InputStream in) {
		try {
			in.close();
		}
		catch (IOException ex) {
			log.debug("Failed to close stream", ex);
		}
	}

	private static java.util.concurrent.ThreadFactory namedThreads(String prefix) {
		AtomicInteger counter = new AtomicInteger();
		return (runnable) -> {
			Thread thread = new Thread(runnable, prefix + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}

	/** 미리보기 요청 결과. */
	public sealed interface PreviewResult permits Ready, Pending {
	}

	/** 바로 보낼 수 있는 PDF. */
	public record Ready(VersionFile file) implements PreviewResult {
	}

	/** 변환 대기·진행 중. */
	public enum Pending implements PreviewResult {

		INSTANCE

	}

	private record Failure(PreviewConversionException.Kind kind, Instant at) {
	}

}
