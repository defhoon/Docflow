package com.docflow.analysis.llm;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.RequestTimeoutException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.JsonOutputFormat;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.service.AiProperties;

/**
 * Claude API 호출 (공식 Java SDK, ADR-0009). 재시도는 SDK가 아니라 작업 큐가 맡는다({@code maxRetries(0)}): 시도 횟수와
 * 비용을 한곳에서 세기 위함이다. 구조화 출력(JSON Schema)으로 응답 형식을 고정한다.
 */
public class AnthropicLlmClient implements LlmClient {

	/** 거부(refusal) 시 서버 측 대체 모델 기능 베타 헤더. */
	static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);

	private final AnthropicClient client;

	private final boolean refusalFallback;

	public AnthropicLlmClient(AiProperties properties) {
		this.client = AnthropicOkHttpClient.builder()
			.apiKey(properties.apiKey())
			.timeout(properties.requestTimeout())
			.maxRetries(0)
			.build();
		this.refusalFallback = properties.refusalFallback();
	}

	@Override
	public LlmResponse complete(LlmRequest request) throws LlmException {
		JsonOutputFormat.Schema.Builder schema = JsonOutputFormat.Schema.builder();
		for (Map.Entry<String, Object> entry : request.jsonSchema().entrySet()) {
			schema.putAdditionalProperty(entry.getKey(), JsonValue.from(entry.getValue()));
		}
		MessageCreateParams.Builder params = MessageCreateParams.builder()
			.model(request.model())
			.maxTokens(request.maxTokens())
			.system(request.system())
			.addUserMessage(request.user())
			.outputConfig(OutputConfig.builder().format(JsonOutputFormat.builder().schema(schema.build()).build()).build());
		if (this.refusalFallback) {
			params.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
		}
		Message message;
		try {
			message = this.client.messages().create(params.build());
		}
		catch (RateLimitException | InternalServerException | RequestTimeoutException ex) {
			// 429, 5xx(과부하·일시 중단·게이트웨이 시간 초과 포함), 408
			throw new LlmException(AnalysisFailure.AI_API_ERROR, true, "Claude API transient error: " + ex.statusCode());
		}
		catch (UnauthorizedException | PermissionDeniedException ex) {
			throw new LlmException(AnalysisFailure.AI_NOT_CONFIGURED, false, "Claude API auth error: " + ex.statusCode());
		}
		catch (BadRequestException | NotFoundException ex) {
			log.warn("Claude API rejected request: status={}, type={}", ex.statusCode(),
					ex.errorType().map(Object::toString).orElse("unknown"));
			throw new LlmException(AnalysisFailure.AI_API_ERROR, false, "Claude API request error: " + ex.statusCode());
		}
		catch (AnthropicServiceException ex) {
			boolean retry = ex.statusCode() >= 500 || ex.statusCode() == 408 || ex.statusCode() == 409;
			throw new LlmException(AnalysisFailure.AI_API_ERROR, retry, "Claude API error: " + ex.statusCode());
		}
		catch (AnthropicIoException ex) {
			throw new LlmException(AnalysisFailure.AI_API_ERROR, true, "Claude API connection error");
		}
		long input = message.usage().inputTokens();
		long output = message.usage().outputTokens();
		StopReason stop = message.stopReason().orElse(null);
		if (StopReason.REFUSAL.equals(stop)) {
			throw new LlmException(AnalysisFailure.AI_REFUSED, false, "Claude refused the request", input, output);
		}
		if (StopReason.MAX_TOKENS.equals(stop)) {
			throw new LlmException(AnalysisFailure.AI_OUTPUT_INVALID, false, "Claude output truncated", input, output);
		}
		StringBuilder text = new StringBuilder();
		for (ContentBlock block : message.content()) {
			block.text().ifPresent((t) -> text.append(t.text()));
		}
		return new LlmResponse(text.toString(), message.model().asString(), input, output);
	}

}
