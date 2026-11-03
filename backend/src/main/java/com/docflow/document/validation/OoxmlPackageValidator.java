package com.docflow.document.validation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * OOXML(DOCX·XLSX·PPTX) 검증 (SPEC-03 §5.5.3, 결정 D8·D10·D11).
 * <p>
 * {@code [Content_Types].xml}을 JDK 기본 StAX 파서로 파싱해 주 문서 파트의 실효 Content Type이 형식별 값과 정확히
 * 일치하는지 확인하고, 주 문서 파트를 끝까지 압축 해제해 크기·CRC-32를 확인한다. 그 밖의 엔트리는 압축을 풀지 않는다.
 * 패키지 전체의 유효성은 보장하지 않는다.
 */
public class OoxmlPackageValidator {

	static final String CONTENT_TYPES_ENTRY = "[Content_Types].xml";

	static final String CONTENT_TYPES_NAMESPACE = "http://schemas.openxmlformats.org/package/2006/content-types";

	static final String LIMIT_EXCEEDED = "파일 구조가 허용 범위를 넘어 확인할 수 없습니다.";

	static final String TYPE_MISMATCH = "파일 확장자와 실제 문서 형식이 일치하지 않습니다.";

	private static final int BUFFER_SIZE = 8192;

	private final Limits limits;

	public OoxmlPackageValidator(Limits limits) {
		this.limits = limits;
	}

	/**
	 * 암호화된 엔트리(일반 목적 비트 플래그 bit 0)가 있으면 JDK {@link ZipFile}이 열 때 {@code ZipException}을 던지므로
	 * 손상으로 거부된다.
	 */
	public void validate(Path file, DocumentFileType type) {
		try (ZipFile zip = new ZipFile(file.toFile())) {
			if (zip.size() > this.limits.maxEntries()) {
				throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
			}
			String mainEntryName = type.mainPartName().substring(1);
			ZipEntry contentTypesEntry = null;
			ZipEntry mainEntry = null;
			int count = 0;
			Enumeration<? extends ZipEntry> entries = zip.entries();
			while (entries.hasMoreElements()) {
				ZipEntry entry = entries.nextElement();
				if (++count > this.limits.maxEntries()) {
					throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
				}
				if (entry.getName().equals(CONTENT_TYPES_ENTRY)) {
					contentTypesEntry = entry;
				}
				else if (entry.getName().equals(mainEntryName)) {
					mainEntry = entry;
				}
			}
			if (contentTypesEntry == null || mainEntry == null) {
				throw new InvalidDocumentFileException(TYPE_MISMATCH);
			}
			String effectiveType = effectiveContentType(zip, contentTypesEntry, type.mainPartName());
			if (!type.mainPartContentType().equals(effectiveType)) {
				throw new InvalidDocumentFileException(TYPE_MISMATCH);
			}
			verifyEntry(zip, mainEntry, this.limits.maxMainPartBytes());
		}
		catch (IOException | XMLStreamException | RuntimeException ex) {
			if (ex instanceof InvalidDocumentFileException invalid) {
				throw invalid;
			}
			// ZipException, 압축 해제 오류, XML 형식 오류(DOCTYPE 내부 서브셋·미선언 엔티티 포함) 등
			throw malformed();
		}
	}

