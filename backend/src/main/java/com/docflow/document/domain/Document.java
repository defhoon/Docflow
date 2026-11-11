package com.docflow.document.domain;

import java.time.Instant;

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
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
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

	/** 최종 버전 (SPEC-07). ACTIVE이면 null. DB 복합 FK가 같은 문서의 버전만 허용한다. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "final_version_id")
	private DocumentVersion finalVersion;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "finalized_by")
	private User finalizedBy;

	@Column(name = "finalized_at")
	private Instant finalizedAt;

	protected Document() {
	}

	private Document(Project project, String title, String description, User createdBy) {
		this.project = project;
		this.title = title;
		this.description = description;
		this.status = DocumentStatus.ACTIVE;
		this.createdBy = createdBy;
	}

	/** 정규화·검증이 끝난 값으로 ACTIVE 문서를 만든다. */
	public static Document create(Project project, String title, String description, User createdBy) {
		return new Document(project, title, description, createdBy);
	}

	/**
	 * 새 버전이 추가되었음을 기록한다. {@code updated_at}만 갱신하고 제목·설명·상태·작성자는 바꾸지 않는다 (SPEC-04 §5.3,
	 * 결정 V2).
	 */
	public void markNewVersionAdded() {
		markModified();
	}

	/**
	 * 최종 버전을 확정한다 (SPEC-07 FR-4). 호출자는 문서 행을 잠근 트랜잭션 안에서 호출한다. 이미 확정된 문서는
	 * {@link ErrorCode#DOCUMENT_ALREADY_FINALIZED}로 거부한다(FINALIZED → 어떤 상태로도 전이 불가).
	 */
	public void finalizeWith(DocumentVersion version, User user, Instant now) {
		if (!this.status.isCollaborationOpen()) {
			throw new BusinessException(ErrorCode.DOCUMENT_ALREADY_FINALIZED);
		}
		if (version.getDocument() != this) {
			throw new IllegalArgumentException("Version does not belong to document: documentId=" + this.id);
		}
		this.status = DocumentStatus.FINALIZED;
		this.finalVersion = version;
		this.finalizedBy = user;
		this.finalizedAt = now;
		markModified();
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

	public DocumentVersion getFinalVersion() {
		return this.finalVersion;
	}

	public User getFinalizedBy() {
		return this.finalizedBy;
	}

	public Instant getFinalizedAt() {
		return this.finalizedAt;
	}

}
