package com.docflow.invitation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.docflow.user.domain.User;

/** 일회용 초대코드 (#26). 원문 대신 SHA-256 해시만 저장한다. */
@Entity
@Table(name = "invitations")
@EntityListeners(AuditingEntityListener.class)
public class Invitation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "code_hash", nullable = false, updatable = false, length = 64)
	private String codeHash;

	@Column(name = "grants_admin", nullable = false, updatable = false)
	private boolean grantsAdmin;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by", updatable = false)
	private User createdBy;

	@Column(name = "note", updatable = false, length = 100)
	private String note;

	@Column(name = "expires_at", nullable = false, updatable = false)
	private Instant expiresAt;

	@Column(name = "used_at")
	private Instant usedAt;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "used_by")
	private User usedBy;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Invitation() {
	}

	private Invitation(String codeHash, boolean grantsAdmin, User createdBy, String note, Instant expiresAt) {
		this.codeHash = codeHash;
		this.grantsAdmin = grantsAdmin;
		this.createdBy = createdBy;
		this.note = note;
		this.expiresAt = expiresAt;
	}

	public static Invitation issue(String codeHash, boolean grantsAdmin, User createdBy, String note,
			Instant expiresAt) {
		return new Invitation(codeHash, grantsAdmin, createdBy, note, expiresAt);
	}

	public boolean isUsable(Instant now) {
		return this.usedAt == null && this.revokedAt == null && now.isBefore(this.expiresAt);
	}

	public InvitationStatus status(Instant now) {
		if (this.usedAt != null) {
			return InvitationStatus.USED;
		}
		if (this.revokedAt != null) {
			return InvitationStatus.REVOKED;
		}
		return now.isBefore(this.expiresAt) ? InvitationStatus.ACTIVE : InvitationStatus.EXPIRED;
	}

	/** 호출자는 행을 잠근 트랜잭션에서 {@link #isUsable}을 확인한 뒤 호출한다. */
	public void markUsed(User user, Instant now) {
		if (!isUsable(now)) {
			throw new IllegalStateException("Invitation is not usable: id=" + this.id);
		}
		this.usedBy = user;
		this.usedAt = now;
	}

	/** 사용되지 않은 코드만 폐기한다. 이미 폐기·만료된 코드는 그대로 둔다. */
	public boolean revoke(Instant now) {
		if (this.usedAt != null || this.revokedAt != null) {
			return false;
		}
		this.revokedAt = now;
		return true;
	}

	public Long getId() {
		return this.id;
	}

	public boolean isGrantsAdmin() {
		return this.grantsAdmin;
	}

	public User getCreatedBy() {
		return this.createdBy;
	}

	public String getNote() {
		return this.note;
	}

	public Instant getExpiresAt() {
		return this.expiresAt;
	}

	public Instant getUsedAt() {
		return this.usedAt;
	}

	public User getUsedBy() {
		return this.usedBy;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
