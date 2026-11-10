package com.docflow.auth.service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.auth.dto.AuthUserResponse;
import com.docflow.auth.dto.SignupRequest;
import com.docflow.auth.security.AuthAttemptGuard;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.invitation.domain.Invitation;
import com.docflow.invitation.domain.InvitationCodes;
import com.docflow.invitation.repository.InvitationRepository;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 초대코드 회원가입 (#26). 코드 행을 {@code FOR UPDATE}로 잠근 트랜잭션에서 사용 가능 여부 확인 → 계정 생성 → 코드 사용 처리를
 * 함께 커밋하므로 같은 코드로 두 계정이 생기지 않는다. 존재하지 않음·만료·사용됨·폐기는 같은 오류로 응답한다.
 */
@Service
public class SignupService {

	static final int BCRYPT_MAX_BYTES = 72;

	private static final Logger log = LoggerFactory.getLogger(SignupService.class);

	private final InvitationRepository invitationRepository;

	private final UserAccountService userAccountService;

	private final AuthAttemptGuard attemptGuard;

	private final TransactionTemplate transactionTemplate;

	public SignupService(InvitationRepository invitationRepository, UserAccountService userAccountService,
			AuthAttemptGuard attemptGuard, PlatformTransactionManager transactionManager) {
		this.invitationRepository = invitationRepository;
		this.userAccountService = userAccountService;
		this.attemptGuard = attemptGuard;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public AuthUserResponse signup(SignupRequest request) {
		if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED);
		}
		this.attemptGuard.checkSignup(request.email());
		try {
			AuthUserResponse created = this.transactionTemplate.execute((status) -> createWithInvitation(request));
			log.info("User signed up: userId={}", created.id());
			return created;
		}
		catch (BusinessException ex) {
			if (ex.errorCode() == ErrorCode.INVITATION_INVALID || ex.errorCode() == ErrorCode.EMAIL_ALREADY_REGISTERED) {
				this.attemptGuard.signupFailed(request.email());
				log.info("Signup rejected: reason={}", ex.errorCode());
			}
			throw ex;
		}
		catch (DataIntegrityViolationException ex) {
			// 같은 이메일 동시 가입: uk_users_email이 최종 판정한다. 트랜잭션은 롤백되어 코드는 미사용으로 남는다.
			this.attemptGuard.signupFailed(request.email());
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
	}

	private AuthUserResponse createWithInvitation(SignupRequest request) {
		Instant now = Instant.now();
		Invitation invitation = this.invitationRepository
			.findByCodeHashForUpdate(InvitationCodes.hash(request.inviteCode()))
			.filter((candidate) -> candidate.isUsable(now))
			.orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_INVALID));
		if (this.userAccountService.existsByEmail(request.email())) {
			throw new BusinessException(ErrorCode.EMAIL_ALREADY_REGISTERED);
		}
		User user = this.userAccountService.createUser(request.email(), request.password(), request.displayName());
		if (invitation.isGrantsAdmin()) {
			user.grantAdmin();
		}
		invitation.markUsed(user, now);
		this.invitationRepository.flush();
		return AuthUserResponse.from(user);
	}

}
