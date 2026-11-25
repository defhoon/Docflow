package com.docflow.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.docflow.extraction.TestPdfs.TestPage;
import com.docflow.extraction.TestPdfs.TextLine;
import com.docflow.extraction.domain.ExtractionStatus;
import com.docflow.extraction.pdf.ExtractedText;
import com.docflow.extraction.pdf.PdfExtractionLimits;
import com.docflow.extraction.pdf.PdfExtractionOutcome;
import com.docflow.extraction.pdf.PdfTextExtractor;
import com.docflow.extraction.pdf.TextNormalizer;

/** SPEC-10 추출기 단위 테스트 (AC-EXT-01~08). */
class PdfTextExtractorTest {

	private static final PdfExtractionLimits LIMITS = new PdfExtractionLimits(10, 1000, Duration.ofSeconds(30));

	@TempDir
	Path dir;

	private PdfExtractionOutcome extract(byte[] pdf, PdfExtractionLimits limits) throws IOException {
		Path file = Files.write(this.dir.resolve("t.pdf"), pdf);
		return new PdfTextExtractor(limits).extract(file);
	}

	private PdfExtractionOutcome extract(byte[] pdf) throws IOException {
		return extract(pdf, LIMITS);
	}

	private static List<String> texts(ExtractedText text) {
		return text.words().stream().map(ExtractedText.Word::text).toList();
	}

	// AC-EXT-01: 한글·영문 단어와 페이지 정보
	@Test
	void extractsKoreanAndEnglishWordsPerPage() throws IOException {
		PdfExtractionOutcome outcome = extract(TestPdfs.pages(TestPage.a4("사업 계획서 Draft", "예산 1,200만원"),
				TestPage.a4("2페이지 내용")));

		assertThat(outcome.status()).isEqualTo(ExtractionStatus.EXTRACTED);
		ExtractedText text = outcome.text();
		assertThat(texts(text)).containsExactly("사업", "계획서", "Draft", "예산", "1,200만원", "2페이지", "내용");
		assertThat(text.pages()).hasSize(2);
		assertThat(text.pages().get(0).width()).isEqualTo(595f);
		assertThat(text.pages().get(0).height()).isEqualTo(842f);
		assertThat(text.words().get(5).page()).isEqualTo(1);
		assertThat(text.lines()).hasSize(3);
		assertThat(text.plainTextWithPageMarkers()).isEqualTo("[p.1]\n사업 계획서 Draft\n예산 1,200만원\n\n[p.2]\n2페이지 내용");
	}

	// AC-EXT-02: 단어 좌표 (CropBox 왼쪽 아래 기준 사용자 공간)
	@Test
	void wordBoxesFollowGlyphPositions() throws IOException {
		PdfExtractionOutcome outcome = extract(TestPdfs.pages(new TestPage(new float[] { 0, 0, 595, 842 }, 0,
				List.of(new TextLine(100, 700, "AB CD")))));

		List<ExtractedText.Word> words = outcome.text().words();
		float glyph = TestPdfs.FONT_SIZE * TestPdfs.GLYPH_WIDTH / 1000f;
		assertThat(words.get(0).box()).containsExactly(100f, 700f - 12 * 0.22f, 100f + 2 * glyph, 700f + 12 * 0.8f);
		assertThat(words.get(1).box()[0]).isEqualTo(100f + 3 * glyph);
	}

	// AC-EXT-02: CropBox 원점이 0이 아니어도, 페이지가 회전되어 있어도 좌표는 회전 전 CropBox 기준
	@Test
	void boxesAreRelativeToCropBoxAndIgnorePageRotation() throws IOException {
		PdfExtractionOutcome outcome = extract(TestPdfs.pages(
				new TestPage(new float[] { 50, 100, 645, 942 }, 90, List.of(new TextLine(150, 800, "Rotated")))));

		ExtractedText text = outcome.text();
		assertThat(text.pages().get(0).rotation()).isEqualTo(90);
		assertThat(text.pages().get(0).width()).isEqualTo(595f);
		float glyph = TestPdfs.FONT_SIZE * TestPdfs.GLYPH_WIDTH / 1000f;
		for (int rotation : new int[] { 90, 180, 270 }) {
			ExtractedText rotated = extract(TestPdfs.pages(new TestPage(new float[] { 50, 100, 645, 942 }, rotation,
					List.of(new TextLine(150, 800, "Rotated"))))).text();
			assertThat(rotated.pages().get(0).rotation()).isEqualTo(rotation);
			assertThat(rotated.words().get(0).box()).containsExactly(100f, 700f - 12 * 0.22f, 100f + 7 * glyph,
					700f + 12 * 0.8f);
		}
	}

