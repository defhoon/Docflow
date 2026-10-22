package com.docflow.user.domain;

import java.util.Locale;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.docflow.global.common.BaseTimeEntity;

/**
 * 사용자 계정. 비밀번호는 해시만 보관하며 API 응답으로 직접 반환하지 않는다.
 */
@Entity
@Table(name = "users")
public class User extends BaseTimeEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "email", nullable = false, length = 255)
	private String email;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "display_name", nullable = false, length = 100)
	private String displayName;

	@Column(name = "enabled", nullable = false)
	private boolean enabled;

	protected User() {
	}

	private User(String email, String passwordHash, String displayName) {
		this.email = normalizeEmail(email);
		this.passwordHash = passwordHash;
		this.displayName = displayName;
		this.enabled = true;
	}

	public static User create(String email, String passwordHash, String displayName) {
		return new User(email, passwordHash, displayName);
	}

	/**
	 * 로그인과 계정 생성에서 공통으로 사용하는 이메일 정규화 규칙 (앞뒤 공백 제거, 소문자).
	 */
	public static String normalizeEmail(String email) {
		return (email != null) ? email.trim().toLowerCase(Locale.ROOT) : null;
	}

	public void disable() {
		this.enabled = false;
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getPasswordHash() {
		return this.passwordHash;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	public boolean isEnabled() {
		return this.enabled;
	}

}
