package com.docflow.feedback.domain;

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
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.docflow.document.domain.DocumentVersion;
import com.docflow.user.domain.User;

/** 특정 문서 버전에 대한 피드백 (SPEC-08 §5). 상태가 없고 변경 메서드도 없다. */
@Entity
@Table(name = "feedbacks")
@EntityListeners(AuditingEntityListener.class)
public class Feedback {

	public static final int MAX_CONTENT_LENGTH = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "document_version_id", nullable = false, updatable = false)
	private DocumentVersion documentVersion;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "author_id", nullable = false, updatable = false)
	private User author;

	@Column(name = "content", nullable = false, updatable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Feedback() {
	}

	private Feedback(DocumentVersion documentVersion, User author, String content) {
		this.documentVersion = documentVersion;
		this.author = author;
		this.content = content;
	}

	/** 정규화·검증이 끝난 내용으로 만든다. */
	public static Feedback create(DocumentVersion documentVersion, User author, String content) {
		return new Feedback(documentVersion, author, content);
	}

	public Long getId() {
		return this.id;
	}

	public DocumentVersion getDocumentVersion() {
		return this.documentVersion;
	}

	public User getAuthor() {
		return this.author;
	}

	public String getContent() {
		return this.content;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
