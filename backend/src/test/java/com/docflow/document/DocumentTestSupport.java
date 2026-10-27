package com.docflow.document;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.docflow.auth.security.AuthenticatedUser;
import com.docflow.user.domain.User;

/**
 * SPEC-03 통합 테스트 공통: JVM당 하나의 임시 저장 루트(같은 설정이면 테스트 컨텍스트를 재사용)와 정리 순서.
 */
public final class DocumentTestSupport {

	public static final Path STORAGE_ROOT = createRoot();

	private DocumentTestSupport() {
	}

	public static void registerStorage(DynamicPropertyRegistry registry) {
		registry.add("docflow.storage.dir", STORAGE_ROOT::toString);
	}

	/** 세션 인증 사용자 ({@code ProjectApiIntegrationTest.as}와 같은 방식). */
	public static RequestPostProcessor as(User user) {
		return SecurityMockMvcRequestPostProcessors.authentication(
				UsernamePasswordAuthenticationToken.authenticated(AuthenticatedUser.from(user), null, List.of()));
	}

	/** FK 순서: document_versions → documents → projects(구성원 CASCADE) → users. */
	public static void cleanDatabase(JdbcTemplate jdbcTemplate) {
		jdbcTemplate.update("DELETE FROM document_versions");
		jdbcTemplate.update("DELETE FROM documents");
		jdbcTemplate.update("DELETE FROM projects");
		jdbcTemplate.update("DELETE FROM users");
	}

	public static void cleanStorage() {
		try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
			paths.sorted((a, b) -> b.getNameCount() - a.getNameCount())
				.filter((path) -> !path.equals(STORAGE_ROOT))
				.forEach((path) -> {
					try {
						Files.delete(path);
					}
					catch (IOException ex) {
						throw new UncheckedIOException(ex);
					}
				});
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** 저장 루트 아래 일반 파일 (임시 파일 포함), 루트 기준 상대 경로. */
	public static List<String> storedFiles() {
		try (Stream<Path> paths = Files.walk(STORAGE_ROOT)) {
			return paths.filter(Files::isRegularFile).map((path) -> STORAGE_ROOT.relativize(path).toString()).sorted()
				.toList();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static Path createRoot() {
		try {
			Path root = Files.createTempDirectory("docflow-storage-test-");
			root.toFile().deleteOnExit();
			return root;
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
