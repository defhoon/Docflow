package com.docflow.document.storage;

import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * 서버가 만드는 UUID 기반 저장 키. 원본 파일명은 저장 키에 쓰지 않는다.
 */
@Component
public class UuidStorageKeyGenerator implements StorageKeyGenerator {

	@Override
	public String newKey() {
		return "documents/" + UUID.randomUUID();
	}

}
