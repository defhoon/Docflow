package com.docflow.analysis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

import com.docflow.analysis.llm.LlmClient;
import com.docflow.analysis.llm.LlmException;
import com.docflow.analysis.llm.LlmRequest;
import com.docflow.analysis.llm.LlmResponse;

/** 테스트용 LLM. 실제 API를 호출하지 않고 요청을 기록하며 지정한 응답·예외를 돌려준다. */
public class FakeLlmClient implements LlmClient {

	@FunctionalInterface
	public interface Responder {

		LlmResponse respond(LlmRequest request) throws LlmException;

	}

	public final List<LlmRequest> requests = Collections.synchronizedList(new ArrayList<>());

	private volatile Responder responder = (request) -> json("{\"summary\":\"요약\",\"keyPoints\":[],\"keyFacts\":[]}");

	public void respond(Responder responder) {
		this.responder = responder;
	}

	public void respondJson(Function<LlmRequest, String> json) {
		this.responder = (request) -> json(json.apply(request));
	}

	public void reset() {
		this.requests.clear();
		this.responder = (request) -> json("{\"summary\":\"요약\",\"keyPoints\":[],\"keyFacts\":[]}");
	}

	public static LlmResponse json(String json) {
		return new LlmResponse(json, "claude-sonnet-5-5", 1000, 200);
	}

	@Override
	public LlmResponse complete(LlmRequest request) throws LlmException {
		this.requests.add(request);
		return this.responder.respond(request);
	}

}
