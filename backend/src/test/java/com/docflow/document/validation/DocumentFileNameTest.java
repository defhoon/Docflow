package com.docflow.document.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * SPEC-03 §5.7 원본 파일명 정규화·검증, §5.5.1-1 확장자 판별 (AC-DOC-04, 10).
 */
class DocumentFileNameTest {

	@Test
	void keepsOnlyNameAfterLastPathSeparatorAndTrimsEdges() {
		DocumentFileName name = DocumentFileName.of("C:\\Users\\me\\docs/../  요구사항 명세서.PDF  ");

		assertThat(name.value()).isEqualTo("요구사항 명세서.PDF");
		assertThat(name.type()).isEqualTo(DocumentFileType.PDF);
	}

	@Test
	void resolvesEachAllowedExtensionCaseInsensitively() {
		assertThat(DocumentFileName.of("a.pdf").type()).isEqualTo(DocumentFileType.PDF);
		assertThat(DocumentFileName.of("a.DocX").type()).isEqualTo(DocumentFileType.DOCX);
		assertThat(DocumentFileName.of("a.xlsx").type()).isEqualTo(DocumentFileType.XLSX);
		assertThat(DocumentFileName.of("a.PPTX").type()).isEqualTo(DocumentFileType.PPTX);
	}

	@ParameterizedTest
	@ValueSource(strings = { "report.pdf.exe", "report.doc", "report.docm", "report.xlsm", "report", "report.",
			".pdf", "archive.zip", "notes.txt" })
	void rejectsUnsupportedOrMissingExtension(String submitted) {
		assertThatThrownBy(() -> DocumentFileName.of(submitted)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(DocumentFileName.UNSUPPORTED_TYPE);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", ".", "..", "dir/", "a\u0000.pdf", "a\n.pdf", "a\u2028.pdf", "a\u200B.pdf",
			"invoice\u202Efdp.exe.pdf", "\u2066a.pdf" })
	void rejectsEmptyDotOnlyAndForbiddenCharacters(String submitted) {
		assertThatThrownBy(() -> DocumentFileName.of(submitted)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(DocumentFileName.INVALID_NAME);
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> DocumentFileName.of(null)).isInstanceOf(InvalidDocumentFileException.class);
	}

	@Test
	void acceptsExactly255CharactersAndRejects256WithoutTruncation() {
		String base255 = "가".repeat(251) + ".pdf";
		assertThat(DocumentFileName.of(base255).value()).hasSize(255);

		assertThatThrownBy(() -> DocumentFileName.of("가" + base255)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(DocumentFileName.TOO_LONG);
	}

}
