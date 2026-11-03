package com.docflow.document.validation;

import static com.docflow.document.DocumentTestFiles.CT_NS;
import static com.docflow.document.DocumentTestFiles.contentTypes;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.docflow.document.DocumentTestFiles;

/**
 * SPEC-03 §5.5.3 OOXML 검증, §5.5.4 검사 자원 상한, XML 보안 설정 (AC-DOC-04, 05).
 */
class OoxmlPackageValidatorTest {

	private static final DocumentFileType DOCX = DocumentFileType.DOCX;

	private final OoxmlPackageValidator validator = new OoxmlPackageValidator(OoxmlPackageValidator.Limits.DEFAULT);

	@TempDir
	private Path dir;

	private HttpServer server;

	private final AtomicInteger externalHits = new AtomicInteger();

	@BeforeEach
	void startServer() throws IOException {
		this.server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		this.server.createContext("/", (exchange) -> {
			this.externalHits.incrementAndGet();
			byte[] body = "<!ENTITY x \"pwned\">".getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		this.server.start();
	}

	@AfterEach
	void stopServer() {
		this.server.stop(0);
	}

	@ParameterizedTest
	@EnumSource(value = DocumentFileType.class, names = { "DOCX", "XLSX", "PPTX" })
	void acceptsMinimalPackageForEachFormat(DocumentFileType type) throws IOException {
		assertValid(DocumentTestFiles.ooxml(type), type);
	}

	@Test
	void usesDefaultWhenNoOverrideAndComparesCaseInsensitively() throws IOException {
		String xml = "<Types xmlns=\"" + CT_NS + "\"><Default Extension=\"XML\" ContentType=\""
				+ DOCX.mainPartContentType() + "\"/></Types>";
		assertValid(DocumentTestFiles.ooxml(DOCX, xml), DOCX);

		String upperPartName = contentTypes("/WORD/Document.XML", DOCX.mainPartContentType());
		assertValid(DocumentTestFiles.ooxml(DOCX, upperPartName), DOCX);
	}

	@Test
	void rejectsFormatDeclaredForAnotherExtension() throws IOException {
		// .xlsx로 올렸지만 Word 패키지
		assertInvalid(DocumentTestFiles.ooxml(DOCX), DocumentFileType.XLSX, OoxmlPackageValidator.TYPE_MISMATCH);
		// 주 문서 엔트리는 있지만 다른 형식의 Content Type으로 선언
		String xml = contentTypes(DOCX.mainPartName(), DocumentFileType.XLSX.mainPartContentType());
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, OoxmlPackageValidator.TYPE_MISMATCH);
	}

	@ParameterizedTest
	@ValueSource(strings = { "application/vnd.ms-word.document.macroEnabled.main+xml",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.template.main+xml", "application/xml",
			"APPLICATION/VND.OPENXMLFORMATS-OFFICEDOCUMENT.WORDPROCESSINGML.DOCUMENT.MAIN+XML" })
	void rejectsMacroTemplateGenericOrNonExactContentType(String contentType) throws IOException {
		assertInvalid(DocumentTestFiles.ooxml(DOCX, contentTypes(DOCX.mainPartName(), contentType)), DOCX,
				OoxmlPackageValidator.TYPE_MISMATCH);
	}

	@Test
	void rejectsContentTypeStringNotBoundToMainPart() throws IOException {
		// 주 문서 Content Type 문자열은 있지만 다른 파트에 연결되고, 주 문서 파트는 application/xml(Default)
		String xml = contentTypes("/word/other.xml", DOCX.mainPartContentType());
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, OoxmlPackageValidator.TYPE_MISMATCH);
		// 주석·텍스트에만 존재
		String commentOnly = "<Types xmlns=\"" + CT_NS + "\"><!-- " + DOCX.mainPartContentType() + " --></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, commentOnly), DOCX, OoxmlPackageValidator.TYPE_MISMATCH);
	}

	@Test
	void rejectsDuplicateOverrideForSamePart() throws IOException {
		String xml = "<Types xmlns=\"" + CT_NS + "\">" + "<Override PartName=\"/word/document.xml\" ContentType=\""
				+ DOCX.mainPartContentType() + "\"/>" + "<Override PartName=\"/WORD/document.xml\" ContentType=\""
				+ DOCX.mainPartContentType() + "\"/></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, InvalidDocumentFileException.MALFORMED);
	}

