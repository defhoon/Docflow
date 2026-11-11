package com.docflow.invitation.service;

import java.time.Duration;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.docflow.user.domain.SystemRole;
import com.docflow.user.repository.UserRepository;

/**
 * 최초 운영 관리자 준비 (#26, #37).
 * <ul>
 * <li>관리자가 이미 있으면 남은 미사용 관리자 초대를 폐기하고 아무것도 발급하지 않는다(재발급 불가).</li>
 * <li>사용자가 한 명도 없고 운영자가 {@code DOCFLOW_BOOTSTRAP_ADMIN_CODE}(24~64자)를 비밀 변수로 주면, 그 코드의 해시로
 * 관리자 초대(기본 24시간)를 준비한다. 코드 원문은 저장하거나 로그에 남기지 않는다.</li>
 * <li>코드가 없으면 발급하지 않고 설정 방법만 경고한다. 개발 편의용으로 {@code docflow.bootstrap.log-generated-code=true}일
 * 때만 임의 코드를 만들어 로그에 출력한다(운영 금지).</li>
 * <li>사용자는 있지만 관리자가 없으면 발급하지 않는다(누구나 관리자가 되는 경로 방지). 복구는 운영자가 DB에서 수행한다.</li>
 * </ul>
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class AdminBootstrap implements ApplicationRunner {

	static final int MIN_CODE_LENGTH = 24;

	static final int MAX_CODE_LENGTH = 64;

	private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

	private final UserRepository userRepository;

	private final InvitationService invitationService;

	private final String configuredCode;

	private final boolean logGeneratedCode;

	private final Duration validity;

	public AdminBootstrap(UserRepository userRepository, InvitationService invitationService,
			@Value("${docflow.bootstrap.admin-code:}") String configuredCode,
			@Value("${docflow.bootstrap.log-generated-code:false}") boolean logGeneratedCode,
			@Value("${docflow.bootstrap.admin-invite-validity:24h}") Duration validity) {
		this.userRepository = userRepository;
		this.invitationService = invitationService;
		this.configuredCode = (configuredCode == null) ? "" : configuredCode.strip();
		this.logGeneratedCode = logGeneratedCode;
		this.validity = validity;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (this.userRepository.existsBySystemRole(SystemRole.ADMIN)) {
			int revoked = this.invitationService.revokeUnusedAdminInvitations();
			if (revoked > 0) {
				log.info("Administrator exists; revoked {} unused administrator invitation(s)", revoked);
			}
			if (StringUtils.hasText(this.configuredCode)) {
				log.warn("DOCFLOW_BOOTSTRAP_ADMIN_CODE is still set although an administrator exists; remove it");
			}
			return;
		}
		if (this.userRepository.count() > 0) {
			log.warn("Users exist but no administrator; bootstrap invitation is not issued (restore an administrator in the database)");
			return;
		}
		if (StringUtils.hasText(this.configuredCode)) {
			int length = this.configuredCode.length();
			if (length < MIN_CODE_LENGTH || length > MAX_CODE_LENGTH) {
				log.error("DOCFLOW_BOOTSTRAP_ADMIN_CODE must be {}..{} characters; bootstrap invitation not issued",
						MIN_CODE_LENGTH, MAX_CODE_LENGTH);
				return;
			}
			Optional<java.time.Instant> expiresAt = this.invitationService
				.prepareBootstrapAdminInvitation(this.configuredCode, this.validity);
			if (expiresAt.isPresent()) {
				log.warn("No users exist. Administrator bootstrap invitation is ready (expires at {}). "
						+ "Sign up with the configured code, then remove DOCFLOW_BOOTSTRAP_ADMIN_CODE.", expiresAt.get());
			}
			else {
				log.error("The configured DOCFLOW_BOOTSTRAP_ADMIN_CODE has expired or was revoked; set a new code");
			}
			return;
		}
		if (this.logGeneratedCode) {
			String code = com.docflow.invitation.domain.InvitationCodes.generate();
			this.invitationService.prepareBootstrapAdminInvitation(code, this.validity);
			log.warn("[development only] One-time administrator invitation code (valid {}): {}", this.validity, code);
			return;
		}
		log.warn("No users exist. Set DOCFLOW_BOOTSTRAP_ADMIN_CODE ({}..{} characters) as a secret to create the first administrator",
				MIN_CODE_LENGTH, MAX_CODE_LENGTH);
	}

}
