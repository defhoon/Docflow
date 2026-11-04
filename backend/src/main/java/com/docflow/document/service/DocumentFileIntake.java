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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import com.docflow.document.domain.StoredDocumentFile;
import com.docflow.document.storage.FileStorage;
import com.docflow.document.validation.DocumentFileName;
import com.docflow.document.validation.DocumentFileType;
import com.docflow.document.validation.DocumentFileValidator;
import com.docflow.document.validation.InvalidDocumentFileException;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;

/**
 * 업로드 파일 수신·검증·배치 (SPEC-03 §7.2, SPEC-04 §8 결정 I1). 문서 등록과 새 버전 등록이 함께 쓴다.
 * <p>
 * Spring Bean이 아니며 각 Service가 생성한다. 상태를 갖지 않는다.
 */
final class DocumentFileIntake {

	/** 20MiB (SPEC-03 §5.6, 결정 Q5). */
	static final long MAX_FILE_BYTES = 20L * 1024 * 1024;

	static final String EMPTY_FILE = "빈 파일은 업로드할 수 없습니다.";

	private static final Logger log = LoggerFactory.getLogger(DocumentFileIntake.class);

	private static final int BUFFER_SIZE = 8192;

	private final FileStorage fileStorage;

	private final DocumentFileValidator documentFileValidator;

	DocumentFileIntake(FileStorage fileStorage, DocumentFileValidator documentFileValidator) {
		this.fileStorage = fileStorage;
		this.documentFileValidator = documentFileValidator;
	}

	/**
	 * 업로드 스트림을 임시 파일로 복사하며 크기·SHA-256을 계산하고, 형식을 검증한 뒤 최종 위치에 배치한다. 임시 파일은
	 * 성공·실패와 관계없이 삭제를 시도한다. 반환 시점에 최종 파일은 이 요청이 생성한 것이며 아직 DB가 참조하지 않는다.
	 */
	StoredDocumentFile store(MultipartFile file, DocumentFileName fileName) {
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
