package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.docflow.document.repository.DocumentRepository;
import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * SPEC-04 동시성·잠금 통합 테스트 (§7.4, §11.3, AC-VER-04·06·10, 결정 I3~I5). 실제 PostgreSQL에서 별도 JDBC 연결이 잠금을
 * 잡거나 미커밋 쓰기를 둔 상태로 요청을 보내고, {@code pg_locks}로 요청이 대기 상태에 들어간 것을 확인한 뒤 진행한다. 테스트
 * 트랜잭션이 결과를 가리지 않도록 {@code @Transactional}을 쓰지 않는다.
 * <p>
 * 기존 테스트 컨텍스트가 캐시에 남아 있는 상태에서 PostgreSQL 기본 연결 상한(100)을 넘지 않도록, 클래스가 끝나면 이
 * 컨텍스트(Hikari 풀)를 닫는다.
 */
@DirtiesContext
@SpringBootTest(properties = "logging.level.org.hibernate.SQL=DEBUG")
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class DocumentVersionConcurrencyIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

	/** 결정 I5: 기존 Hikari 설정(기본 풀 10개)을 유지하고 동시 요청은 8개로 한다. */
	private static final int PARALLEL_REQUESTS = 8;

	private static final String LOCK_NOT_AVAILABLE = "55P03";

	@DynamicPropertySource
	static void storage(DynamicPropertyRegistry registry) {
		DocumentTestSupport.registerStorage(registry);
	}

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserAccountService userAccountService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private DocumentRepository documentRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	private ExecutorService executor;

	private User owner;

	private User member;

	private Long projectId;

	@BeforeEach
	void setUp() throws InterruptedException {
		cleanUp();
		this.executor = Executors.newFixedThreadPool(PARALLEL_REQUESTS);
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.projectId = this.jdbcTemplate.queryForObject(
				"INSERT INTO projects (name, owner_id) VALUES ('P', ?) RETURNING id", Long.class, this.owner.getId());
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", this.projectId,
				this.member.getId());
	}

	@AfterEach
	void cleanUp() throws InterruptedException {
		if (this.executor != null) {
			this.executor.shutdownNow();
			this.executor.awaitTermination(30, TimeUnit.SECONDS);
		}
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// §7.4, §13.2, 결정 I3: 잠금 조회는 join 없이 documents 행 하나만 FOR NO KEY UPDATE로 잠그고 users 행은 잠그지 않는다
	@Test
	void lockQueryLocksOnlyTheDocumentRow(CapturedOutput output) throws Exception {
		Long documentId = createDocument();
		int logStart = output.getOut().length();

		new TransactionTemplate(this.transactionManager).executeWithoutResult((status) -> {
			assertThat(this.documentRepository.findByIdAndProjectIdForUpdate(documentId, this.projectId)).isPresent();
			try (Connection other = this.dataSource.getConnection()) {
				// 작성자(users) 행은 다른 트랜잭션이 배타적으로 잠글 수 있다
				lockNoWait(other, "SELECT id FROM users WHERE id = ? FOR NO KEY UPDATE NOWAIT", this.owner.getId());
				// 문서 행은 이미 잠겨 있다
				assertThatThrownBy(() -> lockNoWait(other,
						"SELECT id FROM documents WHERE id = ? FOR NO KEY UPDATE NOWAIT", documentId))
					.isInstanceOfSatisfying(SQLException.class,
							(ex) -> assertThat(ex.getSQLState()).isEqualTo(LOCK_NOT_AVAILABLE));
			}
			catch (SQLException ex) {
				throw new IllegalStateException(ex);
			}
		});

		List<String> lockStatements = output.getOut()
			.substring(logStart)
			.lines()
			.filter((line) -> line.contains("org.hibernate.SQL") && line.contains(" for "))
			.toList();
		System.out.println("[SPEC-04 I3] Hibernate lock SQL: " + lockStatements);
		assertThat(lockStatements).hasSize(1);
		assertThat(lockStatements.get(0)).contains("from documents")
			.contains("for no key update")
			.doesNotContain(" join ")
			.doesNotContain("users");
	}

	// AC-VER-04 (결정적 직렬화): 문서 행이 잠긴 동안 두 요청이 차례로 대기하고, 해제 후 v2·v3을 중복 없이 받는다
	@Test
	void requestsWaitForDocumentLockAndReceiveDistinctNumbers() throws Exception {
		Long documentId = createDocument();
		Future<Timed> first;
		Future<Timed> second;
		long blockedAt;
		try (Connection blocker = this.dataSource.getConnection()) {
			blocker.setAutoCommit(false);
			lockNoWait(blocker, "SELECT id FROM documents WHERE id = ? FOR NO KEY UPDATE", documentId);
			first = submit(this.owner, documentId);
			awaitWaitingLocks(1);
			second = submit(this.member, documentId);
			awaitWaitingLocks(2);
			// 두 요청 모두 파일을 배치한 뒤 잠금을 기다린다(파일 처리 중에는 잠금을 잡지 않는다).
			assertThat(DocumentTestSupport.storedFiles()).hasSize(3);
			assertThat(versionCount(documentId)).isEqualTo(1);
			blockedAt = System.nanoTime();
			blocker.commit();
		}
		Timed a = first.get(30, TimeUnit.SECONDS);
		Timed b = second.get(30, TimeUnit.SECONDS);
		long afterRelease = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - blockedAt);

		assertThat(a.response().getStatus()).isEqualTo(201);
		assertThat(b.response().getStatus()).isEqualTo(201);
		Set<Integer> numbers = Set.of(versionNumber(a), versionNumber(b));
		assertThat(numbers).containsExactlyInAnyOrder(2, 3);
		assertThat(versionNumbers(documentId)).containsExactly(1, 2, 3);
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyInAnyOrderElementsOf(storageKeys(documentId));
		System.out.printf("[SPEC-04 AC-VER-04 lock-wait] serialized: first=v%d %dms, second=v%d %dms, release→both done %dms%n",
				versionNumber(a), a.millis(), versionNumber(b), b.millis(), afterRelease);
	}

	// AC-VER-04 (병렬 부하, 결정 OQ-4·I4·I5): 같은 문서에 8개 동시 등록 → 모두 201, 번호 2..9 연속, 행·파일 수 일치. 대기 시간은 측정만 한다
	@Test
	void parallelUploadsGetContiguousNumbersWithoutLoss() throws Exception {
		Long documentId = createDocument();
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Timed>> futures = new ArrayList<>();
		for (int i = 0; i < PARALLEL_REQUESTS; i++) {
			User user = (i % 2 == 0) ? this.owner : this.member;
			futures.add(this.executor.submit(() -> {
				start.await();
				return timedUpload(user, documentId, pdf());
			}));
		}
		WaitSampler sampler = new WaitSampler();
		Thread samplerThread = Thread.ofPlatform().start(sampler);
		long begin = System.nanoTime();
		start.countDown();
		List<Timed> results = new ArrayList<>();
		for (Future<Timed> future : futures) {
			results.add(future.get(60, TimeUnit.SECONDS));
		}
		long totalMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin);
		sampler.stop();
		samplerThread.join();

		Set<Integer> numbers = new TreeSet<>();
		for (Timed result : results) {
			assertThat(result.response().getStatus()).isEqualTo(201);
			numbers.add(versionNumber(result));
		}
		assertThat(numbers).containsExactly(2, 3, 4, 5, 6, 7, 8, 9);
		assertThat(versionNumbers(documentId)).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
		List<String> keys = storageKeys(documentId);
		assertThat(keys).doesNotHaveDuplicates();
		// 신규 파일 8개 + v1, 임시 파일·보상 잔존 없음
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyInAnyOrderElementsOf(keys);
		long[] millis = results.stream().mapToLong(Timed::millis).sorted().toArray();
		System.out.printf(
				"[SPEC-04 AC-VER-04 lock-wait] parallel N=%d: total=%dms, per-request min=%dms median=%dms max=%dms, max concurrent lock waiters=%d%n",
				PARALLEL_REQUESTS, totalMillis, millis[0], millis[millis.length / 2], millis[millis.length - 1],
				sampler.maxWaiting);
	}

	// AC-VER-06: 사전 확인을 통과하고 파일을 배치한 요청이 잠금을 기다리는 동안 문서가 FINALIZED로 확정되면 409(SPEC-07 FR-5, AC-FIN-10), 롤백, 보상 삭제
	@Test
	void statusChangedWhileWaitingForLockIsRejectedAndFileIsCompensated() throws Exception {
		Long documentId = createDocument();
		List<String> before = DocumentTestSupport.storedFiles();
		Future<Timed> request;
		try (Connection blocker = this.dataSource.getConnection()) {
			blocker.setAutoCommit(false);
			try (PreparedStatement update = blocker.prepareStatement("""
					UPDATE documents SET status = 'FINALIZED',
					    final_version_id = (SELECT id FROM document_versions WHERE document_id = ? AND version_number = 1),
					    finalized_by = created_by, finalized_at = now()
					WHERE id = ?""")) {
				update.setLong(1, documentId);
				update.setLong(2, documentId);
				update.executeUpdate();
			}
			request = submit(this.member, documentId);
			awaitWaitingLocks(1);
			assertThat(DocumentTestSupport.storedFiles()).hasSize(before.size() + 1);
			blocker.commit();
		}
		MockHttpServletResponse response = request.get(30, TimeUnit.SECONDS).response();

		assertThat(response.getStatus()).isEqualTo(409);
		JsonNode body = this.jsonMapper.readTree(response.getContentAsString());
		assertThat(body.propertyNames()).containsExactlyInAnyOrder("code", "message");
		assertThat(body.get("code").asString()).isEqualTo("DOCUMENT_STATUS_CONFLICT");
		assertThat(versionNumbers(documentId)).containsExactly(1);
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyElementsOf(before);
	}

	// §7.4: 다른 문서의 새 버전 등록과 SPEC-03 문서 등록은 잠긴 문서에 막히지 않는다
	@Test
	void lockOnOneDocumentDoesNotBlockOtherDocumentsOrDocumentCreation() throws Exception {
		Long locked = createDocument();
		Long other = createDocument();
		try (Connection blocker = this.dataSource.getConnection()) {
			blocker.setAutoCommit(false);
			lockNoWait(blocker, "SELECT id FROM documents WHERE id = ? FOR NO KEY UPDATE", locked);

			Timed otherVersion = submit(this.member, other).get(10, TimeUnit.SECONDS);
			assertThat(otherVersion.response().getStatus()).isEqualTo(201);
			assertThat(versionNumber(otherVersion)).isEqualTo(2);
			assertThat(createDocument()).isNotNull();
			blocker.rollback();
		}
		assertThat(versionNumbers(locked)).containsExactly(1);
	}

	// AC-VER-10 (결정 I3): 문서 잠금을 거치지 않은 쓰기가 같은 번호를 먼저 커밋하면 실제 PostgreSQL UNIQUE 위반 → 500, 롤백, 보상 삭제, 원인 로그
	@Test
	void unexpectedVersionNumberUniqueViolationIsServerErrorWithCauseLog(CapturedOutput output) throws Exception {
		Long documentId = createDocument();
		List<String> before = DocumentTestSupport.storedFiles();
		Object updatedAtBefore = updatedAt(documentId);
		int logStart = output.getOut().length();
		MockMultipartFile file = new MockMultipartFile("file", "secret-original-name.pdf", "application/pdf",
				DocumentTestFiles.pdf());
		Future<Timed> request;
		try (Connection writer = this.dataSource.getConnection()) {
			writer.setAutoCommit(false);
			// 잠금을 거치지 않고 v2를 미커밋 상태로 둔다. FK 검사가 문서 행에 거는 FOR KEY SHARE는 요청의 FOR NO KEY UPDATE와 충돌하지 않는다.
			try (PreparedStatement insert = writer.prepareStatement(
					"INSERT INTO document_versions (document_id, version_number, original_filename, content_type, size_bytes, storage_key, sha256, uploaded_by) "
							+ "VALUES (?, 2, 'bypass.pdf', 'application/pdf', 1, 'documents/bypass-writer', ?, ?)")) {
				insert.setLong(1, documentId);
				insert.setString(2, "a".repeat(64));
				insert.setLong(3, this.owner.getId());
				insert.executeUpdate();
			}
			request = this.executor.submit(() -> timedUpload(this.member, documentId, file));
			// 요청은 잠금을 얻고 MAX=1(미커밋 행은 보이지 않음)로 v2를 INSERT하다가 같은 키의 미커밋 행 때문에 대기한다.
			awaitWaitingLocks(1);
			assertThat(DocumentTestSupport.storedFiles()).hasSize(before.size() + 1);
			writer.commit();
		}
		MockHttpServletResponse response = request.get(30, TimeUnit.SECONDS).response();

		assertThat(response.getStatus()).isEqualTo(500);
		assertThat(this.jsonMapper.readTree(response.getContentAsString()).get("code").asString())
			.isEqualTo("INTERNAL_ERROR");
		assertThat(response.getContentAsString()).doesNotContain("documents/")
			.doesNotContain("uk_document_versions")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString());
		// 롤백: 요청의 행은 없고 우회 쓰기의 v2만 남는다. updated_at 불변, 이 요청이 만든 파일만 삭제
		assertThat(versionNumbers(documentId)).containsExactly(1, 2);
		assertThat(this.jdbcTemplate.queryForObject("SELECT original_filename FROM document_versions WHERE document_id = ? AND version_number = 2",
				String.class, documentId)).isEqualTo("bypass.pdf");
		assertThat(updatedAt(documentId)).isEqualTo(updatedAtBefore);
		assertThat(DocumentTestSupport.storedFiles()).containsExactlyElementsOf(before);

		String log = output.getOut().substring(logStart);
		List<String> causeLines = log.lines().filter((line) -> line.contains("Unexpected version number conflict")).toList();
		assertThat(causeLines).hasSize(1);
		assertThat(causeLines.get(0)).contains("ERROR")
			.contains("projectId=" + this.projectId)
			.contains("documentId=" + documentId)
			.contains("versionNumber=2")
			.contains("constraint=uk_document_versions_document_version")
			.containsPattern("storageKey=documents/[0-9a-f-]{36}")
			.doesNotContain("secret-original-name")
			.doesNotContain(DocumentTestSupport.STORAGE_ROOT.toString());
		assertThat(log).doesNotContain("secret-original-name");
	}

	private Future<Timed> submit(User user, Long documentId) {
		return this.executor.submit(() -> timedUpload(user, documentId, pdf()));
	}

	private Timed timedUpload(User user, Long documentId, MockMultipartFile file) throws Exception {
		long begin = System.nanoTime();
		MockHttpServletResponse response = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents/" + documentId + "/versions").file(file)
				.with(as(user))
				.with(csrf()))
			.andReturn()
			.getResponse();
		return new Timed(response, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - begin));
	}

	private int versionNumber(Timed result) throws Exception {
		return this.jsonMapper.readTree(result.response().getContentAsString())
			.get("latestVersion")
			.get("versionNumber")
			.asInt();
	}

	private int versionCount(Long documentId) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM document_versions WHERE document_id = ?",
				Integer.class, documentId);
	}

	private List<Integer> versionNumbers(Long documentId) {
		return this.jdbcTemplate.queryForList(
				"SELECT version_number FROM document_versions WHERE document_id = ? ORDER BY version_number",
				Integer.class, documentId);
	}

	private List<String> storageKeys(Long documentId) {
		return this.jdbcTemplate.queryForList("SELECT storage_key FROM document_versions WHERE document_id = ?",
				String.class, documentId);
	}

	private Object updatedAt(Long documentId) {
		return this.jdbcTemplate.queryForObject("SELECT updated_at FROM documents WHERE id = ?", Object.class,
				documentId);
	}

	/** 요청 트랜잭션이 잠금을 기다리기 시작할 때까지 기다린다 ([기존] SPEC-02 기법). */
	private void awaitWaitingLocks(int expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
		while (System.nanoTime() < deadline) {
			if (waitingLocks() >= expected) {
				return;
			}
			Thread.sleep(20);
		}
		throw new AssertionError("요청이 잠금 대기 상태에 도달하지 않았습니다: expected=" + expected);
	}

	private int waitingLocks() {
		Integer waiting = this.jdbcTemplate.queryForObject("SELECT count(*) FROM pg_locks WHERE NOT granted",
				Integer.class);
		return (waiting != null) ? waiting : 0;
	}

	private record Timed(MockHttpServletResponse response, long millis) {
	}

	/** 병렬 부하 중 잠금 대기 중인 요청 수의 최댓값을 표본 조사한다 (측정 기록용, 판정 기준 아님). */
	private final class WaitSampler implements Runnable {

		private volatile boolean running = true;

		private volatile int maxWaiting;

		@Override
		public void run() {
			while (this.running) {
				this.maxWaiting = Math.max(this.maxWaiting, waitingLocks());
				try {
					Thread.sleep(5);
				}
				catch (InterruptedException ex) {
					return;
				}
			}
		}

		void stop() {
			this.running = false;
		}

	}

	private Long createDocument() throws Exception {
		String location = this.mockMvc
			.perform(multipart("/api/projects/" + this.projectId + "/documents").file(pdf())
				.param("title", "문서")
				.with(as(this.owner))
				.with(csrf()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private static void lockNoWait(Connection connection, String sql, Long id) throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setLong(1, id);
			statement.executeQuery().close();
		}
	}

	private static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "v.pdf", "application/pdf", DocumentTestFiles.pdf());
	}

}