	// AC-EXT-03: 텍스트 없는 PDF
	@Test
	void pdfWithoutTextIsNoText() throws IOException {
		assertThat(extract(TestPdfs.noText()).status()).isEqualTo(ExtractionStatus.NO_TEXT);
	}

	// AC-EXT-04: 암호화된 PDF
	@Test
	void encryptedPdfIsReported() throws IOException {
		assertThat(extract(TestPdfs.encrypted()).status()).isEqualTo(ExtractionStatus.ENCRYPTED);
	}

	// AC-EXT-05: 손상된 PDF
	@Test
	void corruptedPdfIsReported() throws IOException {
		assertThat(extract("%PDF-1.4\nnot really a pdf".getBytes()).status()).isEqualTo(ExtractionStatus.CORRUPTED);
	}

	// AC-EXT-06: 페이지·단어 수 상한
	@Test
	void limitsAreEnforced() throws IOException {
		assertThat(extract(TestPdfs.blankPages(11)).status()).isEqualTo(ExtractionStatus.TOO_LARGE);
		PdfExtractionLimits fewWords = new PdfExtractionLimits(10, 3, Duration.ofSeconds(30));
		assertThat(extract(TestPdfs.pages(TestPage.a4("one two three four")), fewWords).status())
			.isEqualTo(ExtractionStatus.TOO_LARGE);
	}

	// AC-EXT-06: 처리 시간 상한
	@Test
	void timeoutIsEnforced() throws IOException {
		PdfExtractionLimits instant = new PdfExtractionLimits(10, 1000, Duration.ofNanos(1));
		assertThat(extract(TestPdfs.pages(TestPage.a4("slow")), instant).status()).isEqualTo(ExtractionStatus.TIMEOUT);
	}

	// AC-EXT-01: 표준 글꼴 영어 문서
	@Test
	void extractsStandardFontText() throws IOException {
		PdfExtractionOutcome outcome = extract(TestPdfs.helvetica("Quarterly report", "Revenue grew 12%"));
		assertThat(texts(outcome.text())).containsExactly("Quarterly", "report", "Revenue", "grew", "12%");
		assertThat(outcome.text().words()).allMatch(ExtractedText.Word::located);
	}

	// AC-EXT-08: 글꼴이 바뀌는 한글·숫자 경계(자동 간격)는 한 단어, 실제 공백은 두 단어
	@Test
	void mergesHangulAutospaceButKeepsRealSpaces() throws IOException {
		float glyph = TestPdfs.FONT_SIZE * TestPdfs.GLYPH_WIDTH / 1000f;
		TestPage page = new TestPage(new float[] { 0, 0, 595, 842 }, 0,
				List.of(new TextLine(100, 700, "11"), new TextLine(100 + 2 * glyph + 2.4f, 700, "월"),
						new TextLine(100, 650, "A"), new TextLine(100 + glyph + 0.5f, 650, "B"),
						new TextLine(100, 600, "C"), new TextLine(100 + glyph + 4f, 600, "D")));
		ExtractedText text = extract(TestPdfs.pages(page)).text();
		assertThat(texts(text)).containsExactly("11월", "AB", "C", "D");
		assertThat(text.words().get(0).box()[2]).isEqualTo(100 + 3 * glyph + 2.4f);
	}

	// AC-EXT-07: 정규화 — 쪽 번호 제외, 줄 끝 하이픈 합치기, 원본 단어 색인 유지, 문단 구분
	@Test
	void normalizationKeepsWordMapping() throws IOException {
		TestPage page = new TestPage(new float[] { 0, 0, 595, 842 }, 0,
				List.of(new TextLine(72, 780, "The docu-"), new TextLine(72, 762, "ment is ready"),
						new TextLine(72, 700, "새 문단"), new TextLine(290, 30, "- 1 -")));
		ExtractedText text = extract(TestPdfs.pages(page)).text();

		List<TextNormalizer.Paragraph> paragraphs = TextNormalizer.paragraphs(text);
		assertThat(paragraphs).extracting(TextNormalizer.Paragraph::joined)
			.containsExactly("The document is ready", "새 문단");
		TextNormalizer.Token joined = paragraphs.get(0).tokens().get(1);
		assertThat(joined.text()).isEqualTo("document");
		assertThat(joined.words()).containsExactly(1, 2);
		assertThat(text.lines().getLast().artifact()).isTrue();
		assertThat(text.plainTextWithPageMarkers()).doesNotContain("- 1 -");
	}

}
