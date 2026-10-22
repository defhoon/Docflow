package com.docflow.user.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.user.domain.User;
import com.docflow.user.repository.UserRepository;

/**
 * 사용자 계정 생성·조회. 다른 도메인은 이 Service를 통해 사용자에 접근한다.
 */
@Service
public class UserAccountService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional(readOnly = true)
	public Optional<User> findByEmail(String email) {
		return this.userRepository.findByEmail(User.normalizeEmail(email));
	}

	@Transactional(readOnly = true)
	public Optional<User> findActiveById(Long id) {
		return this.userRepository.findById(id).filter(User::isEnabled);
	}

	@Transactional(readOnly = true)
	public boolean existsByEmail(String email) {
		return this.userRepository.existsByEmail(User.normalizeEmail(email));
	}

	/**
	 * 새 계정을 생성한다. 비밀번호 원문은 해시한 뒤 버리며 저장하거나 기록하지 않는다.
	 */
	@Transactional
	public User createUser(String email, String rawPassword, String displayName) {
		User user = User.create(email, this.passwordEncoder.encode(rawPassword), displayName);
		return this.userRepository.save(user);
	}

}
