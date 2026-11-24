package com.docflow.analysis.llm;

import java.util.Map;

/**
 * 구조화 출력 요청. {@code system}은 운영자 지시, {@code user}는 신뢰할 수 없는 문서 데이터를 담은 사용자 메시지다.
 *
 * @param jsonSchema 출력 JSON Schema (structured outputs)
 */
public record LlmRequest(String model, String system, String user, Map<String, Object> jsonSchema, int maxTokens) {
}
