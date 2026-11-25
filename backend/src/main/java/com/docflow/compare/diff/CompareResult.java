package com.docflow.compare.diff;

import java.util.List;

/**
 * 두 버전 비교 결과 (SPEC-12 §5). 변경 위치는 결정론적 Diff로만 정하며, 상자 좌표는 SPEC-10 추출 결과의 원본 단어 상자다.
 *
 * @param identical 본문 변경 없음(공백·줄바꿈·쪽 번호 차이만 있는 경우 포함)
 * @param counts 변경 유형별 개수
 * @param changes 변경 목록(문서 순서). {@code id}는 0부터
 * @param blocks 나란히 보기용 문서 순서 블록
 * @param coarseBlocks 너무 커서 단어 단위로 나누지 않은 블록 수
 */
public record CompareResult(boolean identical, Counts counts, List<Change> changes, List<Block> blocks,
		int coarseBlocks) {

	public record Counts(int added, int deleted, int modified) {

		public int total() {
			return this.added + this.deleted + this.modified;
		}

	}

	public enum ChangeType {

		ADDED, DELETED, MODIFIED

	}

	/**
	 * @param base 이전 버전 쪽(ADDED이면 빈 값)
	 * @param target 이후 버전 쪽(DELETED이면 빈 값)
	 */
	public record Change(int id, ChangeType type, Side base, Side target) {
	}

	/**
	 * @param text 변경된 텍스트
	 * @param pages 페이지 번호(1부터)
	 * @param boxes 강조 상자(줄 단위로 합침). 위치를 확정할 수 없는 단어는 넣지 않는다
	 * @param location {@code FULL}(모든 단어 위치 확인), {@code PARTIAL}, {@code NONE}, 빈 쪽은 {@code EMPTY}
	 */
	public record Side(String text, List<Integer> pages, List<Box> boxes, String location) {

		public static final Side EMPTY = new Side("", List.of(), List.of(), "EMPTY");

	}

	/** @param page 페이지 번호(1부터), {@code rect} {@code [x1,y1,x2,y2]}: CropBox 왼쪽 아래 기준 사용자 공간(pt) */
	public record Box(int page, float[] rect) {
	}

	/**
	 * 문서 순서 블록. {@code EQUAL}은 같은 문단 묶음(앞·뒤 문단 일부만), {@code CHANGED}는 양쪽 구간.
	 */
	public record Block(String type, int paragraphCount, String firstText, String lastText, List<Segment> base,
			List<Segment> target) {
	}

	/** @param op {@code EQUAL}, {@code INSERT}, {@code DELETE}, {@code MODIFY}. {@code changeId}는 변경 구간일 때만 */
	public record Segment(String op, String text, Integer changeId) {
	}

}
