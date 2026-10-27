package com.docflow.project.dto;

import java.time.Instant;

import com.docflow.project.domain.ProjectMember;
import com.docflow.project.domain.ProjectRole;
import com.docflow.user.domain.User;

/**
 * 구성원 응답 (SPEC-02 §6.3). 이메일을 포함하지 않는다 (결정 Q3). Owner는 구성원 행이 없으므로 {@code addedAt}이
 * {@code null}이다.
 */
public record MemberResponse(Long userId, String displayName, ProjectRole role, Instant addedAt) {

	public static MemberResponse owner(User owner) {
		return new MemberResponse(owner.getId(), owner.getDisplayName(), ProjectRole.OWNER, null);
	}

	public static MemberResponse member(ProjectMember member) {
		return new MemberResponse(member.getUser().getId(), member.getUser().getDisplayName(), ProjectRole.MEMBER,
				member.getCreatedAt());
	}

}
