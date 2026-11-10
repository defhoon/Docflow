package com.docflow.invitation.service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.invitation.domain.Invitation;
import com.docflow.invitation.domain.InvitationCodes;
import com.docflow.invitation.dto.InvitationResponse;
import com.docflow.invitation.dto.IssuedInvitationResponse;
import com.docflow.invitation.repository.InvitationRepository;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/** 초대코드 발급·폐기·조회 (#26). 관리자(ADMIN)만 사용한다. 코드 원문은 로그에 남기지 않는다(최초 관리자 코드 제외). */
@Service
public class InvitationService {

	public static final int DEFAULT_EXPIRES_IN_HOURS = 72;

	private static final Logger log = LoggerFactory.getLogger(InvitationService.class);

	private final InvitationRepository invitationRepository;

	private final UserAccountService userAccountService;

	public InvitationService(InvitationRepository invitationRepository, UserAccountService userAccountService) {
		this.invitationRepository = invitationRepository;
		this.userAccountService = userAccountService;
	}

	@Transactional
	public IssuedInvitationResponse issue(Long sessionUserId, Integer expiresInHours, String note) {
		User admin = requireAdmin(sessionUserId);
		int hours = (expiresInHours == null) ? DEFAULT_EXPIRES_IN_HOURS : expiresInHours;
		String code = InvitationCodes.generate();
		Invitation invitation = this.invitationRepository.save(Invitation.issue(InvitationCodes.hash(code), false,
				admin, StringUtils.hasText(note) ? note.strip() : null, Instant.now().plus(Duration.ofHours(hours))));
		log.info("Invitation issued: invitationId={}, issuedBy={}", invitation.getId(), sessionUserId);
		return new IssuedInvitationResponse(invitation.getId(), code, invitation.getExpiresAt());
	}

	@Transactional
	public void revoke(Long sessionUserId, Long invitationId) {
		requireAdmin(sessionUserId);
		Invitation invitation = this.invitationRepository.findById(invitationId)
			.orElseThrow(() -> new BusinessException(ErrorCode.INVITATION_NOT_FOUND));
		if (invitation.revoke(Instant.now())) {
			log.info("Invitation revoked: invitationId={}, revokedBy={}", invitationId, sessionUserId);
		}
	}

	@Transactional(readOnly = true)
	public List<InvitationResponse> findRecent(Long sessionUserId) {
		requireAdmin(sessionUserId);
		Instant now = Instant.now();
		return this.invitationRepository.findRecent(Limit.of(100))
			.stream()
			.map((invitation) -> InvitationResponse.from(invitation, now))
			.toList();
	}

	/**
	 * 최초 운영 관리자용 코드 (#26 §최초 관리자). 사용자가 한 명도 없을 때만 호출한다. 이전에 발급된 미사용 관리자 코드는
	 * 폐기한다(원문을 다시 알 수 없으므로).
	 */
	@Transactional
	public String issueBootstrapAdminCode(Duration validity) {
		Instant now = Instant.now();
		this.invitationRepository.findUnusedAdminInvitations().forEach((invitation) -> invitation.revoke(now));
		String code = InvitationCodes.generate();
		this.invitationRepository
			.save(Invitation.issue(InvitationCodes.hash(code), true, null, "최초 운영 관리자", now.plus(validity)));
		return code;
	}

	private User requireAdmin(Long sessionUserId) {
		return this.userAccountService.findActiveById(sessionUserId)
			.filter(User::isAdmin)
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCESS_DENIED));
	}

}
