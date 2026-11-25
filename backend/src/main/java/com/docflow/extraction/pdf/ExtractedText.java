package com.docflow.extraction.pdf;

import java.util.List;

/**
 * PDF에서 추출한 텍스트와 원본 위치 (SPEC-10 §6). 단어가 기본 단위이며, 줄은 단어 범위를, 정규화 결과는 단어 색인을 가리키므로
 * 어떤 가공을 거쳐도 원본 좌표로 되돌아갈 수 있다.
 * <p>
 * 좌표는 PDF 사용자 공간(포인트, 왼쪽 아래 원점)에서 페이지 CropBox 왼쪽 아래를 원점으로 옮긴 값이다. 페이지 회전은 적용하지
 * 않는다(표시 단계에서 적용). 직립하지 않은 글자(회전된 텍스트 등)는 위치를 확정할 수 없으므로 상자가 없다.
 *
 * @param pages 페이지 정보 (0부터 색인)
 * @param words 읽기 순서의 단어
 * @param lines 읽기 순서의 줄
 */
public record ExtractedText(List<Page> pages, List<Word> words, List<Line> lines) {

	/**
	 * @param width CropBox 너비(pt)
	 * @param height CropBox 높이(pt)
	 * @param rotation 페이지 {@code /Rotate} (0, 90, 180, 270)
	 */
	public record Page(float width, float height, int rotation) {
	}

	/**
	 * @param text 단어 원문 (공백 없음)
	 * @param page 페이지 색인 (0부터)
	 * @param box {@code [x1, y1, x2, y2]} 또는 위치를 확정할 수 없으면 {@code null}
	 */
	public record Word(String text, int page, float[] box) {

		public boolean located() {
			return this.box != null;
		}

	}

	/**
	 * @param page 페이지 색인
	 * @param start 첫 단어 색인
	 * @param end 마지막 단어 다음 색인 (배타)
	 * @param paragraphStart 이 줄에서 새 문단이 시작한다
	 * @param artifact 쪽 번호 등 머리말·꼬리말로 판단한 줄 (비교·요약 본문에서 제외)
	 */
	public record Line(int page, int start, int end, boolean paragraphStart, boolean artifact) {
	}

	public int pageCount() {
		return this.pages.size();
	}

	public int charCount() {
		int count = 0;
		for (Word word : this.words) {
			count += word.text().length();
		}
		return count;
	}

	/**
	 * 사람이 읽는 순서의 본문. 페이지마다 {@code [p.N]} 표시를 두고 문단은 빈 줄로 구분한다. 머리말·꼬리말 줄은 뺀다. AI 요약
	 * 입력에 쓴다.
	 */
	public String plainTextWithPageMarkers() {
		StringBuilder text = new StringBuilder();
		int currentPage = -1;
		for (Line line : this.lines) {
			if (line.artifact()) {
				continue;
			}
			if (line.page() != currentPage) {
				currentPage = line.page();
				if (!text.isEmpty()) {
					text.append("\n\n");
				}
				text.append("[p.").append(currentPage + 1).append("]\n");
			}
			else if (line.paragraphStart()) {
				text.append("\n\n");
			}
			else {
				text.append('\n');
			}
			for (int i = line.start(); i < line.end(); i++) {
				if (i > line.start()) {
					text.append(' ');
				}
				text.append(this.words.get(i).text());
			}
		}
		return text.toString();
	}

}
