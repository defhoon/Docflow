package com.docflow.auth.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.docflow.user.service.UserAccountService;

/**
 * 이메일로 사용자를 찾아 {@link AuthenticatedUser}로 변환한다. 존재하지 않는 계정과 비활성 계정의 구분은
 * {@code DaoAuthenticationProvider}가 처리하며, 응답에서는 모두 같은 인증 실패로 보인다.
 */
@Service
public class DocflowUserDetailsService implements UserDetailsService {

	private final UserAccountService userAccountService;

	public DocflowUserDetailsService(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@Override
	public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
		return this.userAccountService.findByEmail(email)
			.map(AuthenticatedUser::from)
			.orElseThrow(() -> new UsernameNotFoundException("User not found"));
	}

}
