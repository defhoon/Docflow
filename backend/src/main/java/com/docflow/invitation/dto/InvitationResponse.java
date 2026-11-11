package com.docflow.invitation.dto;

import java.time.Instant;

import com.docflow.invitation.domain.Invitation;
import com.docflow.invitation.domain.InvitationStatus;

/** 초대코드 목록 항목. 코드 원문·해시는 포함하지 않는다. */
public record InvitationResponse(Long id, String note, InvitationStatus status, boolean grantsAdmin,
		Instant createdAt, Instant expiresAt, Instant usedAt, String usedByDisplayName) {

	public static InvitationResponse from(Invitation invitation, Instant now) {
		return new InvitationResponse(invitation.getId(), invitation.getNote(), invitation.status(now),
				invitation.isGrantsAdmin(), invitation.getCreatedAt(), invitation.getExpiresAt(), invitation.getUsedAt(),
				invitation.getUsedBy() == null ? null : invitation.getUsedBy().getDisplayName());
	}

}
