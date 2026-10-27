package com.docflow.document.service;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;

import com.docflow.document.storage.FileStorage;

/**
 * DB 트랜잭션 결과에 따른 최종 파일 보상 (SPEC-03 §7.2). 이 요청이 {@code CREATE_NEW}로 생성한 저장 키에만 등록한다.
 * <ul>
 * <li>롤백 확정: 파일을 삭제한다. 삭제 실패는 로그로만 남겨 원래 오류를 덮지 않는다(고아 파일 잔존).</li>
 * <li>결과 불명확: 파일 없는 DB 행이 생기지 않도록 삭제하지 않고 저장 키를 WARN 로그로 남긴다.</li>
 * <li>커밋: 아무것도 하지 않는다.</li>
 * </ul>
 */
class StoredFileCompensation implements TransactionSynchronization {

	private static final Logger log = LoggerFactory.getLogger(StoredFileCompensation.class);

	private final FileStorage fileStorage;

	private final String storageKey;

	StoredFileCompensation(FileStorage fileStorage, String storageKey) {
		this.fileStorage = fileStorage;
		this.storageKey = storageKey;
	}

	@Override
	public void afterCompletion(int status) {
		if (status == STATUS_ROLLED_BACK) {
			deleteOwnedFile(this.fileStorage, this.storageKey, null);
		}
		else if (status == STATUS_UNKNOWN) {
			log.warn("Document transaction outcome unknown; keeping stored file: storageKey={}", this.storageKey);
		}
	}

	/**
	 * 이 요청이 생성한 최종 파일을 삭제한다. 실패하면 원래 예외에 suppressed로 붙이고({@code cause}가 있을 때) 로그를
	 * 남긴다. 예외를 던지지 않는다.
	 */
	static void deleteOwnedFile(FileStorage fileStorage, String storageKey, Throwable cause) {
		try {
			fileStorage.delete(storageKey);
		}
		catch (IOException | RuntimeException ex) {
			if (cause != null) {
				cause.addSuppressed(ex);
			}
			log.warn("Failed to delete stored file; orphan remains: storageKey={}", storageKey, ex);
		}
	}

}
