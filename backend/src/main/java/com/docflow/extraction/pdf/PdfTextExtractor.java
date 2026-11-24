package com.docflow.extraction.pdf;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.apache.pdfbox.util.Matrix;

import com.docflow.extraction.domain.ExtractionStatus;

/**
 * PDFBox 기반 텍스트·단어 좌표 추출기 (SPEC-10 §6, ADR-0008). Spring에 의존하지 않는 순수 컴포넌트다.
 * <p>
 * 메모리: 문서 버퍼를 임시 파일로만 두고({@link IOUtils#createTempFileOnlyStreamCache()}), 결과로 단어 목록만 메모리에
 * 남긴다. 시간: 페이지 시작과 글자 출력마다 마감 시각을 확인해 협조적으로 중단한다. OCR은 하지 않는다.
 */
public class PdfTextExtractor {

	/** 머리말·꼬리말로 판단하는 페이지 위·아래 영역 비율. */
	private static final float MARGIN_RATIO = 0.08f;

	/** 쪽 번호 형태: {@code 3}, {@code - 3 -}, {@code 3 / 10}, {@code Page 3}, {@code 3쪽}. */
	private static final Pattern PAGE_NUMBER = Pattern
		.compile("(?i)^[-–—\\s]*(page\\s*)?\\d{1,4}(\\s*(/|of)\\s*\\d{1,4})?\\s*(쪽|페이지)?[-–—\\s]*$");

	/** 직립 판정 허용 오차 (회전·기울임 성분 비율). */
	private static final float UPRIGHT_TOLERANCE = 0.01f;

	private final PdfExtractionLimits limits;

	public PdfTextExtractor(PdfExtractionLimits limits) {
		this.limits = limits;
	}

	public PdfExtractionOutcome extract(Path pdf) {
		long deadline = System.nanoTime() + this.limits.timeout().toNanos();
		try (PDDocument document = Loader.loadPDF(pdf.toFile(), IOUtils.createTempFileOnlyStreamCache())) {
			int pageCount = document.getNumberOfPages();
			if (document.isEncrypted() && !document.getCurrentAccessPermission().canExtractContent()) {
				return PdfExtractionOutcome.of(ExtractionStatus.ENCRYPTED, pageCount);
			}
			if (pageCount > this.limits.maxPages()) {
				return PdfExtractionOutcome.of(ExtractionStatus.TOO_LARGE, pageCount);
			}
			// 좌표·줄 판정을 회전 전 사용자 공간에서 하기 위해 메모리의 문서에서만 페이지 회전을 0으로 둔다(저장하지 않음).
			List<Integer> rotations = new ArrayList<>(pageCount);
			for (PDPage page : document.getPages()) {
				rotations.add(((page.getRotation() % 360) + 360) % 360);
				page.setRotation(0);
			}
			CollectingStripper stripper = new CollectingStripper(deadline, this.limits.maxWords(), rotations);
			stripper.writeText(document, Writer.nullWriter());
			stripper.finishLine();
			ExtractedText text = new ExtractedText(List.copyOf(stripper.pages), List.copyOf(stripper.words),
					markArtifacts(stripper.lines, stripper.words, stripper.pages));
			if (text.words().isEmpty()) {
				return PdfExtractionOutcome.of(ExtractionStatus.NO_TEXT, pageCount);
			}
			return new PdfExtractionOutcome(ExtractionStatus.EXTRACTED, text, pageCount);
		}
		catch (InvalidPasswordException ex) {
			return PdfExtractionOutcome.of(ExtractionStatus.ENCRYPTED, 0);
		}
		catch (LimitExceeded ex) {
			return PdfExtractionOutcome.of(ex.status, 0);
		}
		catch (IOException | RuntimeException ex) {
			// 손상된 구조, 지원하지 않는 필터 등. 원인 메시지에 문서 내용이 섞일 수 있어 기록하지 않는다(호출 측이 상태만 남긴다).
			return PdfExtractionOutcome.of(ExtractionStatus.CORRUPTED, 0);
		}
	}

