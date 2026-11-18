package com.docflow.document.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;

import com.docflow.document.domain.Document;
import com.docflow.project.domain.ProjectText;
import com.docflow.project.dto.ProjectDescriptionCharacters;

/**
 * 문서 설명 변경 요청 ({@code application/json}, SPEC-09 §6). {@code description} 키는 필수다. 값이 {@code null}·빈
 * 값·공백뿐이면 설명을 지운다. 키가 없는 본문(예: {@code {"status":"ACTIVE"}})은 400으로 거부해 실수로 설명이 지워지지 않게
 * 한다. 정의되지 않은 필드(상태 등)는 바인딩하지 않으므로 이 요청으로는 설명 외에 아무것도 바뀌지 않는다.
 */
public record UpdateDocumentDescriptionRequest(@JsonProperty(value = "description", required = true)
		@Size(max = Document.MAX_DESCRIPTION_LENGTH, message = "설명은 2000자 이하로 입력해 주세요.") @ProjectDescriptionCharacters String description) {

	public UpdateDocumentDescriptionRequest {
		description = ProjectText.trimEdgesToNull(description);
	}

}
