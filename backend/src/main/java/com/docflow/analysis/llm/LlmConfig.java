package com.docflow.analysis.llm;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.service.AiProperties;

/**
 * LLM 클라이언트 구성. API 키가 없으면 호출하지 않는 클라이언트를 둔다(외부 연결 시도 없음). 테스트는 이 빈을 가짜로 바꾼다.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class LlmConfig {

	@Bean
	LlmClient llmClient(AiProperties properties) {
		if (properties.apiKey() == null) {
			return (request) -> {
				throw new LlmException(AnalysisFailure.AI_NOT_CONFIGURED, false, "Claude API key is not configured");
			};
		}
		return new AnthropicLlmClient(properties);
	}

}