	/**
	 * 페이지 위·아래 여백에 있는 쪽 번호 형태의 줄을 머리말·꼬리말로 표시한다. 비교·요약에서 페이지 번호 변화가 변경으로
	 * 잡히지 않게 하기 위함이다(원본 단어는 그대로 남는다).
	 */
	static List<ExtractedText.Line> markArtifacts(List<ExtractedText.Line> lines, List<ExtractedText.Word> words,
			List<ExtractedText.Page> pages) {
		List<ExtractedText.Line> result = new ArrayList<>(lines.size());
		for (ExtractedText.Line line : lines) {
			StringBuilder joined = new StringBuilder();
			boolean inMargin = true;
			for (int i = line.start(); i < line.end(); i++) {
				ExtractedText.Word word = words.get(i);
				joined.append(word.text()).append(' ');
				float height = pages.get(word.page()).height();
				if (word.box() == null || (word.box()[1] > height * MARGIN_RATIO
						&& word.box()[3] < height * (1 - MARGIN_RATIO))) {
					inMargin = false;
				}
			}
			boolean artifact = inMargin && PAGE_NUMBER.matcher(joined.toString().strip()).matches();
			result.add(new ExtractedText.Line(line.page(), line.start(), line.end(), line.paragraphStart(), artifact));
		}
		return List.copyOf(result);
	}

	/** 상한 초과로 중단. */
	static final class LimitExceeded extends IOException {

		private final ExtractionStatus status;

		LimitExceeded(ExtractionStatus status) {
			super(status.name());
			this.status = status;
		}

	}

	/**
	 * {@link PDFTextStripper}가 줄·단어 단위로 넘기는 글자 위치를 모은다. 출력 문자열은 쓰지 않는다.
	 */
	static final class CollectingStripper extends PDFTextStripper {

		final List<ExtractedText.Page> pages = new ArrayList<>();

		final List<ExtractedText.Word> words = new ArrayList<>();

		final List<ExtractedText.Line> lines = new ArrayList<>();

		private final long deadline;

		private final int maxWords;

		private int pageIndex = -1;

		private int lineStart = 0;

		private boolean paragraphPending = true;

		private float[] previousLineBox;

		/** 글자 높이가 이 비율 이상 달라지면 새 문단으로 본다. */
		static final float FONT_CHANGE_RATIO = 0.2f;

		/** 같은 줄에서 이 간격(글자 높이 비율)보다 가까운 단어는 한 단어로 합친다. */
		static final float TOUCHING_GAP_RATIO = 0.1f;

		/** 한글과 다른 문자가 만나는 곳의 합치기 간격(글자 높이 비율). 실제 공백(약 0.3em)보다 작다. */
		static final float HANGUL_AUTOSPACE_RATIO = 0.22f;

		/** 문단 구분 기준: 기준선 간격 / 글자 높이. 일반 줄 간격(1.2~1.5)보다 크고 빈 줄 하나보다 작다. */
		static final float PARAGRAPH_GAP_RATIO = 1.8f;

		private final List<Integer> rotations;

		CollectingStripper(long deadline, int maxWords, List<Integer> rotations) {
			this.deadline = deadline;
			this.rotations = rotations;
			this.maxWords = maxWords;
			setSortByPosition(false);
			setAddMoreFormatting(false);
		}

		@Override
		protected void startPage(PDPage page) throws IOException {
			checkDeadline();
			finishLine();
			this.pageIndex++;
			PDRectangle crop = page.getCropBox();
			this.pages.add(new ExtractedText.Page(crop.getWidth(), crop.getHeight(), this.rotations.get(this.pageIndex)));
			this.paragraphPending = true;
			this.previousLineBox = null;
		}

		@Override
		protected void writeLineSeparator() {
			finishLine();
		}

		@Override
		protected void writeString(String text, List<TextPosition> positions) throws IOException {
			checkDeadline();
			if (positions.isEmpty()) {
				return;
			}
			// 한 덩어리 안에 공백이 있으면 글자 위치를 따라 나눈다. 글자 수와 위치 수가 다르면(합자 등) 나누지 않는다.
			List<List<TextPosition>> groups = new ArrayList<>();
			List<String> texts = new ArrayList<>();
			if (positions.size() == text.length()) {
				StringBuilder current = new StringBuilder();
				List<TextPosition> currentPositions = new ArrayList<>();
				for (int i = 0; i < text.length(); i++) {
					char c = text.charAt(i);
					if (Character.isWhitespace(c) || Character.isSpaceChar(c)) {
						if (!current.isEmpty()) {
							texts.add(current.toString());
							groups.add(currentPositions);
							current = new StringBuilder();
							currentPositions = new ArrayList<>();
						}
						continue;
					}
					current.append(c);
					currentPositions.add(positions.get(i));
				}
				if (!current.isEmpty()) {
					texts.add(current.toString());
					groups.add(currentPositions);
				}
			}
			else {
				for (String part : text.split("[\\s\\p{Z}]+")) {
					if (!part.isEmpty()) {
						texts.add(part);
						groups.add(positions);
					}
				}
			}
			for (int i = 0; i < texts.size(); i++) {
				addWord(texts.get(i), groups.get(i));
			}
		}

