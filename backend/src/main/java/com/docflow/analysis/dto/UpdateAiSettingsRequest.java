package com.docflow.analysis.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotNull;

/**
 * @param enabled 켜기·끄기
 * @param consent 켤 때 필수: 문서 내용이 외부 AI API로 전송된다는 안내에 동의
 */
public record UpdateAiSettingsRequest(@JsonProperty(required = true) @NotNull Boolean enabled, Boolean consent) {
}
