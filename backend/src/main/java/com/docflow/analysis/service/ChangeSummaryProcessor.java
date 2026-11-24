package com.docflow.analysis.service;

import java.io.UncheckedIOException;

import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.llm.LlmClient;
import com.docflow.analysis.repository.AnalysisRepository.Usage;
import com.docflow.compare.diff.CompareResult;
import com.docflow.compare.service.CompareTextUnavailableException;
import com.docflow.compare.service.VersionCompareService;
import com.docflow.extraction.domain.ExtractionStatus;

/**
 * 변경 요약 실행 (SPEC-14 §4): 결정론적 Diff(SPEC-12) → 변경이 없으면 호출 없이 완료 → 크기·예산 확인 → 요약 생성 → 근거
 * 검증.
 */
@Component
public class ChangeSummaryProcessor implements AnalysisProcessor {

	private final VersionCompareService compareService;

	private final AnalysisBudget budget;

	private final AiProperties properties;

	private final ChangeSummaryGenerator generator;

	private final JsonMapper jsonMapper;

	public ChangeSummaryProcessor(VersionCompareService compareService, AnalysisBudget budget, AiProperties properties,
			LlmClient llmClient, JsonMapper jsonMapper) {
		this.compareService = compareService;
		this.budget = budget;
		this.properties = properties;
		this.generator = new ChangeSummaryGenerator(llmClient, jsonMapper, properties);
		this.jsonMapper = jsonMapper;
	}

	@Override
	public AnalysisType type() {
		return AnalysisType.CHANGE_SUMMARY;
	}

	@Override
	public ProcessingOutcome process(AnalysisRecord job) {
		CompareResult diff;
		try {
			diff = this.compareService.compareVersions(job.baseVersionId(), job.versionId());
		}
		catch (CompareTextUnavailableException ex) {
			return new ProcessingOutcome.Failed(SummaryProcessor.textFailure(ex.status()),
					ex.status() == ExtractionStatus.TIMEOUT, Usage.NONE);
		}
		catch (UncheckedIOException ex) {
			return new ProcessingOutcome.Failed(AnalysisFailure.FILE_UNAVAILABLE, true, Usage.NONE);
		}
		if (diff.identical()) {
			return new ProcessingOutcome.Completed(null,
					this.jsonMapper.writeValueAsString(ChangeSummaryGenerator.unchanged()), Usage.NONE);
		}
		String input = ChangeSummaryGenerator.input(diff);
		int estimated = SummaryGenerator.estimateTokens(input);
		if (estimated > this.properties.maxRequestInputTokens()) {
			return new ProcessingOutcome.Failed(AnalysisFailure.COMPARE_TOO_LARGE, false, Usage.NONE);
		}
		if (!this.budget.allows(job.projectId(), 1, this.properties.costMicros(estimated, this.properties.maxOutputTokens()))) {
			return new ProcessingOutcome.Failed(AnalysisFailure.QUOTA_EXCEEDED, false, Usage.NONE);
		}
		try {
			ChangeSummaryGenerator.Generated generated = this.generator.generate(diff, input);
			return new ProcessingOutcome.Completed(generated.model(),
					this.jsonMapper.writeValueAsString(generated.result()), generated.usage());
		}
		catch (SummaryGenerator.GenerationException ex) {
			return new ProcessingOutcome.Failed(ex.llm().failure(), ex.llm().isTransient(), ex.usage());
		}
	}

}
