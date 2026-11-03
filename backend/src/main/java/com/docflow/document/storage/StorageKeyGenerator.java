package com.docflow.document.storage;

/**
 * 저장 키 생성 (SPEC-03 §5.4). 기본 구현은 UUID이며, 통합 테스트에서만 고정 키 구현으로 교체한다.
 */
@FunctionalInterface
public interface StorageKeyGenerator {

	String newKey();

}
