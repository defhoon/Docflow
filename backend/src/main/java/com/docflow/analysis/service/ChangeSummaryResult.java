package com.docflow.analysis.service;

import java.util.List;

/**
 * AI 변경 요약 결과 (SPEC-14 §5). 모든 항목은 SPEC-12 Diff의 변경 ID를 근거로 가진다. 근거가 없거나 존재하지 않는 변경을
 * 가리키는 항목은 저장하지 않는다. 변경 위치는 변경 ID로만 연결하며 AI가 정하지 않는다.
 *
 * @param overview 전체 변경 요약
 * @param added 추가된 주요 내용
 * @param deleted 삭제된 주요 내용
 * @param modified 수정된 주요 내용
 * @param keyChanges 일정·수치·요구사항 등 핵심 변경(이전 값 → 이후 값)
 * @param significance 변경 중요도 참고 설명
 */
public record ChangeSummaryResult(String overview, List<Item> added, List<Item> deleted, List<Item> modified,
		List<KeyChange> keyChanges, Significance significance, Meta meta) {

	public record Item(String text, List<Integer> changeIds) {
	}

	/** @param type {@code DATE}, {@code NUMBER}, {@code REQUIREMENT}, {@code OTHER} */
	public record KeyChange(String type, String label, String before, String after, List<Integer> changeIds) {
	}

	/** @param level {@code LOW}, {@code MEDIUM}, {@code HIGH} (변경 없음은 {@code NONE}) */
	public record Significance(String level, String reason) {
	}

	/**
	 * @param changeCount Diff 변경 수
	 * @param droppedItems 근거 변경을 확인하지 못해 뺀 항목 수
	 */
	public record Meta(int changeCount, int droppedItems) {
	}

}
