package com.docflow.analysis.dto;

import java.time.Instant;

import tools.jackson.databind.JsonNode;

/**
 * 버전 분석 상태와 결과 (SPEC-11 §3, SPEC-14).
 *
 * @param status {@code NONE}(작업 없음), {@code PENDING}, {@code PROCESSING}, {@code COMPLETED}, {@code FAILED}
 * @param errorCode 실패 사유 ({@code AnalysisFailure})
 * @param retryable 다시 요청할 수 있는지
 * @param aiEnabled 프로젝트 AI 기능 사용 여부
 * @param supported 이 버전(조합)을 분석할 수 있는 형식인지(PDF)
 * @param result 완료 결과 (종류별 JSON)
 * @param model 결과를 만든 모델 ID
 */
public record AnalysisResponse(String status, String errorCode, boolean retryable, boolean aiEnabled,
		boolean supported, JsonNode result, String model, Instant updatedAt, Instant completedAt) {
}
