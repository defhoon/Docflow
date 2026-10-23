package com.docflow.project.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.dto.CreateProjectRequest;
import com.docflow.project.dto.ProjectResponse;
import com.docflow.project.dto.ProjectSummaryResponse;
import com.docflow.project.repository.ProjectRepository;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * 프로젝트 생성·조회와 접근 확인 (SPEC-01 §7).
 */
@Service
public class ProjectService {

	private final ProjectRepository projectRepository;

	private final UserAccountService userAccountService;

	public ProjectService(ProjectRepository projectRepository, UserAccountService userAccountService) {
		this.projectRepository = projectRepository;
		this.userAccountService = userAccountService;
	}

	/**
	 * 세션 사용자를 Owner로 지정해 프로젝트를 생성한다. IDENTITY 전략이라 INSERT는 {@code save()} 시점에 실행되며,
	 * 실패하면 트랜잭션 전체가 롤백된다.
	 */
	@Transactional
	public ProjectResponse create(Long sessionUserId, CreateProjectRequest request) {
		User owner = this.userAccountService.getReference(sessionUserId);
		Project project = this.projectRepository.save(Project.create(request.name(), request.description(), owner));
		return ProjectResponse.from(project);
	}

	@Transactional(readOnly = true)
	public List<ProjectSummaryResponse> findMine(Long sessionUserId) {
		return this.projectRepository.findAllByOwnerIdOrderByCreatedAtDescIdDesc(sessionUserId)
			.stream()
			.map(ProjectSummaryResponse::from)
			.toList();
	}

	@Transactional(readOnly = true)
	public ProjectResponse getProject(Long projectId, Long sessionUserId) {
		return ProjectResponse.from(getAccessibleProject(projectId, sessionUserId));
	}

	/**
	 * 프로젝트 접근 확인의 공통 진입점. 접근할 수 없으면 존재하지 않는 경우와 같은
	 * {@link ErrorCode#PROJECT_NOT_FOUND}를 던진다.
	 * <p>
	 * 이 메서드는 "프로젝트에 접근할 수 있는가"만 판단한다. 구성원 관리·문서 등록 등 작업별 권한은 이후 SPEC에서
	 * 이 확인 다음 단계로 별도 판단한다 (SPEC-01 §7.8).
	 */
	@Transactional(readOnly = true)
	public Project getAccessibleProject(Long projectId, Long sessionUserId) {
		return this.projectRepository.findByIdAndOwnerId(projectId, sessionUserId)
			.orElseThrow(() -> new BusinessException(ErrorCode.PROJECT_NOT_FOUND));
	}

}
