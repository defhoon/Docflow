package com.docflow.project.domain;

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

import com.docflow.user.domain.User;

/**
 * 프로젝트 구성원(MEMBER) (SPEC-02 §5). Owner는 이 Entity로 저장하지 않는다.
 * <p>
 * 생성·삭제만 하고 수정하지 않으므로 {@code updated_at}이 없어 {@code BaseTimeEntity}를 상속하지 않는다.
 */
@Entity
@Table(name = "project_members")
@EntityListeners(AuditingEntityListener.class)
public class ProjectMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false, updatable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false, updatable = false)
	private User user;

	@CreatedDate
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProjectMember() {
	}

	private ProjectMember(Project project, User user) {
		this.project = project;
		this.user = user;
	}

	public static ProjectMember create(Project project, User user) {
		return new ProjectMember(project, user);
	}

	public Long getId() {
		return this.id;
	}

	public Project getProject() {
		return this.project;
	}

	public User getUser() {
		return this.user;
	}

	public Instant getCreatedAt() {
		return this.createdAt;
	}

}
