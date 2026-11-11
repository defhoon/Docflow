package com.docflow.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
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
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-05 실제 HTTP(내장 Tomcat) 다운로드: 20MiB 스트리밍과 SHA-256 일치(AC-VDL-07), 클라이언트 연결 중단 시 ERROR 로그
 * 없음(AC-VDL-09). 세션 로그인과 CSRF를 실제로 사용한다.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DirtiesContext
@ExtendWith(OutputCaptureExtension.class)
class DocumentVersionDownloadHttpIntegrationTest {

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
	void streams20MiBDownloadWithMatchingHash() throws Exception {
		byte[] content = DocumentTestFiles.pdfOfSize(MAX_BYTES);
		Long documentId = createDocument(content);

		HttpResponse<InputStream> response = this.client.send(
				HttpRequest.newBuilder(uri(downloadPath(documentId))).header("Accept", "*/*").GET().build(),
				HttpResponse.BodyHandlers.ofInputStream());

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.headers().firstValueAsLong("Content-Length")).hasValue(MAX_BYTES);
		MessageDigest digest = MessageDigest.getInstance("SHA-256");
		long total = 0;
		try (InputStream in = response.body()) {
			byte[] buffer = new byte[64 * 1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				digest.update(buffer, 0, read);
				total += read;
			}
		}
		assertThat(total).isEqualTo(MAX_BYTES);
		assertThat(HexFormat.of().formatHex(digest.digest())).isEqualTo(sha256(content));
	}

	@Test
	void clientAbortDuringDownloadIsNotLoggedAsError(CapturedOutput output) throws Exception {
		Long documentId = createDocument(DocumentTestFiles.pdfOfSize(MAX_BYTES));
		int logStart = output.getOut().length();

		try (Socket socket = new Socket("localhost", this.port)) {
			socket.setSoTimeout(10_000);
			OutputStream out = socket.getOutputStream();
			out.write(("GET " + downloadPath(documentId) + " HTTP/1.1\r\nHost: localhost:" + this.port + "\r\nCookie: "
					+ cookieHeader() + "\r\nAccept: */*\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
			out.flush();
			InputStream in = socket.getInputStream();
			byte[] buffer = new byte[64 * 1024];
			int received = in.read(buffer);
			assertThat(new String(buffer, 0, received, StandardCharsets.ISO_8859_1)).startsWith("HTTP/1.1 200");
			socket.setSoLinger(true, 0); // RST로 즉시 끊는다
		}
		// 서버가 남은 본문을 쓰다가 중단을 감지할 시간을 준다
		Thread.sleep(1500);

		String log = output.getOut().substring(logStart);
		assertThat(log.lines().filter((line) -> line.contains(" ERROR ")).collect(Collectors.toList())).isEmpty();
		// 이후 요청은 정상
		HttpResponse<byte[]> next = this.client.send(
				HttpRequest.newBuilder(uri(downloadPath(documentId))).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
		assertThat(next.statusCode()).isEqualTo(200);
		assertThat(next.body()).hasSize((int) MAX_BYTES);
	}

	private Long createDocument(byte[] content) throws Exception {
		String boundary = "----docflow" + UUID.randomUUID();
		ByteArrayOutputStream body = new ByteArrayOutputStream(content.length + 1024);
		body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"title\"\r\n\r\n다운로드\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(("--" + boundary
				+ "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"big.pdf\"\r\nContent-Type: application/pdf\r\n\r\n")
			.getBytes(StandardCharsets.UTF_8));
		body.write(content);
		body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
		HttpResponse<String> created = send(HttpRequest.newBuilder(uri("/api/projects/" + this.projectId + "/documents"))
			.header("Content-Type", "multipart/form-data; boundary=" + boundary)
			.header("X-XSRF-TOKEN", csrfToken())
			.timeout(Duration.ofSeconds(60))
			.POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())));
		assertThat(created.statusCode()).isEqualTo(201);
		String location = created.headers().firstValue("Location").orElseThrow();
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
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

	private String cookieHeader() {
		return this.cookies.getCookieStore()
			.getCookies()
			.stream()
			.map((cookie) -> cookie.getName() + "=" + cookie.getValue())
			.collect(Collectors.joining("; "));
	}

	private String downloadPath(Long documentId) {
		return "/api/projects/" + this.projectId + "/documents/" + documentId + "/versions/1/download";
	}

	private URI uri(String path) {
		return URI.create("http://localhost:" + this.port + path);
	}

	private static String sha256(byte[] content) throws Exception {
		return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
	}

}
