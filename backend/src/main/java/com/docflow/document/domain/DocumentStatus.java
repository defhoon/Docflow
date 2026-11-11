package com.docflow.document.domain;

/**
 * 문서 협업 상태 (`spec.md` §7.1, SPEC-07). 허용 전이는 {@link #ACTIVE} → {@link #FINALIZED} 하나이며
 * {@link #FINALIZED}는 영구 종료 상태다.
 */
public enum DocumentStatus {

	ACTIVE, FINALIZED;

	/**
	 * 협업(새 버전 등록, 피드백 작성, 최종 확정)이 열려 있는가. 허용 규칙은 이 메서드 한 곳에 둔다. 상태가 추가되면 컴파일
	 * 단계에서 판단을 강제하도록 모든 상태를 나열한다.
	 */
	public boolean isCollaborationOpen() {
		return switch (this) {
			case ACTIVE -> true;
			case FINALIZED -> false;
		};
	}

	/** 이 상태에서 새 버전을 등록할 수 있는가 (SPEC-04 §7.2, SPEC-07 FR-6). */
	public boolean allowsNewVersion() {
		return isCollaborationOpen();
	}

}
