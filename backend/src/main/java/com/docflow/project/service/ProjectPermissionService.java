package com.docflow.project.service;

import org.springframework.stereotype.Service;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectRole;

/**
 * 작업별 권한 판단 (SPEC-02 §7.2, SPEC-01 §7.8 원칙 2). 작업별 권한 규칙은 이 컴포넌트에만 둔다.
 * <p>
 * 모든 메서드는 {@link ProjectService#getAccessibleProject}로 접근 확인을 통과한 프로젝트에만 사용한다. 접근 확인을
 * 통과했다면 Owner가 아닌 사용자는 MEMBER다.
 */
@Service
public class ProjectPermissionService {

	public ProjectRole resolveRole(Project accessibleProject, Long userId) {
		return accessibleProject.isOwnedBy(userId) ? ProjectRole.OWNER : ProjectRole.MEMBER;
	}

	public void requireOwner(Project accessibleProject, Long userId) {
		if (resolveRole(accessibleProject, userId) != ProjectRole.OWNER) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
	}

}
