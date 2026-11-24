package com.docflow.analysis.service;

import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.llm.LlmClient;
import com.docflow.analysis.repository.AnalysisRepository.Usage;
import com.docflow.extraction.domain.ExtractionStatus;
import com.docflow.extraction.service.DocumentTextService;
import com.docflow.extraction.service.VersionText;

/** 버전 요약 실행 (SPEC-11 §6): 텍스트 추출(SPEC-10) → 크기·예산 확인 → 요약 생성 → 검증. */
@Component
public class SummaryProcessor implements AnalysisProcessor {

	private final DocumentTextService documentTextService;

	private final AnalysisBudget budget;

	private final AiProperties properties;

	private final SummaryGenerator generator;

	private final JsonMapper jsonMapper;

	public SummaryProcessor(DocumentTextService documentTextService, AnalysisBudget budget, AiProperties properties,
			LlmClient llmClient, JsonMapper jsonMapper) {
		this.documentTextService = documentTextService;
		this.budget = budget;
		this.properties = properties;
		this.generator = new SummaryGenerator(llmClient, jsonMapper, properties);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public AnalysisType type() {
		return AnalysisType.SUMMARY;
	}

	@Override
	public ProcessingOutcome process(AnalysisRecord job) {
		VersionText text;
		try {
			text = this.documentTextService.getOrExtract(job.versionId());
		}
		catch (UncheckedIOException ex) {
			return new ProcessingOutcome.Failed(AnalysisFailure.FILE_UNAVAILABLE, true, Usage.NONE);
		}
		if (!text.hasText()) {
			return new ProcessingOutcome.Failed(textFailure(text.status()), text.status() == ExtractionStatus.TIMEOUT,
					Usage.NONE);
		}
		String source = text.text().plainTextWithPageMarkers();
		int estimated = SummaryGenerator.estimateTokens(source);
		if (estimated > this.properties.maxDocumentInputTokens()) {
			return new ProcessingOutcome.Failed(AnalysisFailure.DOCUMENT_TOO_LARGE, false, Usage.NONE);
		}
		var chunks = this.generator.chunks(source);
		int calls = SummaryGenerator.callsFor(chunks);
		long estimatedCost = this.properties.costMicros(estimated + (calls - 1L) * 4000,
				(long) calls * this.properties.maxOutputTokens());
		if (!this.budget.allows(job.projectId(), calls, estimatedCost)) {
			return new ProcessingOutcome.Failed(AnalysisFailure.QUOTA_EXCEEDED, false, Usage.NONE);
		}
		try {
			SummaryGenerator.Generated generated = this.generator.generate(source, text.pageCount());
			return new ProcessingOutcome.Completed(generated.model(),
					this.jsonMapper.writeValueAsString(generated.result()), generated.usage());
		}
		catch (SummaryGenerator.GenerationException ex) {
			return new ProcessingOutcome.Failed(ex.llm().failure(), ex.llm().isTransient(), ex.usage());
		}
	}

	static AnalysisFailure textFailure(ExtractionStatus status) {
		return switch (status) {
			case NO_TEXT -> AnalysisFailure.TEXT_NO_TEXT;
			case ENCRYPTED -> AnalysisFailure.TEXT_ENCRYPTED;
			case CORRUPTED -> AnalysisFailure.TEXT_CORRUPTED;
			case TOO_LARGE -> AnalysisFailure.TEXT_TOO_LARGE;
			case TIMEOUT -> AnalysisFailure.TEXT_TIMEOUT;
			case UNSUPPORTED_TYPE -> AnalysisFailure.UNSUPPORTED_TYPE;
			case EXTRACTED -> throw new IllegalArgumentException("not a failure");
		};
	}

}
