package com.docflow.document.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.docflow.global.common.BaseTimeEntity;
import com.docflow.project.domain.Project;
import com.docflow.user.domain.User;

/**
 * 프로젝트에 속한 문서 (SPEC-03 §5.1). 파일은 {@link DocumentVersion}이 가진다. 작성자는 최초 등록 사용자이며
 * 변경하지 않는다.
 */
@Entity
@Table(name = "documents")
public class Document extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false, updatable = false)
	private Project project;

	@Column(name = "title", nullable = false, length = 200)
	private String title;

	@Column(name = "description", length = 1000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 30)
	private DocumentStatus status;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "created_by", nullable = false, updatable = false)
	private User createdBy;

	protected Document() {
	}

	private Document(Project project, String title, String description, User createdBy) {
		this.project = project;
		this.title = title;
		this.description = description;
		this.status = DocumentStatus.DRAFT;
		this.createdBy = createdBy;
	}

	/** 정규화·검증이 끝난 값으로 DRAFT 문서를 만든다. */
	public static Document create(Project project, String title, String description, User createdBy) {
		return new Document(project, title, description, createdBy);
	}

	public Long getId() {
		return this.id;
	}

	public Project getProject() {
		return this.project;
	}

	public String getTitle() {
		return this.title;
	}

	public String getDescription() {
		return this.description;
	}

	public DocumentStatus getStatus() {
		return this.status;
	}

	public User getCreatedBy() {
		return this.createdBy;
	}

}
