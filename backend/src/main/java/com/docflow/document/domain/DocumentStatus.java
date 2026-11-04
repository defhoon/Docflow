package com.docflow.document.domain;

/**
 * 문서 상태 (`spec.md` §7.1). SPEC-03은 {@link #DRAFT}로 생성만 하며 상태 전이는 SPEC-06 이후에서 다룬다.
 */
public enum DocumentStatus {

	DRAFT, IN_REVIEW, CHANGES_REQUESTED, RE_REVIEW, APPROVED;

	/**
	 * 이 상태에서 새 버전을 등록할 수 있는가 (SPEC-04 §7.2, 결정 V4). 허용 규칙은 이 메서드 한 곳에 둔다. 상태가 추가되면
	 * 컴파일 단계에서 판단을 강제하도록 모든 상태를 나열한다. {@link #APPROVED}는 잠정 거부이며 SPEC-12에서 재검토한다.
	 */
	public boolean allowsNewVersion() {
		return switch (this) {
			case DRAFT, CHANGES_REQUESTED -> true;
			case IN_REVIEW, RE_REVIEW, APPROVED -> false;
		};
	}

}
