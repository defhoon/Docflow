package com.docflow.document.validation;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.docflow.document.DocumentTestFiles;

/**
 * SPEC-03 §5.5.2 PDF 최소 구조 검증 P1~P5와 그 한계 (AC-DOC-04, 05).
 */
class PdfStructureValidatorTest {

	private final PdfStructureValidator validator = new PdfStructureValidator();

	@TempDir
	private Path dir;

	@Test
	void acceptsMinimalPdfWithXrefTable() throws IOException {
		assertValid(DocumentTestFiles.pdf());
	}

	@Test
	void acceptsXrefStreamTarget() throws IOException {
		String body = "%PDF-1.7\n";
		String obj = "5 0 obj\n<< /Type /XRef /Size 6 >>\nstream\nendstream\nendobj\n";
		assertValid(ascii(body + obj + "startxref\n" + body.length() + "\n%%EOF"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "1.0", "1.7", "2.0" })
	void acceptsSupportedVersions(String version) throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1).replace("%PDF-1.4",
				"%PDF-" + version);
		assertValid(ascii(pdf));
	}

	@Test
	void usesHeaderOffsetAsBaseWhenJunkPrecedesHeader() throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1);
		assertValid(ascii("JUNK-PREFIX\n" + pdf));
	}

	@Test
	void acceptsInternallyCorruptPdfWithMinimalStructure() throws IOException {
		// 한계(§5.5.2 "보장하지 않는 것"): 객체 내용이 깨져도 최소 구조만 있으면 통과한다. 길이를 유지해 오프셋은 그대로 둔다.
		String original = "<< /Type /Catalog >>";
		String broken = "<<\u0001\u0002 broken" + " ".repeat(original.length() - 11);
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1).replace(original, broken);
		assertValid(ascii(pdf));
	}

	@Test
	void acceptsIncrementalUpdateWithTwoEofMarkers() throws IOException {
		// 증분 업데이트: 마지막 %%EOF 앞의 startxref가 새 교차 참조 표를 가리킨다
		String original = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1);
		String update = "2 0 obj\n<< /Updated true >>\nendobj\n";
		int xrefOffset = original.length() + update.length();
		String pdf = original + update + "xref\n2 1\n" + String.format("%010d", original.length())
				+ " 00000 n \ntrailer\n<< /Size 3 >>\nstartxref\n" + xrefOffset + "\n%%EOF\n";
		assertValid(ascii(pdf));
	}

	@Test
	void rejectsOoxmlRenamedToPdf() throws IOException {
		assertInvalid(DocumentTestFiles.ooxml(DocumentFileType.DOCX));
	}

	@Test
	void p1RejectsTextFileContainingPdfMarkerOutsideHeaderWindow() throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1);
		assertInvalid(ascii(" ".repeat(PdfStructureValidator.WINDOW) + pdf));
	}

	@ParameterizedTest
	@ValueSource(strings = { "%PDF-", "%PDF-1.8", "%PDF-9.9", "%PDX-1.4", "plain text" })
	void p1RejectsMissingOrUnsupportedHeader(String header) throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1).replace("%PDF-1.4", header);
		assertInvalid(ascii(pdf));
	}

	@Test
	void p2RejectsMissingEof() throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1).replace("%%EOF", "%%END");
		assertInvalid(ascii(pdf));
	}

	@Test
	void p2RejectsEofOutsideTailWindow() throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1);
		assertInvalid(ascii(pdf + " ".repeat(PdfStructureValidator.WINDOW)));
	}

	@Test
	void p3RejectsMissingStartxref() throws IOException {
		String pdf = new String(DocumentTestFiles.pdf(), StandardCharsets.ISO_8859_1).replace("startxref", "startref");
		assertInvalid(ascii(pdf));
	}

	@Test
	void p3RejectsStartxrefAfterLastEof() throws IOException {
		assertInvalid(ascii("%PDF-1.4\nxref\n%%EOF\nstartxref\n9\n"));
	}

	@Test
	void p4RejectsOffsetBeyondFileSize() throws IOException {
		assertInvalid(ascii("%PDF-1.4\nxref\nstartxref\n999999\n%%EOF"));
	}

	@Test
	void p4RejectsOverflowingOffset() throws IOException {
		assertInvalid(ascii("%PDF-1.4\nxref\nstartxref\n9999999999999999999\n%%EOF"));
	}

	@Test
	void p5RejectsOffsetNotPointingToCrossReference() throws IOException {
		// 오프셋 0은 헤더(%PDF-)를 가리킨다.
		assertInvalid(ascii("%PDF-1.4\nxref\nstartxref\n0\n%%EOF"));
	}

	@Test
	void rejectsEmptyFile() throws IOException {
		assertInvalid(new byte[0]);
	}

	private void assertValid(byte[] content) throws IOException {
		Path file = write(content);
		assertThatCode(() -> this.validator.validate(file)).doesNotThrowAnyException();
	}

	private void assertInvalid(byte[] content) throws IOException {
		Path file = write(content);
		assertThatThrownBy(() -> this.validator.validate(file)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(InvalidDocumentFileException.MALFORMED);
	}

	private Path write(byte[] content) throws IOException {
		return Files.write(Files.createTempFile(this.dir, "pdf", ".bin"), content);
	}

	private static byte[] ascii(String value) {
		return value.getBytes(StandardCharsets.ISO_8859_1);
	}

}
