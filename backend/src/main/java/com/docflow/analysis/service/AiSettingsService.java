package com.docflow.analysis.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.docflow.analysis.dto.AiSettingsResponse;
import com.docflow.analysis.repository.ProjectAiSettingsRepository;
import com.docflow.global.error.BusinessException;
import com.docflow.global.error.ErrorCode;
import com.docflow.project.domain.Project;
import com.docflow.project.domain.ProjectRole;
import com.docflow.project.service.ProjectPermissionService;
import com.docflow.project.service.ProjectService;

/**
 * 프로젝트 AI 설정 (SPEC-11 §3). 구성원은 상태를 볼 수 있고 Owner만 바꾼다. 켤 때는 외부 전송 안내 동의가 필요하며, 동의 시각과
 * 사용자를 남긴다. 기본값은 꺼짐이다.
 */
@Service
public class AiSettingsService {

	static final String PROVIDER = "Anthropic Claude API";

	private final ProjectService projectService;

	private final ProjectPermissionService permissionService;

	private final ProjectAiSettingsRepository repository;

	private final AiProperties properties;

	public AiSettingsService(ProjectService projectService, ProjectPermissionService permissionService,
			ProjectAiSettingsRepository repository, AiProperties properties) {
		this.projectService = projectService;
		this.permissionService = permissionService;
		this.repository = repository;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public AiSettingsResponse get(Long projectId, Long userId) {
		Project project = this.projectService.getAccessibleProject(projectId, userId);
		return response(project, userId);
	}

	@Transactional
	public AiSettingsResponse update(Long projectId, Long userId, boolean enabled, boolean consent) {
		Project project = this.projectService.getAccessibleProject(projectId, userId);
		this.permissionService.requireOwner(project, userId);
		if (enabled) {
			if (!this.properties.available()) {
				throw new BusinessException(ErrorCode.AI_NOT_AVAILABLE);
			}
			if (!consent) {
				throw new BusinessException(ErrorCode.AI_CONSENT_REQUIRED);
			}
		}
		this.repository.update(projectId, enabled, userId);
		return response(project, userId);
	}

	private AiSettingsResponse response(Project project, Long userId) {
		ProjectAiSettingsRepository.Settings settings = this.repository.find(project.getId());
		boolean owner = this.permissionService.resolveRole(project, userId) == ProjectRole.OWNER;
		return new AiSettingsResponse(settings.enabled(), this.properties.available(), owner, settings.enabledAt(),
				settings.enabledByName(), PROVIDER, this.properties.model());
	}

}
