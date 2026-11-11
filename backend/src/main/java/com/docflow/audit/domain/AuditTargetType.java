package com.docflow.audit.domain;

/** 활동 이력 대상 유형 (SPEC-06 §5). DB CHECK 제약 {@code ck_audit_logs_target_type}과 같은 집합이다. */
public enum AuditTargetType {

	DOCUMENT, DOCUMENT_VERSION, FEEDBACK

}
