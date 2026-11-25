package com.docflow.analysis.service;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * AI 기능 설정 {@code docflow.ai.*} (SPEC-11 §7). 서비스 전체 스위치({@code enabled})와 API 키가 모두 있어야 프로젝트에서 켤 수
 * 있다. 비용·호출 상한은 모두 설정으로 바꿀 수 있다.
 *
 * @param enabled 서비스 전체 AI 기능 (기본 false)
 * @param apiKey Claude API 키 (환경변수 {@code ANTHROPIC_API_KEY}). 로그·응답에 쓰지 않는다
 * @param model 모델 ID (기본 {@code claude-sonnet-5-5})
 * @param maxOutputTokens 요청당 출력 토큰 상한 (기본 16,000; 사고 토큰 포함)
 * @param maxRequestInputTokens 요청당 추정 입력 토큰 상한, 넘으면 쪽 단위로 나눈다 (기본 150,000)
 * @param maxDocumentInputTokens 문서당 추정 입력 토큰 상한, 넘으면 {@code DOCUMENT_TOO_LARGE} (기본 400,000)
 * @param requestTimeout API 요청 시간 제한 (기본 180초)
 * @param maxAttempts 일시적 오류 재시도 포함 최대 시도 (기본 3)
 * @param retryDelay 첫 재시도 대기, 이후 2배씩 (기본 30초)
 * @param projectDailyCalls 프로젝트별 하루 API 호출 상한 (기본 50)
 * @param globalDailyCalls 서비스 전체 하루 API 호출 상한 (기본 300)
 * @param monthlyBudgetUsd 서비스 전체 월 추정 비용 상한 USD (기본 20)
 * @param inputPricePerMtok 입력 단가 USD/100만 토큰 (기본 2.0, Sonnet 5.5)
 * @param outputPricePerMtok 출력 단가 USD/100만 토큰 (기본 10.0, Sonnet 5.5)
 * @param staleAfter PROCESSING이 이 시간 넘게 지속되면 중단된 것으로 보고 되돌린다 (기본 15분)
 * @param refusalFallback 거부 응답 시 서버 측 대체 모델 사용 (기본 true)
 */
@ConfigurationProperties("docflow.ai")
public record AiProperties(Boolean enabled, String apiKey, String model, Integer maxOutputTokens,
		Integer maxRequestInputTokens, Integer maxDocumentInputTokens, Duration requestTimeout, Integer maxAttempts,
		Duration retryDelay, Integer projectDailyCalls, Integer globalDailyCalls, Double monthlyBudgetUsd,
		Double inputPricePerMtok, Double outputPricePerMtok, Duration staleAfter, Boolean refusalFallback) {

	public static final String DEFAULT_MODEL = "claude-sonnet-5-5";

	@ConstructorBinding
	public AiProperties {
		enabled = Boolean.TRUE.equals(enabled);
		apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey.strip() : null;
		model = (model != null && !model.isBlank()) ? model.strip() : DEFAULT_MODEL;
		maxOutputTokens = (maxOutputTokens != null) ? maxOutputTokens : 16_000;
		maxRequestInputTokens = (maxRequestInputTokens != null) ? maxRequestInputTokens : 150_000;
		maxDocumentInputTokens = (maxDocumentInputTokens != null) ? maxDocumentInputTokens : 400_000;
		requestTimeout = (requestTimeout != null) ? requestTimeout : Duration.ofSeconds(180);
		maxAttempts = (maxAttempts != null) ? maxAttempts : 3;
		retryDelay = (retryDelay != null) ? retryDelay : Duration.ofSeconds(30);
		projectDailyCalls = (projectDailyCalls != null) ? projectDailyCalls : 50;
		globalDailyCalls = (globalDailyCalls != null) ? globalDailyCalls : 300;
		monthlyBudgetUsd = (monthlyBudgetUsd != null) ? monthlyBudgetUsd : 20.0;
		inputPricePerMtok = (inputPricePerMtok != null) ? inputPricePerMtok : 2.0;
		outputPricePerMtok = (outputPricePerMtok != null) ? outputPricePerMtok : 10.0;
		staleAfter = (staleAfter != null) ? staleAfter : Duration.ofMinutes(15);
		refusalFallback = !Boolean.FALSE.equals(refusalFallback);
		if (maxOutputTokens < 1 || maxRequestInputTokens < 1000 || maxDocumentInputTokens < maxRequestInputTokens
				|| maxAttempts < 1 || projectDailyCalls < 0 || globalDailyCalls < 0 || monthlyBudgetUsd < 0) {
			throw new IllegalArgumentException("Invalid docflow.ai settings");
		}
	}

	/** 프로젝트에서 AI를 켤 수 있는 상태: 전체 스위치와 API 키. */
	public boolean available() {
		return this.enabled && this.apiKey != null;
	}

	/** 추정 비용(USD × 10^6). */
	public long costMicros(long inputTokens, long outputTokens) {
		return Math.round(inputTokens * this.inputPricePerMtok + outputTokens * this.outputPricePerMtok);
	}

	public long monthlyBudgetMicros() {
		return Math.round(this.monthlyBudgetUsd * 1_000_000);
	}

	@Override
	public String toString() {
		// API 키가 로그에 남지 않게 한다.
		return "AiProperties[enabled=" + this.enabled + ", apiKey=" + (this.apiKey != null ? "***" : "none") + ", model="
				+ this.model + "]";
	}

}
