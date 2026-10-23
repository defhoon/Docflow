package com.docflow.project.domain;

/**
 * 프로젝트 이름·설명의 정규화와 허용 문자 규칙 (SPEC-01 §5.5).
 * <p>
 * DTO 정규화, Bean Validation, 단위 테스트가 같은 규칙을 공유하도록 한 곳에 둔다.
 */
public final class ProjectText {

	private ProjectText() {
	}

	/**
	 * 앞뒤의 공백 문자를 제거한다. {@code String.strip()}이 제거하지 않는 NBSP(U+00A0), U+2007, U+202F도
	 * 공백으로 본다. 내부 문자는 변경하지 않는다.
	 */
	public static String trimEdges(String value) {
		if (value == null) {
			return null;
		}
		int start = 0;
		int end = value.length();
		while (start < end && isEdgeSpace(value.charAt(start))) {
			start++;
		}
		while (end > start && isEdgeSpace(value.charAt(end - 1))) {
			end--;
		}
		return value.substring(start, end);
	}

	/**
	 * {@link #trimEdges(String)} 결과가 비어 있으면 {@code null}을 반환한다.
	 */
	public static String trimEdgesToNull(String value) {
		String trimmed = trimEdges(value);
		return (trimmed == null || trimmed.isEmpty()) ? null : trimmed;
	}

	/**
	 * 이름 허용 문자 규칙. {@code null}·빈 값은 필수 검증({@code @NotBlank})이 담당하므로 통과시킨다.
	 * <ul>
	 * <li>거부: 제어 문자(Cc), 줄·문단 구분자(Zl, Zp), 폭 없는 문자, 양방향 서식 제어</li>
	 * <li>보이는 문자(Z·Cc·Cf가 아닌 문자)가 하나 이상 있어야 한다</li>
	 * </ul>
	 */
	public static boolean isAllowedName(String value) {
		if (value == null || value.isEmpty()) {
			return true;
		}
		boolean hasVisible = false;
		for (int i = 0; i < value.length();) {
			int codePoint = value.codePointAt(i);
			if (isForbiddenInName(codePoint)) {
				return false;
			}
			if (isVisible(codePoint)) {
				hasVisible = true;
			}
			i += Character.charCount(codePoint);
		}
		return hasVisible;
	}

	/**
	 * 설명 허용 문자 규칙. 제어 문자 중 탭·줄바꿈({@code \t}, {@code \n}, {@code \r})만 허용한다.
	 */
	public static boolean isAllowedDescription(String value) {
		if (value == null) {
			return true;
		}
		for (int i = 0; i < value.length();) {
			int codePoint = value.codePointAt(i);
			if (Character.getType(codePoint) == Character.CONTROL && codePoint != '\t' && codePoint != '\n'
					&& codePoint != '\r') {
				return false;
			}
			i += Character.charCount(codePoint);
		}
		return true;
	}

	private static boolean isEdgeSpace(char c) {
		return Character.isWhitespace(c) || Character.isSpaceChar(c);
	}

	private static boolean isForbiddenInName(int codePoint) {
		int type = Character.getType(codePoint);
		if (type == Character.CONTROL || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR) {
			return true;
		}
		return codePoint == 0x200B // ZERO WIDTH SPACE
				|| codePoint == 0x2060 // WORD JOINER
				|| codePoint == 0xFEFF // ZERO WIDTH NO-BREAK SPACE (BOM)
				|| codePoint == 0x180E // MONGOLIAN VOWEL SEPARATOR
				|| (codePoint >= 0x202A && codePoint <= 0x202E) // bidi embedding·override
				|| (codePoint >= 0x2066 && codePoint <= 0x2069); // bidi isolate
	}

	private static boolean isVisible(int codePoint) {
		int type = Character.getType(codePoint);
		return type != Character.SPACE_SEPARATOR && type != Character.LINE_SEPARATOR
				&& type != Character.PARAGRAPH_SEPARATOR && type != Character.CONTROL && type != Character.FORMAT;
	}

}
