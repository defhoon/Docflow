package com.docflow.extraction;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * 추출·비교 테스트용 PDF 생성기. 한글은 글꼴 파일 없이 {@code ToUnicode}만 가진 Type0(Identity-H) 글꼴로 쓴다(라이선스가 있는
 * 글꼴을 저장소에 넣지 않기 위함). 글자 폭은 모두 0.6em이다.
 */
public final class TestPdfs {

	public static final float FONT_SIZE = 12f;

	/** 글자 폭 (1/1000 em). */
	public static final int GLYPH_WIDTH = 600;

	private TestPdfs() {
	}

	/** 한 줄 텍스트와 기준선 위치. */
	public record TextLine(float x, float y, String text) {
	}

	/** 페이지: MediaBox·CropBox {@code [llx lly urx ury]}, 회전, 줄 목록. */
	public record TestPage(float[] box, int rotate, List<TextLine> lines) {

		public static TestPage a4(String... lines) {
			List<TextLine> result = new ArrayList<>();
			float y = 780;
			for (String line : lines) {
				result.add(new TextLine(72, y, line));
				y -= line.isEmpty() ? 30 : 18;
			}
			return new TestPage(new float[] { 0, 0, 595, 842 }, 0, result.stream().filter((l) -> !l.text().isEmpty()).toList());
		}

	}

	/** 빈 줄("")은 문단 구분(추가 간격)으로 쓴다. 각 인수가 한 페이지다. */
	public static byte[] pages(TestPage... pages) {
		Map<Character, Integer> cids = new LinkedHashMap<>();
		for (TestPage page : pages) {
			for (TextLine line : page.lines()) {
				for (char c : line.text().toCharArray()) {
					cids.computeIfAbsent(c, (k) -> cids.size() + 1);
				}
			}
		}
		List<String> objects = new ArrayList<>();
		// 1 Catalog, 2 Pages, 3 Type0 font, 4 CIDFont, 5 ToUnicode, 6 FontDescriptor, then page/content pairs
		int firstPage = 7;
		StringBuilder kids = new StringBuilder();
		for (int i = 0; i < pages.length; i++) {
			kids.append(firstPage + i * 2).append(" 0 R ");
		}
		objects.add("<< /Type /Catalog /Pages 2 0 R >>");
		objects.add("<< /Type /Pages /Kids [" + kids + "] /Count " + pages.length + " >>");
		objects.add("<< /Type /Font /Subtype /Type0 /BaseFont /TestFont /Encoding /Identity-H /DescendantFonts [4 0 R] /ToUnicode 5 0 R >>");
		objects.add("<< /Type /Font /Subtype /CIDFontType2 /BaseFont /TestFont /CIDSystemInfo << /Registry (Adobe) /Ordering (Identity) /Supplement 0 >> /FontDescriptor 6 0 R /DW "
				+ GLYPH_WIDTH + " /CIDToGIDMap /Identity >>");
		StringBuilder cmap = new StringBuilder();
		cmap.append("/CIDInit /ProcSet findresource begin 12 dict begin begincmap /CIDSystemInfo << /Registry (Adobe) /Ordering (UCS) /Supplement 0 >> def /CMapName /Test-UCS def /CMapType 2 def\n");
		cmap.append("1 begincodespacerange <0000> <FFFF> endcodespacerange\n");
		List<Map.Entry<Character, Integer>> entries = new ArrayList<>(cids.entrySet());
		for (int start = 0; start < entries.size(); start += 100) {
			List<Map.Entry<Character, Integer>> chunk = entries.subList(start, Math.min(entries.size(), start + 100));
			cmap.append(chunk.size()).append(" beginbfchar\n");
			for (Map.Entry<Character, Integer> e : chunk) {
				cmap.append(String.format("<%04X> <%04X>%n", e.getValue(), (int) e.getKey()));
			}
			cmap.append("endbfchar\n");
		}
		cmap.append("endcmap CMapName currentdict /CMap defineresource pop end end");
		objects.add(stream("", cmap.toString()));
		objects.add("<< /Type /FontDescriptor /FontName /TestFont /Flags 32 /FontBBox [0 -200 1000 900] /ItalicAngle 0 /Ascent 900 /Descent -200 /CapHeight 700 /StemV 80 >>");
		for (int i = 0; i < pages.length; i++) {
			TestPage page = pages[i];
			String box = String.format("[%s %s %s %s]", page.box()[0], page.box()[1], page.box()[2], page.box()[3]);
			objects.add("<< /Type /Page /Parent 2 0 R /MediaBox " + box + " /CropBox " + box + " /Rotate " + page.rotate()
					+ " /Resources << /Font << /F1 3 0 R >> >> /Contents " + (firstPage + i * 2 + 1) + " 0 R >>");
			StringBuilder content = new StringBuilder();
			for (TextLine line : page.lines()) {
				content.append("BT /F1 ").append(FONT_SIZE).append(" Tf ").append(line.x()).append(' ').append(line.y())
					.append(" Td <");
				for (char c : line.text().toCharArray()) {
					content.append(String.format("%04X", cids.get(c)));
				}
				content.append("> Tj ET\n");
			}
			objects.add(stream("", content.toString()));
		}
		return assemble(objects);
	}

