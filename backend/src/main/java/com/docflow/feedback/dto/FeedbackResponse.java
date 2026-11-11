package com.docflow.feedback.dto;

import java.time.Instant;

import com.docflow.feedback.domain.Feedback;

/** 피드백 응답 (SPEC-08 §6). 작성자는 표시 이름만 포함한다. */
public record FeedbackResponse(Long id, int versionNumber, Author author, String content, Instant createdAt) {

	public record Author(Long id, String displayName) {
	}

	public static FeedbackResponse from(Feedback feedback) {
		return new FeedbackResponse(feedback.getId(), feedback.getDocumentVersion().getVersionNumber(),
				new Author(feedback.getAuthor().getId(), feedback.getAuthor().getDisplayName()), feedback.getContent(),
				feedback.getCreatedAt());
	}

}