		private void addWord(String raw, List<TextPosition> positions) throws LimitExceeded {
			String text = clean(raw);
			if (text.isEmpty()) {
				return;
			}
			if (this.words.size() >= this.maxWords) {
				throw new LimitExceeded(ExtractionStatus.TOO_LARGE);
			}
			this.words.add(new ExtractedText.Word(text, Math.max(this.pageIndex, 0), box(positions)));
		}

		/**
		 * 단어 상자: 글자마다 글자 공간의 사각형(진행 폭 × 기준선 아래 0.22em ~ 위 0.8em)을 글자 행렬로 옮긴 뒤 모두 감싸는
		 * 축 정렬 사각형이다. 글자 방향이 0·90·180·270도가 아니면(기울어진 텍스트) 위치를 확정하지 않는다({@code null}).
		 */
		private float[] box(List<TextPosition> positions) {
			float x1 = Float.MAX_VALUE;
			float y1 = Float.MAX_VALUE;
			float x2 = -Float.MAX_VALUE;
			float y2 = -Float.MAX_VALUE;
			for (TextPosition position : positions) {
				Matrix m = position.getTextMatrix();
				float a = m.getScaleX();
				float b = m.getShearY();
				float c = m.getShearX();
				float d = m.getScaleY();
				float scale = Math.max(Math.abs(a) + Math.abs(b), Math.abs(c) + Math.abs(d));
				boolean axisAligned = (Math.abs(b) <= scale * UPRIGHT_TOLERANCE && Math.abs(c) <= scale * UPRIGHT_TOLERANCE)
						|| (Math.abs(a) <= scale * UPRIGHT_TOLERANCE && Math.abs(d) <= scale * UPRIGHT_TOLERANCE);
				float advance = advanceEm(position);
				if (!axisAligned || !(scale > 0) || advance < 0) {
					return null;
				}
				float[][] corners = { { 0, -0.22f }, { advance, -0.22f }, { 0, 0.8f }, { advance, 0.8f } };
				for (float[] corner : corners) {
					float x = a * corner[0] + c * corner[1] + m.getTranslateX();
					float y = b * corner[0] + d * corner[1] + m.getTranslateY();
					x1 = Math.min(x1, x);
					x2 = Math.max(x2, x);
					y1 = Math.min(y1, y);
					y2 = Math.max(y2, y);
				}
			}
			if (!(x2 > x1) || !(y2 > y1) || !Float.isFinite(x1) || !Float.isFinite(y2)) {
				return null;
			}
			return new float[] { round(x1), round(y1), round(x2), round(y2) };
		}

		/**
		 * 글자 진행 폭(글자 공간, em). 글자 행렬에 글꼴 크기와 수평 배율이 들어 있으므로 글꼴 폭(1/1000 em)만 더한다. 계산할 수
		 * 없으면 -1.
		 */
		private static float advanceEm(TextPosition position) {
			try {
				float total = 0;
				for (int code : position.getCharacterCodes()) {
					total += position.getFont().getWidth(code) / 1000f;
				}
				return (Float.isFinite(total) && total > 0) ? total : -1;
			}
			catch (IOException | RuntimeException ex) {
				return -1;
			}
		}

