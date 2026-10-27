package com.docflow.project.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectMember;
import com.docflow.project.dto.AddMemberRequest;
import com.docflow.project.dto.MemberResponse;
import com.docflow.project.repository.ProjectMemberRepository;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 프로젝트 구성원 목록·추가·제거 (SPEC-02 §6, §7). 모든 작업은 접근 확인(404) → 작업별 권한(403) 순서로 판단한다.
 * <p>
 * 요청 이메일은 응답·로그에 남기지 않는다 (결정 Q3, D6).
 */
@Service
public class ProjectMemberService {

	private final ProjectService projectService;

	private final ProjectPermissionService projectPermissionService;

	private final ProjectMemberRepository projectMemberRepository;

	private final UserAccountService userAccountService;

	private final TransactionTemplate transactionTemplate;

	public ProjectMemberService(ProjectService projectService, ProjectPermissionService projectPermissionService,
			ProjectMemberRepository projectMemberRepository, UserAccountService userAccountService,
			PlatformTransactionManager transactionManager) {
		this.projectService = projectService;
		this.projectPermissionService = projectPermissionService;
		this.projectMemberRepository = projectMemberRepository;
		this.userAccountService = userAccountService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/** 구성원 목록: Owner가 첫 항목, MEMBER는 추가 순. */
	@Transactional(readOnly = true)
	public List<MemberResponse> findMembers(Long projectId, Long sessionUserId) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		List<MemberResponse> members = new ArrayList<>();
		members.add(MemberResponse.owner(project.getOwner()));
		this.projectMemberRepository.findAllWithUserByProjectId(projectId)
			.forEach((member) -> members.add(MemberResponse.member(member)));
		return members;
	}

	/**
	 * 구성원을 추가한다 (SPEC-02 §7.3).
	 * <p>
	 * 이 메서드에는 트랜잭션을 두지 않는다. 추가 작업은 {@link TransactionTemplate}으로 한 트랜잭션에서 실행하고, 동시
	 * 추가 경합으로 발생한 UNIQUE 위반은 트랜잭션이 롤백된 뒤 여기서 {@code 409}로 바꾼다. 같은 트랜잭션 안에서 잡으면
	 * rollback-only 표시 때문에 커밋 시 {@code UnexpectedRollbackException}이 발생한다. 커밋 시점에 발생한 위반도 이
	 * 지점에서 잡힌다. 다른 DB 오류는 그대로 전파해 {@code 500}으로 처리한다.
	 */
	public MemberResponse addMember(Long projectId, Long sessionUserId, AddMemberRequest request) {
		try {
			return this.transactionTemplate.execute((status) -> add(projectId, sessionUserId, request.email()));
		}
		catch (DataIntegrityViolationException ex) {
			if (ProjectMemberConstraints.isDuplicateMember(ex)) {
				throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
			}
			throw ex;
		}
	}

	private MemberResponse add(Long projectId, Long sessionUserId, String email) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireOwner(project, sessionUserId);

		// 미가입과 비활성 계정은 같은 응답으로 처리한다 (SPEC-02 §7.2, AC-MEMBER-08).
		User user = this.userAccountService.findByEmail(email)
			.filter(User::isEnabled)
			.orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
		if (project.isOwnedBy(user.getId())
				|| this.projectMemberRepository.existsByProjectIdAndUserId(projectId, user.getId())) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_ALREADY_EXISTS);
		}

		// IDENTITY 전략이라 INSERT는 save() 시점에 실행된다. 경합 시 UNIQUE 위반은 여기서 발생한다.
		ProjectMember member = this.projectMemberRepository.save(ProjectMember.create(project, user));
		return MemberResponse.member(member);
	}

	/**
	 * MEMBER를 제거한다 (SPEC-02 §7.4). 대상은 경로의 {@code projectId}와 {@code userId}로만 정한다. 권한 변경은 이
	 * 트랜잭션이 커밋된 뒤 시작되는 요청부터 적용된다 (결정 D5).
	 */
	@Transactional
	public void removeMember(Long projectId, Long sessionUserId, Long targetUserId) {
		Project project = this.projectService.getAccessibleProject(projectId, sessionUserId);
		this.projectPermissionService.requireOwner(project, sessionUserId);
		if (project.isOwnedBy(targetUserId)) {
			throw new BusinessException(ErrorCode.PROJECT_OWNER_NOT_REMOVABLE);
		}
		if (this.projectMemberRepository.deleteByProjectIdAndUserId(projectId, targetUserId) == 0) {
			throw new BusinessException(ErrorCode.PROJECT_MEMBER_NOT_FOUND);
		}
	}

}
