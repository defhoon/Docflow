package com.docflow.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-03 §5.6 크기 제한을 실제 HTTP multipart 요청(내장 Tomcat, 실제 로그인 세션·CSRF 쿠키)으로 확인한다
 * (AC-DOC-07, AC-DOC-09의 Backend 직접 경로 부분). MockMvc는 multipart 크기 제한을 적용하지 않으므로 사용하지 않는다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DocumentUploadSizeIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	private static final int MIB = 1024 * 1024;

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

	@BeforeEach
	void setUp() throws Exception {
		cleanUp();
		User owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, owner.getId());
		login();
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	@Test
	void acceptsUpToExactly20MiB() throws Exception {
		assertThat(upload(DocumentTestFiles.pdfOfSize(MIB)).statusCode()).isEqualTo(201);
		assertThat(upload(DocumentTestFiles.pdfOfSize(19L * MIB)).statusCode()).isEqualTo(201);

		HttpResponse<String> boundary = upload(DocumentTestFiles.pdfOfSize(20_971_520));
		assertThat(boundary.statusCode()).isEqualTo(201);
		assertThat(boundary.body()).contains("\"sizeBytes\":20971520");
		assertThat(this.jdbcTemplate.queryForObject("SELECT max(size_bytes) FROM document_versions", Long.class))
			.isEqualTo(20_971_520L);
	}

	@Test
	void rejectsOneByteOver20MiBWithFileTooLarge() throws Exception {
		HttpResponse<String> response = upload(DocumentTestFiles.pdfOfSize(20_971_521));

		assertThat(response.statusCode()).isEqualTo(413);
		assertThat(response.body()).contains("\"code\":\"FILE_TOO_LARGE\"")
			.contains("파일은 20MB 이하만 업로드할 수 있습니다.");
		assertNothingStored();
	}

	/**
	 * 요청 상한(21MiB) 초과: 서버는 본문을 끝까지 읽지 않고 {@code 413}과 {@code Connection: close}로 응답한 뒤 연결을
	 * 닫는다 (§5.6 D4 [검증 필요] 재현). 본문을 모두 보낸 뒤에 응답을 읽는 클라이언트(Java {@link HttpClient})는 응답 대신
	 * 연결 오류를 받으므로, 쓰기 실패와 관계없이 응답을 읽는 소켓 클라이언트로 확인한다.
	 */
	@Test
	void rejectsRequestOverMultipartRequestLimitBeforeReadingWholeBody() throws Exception {
		String response = uploadOverRawSocket(22L * MIB);

		assertThat(response).startsWith("HTTP/1.1 413");
		assertThat(response).containsIgnoringCase("Connection: close");
		assertThat(response).contains("\"code\":\"FILE_TOO_LARGE\"");
		assertNothingStored();
	}

	private void assertNothingStored() {
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM documents", Integer.class)).isZero();
		assertThat(DocumentTestSupport.storedFiles()).isEmpty();
	}

	private void login() throws Exception {
		send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET());
		HttpResponse<String> login = send(HttpRequest.newBuilder(uri("/api/auth/login"))
			.header("Content-Type", "application/json")
			.header("X-XSRF-TOKEN", csrfToken())
			.POST(HttpRequest.BodyPublishers
				.ofString("{\"email\":\"owner@test.local\",\"password\":\"" + PASSWORD + "\"}")));
		assertThat(login.statusCode()).isEqualTo(200);
		// 로그인 시 CSRF 토큰이 교체되므로 다시 발급받는다
		send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET());
	}

	private HttpResponse<String> upload(byte[] content) throws Exception {
		String boundary = "----docflow" + UUID.randomUUID();
		ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 1024);
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n크기 테스트\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(("--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(content);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return send(HttpRequest.newBuilder(uri("/api/projects/" + this.projectId + "/documents"))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.header("X-XSRF-TOKEN", csrfToken())
			.timeout(Duration.ofSeconds(60))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
	}

	/** 본문 쓰기가 실패해도(서버 조기 종료) 이미 도착한 응답을 읽는다. */
	private String uploadOverRawSocket(long fileSize) throws Exception {
		String boundary = "----docflow" + UUID.randomUUID();
		byte[] head = ("--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\nbig\r\n--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8);
		byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
		String cookieHeader = this.cookies.getCookieStore()
			.getCookies()
			.stream()
			.map((cookie) -> cookie.getName() + "=" + cookie.getValue())
			.reduce((a, b) -> a + "; " + b)
			.orElseThrow();
		try (Socket socket = new Socket("localhost", this.port)) {
			socket.setSoTimeout(30_000);
			OutputStream out = socket.getOutputStream();
			try {
				out.write(("POST /api/projects/" + this.projectId + "/documents HTTP/1.1\r\nHost: localhost\r\n"
						+ "Content-Type: multipart/form-data; boundary=" + boundary + "\r\nContent-Length: "
						+ (head.length + fileSize + tail.length) + "\r\nCookie: " + cookieHeader
						+ "\r\nX-XSRF-TOKEN: " + csrfToken() + "\r\n\r\n")
					.getBytes(StandardCharsets.UTF_8));
				out.write(head);
				byte[] chunk = new byte[64 * 1024];
				for (long sent = 0; sent < fileSize; sent += chunk.length) {
					out.write(chunk, 0, (int) Math.min(chunk.length, fileSize - sent));
				}
				out.write(tail);
				out.flush();
			}
			catch (IOException expected) {
				// 서버가 본문을 다 읽기 전에 응답하고 연결을 닫음
			}
			return new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		}
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

}
