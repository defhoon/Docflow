package com.docflow.auth.security;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.docflow.user.domain.User;

/**
 * 세션(SecurityContext)에 저장되는 인증 사용자. 인증이 끝나면 비밀번호 해시를 지우므로 세션에는
 * id, email, displayName만 남는다. 전역 Role이 없으므로 권한 목록은 비어 있다.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer {

	@Serial
	private static final long serialVersionUID = 1L;

	private final Long id;

	private final String email;

	private final String displayName;

	private final boolean enabled;

	private String passwordHash;

	private AuthenticatedUser(Long id, String email, String displayName, boolean enabled, String passwordHash) {
		this.id = id;
		this.email = email;
		this.displayName = displayName;
		this.enabled = enabled;
		this.passwordHash = passwordHash;
	}

	public static AuthenticatedUser from(User user) {
		return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(), user.isEnabled(),
				user.getPasswordHash());
	}

	public Long getId() {
		return this.id;
	}

	public String getEmail() {
		return this.email;
	}

	public String getDisplayName() {
		return this.displayName;
	}

	@Override
	public String getUsername() {
		return this.email;
	}

	@Override
	public String getPassword() {
		return this.passwordHash;
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public boolean isEnabled() {
		return this.enabled;
	}

	@Override
	public void eraseCredentials() {
		this.passwordHash = null;
	}

	@Override
	public String toString() {
		return "AuthenticatedUser[id=" + this.id + "]";
	}

}
