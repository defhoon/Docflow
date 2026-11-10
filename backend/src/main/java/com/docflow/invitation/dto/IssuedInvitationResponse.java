package com.docflow.invitation.dto;

import java.time.Instant;

/** 발급 응답. 코드 원문은 이 응답에서 한 번만 제공되며 다시 조회할 수 없다. */
public record IssuedInvitationResponse(Long id, String code, Instant expiresAt) {
}