	@Test
	void rejectsWrongRootOrNamespace() throws IOException {
		String noNamespace = "<Types><Override PartName=\"/word/document.xml\" ContentType=\""
				+ DOCX.mainPartContentType() + "\"/></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, noNamespace), DOCX, InvalidDocumentFileException.MALFORMED);
		String wrongRoot = "<Relationships xmlns=\"" + CT_NS + "\"/>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, wrongRoot), DOCX, InvalidDocumentFileException.MALFORMED);
	}

	@Test
	void rejectsMalformedXml() throws IOException {
		String xml = contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType()).replace("</Types>", "");
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, InvalidDocumentFileException.MALFORMED);
	}

	@Test
	void rejectsMissingRequiredEntries() throws IOException {
		Map<String, byte[]> noContentTypes = Map.of("word/document.xml", bytes("<a/>"));
		assertInvalid(DocumentTestFiles.zip(noContentTypes), DOCX, OoxmlPackageValidator.TYPE_MISMATCH);

		Map<String, byte[]> noMainPart = Map.of("[Content_Types].xml",
				bytes(contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType())));
		assertInvalid(DocumentTestFiles.zip(noMainPart), DOCX, OoxmlPackageValidator.TYPE_MISMATCH);
	}

	@Test
	void rejectsNonZipAndTruncatedZip() throws IOException {
		assertInvalid(DocumentTestFiles.pdf(), DOCX, InvalidDocumentFileException.MALFORMED);
		byte[] valid = DocumentTestFiles.ooxml(DOCX);
		byte[] truncated = java.util.Arrays.copyOf(valid, valid.length - 30);
		assertInvalid(truncated, DOCX, InvalidDocumentFileException.MALFORMED);
	}

	// XML 보안: 내부 서브셋 DOCTYPE(엔티티 확장 공격 포함)
	@Test
	void rejectsInternalSubsetDoctype() throws IOException {
		String xml = "<?xml version=\"1.0\"?><!DOCTYPE Types [<!ENTITY a \"aaaaaaaaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]>"
				+ "<Types xmlns=\"" + CT_NS + "\"><Override PartName=\"/word/document.xml\" ContentType=\"&b;\"/></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, InvalidDocumentFileException.MALFORMED);
	}

	// XML 보안: 외부 DTD·외부 엔티티는 조회하지 않고 거부
	@Test
	void rejectsExternalDtdAndEntityWithoutFetching() throws IOException {
		String url = "http://127.0.0.1:" + this.server.getAddress().getPort() + "/evil.dtd";
		String externalDtd = "<?xml version=\"1.0\"?><!DOCTYPE Types SYSTEM \"" + url + "\">"
				+ contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType()).replaceFirst("<\\?xml[^>]*\\?>", "");
		assertInvalid(DocumentTestFiles.ooxml(DOCX, externalDtd), DOCX, InvalidDocumentFileException.MALFORMED);

		String externalEntity = "<?xml version=\"1.0\"?><!DOCTYPE Types [<!ENTITY ext SYSTEM \"" + url + "\">]>"
				+ "<Types xmlns=\"" + CT_NS + "\"><Override PartName=\"/word/document.xml\" ContentType=\"&ext;\"/></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, externalEntity), DOCX, InvalidDocumentFileException.MALFORMED);

		String parameterEntity = "<?xml version=\"1.0\"?><!DOCTYPE Types [<!ENTITY % p SYSTEM \"" + url + "\"> %p;]>"
				+ "<Types xmlns=\"" + CT_NS + "\"/>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, parameterEntity), DOCX, InvalidDocumentFileException.MALFORMED);

		assertThat(this.externalHits).hasValue(0);
	}

	@Test
	void rejectsUndeclaredEntityReference() throws IOException {
		String xml = "<Types xmlns=\"" + CT_NS + "\"><Override PartName=\"/word/document.xml\" ContentType=\"&x;\"/></Types>";
		assertInvalid(DocumentTestFiles.ooxml(DOCX, xml), DOCX, InvalidDocumentFileException.MALFORMED);
	}

	@Test
	void secureFactoryDisablesDtdAndExternalAccess() {
		var factory = OoxmlPackageValidator.secureFactory();
		assertThat(factory.getProperty(javax.xml.stream.XMLInputFactory.SUPPORT_DTD)).isEqualTo(false);
		assertThat(factory.getProperty(javax.xml.stream.XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES))
			.isEqualTo(false);
		assertThat(factory.getProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_DTD)).isEqualTo("");
		assertThat(factory.getProperty(javax.xml.XMLConstants.ACCESS_EXTERNAL_SCHEMA)).isEqualTo("");
	}

	// 자원 상한: 엔트리 수
	@Test
	void rejectsTooManyEntries() throws IOException {
		OoxmlPackageValidator limited = new OoxmlPackageValidator(
				new OoxmlPackageValidator.Limits(3, 1024 * 1024, 1024 * 1024));
		Path ok = write(DocumentTestFiles.ooxml(DOCX)); // 3 entries
		assertThatCode(() -> limited.validate(ok, DOCX)).doesNotThrowAnyException();

		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("[Content_Types].xml", bytes(contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType())));
		entries.put("word/document.xml", bytes("<a/>"));
		entries.put("a.xml", bytes("<a/>"));
		entries.put("b.xml", bytes("<a/>"));
		Path tooMany = write(DocumentTestFiles.zip(entries));
		assertThatThrownBy(() -> limited.validate(tooMany, DOCX)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(OoxmlPackageValidator.LIMIT_EXCEEDED);
	}

	@Test
	void defaultLimitsMatchSpec() {
		assertThat(OoxmlPackageValidator.Limits.DEFAULT)
			.isEqualTo(new OoxmlPackageValidator.Limits(10_000, 1_048_576, 134_217_728));
	}

	@Test
	void rejectsDefaultEntryLimitPlusOne() throws IOException {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("[Content_Types].xml", bytes(contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType())));
		entries.put("word/document.xml", bytes("<a/>"));
		for (int i = 0; i < 9_999; i++) {
			entries.put("p/" + i, new byte[0]);
		}
		assertInvalid(DocumentTestFiles.zip(entries), DOCX, OoxmlPackageValidator.LIMIT_EXCEEDED);
		entries.remove("p/0");
		assertValid(DocumentTestFiles.zip(entries), DOCX);
	}

	// 자원 상한: [Content_Types].xml 크기 (선언·실제)
	@Test
	void rejectsContentTypesOverLimit() throws IOException {
		String padded = contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType()).replace("</Types>",
				"<!--" + " ".repeat(1024 * 1024) + "--></Types>");
		assertInvalid(DocumentTestFiles.ooxml(DOCX, padded), DOCX, OoxmlPackageValidator.LIMIT_EXCEEDED);
	}

	@Test
	void enforcesContentTypesLimitOnActualBytesWhenDeclaredSizeIsForged() throws IOException {
		OoxmlPackageValidator limited = new OoxmlPackageValidator(new OoxmlPackageValidator.Limits(100, 600, 1024));
		String padded = contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType()).replace("</Types>",
				"<!--" + " ".repeat(2000) + "--></Types>");
		byte[] zip = forgeUncompressedSize(DocumentTestFiles.ooxml(DOCX, padded), "[Content_Types].xml", 10);
		Path file = write(zip);
		assertThatThrownBy(() -> limited.validate(file, DOCX)).isInstanceOf(InvalidDocumentFileException.class);
	}

	// 자원 상한: 주 문서 파트 크기 (선언·실제), 압축 폭탄
	@Test
	void rejectsMainPartOverLimitByDeclaredSize() throws IOException {
		OoxmlPackageValidator limited = new OoxmlPackageValidator(new OoxmlPackageValidator.Limits(100, 4096, 1000));
		Path file = write(ooxmlWithMainPart(new byte[1001]));
		assertThatThrownBy(() -> limited.validate(file, DOCX)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(OoxmlPackageValidator.LIMIT_EXCEEDED);
		Path exact = write(ooxmlWithMainPart(new byte[1000]));
		assertThatCode(() -> limited.validate(exact, DOCX)).doesNotThrowAnyException();
	}

	@Test
	void rejectsMainPartWhenActualBytesExceedForgedDeclaredSize() throws IOException {
		OoxmlPackageValidator limited = new OoxmlPackageValidator(new OoxmlPackageValidator.Limits(100, 4096, 1000));
		byte[] zip = forgeUncompressedSize(ooxmlWithMainPart(new byte[50_000]), "word/document.xml", 10);
		Path file = write(zip);
		assertThatThrownBy(() -> limited.validate(file, DOCX)).isInstanceOf(InvalidDocumentFileException.class);
	}

	@Test
	void rejectsCompressionBombOverDefaultMainPartLimit() throws IOException {
		// 129MiB의 0 바이트는 deflate 후 약 130KB
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write(bytes(contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType())));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("word/document.xml"));
			byte[] chunk = new byte[1024 * 1024];
			for (int i = 0; i < 129; i++) {
				zip.write(chunk);
			}
			zip.closeEntry();
		}
		assertThat(bytes.size()).isLessThan(1024 * 1024);
		assertInvalid(bytes.toByteArray(), DOCX, OoxmlPackageValidator.LIMIT_EXCEEDED);
	}

	@Test
	void rejectsCrcMismatchInMainPart() throws IOException {
		byte[] zip = ooxmlWithMainPart(bytes("<?xml version=\"1.0\"?><root>original</root>"), true);
		byte[] original = bytes("original");
		int index = indexOf(zip, original);
		zip[index] = 'X'; // STORED 엔트리 내용 변경 → CRC 불일치
		assertInvalid(zip, DOCX, InvalidDocumentFileException.MALFORMED);
	}

	@Test
	void rejectsEncryptedEntryFlag() throws IOException {
		byte[] zip = DocumentTestFiles.ooxml(DOCX);
		// 중앙 디렉터리 헤더(PK\1\2)의 일반 목적 비트 플래그 bit 0 설정
		for (int i = 0; i + 4 < zip.length; i++) {
			if (zip[i] == 'P' && zip[i + 1] == 'K' && zip[i + 2] == 1 && zip[i + 3] == 2) {
				zip[i + 8] |= 1;
			}
		}
		assertInvalid(zip, DOCX, InvalidDocumentFileException.MALFORMED);
	}

	private byte[] ooxmlWithMainPart(byte[] mainPart) throws IOException {
		return ooxmlWithMainPart(mainPart, false);
	}

	private byte[] ooxmlWithMainPart(byte[] mainPart, boolean stored) throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
			zip.write(bytes(contentTypes(DOCX.mainPartName(), DOCX.mainPartContentType())));
			zip.closeEntry();
			ZipEntry main = new ZipEntry("word/document.xml");
			if (stored) {
				CRC32 crc = new CRC32();
				crc.update(mainPart);
				main.setMethod(ZipEntry.STORED);
				main.setSize(mainPart.length);
				main.setCompressedSize(mainPart.length);
				main.setCrc(crc.getValue());
			}
			zip.putNextEntry(main);
			zip.write(mainPart);
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	/** 중앙 디렉터리에서 엔트리의 비압축 크기 필드를 위조한다. */
	private static byte[] forgeUncompressedSize(byte[] zip, String entryName, int forgedSize) {
		ByteBuffer buffer = ByteBuffer.wrap(zip).order(ByteOrder.LITTLE_ENDIAN);
		byte[] name = bytes(entryName);
		for (int i = 0; i + 46 < zip.length; i++) {
			if (buffer.getInt(i) == 0x02014b50) {
				int nameLength = Short.toUnsignedInt(buffer.getShort(i + 28));
				if (nameLength == name.length && indexOf(zip, name, i + 46) == i + 46) {
					buffer.putInt(i + 24, forgedSize);
					return zip;
				}
			}
		}
		throw new IllegalArgumentException("entry not found");
	}

	private static int indexOf(byte[] haystack, byte[] needle) {
		return indexOf(haystack, needle, 0);
	}

	private static int indexOf(byte[] haystack, byte[] needle, int from) {
		outer: for (int i = from; i <= haystack.length - needle.length; i++) {
			for (int j = 0; j < needle.length; j++) {
				if (haystack[i + j] != needle[j]) {
					continue outer;
				}
			}
			return i;
		}
		return -1;
	}

	private void assertValid(byte[] content, DocumentFileType type) throws IOException {
		Path file = write(content);
		assertThatCode(() -> this.validator.validate(file, type)).doesNotThrowAnyException();
	}

	private void assertInvalid(byte[] content, DocumentFileType type, String message) throws IOException {
		Path file = write(content);
		assertThatThrownBy(() -> this.validator.validate(file, type)).isInstanceOf(InvalidDocumentFileException.class)
			.hasMessage(message);
	}

	private Path write(byte[] content) throws IOException {
		return Files.write(Files.createTempFile(this.dir, "ooxml", ".bin"), content);
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}

}
