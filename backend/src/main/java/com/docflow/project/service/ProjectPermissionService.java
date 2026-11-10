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

	/**
	 * 문서 등록 권한 (SPEC-03 §7.1, 결정 Q1). 현재 OWNER·MEMBER 모두 허용하며, 접근 확인을 통과한 프로젝트에만
	 * 사용한다.
	 */
	public void requireDocumentCreator(Project accessibleProject, Long userId) {
		ProjectRole role = resolveRole(accessibleProject, userId);
		if (role != ProjectRole.OWNER && role != ProjectRole.MEMBER) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
	}

	/**
	 * 새 버전 등록 권한 (SPEC-04 §7.1, 결정 V3). 현재 OWNER·MEMBER 모두 허용하며, 문서 작성자가 아니어도 된다. Author·Reviewer
	 * 역할 모델이 정해지면(SPEC-06) 이 메서드만 바꾼다. 접근 확인을 통과한 프로젝트에만 사용한다.
	 */
	public void requireVersionUploader(Project accessibleProject, Long userId) {
		ProjectRole role = resolveRole(accessibleProject, userId);
		if (role != ProjectRole.OWNER && role != ProjectRole.MEMBER) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
	}

	/**
	 * 최종 버전 확정 권한 (SPEC-07 §4). 프로젝트 Owner만 허용한다. 접근 확인을 통과한 프로젝트에만 사용한다.
	 */
	public void requireDocumentFinalizer(Project accessibleProject, Long userId) {
		requireOwner(accessibleProject, userId);
	}

	/**
	 * 피드백 작성 권한 (SPEC-08 §4). OWNER·MEMBER 모두 허용한다. 접근 확인을 통과한 프로젝트에만 사용한다.
	 */
	public void requireFeedbackWriter(Project accessibleProject, Long userId) {
		ProjectRole role = resolveRole(accessibleProject, userId);
		if (role != ProjectRole.OWNER && role != ProjectRole.MEMBER) {
			throw new BusinessException(ErrorCode.ACCESS_DENIED);
		}
	}

}
