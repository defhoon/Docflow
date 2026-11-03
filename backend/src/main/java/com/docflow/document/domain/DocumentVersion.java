package com.docflow.document.domain;

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

/**
 * 문서의 특정 버전 파일 메타데이터 (SPEC-03 §5.1). 수정하지 않으므로 {@code created_at}만 둔다.
 * <p>
 * {@code storageKey}와 {@code sha256}은 내부 정보이며 API 응답에 넣지 않는다.
 */
@Entity
@Table(name = "document_versions")
@EntityListeners(AuditingEntityListener.class)
public class DocumentVersion {

	public static final int FIRST_VERSION = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_id", nullable = false, updatable = false)
	private Document document;

	@Column(name = "version_number", nullable = false, updatable = false)
	private int versionNumber;

	@Column(name = "original_filename", nullable = false, length = 255, updatable = false)
	private String originalFilename;

	@Column(name = "content_type", nullable = false, length = 100, updatable = false)
	private String contentType;

	@Column(name = "size_bytes", nullable = false, updatable = false)
	private long sizeBytes;

	@Column(name = "storage_key", nullable = false, length = 100, updatable = false)
	private String storageKey;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "sha256", nullable = false, length = 64, updatable = false)
	private String sha256;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "uploaded_by", nullable = false, updatable = false)
	private User uploadedBy;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected DocumentVersion() {
	}

	private DocumentVersion(Document document, int versionNumber, StoredDocumentFile file, User uploadedBy) {
		this.document = document;
		this.versionNumber = versionNumber;
		this.originalFilename = file.originalFilename();
		this.contentType = file.contentType();
		this.sizeBytes = file.sizeBytes();
		this.storageKey = file.storageKey();
		this.sha256 = file.sha256();
		this.uploadedBy = uploadedBy;
	}

	/** 최초 등록 시 생성하는 버전 1. */
	public static DocumentVersion first(Document document, StoredDocumentFile file, User uploadedBy) {
		return new DocumentVersion(document, FIRST_VERSION, file, uploadedBy);
	}

	public Long getId() {
		return this.id;
	}

	public Document getDocument() {
		return this.document;
	}

	public int getVersionNumber() {
		return this.versionNumber;
	}

	public String getOriginalFilename() {
		return this.originalFilename;
	}

	public String getContentType() {
		return this.contentType;
	}

	public long getSizeBytes() {
		return this.sizeBytes;
	}

	public String getStorageKey() {
		return this.storageKey;
	}

	public String getSha256() {
		return this.sha256;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
