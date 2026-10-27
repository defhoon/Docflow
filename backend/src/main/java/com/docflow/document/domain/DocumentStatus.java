package com.docflow.document.domain;

/**
 * 문서 상태 (`spec.md` §7.1). SPEC-03은 {@link #DRAFT}로 생성만 하며 상태 전이는 SPEC-06 이후에서 다룬다.
 */
public enum DocumentStatus {

	DRAFT, IN_REVIEW, CHANGES_REQUESTED, RE_REVIEW, APPROVED

}
