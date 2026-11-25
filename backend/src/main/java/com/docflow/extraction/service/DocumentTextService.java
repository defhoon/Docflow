package com.docflow.extraction.service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Types;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import tools.jackson.databind.json.JsonMapper;

import com.docflow.document.domain.DocumentVersion;
import com.docflow.document.repository.DocumentVersionRepository;
import com.docflow.document.storage.FileStorage;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.extraction.domain.DocumentVersionText;
import com.docflow.extraction.domain.ExtractionStatus;
import com.docflow.extraction.pdf.ExtractedText;
import com.docflow.extraction.pdf.PdfExtractionOutcome;
import com.docflow.extraction.pdf.PdfTextExtractor;
import com.docflow.extraction.repository.DocumentVersionTextRepository;

/**
 * 버전별 텍스트 추출과 재사용 (SPEC-10 §5). 권한 확인은 호출 측(요약·비교 서비스)이 먼저 한다. 이 서비스는 버전 ID만 받는다.
 * <p>
 * 같은 버전은 한 번만 추출한다: 저장된 결과가 있으면 그대로 쓰고, 없으면 버전별 잠금 안에서 다시 확인한 뒤 추출한다(단일
 * 인스턴스 전제, ADR-0007). 저장은 {@code ON CONFLICT}로 하므로 잠금 밖의 경합에도 행은 하나다. 추출 중에는 DB 연결을 잡지
 * 않는다. 문서 내용과 원본 파일명은 로그에 남기지 않는다.
 */
@Service
@EnableConfigurationProperties(ExtractionProperties.class)
public class DocumentTextService {

	/** 추출 규칙이 바뀌면 올린다. 낮은 버전으로 저장된 결과는 다음 조회 때 다시 추출한다. */
	public static final int EXTRACTOR_VERSION = 1;

	private static final Logger log = LoggerFactory.getLogger(DocumentTextService.class);

	private final DocumentVersionRepository documentVersionRepository;

	private final DocumentVersionTextRepository textRepository;

	private final FileStorage fileStorage;

	private final JdbcTemplate jdbcTemplate;

	private final JsonMapper jsonMapper;

	private final PdfTextExtractor extractor;

	private final Semaphore permits;

	private final ConcurrentHashMap<Long, Object> locks = new ConcurrentHashMap<>();

	public DocumentTextService(DocumentVersionRepository documentVersionRepository,
			DocumentVersionTextRepository textRepository, FileStorage fileStorage, JdbcTemplate jdbcTemplate,
			JsonMapper jsonMapper, ExtractionProperties properties) {
		this.documentVersionRepository = documentVersionRepository;
		this.textRepository = textRepository;
		this.fileStorage = fileStorage;
		this.jdbcTemplate = jdbcTemplate;
		this.jsonMapper = jsonMapper;
		this.extractor = new PdfTextExtractor(properties.limits());
		this.permits = new Semaphore(properties.concurrency(), true);
	}

	/**
	 * 저장된 결과만 조회한다(추출하지 않음). 추출기 버전이 낮거나, 처리 시간 초과({@code TIMEOUT})처럼 다시 시도하면 결과가 달라질
	 * 수 있는 상태면 없는 것으로 본다(다음 요청에서 다시 추출).
	 */
	public Optional<VersionText> findStored(Long versionId) {
		return this.textRepository.findById(versionId)
			.filter((row) -> row.getExtractorVersion() >= EXTRACTOR_VERSION)
			.filter((row) -> row.getStatus() != ExtractionStatus.TIMEOUT)
			.map(this::toVersionText);
	}

	/**
	 * 저장된 결과를 쓰거나 없으면 추출해 저장한다. 버전이 없으면 {@link IllegalArgumentException}. 원본 파일을 읽을 수 없으면
	 * {@link UncheckedIOException}(저장하지 않으므로 다음 호출에서 다시 시도한다).
	 */
	public VersionText getOrExtract(Long versionId) {
		Optional<VersionText> stored = findStored(versionId);
		if (stored.isPresent()) {
			return stored.get();
		}
		Object lock = this.locks.computeIfAbsent(versionId, (id) -> new Object());
		try {
			synchronized (lock) {
				stored = findStored(versionId);
				if (stored.isPresent()) {
					return stored.get();
				}
				extractAndStore(versionId);
				return findStored(versionId).orElseThrow(
						() -> new IllegalStateException("Extraction result missing: versionId=" + versionId));
			}
		}
		finally {
			this.locks.remove(versionId, lock);
		}
	}

