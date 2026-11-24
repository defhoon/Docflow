package com.docflow.analysis.service;

import java.util.List;

/**
 * 문서 요약 결과 (SPEC-11 §6). 저장·응답 모두 이 형태다. 모든 값은 텍스트로만 표시한다(HTML 해석 없음).
 *
 * @param summary 핵심 요약
 * @param keyPoints 주요 내용과 근거 페이지
 * @param keyFacts 일정·수치·요구사항. 원문 인용({@code quote})이 원문에 실제로 있는 항목만 남긴다
 * @param meta 생성 정보
 */
public record SummaryResult(String summary, List<KeyPoint> keyPoints, List<KeyFact> keyFacts, Meta meta) {

	public record KeyPoint(String text, List<Integer> pages) {
	}

	/** @param type {@code DATE}, {@code NUMBER}, {@code REQUIREMENT}, {@code OTHER} */
	public record KeyFact(String type, String label, String value, String quote, Integer page) {
	}

	/**
	 * @param chunks 나눠 요약한 구간 수 (1이면 한 번에 요약)
	 * @param droppedFacts 원문 인용을 확인하지 못해 뺀 항목 수
	 * @param pageCount 문서 쪽수
	 */
	public record Meta(int chunks, int droppedFacts, int pageCount) {
	}

}
