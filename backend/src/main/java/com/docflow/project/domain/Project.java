package com.docflow.project.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import com.docflow.global.common.BaseTimeEntity;
import com.docflow.user.domain.User;

/**
 * 프로젝트. Owner는 생성한 사용자이며 클라이언트가 지정할 수 없다 (SPEC-01 §5).
 */
@Entity
@Table(name = "projects")
public class Project extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "description", length = 1000)
	private String description;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	protected Project() {
	}

	private Project(String name, String description, User owner) {
		this.name = name;
		this.description = description;
		this.owner = owner;
	}

	/**
	 * 정규화·검증이 끝난 값으로 프로젝트를 만든다 (정규화는 요청 DTO에서 한 번만 수행한다).
	 */
	public static Project create(String name, String description, User owner) {
		return new Project(name, description, owner);
	}

	public boolean isOwnedBy(Long userId) {
		return userId != null && userId.equals(this.owner.getId());
	}

	public Long getId() {
		return this.id;
	}

	public String getName() {
		return this.name;
	}

	public String getDescription() {
		return this.description;
	}

	public User getOwner() {
		return this.owner;
	}

}
