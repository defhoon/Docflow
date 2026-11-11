package com.docflow.audit.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * 활동 이력 한 건 (SPEC-06 §5). append-only이므로 변경 메서드가 없다. 조회는 DTO 프로젝션으로만 하므로 연관 엔티티 대신 ID를
 * 저장한다.
 */
@Entity
@Table(name = "audit_logs")
@EntityListeners(AuditingEntityListener.class)
public class AuditLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private Long projectId;

	@Column(name = "actor_id", nullable = false, updatable = false)
	private Long actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "action", nullable = false, updatable = false, length = 40)
	private AuditAction action;

	@Enumerated(EnumType.STRING)
	@Column(name = "target_type", nullable = false, updatable = false, length = 30)
	private AuditTargetType targetType;

	@Column(name = "target_id", nullable = false, updatable = false)
	private Long targetId;

	@Column(name = "document_id", updatable = false)
	private Long documentId;

	@Column(name = "version_number", updatable = false)
	private Integer versionNumber;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected AuditLog() {
	}

	private AuditLog(Long projectId, Long actorId, AuditAction action, AuditTargetType targetType, Long targetId,
			Long documentId, Integer versionNumber) {
		this.projectId = projectId;
		this.actorId = actorId;
		this.action = action;
		this.targetType = targetType;
		this.targetId = targetId;
		this.documentId = documentId;
		this.versionNumber = versionNumber;
	}

	public static AuditLog of(Long projectId, Long actorId, AuditAction action, AuditTargetType targetType,
			Long targetId, Long documentId, Integer versionNumber) {
		return new AuditLog(projectId, actorId, action, targetType, targetId, documentId, versionNumber);
	}

	public Long getId() {
		return this.id;
	}

	public Long getProjectId() {
		return this.projectId;
	}

	public Long getActorId() {
		return this.actorId;
	}

	public AuditAction getAction() {
		return this.action;
	}

	public AuditTargetType getTargetType() {
		return this.targetType;
	}

	public Long getTargetId() {
		return this.targetId;
	}

	public Long getDocumentId() {
		return this.documentId;
	}

	public Integer getVersionNumber() {
		return this.versionNumber;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