		/**
		 * 줄을 닫는다. 문단 시작은 PDFBox 추정 대신 줄 간격으로 정한다: 페이지 첫 줄이거나, 앞 줄과의 기준선 간격이 글자 높이의
		 * {@value #PARAGRAPH_GAP_RATIO}배를 넘으면 새 문단이다. 위치가 없는 줄은 앞 문단에 잇는다.
		 */
		void finishLine() {
			mergeTouchingWords();
			int end = this.words.size();
			if (end > this.lineStart) {
				float[] box = this.words.get(this.lineStart).box();
				boolean paragraph = this.paragraphPending;
				if (!paragraph && box != null && this.previousLineBox != null) {
					float height = this.previousLineBox[3] - this.previousLineBox[1];
					float gap = this.previousLineBox[1] - box[1];
					float currentHeight = box[3] - box[1];
					// 간격이 크거나, 위로 되돌아가거나(다단), 글자 크기가 달라지면(제목→본문) 새 문단이다.
					paragraph = gap > height * PARAGRAPH_GAP_RATIO || gap < -height
							|| Math.abs(currentHeight - height) > height * FONT_CHANGE_RATIO;
				}
				this.lines.add(new ExtractedText.Line(this.words.get(this.lineStart).page(), this.lineStart, end,
						paragraph, false));
				this.paragraphPending = false;
				if (box != null) {
					this.previousLineBox = box;
				}
			}
			this.lineStart = end;
		}

		/**
		 * 현재 줄에서 사이에 공백이 없이 붙어 있는 단어를 합친다. 글꼴이 바뀌는 곳(예: 숫자와 한글 "11월")에서 PDFBox가 단어를
		 * 나누는 경우를 바로잡는다. 실제 공백(약 0.25em 이상)이 있는 단어는 그대로 둔다.
		 */
		private void mergeTouchingWords() {
			int end = this.words.size();
			if (end - this.lineStart < 2) {
				return;
			}
			List<ExtractedText.Word> merged = new ArrayList<>(end - this.lineStart);
			for (int i = this.lineStart; i < end; i++) {
				ExtractedText.Word word = this.words.get(i);
				if (!merged.isEmpty() && touches(merged.getLast(), word)) {
					ExtractedText.Word last = merged.removeLast();
					float[] a = last.box();
					float[] b = word.box();
					merged.add(new ExtractedText.Word(last.text() + word.text(), last.page(), new float[] {
							Math.min(a[0], b[0]), Math.min(a[1], b[1]), Math.max(a[2], b[2]), Math.max(a[3], b[3]) }));
				}
				else {
					merged.add(word);
				}
			}
			this.words.subList(this.lineStart, end).clear();
			this.words.addAll(merged);
		}

		private static boolean touches(ExtractedText.Word left, ExtractedText.Word right) {
			float[] a = left.box();
			float[] b = right.box();
			if (a == null || b == null || left.page() != right.page()) {
				return false;
			}
			float height = Math.min(a[3] - a[1], b[3] - b[1]);
			boolean sameBaseline = Math.abs(a[1] - b[1]) < height * 0.3f;
			float gap = b[0] - a[2];
			// 한글과 숫자·영문 사이에는 조판 프로그램이 자동 간격(약 0.2em)을 넣으므로 조금 더 넓게 본다.
			float allowed = hangulBoundary(left.text(), right.text()) ? HANGUL_AUTOSPACE_RATIO : TOUCHING_GAP_RATIO;
			return sameBaseline && gap > -height * 0.2f && gap < height * allowed;
		}

		private static boolean hangulBoundary(String left, String right) {
			return isHangul(left.codePointBefore(left.length())) != isHangul(right.codePointAt(0));
		}

		private static boolean isHangul(int codePoint) {
			return Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HANGUL;
		}

		private void checkDeadline() throws LimitExceeded {
			if (System.nanoTime() - this.deadline > 0) {
				throw new LimitExceeded(ExtractionStatus.TIMEOUT);
			}
		}

		private static float round(float value) {
			return Math.round(value * 100f) / 100f;
		}

		/** 호환 문자 정규화(NFKC)와 보이지 않는 문자 제거. */
		static String clean(String raw) {
			String normalized = Normalizer.normalize(raw, Normalizer.Form.NFKC);
			StringBuilder result = new StringBuilder(normalized.length());
			normalized.codePoints().forEach((cp) -> {
				int type = Character.getType(cp);
				if (type != Character.FORMAT && type != Character.CONTROL && !Character.isWhitespace(cp)
						&& !Character.isSpaceChar(cp)) {
					result.appendCodePoint(cp);
				}
			});
			return result.toString();
		}

	}

}
