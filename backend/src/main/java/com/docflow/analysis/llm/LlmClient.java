package com.docflow.analysis.llm;

/** LLM 호출 경계. 운영은 {@link AnthropicLlmClient}, 테스트·CI는 가짜 구현을 쓴다(실제 API 호출 없음). */
public interface LlmClient {

	LlmResponse complete(LlmRequest request) throws LlmException;

}
