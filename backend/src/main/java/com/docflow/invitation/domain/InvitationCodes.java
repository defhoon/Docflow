package com.docflow.invitation.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * 초대코드 생성·해시 (#26). 코드는 128비트 난수를 Crockford Base32(혼동 문자 제외) 26자로 표시하고 4자마다 하이픈을
 * 넣는다. 입력은 대소문자·하이픈·공백을 무시하고 정규화한 뒤 SHA-256으로 비교한다.
 */
public final class InvitationCodes {

	private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

	private static final SecureRandom RANDOM = new SecureRandom();

	private InvitationCodes() {
	}

	public static String generate() {
		byte[] bytes = new byte[16];
		RANDOM.nextBytes(bytes);
		StringBuilder raw = new StringBuilder(26);
		int buffer = 0;
		int bits = 0;
		for (byte b : bytes) {
			buffer = (buffer << 8) | (b & 0xff);
			bits += 8;
			while (bits >= 5) {
				raw.append(ALPHABET[(buffer >> (bits - 5)) & 31]);
				bits -= 5;
			}
		}
		if (bits > 0) {
			raw.append(ALPHABET[(buffer << (5 - bits)) & 31]);
		}
		StringBuilder grouped = new StringBuilder();
		for (int i = 0; i < raw.length(); i++) {
			if (i > 0 && i % 4 == 0) {
				grouped.append('-');
			}
			grouped.append(raw.charAt(i));
		}
		return grouped.toString();
	}

	/** 정규화된 코드의 SHA-256 16진수. 형식이 맞지 않는 입력도 해시해 같은 경로로 실패하게 한다. */
	public static String hash(String code) {
		String normalized = (code == null) ? "" : code.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
