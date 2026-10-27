package com.docflow.document.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;

import com.docflow.document.storage.FileStorage;

/**
 * SPEC-03 §7.2 보상 규칙: 롤백 확정 시에만 삭제, 결과 불명확·커밋 시 유지, 삭제 실패는 예외로 전파하지 않음.
 */
class StoredFileCompensationTest {

	private final FileStorage fileStorage = mock(FileStorage.class);

	private final StoredFileCompensation compensation = new StoredFileCompensation(this.fileStorage, "documents/k");

	@Test
	void deletesOnlyWhenRollbackIsConfirmed() throws IOException {
		this.compensation.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
		verify(this.fileStorage).delete("documents/k");
	}

	@Test
	void keepsFileWhenCommittedOrUnknown() throws IOException {
		this.compensation.afterCompletion(TransactionSynchronization.STATUS_COMMITTED);
		this.compensation.afterCompletion(TransactionSynchronization.STATUS_UNKNOWN);
		verify(this.fileStorage, never()).delete("documents/k");
	}

	@Test
	void deleteFailureIsNotPropagated() throws IOException {
		willThrow(new IOException("fail")).given(this.fileStorage).delete("documents/k");
		assertThatCode(() -> this.compensation.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK))
			.doesNotThrowAnyException();
	}

}
