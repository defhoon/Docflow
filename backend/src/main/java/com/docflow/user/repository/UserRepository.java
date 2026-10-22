package com.docflow.user.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.docflow.user.domain.User;

/**
 * 이메일은 정규화된 값({@link User#normalizeEmail(String)})으로 조회한다.
 */
public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

}