	private void extractAndStore(Long versionId) {
		DocumentVersion version = this.documentVersionRepository.findById(versionId)
			.orElseThrow(() -> new IllegalArgumentException("Unknown version: " + versionId));
		if (DocumentFileType.fromContentType(version.getContentType()).orElse(null) != DocumentFileType.PDF) {
			store(versionId, PdfExtractionOutcome.of(ExtractionStatus.UNSUPPORTED_TYPE, 0));
			return;
		}
		long started = System.nanoTime();
		PdfExtractionOutcome outcome;
		try {
			this.permits.acquire();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while waiting for extraction slot", ex);
		}
		try {
			Path temp = copyToTemp(version);
			try {
				outcome = this.extractor.extract(temp);
			}
			finally {
				deleteQuietly(temp);
			}
		}
		finally {
			this.permits.release();
		}
		store(versionId, outcome);
		log.info("Document text extracted: versionId={}, status={}, pages={}, words={}, elapsedMs={}", versionId,
				outcome.status(), outcome.pageCount(), (outcome.text() != null) ? outcome.text().words().size() : 0,
				(System.nanoTime() - started) / 1_000_000);
	}

	private Path copyToTemp(DocumentVersion version) {
		try {
			long actual = this.fileStorage.size(version.getStorageKey());
			if (actual != version.getSizeBytes()) {
				throw new IOException("Stored file size mismatch");
			}
			Path temp = this.fileStorage.createTempFile();
			try (InputStream in = this.fileStorage.openForRead(version.getStorageKey())) {
				Files.copy(in, temp, StandardCopyOption.REPLACE_EXISTING);
			}
			catch (IOException | RuntimeException ex) {
				deleteQuietly(temp);
				throw ex;
			}
			return temp;
		}
		catch (IOException ex) {
			log.error("Stored document file unavailable for extraction: versionId={}, storageKey={}", version.getId(),
					version.getStorageKey(), ex);
			throw new UncheckedIOException(ex);
		}
	}

	private void deleteQuietly(Path temp) {
		try {
			this.fileStorage.deleteTemp(temp);
		}
		catch (IOException ex) {
			log.warn("Failed to delete extraction temp file: {}", temp.getFileName(), ex);
		}
	}

	private void store(Long versionId, PdfExtractionOutcome outcome) {
		ExtractedText text = outcome.text();
		byte[] content = (text != null) ? compress(text) : null;
		this.jdbcTemplate.update("""
				INSERT INTO document_version_texts
				    (version_id, status, extractor_version, page_count, word_count, char_count, content)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				ON CONFLICT (version_id) DO UPDATE SET
				    status = EXCLUDED.status, extractor_version = EXCLUDED.extractor_version,
				    page_count = EXCLUDED.page_count, word_count = EXCLUDED.word_count,
				    char_count = EXCLUDED.char_count, content = EXCLUDED.content, created_at = now()
				WHERE document_version_texts.extractor_version < EXCLUDED.extractor_version
				   OR document_version_texts.status = 'TIMEOUT'""", (ps) -> {
			ps.setLong(1, versionId);
			ps.setString(2, outcome.status().name());
			ps.setInt(3, EXTRACTOR_VERSION);
			ps.setInt(4, (text != null) ? text.pageCount() : outcome.pageCount());
			ps.setInt(5, (text != null) ? text.words().size() : 0);
			ps.setInt(6, (text != null) ? text.charCount() : 0);
			if (content != null) {
				ps.setBytes(7, content);
			}
			else {
				ps.setNull(7, Types.BINARY);
			}
		});
	}

	private VersionText toVersionText(DocumentVersionText row) {
		ExtractedText text = (row.getContent() != null) ? decompress(row.getContent()) : null;
		return new VersionText(row.getVersionId(), row.getStatus(), row.getPageCount(), text);
	}

	private byte[] compress(ExtractedText text) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (GZIPOutputStream gzip = new GZIPOutputStream(bytes)) {
			this.jsonMapper.writeValue(gzip, text);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

	private ExtractedText decompress(byte[] content) {
		try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(content))) {
			return this.jsonMapper.readValue(gzip, ExtractedText.class);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
