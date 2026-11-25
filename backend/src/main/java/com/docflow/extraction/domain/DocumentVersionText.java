package com.docflow.extraction.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 버전별 추출 결과 (SPEC-10 §5). 버전 ID가 기본 키이므로 버전당 한 행만 있다. 본문은 압축 JSON이며 서비스가 해석한다.
 */
@Entity
@Table(name = "document_version_texts")
public class DocumentVersionText {

	@Id
	@Column(name = "version_id")
	private Long versionId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 20)
	private ExtractionStatus status;

	@Column(name = "extractor_version", nullable = false)
	private int extractorVersion;

	@Column(name = "page_count", nullable = false)
	private int pageCount;

	@Column(name = "word_count", nullable = false)
	private int wordCount;

	@Column(name = "char_count", nullable = false)
	private int charCount;

	@Column(name = "content")
	private byte[] content;

	@Column(name = "created_at", nullable = false, insertable = false, updatable = false)
	private Instant createdAt;

	protected DocumentVersionText() {
	}

	public Long getVersionId() {
		return this.versionId;
	}

	public ExtractionStatus getStatus() {
		return this.status;
	}

	public int getExtractorVersion() {
		return this.extractorVersion;
	}

	public int getPageCount() {
		return this.pageCount;
	}

	public int getWordCount() {
		return this.wordCount;
	}

	public int getCharCount() {
		return this.charCount;
	}

	public byte[] getContent() {
		return this.content;
	}

}
