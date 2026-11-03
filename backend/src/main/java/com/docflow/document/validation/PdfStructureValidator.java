package com.docflow.document.validation;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 최소 구조 검증 (SPEC-03 §5.5.2, 결정 D9). 앞 1,024바이트, 뒤 1,024바이트, {@code startxref}가 가리키는 위치의
 * 64바이트만 읽는다.
 * <p>
 * 헤더(P1), {@code %%EOF}(P2), {@code startxref}(P3), 오프셋 범위(P4), 교차 참조 위치(P5)만 확인한다. 객체·스트림의
 * 유효성과 렌더링 가능 여부는 보장하지 않으므로 최소 구조를 갖추고 내부가 손상된 PDF는 통과할 수 있다.
 */
public class PdfStructureValidator {

	static final int WINDOW = 1024;

	static final int XREF_PEEK = 64;

	private static final Pattern HEADER = Pattern.compile("%PDF-(1\\.[0-7]|2\\.0)");

	private static final Pattern STARTXREF = Pattern.compile("startxref[\\x00\\t\\n\\f\\r ]+(\\d{1,19})");

	private static final Pattern XREF_TARGET = Pattern.compile("^[\\x00\\t\\n\\f\\r ]*(xref|\\d+[\\x00\\t\\n\\f\\r ]+\\d+[\\x00\\t\\n\\f\\r ]+obj)");

	public void validate(Path file) {
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			long size = channel.size();
			String head = read(channel, 0, (int) Math.min(WINDOW, size));
			Matcher header = HEADER.matcher(head);
			if (!header.find()) {
				throw invalid();
			}
			long base = header.start();

			long tailStart = Math.max(0, size - WINDOW);
			String tail = read(channel, tailStart, (int) (size - tailStart));
			int eof = tail.lastIndexOf("%%EOF");
			if (eof < 0) {
				throw invalid();
			}
			Matcher startxref = STARTXREF.matcher(tail.substring(0, eof));
			long offset = -1;
			while (startxref.find()) {
				offset = parseOffset(startxref.group(1));
			}
			if (offset < 0) {
				throw invalid();
			}
			long target = base + offset;
			if (target < 0 || target >= size) {
				throw invalid();
			}
			String atTarget = read(channel, target, (int) Math.min(XREF_PEEK, size - target));
			if (!XREF_TARGET.matcher(atTarget).find()) {
				throw invalid();
			}
		}
		catch (IOException ex) {
			throw invalid();
		}
	}

	private static long parseOffset(String digits) {
		try {
			return Long.parseLong(digits);
		}
		catch (NumberFormatException ex) {
			return -1;
		}
	}

	private static String read(FileChannel channel, long position, int length) throws IOException {
		ByteBuffer buffer = ByteBuffer.allocate(length);
		while (buffer.hasRemaining()) {
			if (channel.read(buffer, position + buffer.position()) < 0) {
				break;
			}
		}
		return new String(buffer.array(), 0, buffer.position(), StandardCharsets.ISO_8859_1);
	}

	private static InvalidDocumentFileException invalid() {
		return new InvalidDocumentFileException(InvalidDocumentFileException.MALFORMED);
	}

}
