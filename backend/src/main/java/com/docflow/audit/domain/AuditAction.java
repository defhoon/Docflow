package com.docflow.audit.domain;

/** 활동 이력 행동 (SPEC-06 FR-4). DB CHECK 제약 {@code ck_audit_logs_action}과 같은 집합이다. */
public enum AuditAction {

	DOCUMENT_CREATED, VERSION_CREATED, FEEDBACK_CREATED, DOCUMENT_FINALIZED,

	/** 문서 설명 변경 (SPEC-09). 대상은 DOCUMENT, 버전 번호는 없다. */
	DOCUMENT_DESCRIPTION_UPDATED

}
