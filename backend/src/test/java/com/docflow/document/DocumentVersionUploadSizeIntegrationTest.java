package com.docflow.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-04 AC-VER-07: 새 버전 파일 크기 경계를 실제 HTTP(내장 Tomcat, multipart 상한 활성)로 확인한다. 세션 로그인과 CSRF
 * 헤더를 실제로 사용한다. 요청 상한(21MiB) 초과 동작은 SPEC-03과 같은 multipart 설정을 공유하므로
 * {@code DocumentUploadSizeIntegrationTest}가 다룬다 (D4).
 * <p>
 * 기존 테스트 컨텍스트가 캐시에 남아 있는 상태에서 PostgreSQL 기본 연결 상한(100)을 넘지 않도록, 클래스가 끝나면 이
 * 컨텍스트(Hikari 풀)를 닫는다.
 */
@DirtiesContext
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentVersionUploadSizeIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	private static final long MAX_BYTES = 20_971_520;

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@LocalServerPort
	private int port;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private final CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);

	private final HttpClient client = HttpClient.newBuilder()
		.cookieHandler(this.cookies)
		.connectTimeout(Duration.ofSeconds(10))
		.build();

	private Long projectId;

	private Long documentId;

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		User owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, owner.getId());
		login();
		HttpResponse<String> created = post("/api/projects/" + this.projectId + "/documents", DocumentTestFiles.pdf(),
				true);
		assertThat(created.statusCode()).isEqualTo(201);
		String location = created.headers().firstValue("Location").orElseThrow();
		this.documentId = Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	@Test
	void acceptsNewVersionOfExactly20MiBWithMatchingSizeAndHash() throws Exception {
		byte[] content = DocumentTestFiles.pdfOfSize(MAX_BYTES);

		HttpResponse<String> response = uploadVersion(content);

		assertThat(response.statusCode()).isEqualTo(201);
		assertThat(response.body()).contains("\"versionNumber\":2").contains("\"sizeBytes\":20971520");
		String key = this.jdbcTemplate.queryForObject(
				"SELECT storage_key FROM document_versions WHERE document_id = ? AND version_number = 2", String.class,
				this.documentId);
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT sha256 FROM document_versions WHERE document_id = ? AND version_number = 2", String.class,
				this.documentId))
			.isEqualTo(sha256(content));
		assertThat(Files.size(DocumentTestSupport.STORAGE_ROOT.resolve(key))).isEqualTo(MAX_BYTES);
	}

	@Test
	void rejectsNewVersionOneByteOver20MiBWithoutStoring() throws Exception {
		List<String> filesBefore = DocumentTestSupport.storedFiles();

		HttpResponse<String> response = uploadVersion(DocumentTestFiles.pdfOfSize(MAX_BYTES + 1));

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(response.body()).contains("\"code\":\"FILE_TOO_LARGE\"")
			.contains("파일은 20MB 이하만 업로드할 수 있습니다.");
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions", Integer.class))
			.isEqualTo(1);
		assertThat(DocumentTestSupport.storedFiles()).isEqualTo(filesBefore);
	}

	private HttpResponse<String> uploadVersion(byte[] content) throws Exception {
		return post("/api/projects/" + this.projectId + "/documents/" + this.documentId + "/versions", content, false);
	}

	private HttpResponse<String> post(String path, byte[] content, boolean withTitle) throws Exception {
		String boundary = "----docflow" + UUID.randomUUID();
		ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 1024);
		if (withTitle) {
			body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n크기 테스트\r\n")
				.getBytes(StandardCharsets.UTF_8));
		}
		body.write(("--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(content);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return send(HttpRequest.newBuilder(uri(path))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.header("X-XSRF-TOKEN", csrfToken())
			.timeout(Duration.ofSeconds(60))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
	}

	private void login() throws Exception {
		send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET());
		HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-XSRF-TOKEN", csrfToken())
			.POST(HttpRequest.BodyPublishers
				.ofString("{\"email\":\"owner@test.local\",\"password\":\"" + PASSWORD + "\"}")));
		assertThat(login.statusCode()).isEqualTo(200);
		send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET());
	}

	private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
		return this.client.send(request.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
	}

	private String csrfToken() {
		return this.cookies.getCookieStore()
			.getCookies()
			.stream()
			.filter((cookie) -> cookie.getName().equals("XSRF-TOKEN"))
			.map(HttpCookie::getValue)
			.findFirst()
			.orElseThrow();
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

}
