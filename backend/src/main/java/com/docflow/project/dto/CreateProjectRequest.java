package com.docflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.docflow.project.domain.ProjectText;

/**
 * 프로젝트 생성 요청 (SPEC-01 §5.5, §6.3).
 * <p>
 * 정규화는 Bean Validation 전에 이 생성자에서 한 번만 수행하며, 길이는 정규화 이후 값의 UTF-16 길이로 검사한다.
 * Owner는 요청으로 받지 않는다. 정의되지 않은 필드({@code ownerId} 등)는 역직렬화 시 무시된다.
 */
public record CreateProjectRequest(
		@NotBlank(message = "프로젝트 이름을 입력해 주세요.")
		@Size(max = 100, message = "프로젝트 이름은 100자 이하로 입력해 주세요.") @ProjectNameCharacters String name,
		@Size(max = 1000, message = "설명은 1000자 이하로 입력해 주세요.") @ProjectDescriptionCharacters String description) {

	public CreateProjectRequest {
		name = ProjectText.trimEdges(name);
		description = ProjectText.trimEdgesToNull(description);
	}

}
