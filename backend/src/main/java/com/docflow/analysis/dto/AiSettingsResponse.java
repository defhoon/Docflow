package com.docflow.analysis.dto;

import java.time.Instant;

/**
 * 프로젝트 AI 설정 (SPEC-11 §3).
 *
 * @param enabled 이 프로젝트에서 AI 기능 사용 여부
 * @param available 서비스에서 AI 기능을 쓸 수 있는지(전체 스위치·API 키). false이면 켤 수 없다
 * @param canManage 현재 사용자가 설정을 바꿀 수 있는지(Owner)
 * @param enabledAt 마지막으로 켠 시각(외부 전송 동의 시각)
 * @param enabledByName 마지막으로 켠 사용자 이름
 * @param provider 외부 처리자 안내용 이름
 * @param model 사용 모델 ID
 */
public record AiSettingsResponse(boolean enabled, boolean available, boolean canManage, Instant enabledAt,
		String enabledByName, String provider, String model) {
}
