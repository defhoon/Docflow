package com.docflow.document.storage;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 문서 파일 저장소 (SPEC-03 §5.4). 저장 경로와 저장 키는 API 응답에 노출하지 않는다.
 */
public interface FileStorage {

	/** 업로드 스트림을 받을 임시 파일을 만든다. 검증을 통과하지 못한 파일이 최종 위치에 생기지 않게 하기 위함이다. */
	Path createTempFile() throws IOException;

	/**
	 * 검증이 끝난 임시 파일 내용을 서버가 만든 새 저장 키 위치에 배타적으로 생성한다. 대상이 이미 있으면 기존 파일을
	 * 건드리지 않고 {@link StorageKeyConflictException}으로 실패한다. 반환되면 내용 복사·flush·close가 끝난 상태이며,
	 * 반환된 저장 키의 파일은 이 호출이 생성한 것이다.
	 */
	String store(Path validatedTempFile) throws IOException;

	/** 보상 삭제. {@link #store}가 이 요청에서 생성한 저장 키에만 호출한다. 없으면 아무 일도 하지 않는다. */
	void delete(String storageKey) throws IOException;

	/** 임시 파일 정리. 없으면 아무 일도 하지 않는다. */
	void deleteTemp(Path tempFile) throws IOException;

}
