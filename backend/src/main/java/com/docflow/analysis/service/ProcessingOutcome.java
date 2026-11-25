package com.docflow.analysis.service;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.repository.AnalysisRepository.Usage;

/** 작업 처리 결과. */
public sealed interface ProcessingOutcome {

	Usage usage();

	record Completed(String model, String resultJson, Usage usage) implements ProcessingOutcome {
	}

	/** {@code transientFailure}이면 남은 시도 횟수 안에서 나중에 다시 실행한다. */
	record Failed(AnalysisFailure failure, boolean transientFailure, Usage usage) implements ProcessingOutcome {
	}

}
