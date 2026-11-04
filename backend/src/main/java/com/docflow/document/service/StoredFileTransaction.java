package com.docflow.document.service;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.document.domain.StoredDocumentFile;
import com.docflow.document.storage.FileStorage;

/**
 * 저장소에 배치한 파일을 참조하는 DB 트랜잭션 실행 (SPEC-03 §7.2, SPEC-04 §7.3 결정 I1).
 * <p>
 * 트랜잭션 안에서 가장 먼저 {@link StoredFileCompensation}을 등록하므로 롤백이 확정되면 이 요청이 만든 파일이 삭제되고,
 * 커밋 결과가 불명확하면 파일이 남는다. 트랜잭션 시작(연결 획득) 전 실패는 DB에 아무것도 쓰지 않았으므로 여기서 직접
 * 삭제한다. Spring Bean이 아니며 각 Service가 생성한다.
 */
final class StoredFileTransaction {

	private final TransactionTemplate transactionTemplate;

	private final FileStorage fileStorage;

	StoredFileTransaction(PlatformTransactionManager transactionManager, FileStorage fileStorage) {
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.fileStorage = fileStorage;
	}

	<T> T execute(StoredDocumentFile storedFile, TransactionCallback<T> work) {
		boolean[] compensationRegistered = { false };
		try {
			return this.transactionTemplate.execute((status) -> {
				TransactionSynchronizationManager
					.registerSynchronization(new StoredFileCompensation(this.fileStorage, storedFile.storageKey()));
				compensationRegistered[0] = true;
				return work.doInTransaction(status);
			});
		}
		catch (RuntimeException | Error ex) {
			if (!compensationRegistered[0]) {
				StoredFileCompensation.deleteOwnedFile(this.fileStorage, storedFile.storageKey(), ex);
			}
			throw ex;
		}
	}

}
