package com.docflow.extraction.pdf;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 비교·요약용 정규화 (SPEC-10 §6.3). 결과 토큰은 원본 단어 색인을 그대로 가지므로 정규화 뒤에도 원본 좌표로 돌아갈 수 있다.
 * <ul>
 * <li>머리말·꼬리말 줄(쪽 번호) 제외</li>
 * <li>줄 끝 하이픈으로 나뉜 영어 단어 합치기 ({@code docu-} + {@code ment} → {@code document}, 단어 색인 2개)</li>
 * <li>따옴표·대시 변형 통일. 줄바꿈과 연속 공백은 토큰 경계로만 쓰이므로 비교에 영향이 없다</li>
 * </ul>
 */
public final class TextNormalizer {

	private TextNormalizer() {
	}

	/**
	 * @param text 비교용 문자열
	 * @param words 원본 단어 색인 (오름차순, 1개 이상)
	 */
	public record Token(String text, int[] words) {
	}

	/**
	 * @param tokens 문단 토큰 (1개 이상)
	 * @param page 첫 토큰의 페이지 색인
	 */
	public record Paragraph(List<Token> tokens, int page) {

		public String joined() {
			StringBuilder joined = new StringBuilder();
			for (Token token : this.tokens) {
				if (!joined.isEmpty()) {
					joined.append(' ');
				}
				joined.append(token.text());
			}
			return joined.toString();
		}

	}

	public static List<Paragraph> paragraphs(ExtractedText text) {
		List<Paragraph> paragraphs = new ArrayList<>();
		List<Token> current = new ArrayList<>();
		int currentPage = 0;
		ExtractedText.Line previous = null;
		for (ExtractedText.Line line : text.lines()) {
			if (line.artifact()) {
				continue;
			}
			if (line.paragraphStart() && !current.isEmpty()) {
				paragraphs.add(new Paragraph(List.copyOf(current), currentPage));
				current = new ArrayList<>();
			}
			for (int i = line.start(); i < line.end(); i++) {
				String word = normalize(text.words().get(i).text());
				if (word.isEmpty()) {
					continue;
				}
				if (current.isEmpty()) {
					currentPage = text.words().get(i).page();
				}
				boolean firstInLine = (i == line.start());
				if (firstInLine && previous != null && !current.isEmpty() && joinsHyphenated(current.getLast(), word)) {
					Token last = current.removeLast();
					String joined = last.text().substring(0, last.text().length() - 1) + word;
					int[] indexes = Arrays.copyOf(last.words(), last.words().length + 1);
					indexes[indexes.length - 1] = i;
					current.add(new Token(joined, indexes));
				}
				else {
					current.add(new Token(word, new int[] { i }));
				}
			}
			previous = line;
		}
		if (!current.isEmpty()) {
			paragraphs.add(new Paragraph(List.copyOf(current), currentPage));
		}
		return List.copyOf(paragraphs);
	}

	/** 앞 줄 끝이 "영문자-"이고 다음 줄이 영어 소문자로 시작할 때만 합친다(복합어 "e-mail" 같은 줄 중간 하이픈은 그대로). */
	private static boolean joinsHyphenated(Token last, String next) {
		String previous = last.text();
		int length = previous.length();
		return length >= 3 && previous.charAt(length - 1) == '-' && isLatinLetter(previous.charAt(length - 2))
				&& Character.isLowerCase(next.charAt(0)) && isLatinLetter(next.charAt(0));
	}

	private static boolean isLatinLetter(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}

	static String normalize(String word) {
		StringBuilder result = new StringBuilder(word.length());
		for (int i = 0; i < word.length(); i++) {
			char c = word.charAt(i);
			switch (c) {
				case '‘', '’', '‚', '′' -> result.append('\'');
				case '“', '”', '„', '″' -> result.append('"');
				case '‐', '‑', '‒', '–', '—', '−' -> result.append('-');
				case '­' -> {
					// 소프트 하이픈은 표시용이므로 버린다.
				}
				default -> result.append(c);
			}
		}
		return result.toString();
	}

}
