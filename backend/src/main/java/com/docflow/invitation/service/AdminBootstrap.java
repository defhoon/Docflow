package com.docflow.invitation.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.docflow.user.repository.UserRepository;

/**
 * 최초 운영 관리자 준비 (#26). 사용자가 한 명도 없을 때만 관리자 권한을 주는 일회용 초대코드(기본 24시간)를 발급하고 코드를
 * 서버 로그에 한 번 출력한다. 운영자는 비공개 배포 로그에서 코드를 확인해 가입한다. 사용자가 생기면 다시 발급하지 않는다.
 * <p>
 * 고정 비밀번호나 기본 계정을 만들지 않는다. {@code docflow.bootstrap.admin-invite-enabled=false}로 끌 수 있다.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminBootstrap implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository userRepository;

	private final InvitationService invitationService;

	private final boolean enabled;

	private final Duration validity;

	public AdminBootstrap(UserRepository userRepository, InvitationService invitationService,
			@Value("${docflow.bootstrap.admin-invite-enabled:true}") boolean enabled,
			@Value("${docflow.bootstrap.admin-invite-validity:24h}") Duration validity) {
		this.userRepository = userRepository;
		this.invitationService = invitationService;
		this.enabled = enabled;
		this.validity = validity;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (!this.enabled || this.userRepository.count() > 0) {
			return;
		}
		String code = this.invitationService.issueBootstrapAdminCode(this.validity);
		log.warn("No users exist. One-time administrator invitation code (valid {}): {}", this.validity, code);
	}

}
