package com.docflow.analysis.domain;

import java.time.Instant;

/** {@code document_analyses} 행. 결과는 JSON 문자열 그대로 둔다. */
public record AnalysisRecord(Long id, Long projectId, Long documentId, Long versionId, Long baseVersionId,
		AnalysisType type, AnalysisStatus status, int attempts, String model, String resultJson, String errorCode,
		Instant createdAt, Instant updatedAt, Instant completedAt) {

	public boolean retryable() {
		return this.status == AnalysisStatus.FAILED && AnalysisFailure.isRetryable(this.errorCode);
	}

}