	private String effectiveContentType(ZipFile zip, ZipEntry entry, String partName)
			throws IOException, XMLStreamException {
		if (entry.getSize() > this.limits.maxContentTypesBytes()) {
			throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
		}
		Map<String, String> defaults = new HashMap<>();
		Map<String, String> overrides = new HashMap<>();
		try (InputStream in = new LimitedInputStream(zip.getInputStream(entry), this.limits.maxContentTypesBytes())) {
			XMLStreamReader reader = secureFactory().createXMLStreamReader(in);
			try {
				boolean rootSeen = false;
				while (reader.hasNext()) {
					int event = reader.next();
					if (event == XMLStreamConstants.DTD) {
						throw malformed();
					}
					if (event != XMLStreamConstants.START_ELEMENT) {
						continue;
					}
					if (!rootSeen) {
						rootSeen = true;
						if (!"Types".equals(reader.getLocalName())
								|| !CONTENT_TYPES_NAMESPACE.equals(reader.getNamespaceURI())) {
							throw malformed();
						}
						continue;
					}
					if (!CONTENT_TYPES_NAMESPACE.equals(reader.getNamespaceURI())) {
						continue;
					}
					if ("Default".equals(reader.getLocalName())) {
						String extension = reader.getAttributeValue(null, "Extension");
						String contentType = reader.getAttributeValue(null, "ContentType");
						if (extension != null && contentType != null) {
							defaults.putIfAbsent(extension.toLowerCase(Locale.ROOT), contentType);
						}
					}
					else if ("Override".equals(reader.getLocalName())) {
						String name = reader.getAttributeValue(null, "PartName");
						String contentType = reader.getAttributeValue(null, "ContentType");
						if (name != null && contentType != null
								&& overrides.put(name.toLowerCase(Locale.ROOT), contentType) != null) {
							throw malformed();
						}
					}
				}
				if (!rootSeen) {
					throw malformed();
				}
			}
			finally {
				reader.close();
			}
		}
		String override = overrides.get(partName.toLowerCase(Locale.ROOT));
		if (override != null) {
			return override;
		}
		String extension = partName.substring(partName.lastIndexOf('.') + 1);
		return defaults.get(extension.toLowerCase(Locale.ROOT));
	}

	/**
	 * 엔트리를 끝까지 압축 해제하며 실제 바이트 수와 CRC-32가 중앙 디렉터리 선언 값과 같은지 확인한다. 내용은 보관하지
	 * 않는다.
	 */
	private static void verifyEntry(ZipFile zip, ZipEntry entry, long maxBytes) throws IOException {
		long declaredSize = entry.getSize();
		if (declaredSize > maxBytes) {
			throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
		}
		CRC32 crc = new CRC32();
		long total = 0;
		byte[] buffer = new byte[BUFFER_SIZE];
		try (InputStream in = zip.getInputStream(entry)) {
			int read;
			while ((read = in.read(buffer)) != -1) {
				total += read;
				if (total > maxBytes) {
					throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
				}
				crc.update(buffer, 0, read);
			}
		}
		if (declaredSize < 0 || total != declaredSize || entry.getCrc() < 0 || crc.getValue() != entry.getCrc()) {
			throw malformed();
		}
	}

	/**
	 * JDK 기본 StAX 구현을 직접 생성하고 DTD·외부 엔티티·외부 리소스 접근을 모두 끈다 (SPEC-03 §5.5.3-6).
	 */
	static XMLInputFactory secureFactory() {
		XMLInputFactory factory = XMLInputFactory.newDefaultFactory();
		factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
		factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
		factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
		factory.setXMLResolver((publicId, systemId, baseUri, namespace) -> {
			throw new XMLStreamException("External resources are not allowed");
		});
		return factory;
	}

	private static InvalidDocumentFileException malformed() {
		return new InvalidDocumentFileException(InvalidDocumentFileException.MALFORMED);
	}

	/**
	 * OOXML 검사 자원 상한 (SPEC-03 §5.5.4, 결정 D11 잠정값).
	 */
	public record Limits(int maxEntries, long maxContentTypesBytes, long maxMainPartBytes) {

		public static final Limits DEFAULT = new Limits(10_000, 1024 * 1024, 128L * 1024 * 1024);

	}

	/**
	 * 상한을 넘는 바이트를 읽으려 하면 실패하는 스트림. 상한까지만 읽고 성공으로 처리하지 않는다.
	 */
	static final class LimitedInputStream extends InputStream {

		private final InputStream delegate;

		private long remaining;

		LimitedInputStream(InputStream delegate, long limit) {
			this.delegate = delegate;
			this.remaining = limit;
		}

		@Override
		public int read() throws IOException {
			int b = this.delegate.read();
			if (b != -1) {
				consume(1);
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			int read = this.delegate.read(b, off, len);
			if (read > 0) {
				consume(read);
			}
			return read;
		}

		private void consume(long count) {
			this.remaining -= count;
			if (this.remaining < 0) {
				throw new InvalidDocumentFileException(LIMIT_EXCEEDED);
			}
		}

		@Override
		public void close() throws IOException {
			this.delegate.close();
		}

	}

}
