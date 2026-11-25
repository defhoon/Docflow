package com.docflow.analysis.llm;

/** 구조화 출력 응답. {@code json}은 스키마에 맞춘 JSON 문자열이다(검증은 호출 측). */
public record LlmResponse(String json, String model, long inputTokens, long outputTokens) {
}
