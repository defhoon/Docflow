package com.docflow.audit.domain;

/** 활동 이력 행동 (SPEC-06 FR-4). DB CHECK 제약 {@code ck_audit_logs_action}과 같은 집합이다. */
public enum AuditAction {

	DOCUMENT_CREATED, VERSION_CREATED, FEEDBACK_CREATED, DOCUMENT_FINALIZED

}
