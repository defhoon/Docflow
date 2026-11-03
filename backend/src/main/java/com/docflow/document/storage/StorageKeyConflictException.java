package com.docflow.document.storage;

import java.nio.file.FileAlreadyExistsException;

/**
 * 같은 저장 키의 파일이 이미 있어 배치하지 못했다. 기존 파일은 이 요청이 만든 것이 아니므로 삭제하지 않는다.
 */
public class StorageKeyConflictException extends FileAlreadyExistsException {

	public StorageKeyConflictException(String storageKey) {
		super(storageKey);
	}

}