	/** 영어 표준 글꼴(Helvetica) 문서. PDFBox API로 만든다. */
	public static byte[] helvetica(String... lines) {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				stream.beginText();
				stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
				stream.newLineAtOffset(72, 780);
				for (String line : lines) {
					stream.showText(line);
					stream.newLineAtOffset(0, -18);
				}
				stream.endText();
			}
			return save(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 텍스트가 없는(도형만 있는) PDF. 스캔 문서를 흉내 낸다. */
	public static byte[] noText() {
		try (PDDocument document = new PDDocument()) {
			PDPage page = new PDPage(PDRectangle.A4);
			document.addPage(page);
			try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
				stream.addRect(100, 100, 200, 200);
				stream.fill();
			}
			return save(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 사용자 암호가 걸린 PDF. */
	public static byte[] encrypted() {
		try (PDDocument document = new PDDocument()) {
			document.addPage(new PDPage(PDRectangle.A4));
			StandardProtectionPolicy policy = new StandardProtectionPolicy("owner-pass", "user-pass", new AccessPermission());
			policy.setEncryptionKeyLength(128);
			document.protect(policy);
			return save(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 빈 페이지 {@code count}장. */
	public static byte[] blankPages(int count) {
		try (PDDocument document = new PDDocument()) {
			for (int i = 0; i < count; i++) {
				document.addPage(new PDPage(PDRectangle.A4));
			}
			return save(document);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static byte[] save(PDDocument document) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		document.save(bytes);
		return bytes.toByteArray();
	}

	private static String stream(String dict, String data) {
		return "<< " + dict + "/Length " + data.getBytes(StandardCharsets.ISO_8859_1).length + " >>\nstream\n" + data
				+ "\nendstream";
	}

	private static byte[] assemble(List<String> objects) {
		StringBuilder pdf = new StringBuilder("%PDF-1.7\n");
		List<Integer> offsets = new ArrayList<>();
		for (int i = 0; i < objects.size(); i++) {
			offsets.add(pdf.length());
			pdf.append(i + 1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");
		}
		int xref = pdf.length();
		pdf.append("xref\n0 ").append(objects.size() + 1).append("\n0000000000 65535 f \n");
		for (int offset : offsets) {
			pdf.append(String.format("%010d 00000 n \n", offset));
		}
		pdf.append("trailer\n<< /Size ").append(objects.size() + 1).append(" /Root 1 0 R >>\nstartxref\n").append(xref)
			.append("\n%%EOF\n");
		return pdf.toString().getBytes(StandardCharsets.ISO_8859_1);
	}

}
