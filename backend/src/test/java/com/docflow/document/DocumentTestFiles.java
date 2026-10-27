package com.docflow.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.docflow.document.validation.DocumentFileType;

/**
 * SPEC-03 테스트용 최소 문서 생성기. 실제 Office·PDF 도구가 만든 파일이 아니라 검증 규칙이 보는 구조만 갖춘다.
 */
public final class DocumentTestFiles {

	public static final String CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";

	private DocumentTestFiles() {
	}

	/** 최소 구조(P1~P5)를 갖춘 PDF. */
	public static byte[] pdf() {
		return pdfOfSize(0);
	}

	/**
	 * 정확히 {@code size}바이트인 PDF. 헤더와 교차 참조 표 사이를 주석 줄로 채운다. {@code size}가 최소 길이보다 작으면
	 * 최소 PDF를 반환한다.
	 */
	public static byte[] pdfOfSize(long size) {
		String header = "%PDF-1.4\n";
		String body = "1 0 obj\n<< /Type /Catalog >>\nendobj\n";
		int baseLength = (header + body).length();
		String tailWithoutOffset = "xref\n0 2\n0000000000 65535 f \n0000000009 00000 n \ntrailer\n<< /Size 2 /Root 1 0 R >>\nstartxref\n";
		long padding = 0;
		for (int i = 0; i < 3; i++) {
			long xrefOffset = baseLength + padding;
			String tail = tailWithoutOffset + xrefOffset + "\n%%EOF\n";
			long total = baseLength + padding + tail.length();
			if (size <= 0 || total == size) {
				break;
			}
			padding = Math.max(0, padding + (size - total));
		}
		long xrefOffset = baseLength + padding;
		byte[] tail = (tailWithoutOffset + xrefOffset + "\n%%EOF\n").getBytes(StandardCharsets.ISO_8859_1);
		byte[] result = new byte[(int) (baseLength + padding + tail.length)];
		byte[] head = (header + body).getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(head, 0, result, 0, head.length);
		if (padding > 0) {
			Arrays.fill(result, head.length, (int) (head.length + padding), (byte) ' ');
			result[head.length] = '%';
			result[(int) (head.length + padding - 1)] = '\n';
		}
		System.arraycopy(tail, 0, result, (int) (head.length + padding), tail.length);
		if (size > 0 && result.length != size) {
			throw new IllegalArgumentException("Cannot build PDF of size " + size);
		}
		return result;
	}

	public static byte[] ooxml(DocumentFileType type) {
		return ooxml(type, contentTypes(type.mainPartName(), type.mainPartContentType()));
	}

	public static byte[] ooxml(DocumentFileType type, String contentTypesXml) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		entries.put("[Content_Types].xml", contentTypesXml.getBytes(StandardCharsets.UTF_8));
		entries.put("_rels/.rels", ("<?xml version=\"1.0\"?><Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
				+ "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\""
				+ type.mainPartName().substring(1) + "\"/></Relationships>")
			.getBytes(StandardCharsets.UTF_8));
		entries.put(type.mainPartName().substring(1),
				"<?xml version=\"1.0\"?><root>main part</root>".getBytes(StandardCharsets.UTF_8));
		return zip(entries);
	}

	/** {@code Default xml = application/xml}과 주 문서 파트 {@code Override} 하나를 가진 Content Types. */
	public static String contentTypes(String partName, String contentType) {
		return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" + "<Types xmlns=\"" + CT_NS + "\">"
				+ "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
				+ "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" + "<Override PartName=\"" + partName
				+ "\" ContentType=\"" + contentType + "\"/>" + "</Types>";
	}

	public static byte[] zip(Map<String, byte[]> entries) {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return bytes.toByteArray();
	}

	public static byte[] valid(DocumentFileType type) {
		return type.isOoxml() ? ooxml(type) : pdf();
	}

}
