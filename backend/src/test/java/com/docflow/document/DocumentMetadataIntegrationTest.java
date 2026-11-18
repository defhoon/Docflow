package com.docflow.document;

import static com.docflow.document.DocumentApiHelper.documentUrl;
import static com.docflow.document.DocumentApiHelper.pdf;
import static com.docflow.document.DocumentTestSupport.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.user.domain.User;
import com.docflow.user.service.UserAccountService;

/**
 * SPEC-09 문서 설명·버전 정보 통합 테스트 (실제 PostgreSQL, 보안 설정 활성). AC-META-01~12.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext
class DocumentMetadataIntegrationTest {

	private static final String PASSWORD = "Correct-Horse-Battery-9";

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

	private DocumentApiHelper api;

	private User owner;

	private User member;

	private User outsider;

	private Long projectId;

	@BeforeEach
	void setUp() {
		cleanUp();
		this.api = new DocumentApiHelper(this.mockMvc, this.jdbcTemplate);
		this.owner = this.userAccountService.createUser("owner@test.local", PASSWORD, "Owner");
		this.member = this.userAccountService.createUser("member@test.local", PASSWORD, "Member");
		this.outsider = this.userAccountService.createUser("outsider@test.local", PASSWORD, "Outsider");
		this.projectId = this.api.createProject(this.owner, "P1");
		this.api.addMember(this.projectId, this.member);
	}

	@AfterEach
	void cleanUp() {
		DocumentTestSupport.cleanDatabase(this.jdbcTemplate);
		DocumentTestSupport.cleanStorage();
	}

	// AC-META-01: 등록 시 설명·v1 초기 버전 내용 저장, 상세·목록·버전 목록에서 조회(줄바꿈 유지, 앞뒤 공백 제거)
	@Test
	void createStoresDescriptionAndInitialVersionNote() throws Exception {
		Long documentId = createDocument(this.member, "  목적: 요구사항 정리\n범위: MVP  ", "  초안 작성\n- 1장 구성 ")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").value("목적: 요구사항 정리\n범위: MVP"))
			.andExpect(jsonPath("$.latestVersion.versionNote").value("초안 작성\n- 1장 구성"))
			.andReturn()
			.getResponse()
			.getHeader("Location")
			.transform(this::idFromLocation);

		this.mockMvc.perform(get(documentUrl(this.projectId, documentId)).with(as(this.owner)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("목적: 요구사항 정리\n범위: MVP"))
			.andExpect(jsonPath("$.latestVersion.versionNote").value("초안 작성\n- 1장 구성"));
		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/documents").with(as(this.owner)))
			.andExpect(jsonPath("$[0].description").value("목적: 요구사항 정리\n범위: MVP"));
		versions(this.owner, documentId).andExpect(jsonPath("$[0].versionNumber").value(1))
			.andExpect(jsonPath("$[0].versionNote").value("초안 작성\n- 1장 구성"));
		assertThat(versionRow(documentId, 1).get("version_note")).isEqualTo("초안 작성\n- 1장 구성");
	}

	// AC-META-02: 선택 입력 미입력·빈 값·공백뿐인 값은 null로 저장되고 등록은 정상
	@Test
	void optionalFieldsMayBeOmittedOrBlank() throws Exception {
		Long omitted = this.api.createDocument(this.member, this.projectId);
		Long blank = idOf(createDocument(this.member, "   ", " \n\t ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.latestVersion.versionNote").doesNotExist()));
		this.api.uploadVersion(this.member, this.projectId, omitted).andExpect(status().isCreated());
		uploadVersion(this.member, blank, "  ").andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNote").doesNotExist());

		for (Long documentId : List.of(omitted, blank)) {
			assertThat(documentRow(documentId).get("description")).isNull();
			assertThat(versionRow(documentId, 1).get("version_note")).isNull();
			assertThat(versionRow(documentId, 2).get("version_note")).isNull();
			versions(this.owner, documentId).andExpect(status().isOk())
				.andExpect(jsonPath("$[0].versionNote").doesNotExist())
				.andExpect(jsonPath("$[1].versionNote").doesNotExist());
		}
	}

	// AC-META-03, 04: v2 이상 변경사항 저장, 버전별 정보는 서로 독립
	@Test
	void versionNotesAreStoredPerVersion() throws Exception {
		Long documentId = idOf(createDocument(this.owner, null, "v1 내용"));
		uploadVersion(this.member, documentId, "3장 추가").andExpect(status().isCreated())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2))
			.andExpect(jsonPath("$.latestVersion.versionNote").value("3장 추가"));
		uploadVersion(this.owner, documentId, null).andExpect(status().isCreated());
		uploadVersion(this.member, documentId, "오탈자 수정").andExpect(status().isCreated());

		versions(this.member, documentId).andExpect(jsonPath("$[0].versionNumber").value(4))
			.andExpect(jsonPath("$[0].versionNote").value("오탈자 수정"))
			.andExpect(jsonPath("$[1].versionNote").doesNotExist())
			.andExpect(jsonPath("$[2].versionNote").value("3장 추가"))
			.andExpect(jsonPath("$[3].versionNote").value("v1 내용"));
	}

	// AC-META-05: 길이 경계(설명 2,000자 허용·2,001자 거부, 버전 정보 1,000자 허용·1,001자 거부), 거부 시 아무것도 저장되지 않음
	@Test
	void lengthLimitsAreEnforcedWithoutSideEffects() throws Exception {
		Long documentId = idOf(createDocument(this.member, "가".repeat(2000), "나".repeat(1000)).andExpect(status().isCreated()));
		uploadVersion(this.member, documentId, "다".repeat(1000)).andExpect(status().isCreated());
		long documents = count("documents");
		long versions = count("document_versions");
		long activities = count("audit_logs");
		int files = DocumentTestSupport.storedFiles().size();

		createDocument(this.member, "가".repeat(2001), null).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("description"));
		createDocument(this.member, null, "나".repeat(1001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.fieldErrors[0].field").value("versionNote"));
		uploadVersion(this.member, documentId, "다".repeat(1001)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.fieldErrors[0].field").value("versionNote"));
		patchDescription(this.member, documentId, json("가".repeat(2001))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

		assertThat(count("documents")).isEqualTo(documents);
		assertThat(count("document_versions")).isEqualTo(versions);
		assertThat(count("audit_logs")).isEqualTo(activities);
		assertThat(DocumentTestSupport.storedFiles().size()).isEqualTo(files);
		assertThat(documentRow(documentId).get("description")).isEqualTo("가".repeat(2000));
	}

	// AC-META-06: 허용하지 않는 제어 문자(탭·줄바꿈 외)는 400
	@Test
	void controlCharactersAreRejected() throws Exception {
		Long documentId = this.api.createDocument(this.member, this.projectId);
		createDocument(this.member, "a\u0007b", null).andExpect(status().isBadRequest());
		uploadVersion(this.member, documentId, "a\u0000b").andExpect(status().isBadRequest());
		patchDescription(this.member, documentId, "{\"description\":\"a\\u0007b\"}").andExpect(status().isBadRequest());
		assertThat(count("document_versions")).isEqualTo(1);
	}

	// AC-META-07: 구성원·오너 모두 설명 수정, 활동 이력 기록, 같은 값이면 이력 없음, 빈 값·null이면 설명 삭제
	@Test
	void membersUpdateDescriptionWithAudit() throws Exception {
		Long documentId = idOf(createDocument(this.owner, "처음 설명", null));

		patchDescription(this.member, documentId, json("  구성원이 고친 설명\n둘째 줄 ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(documentId))
			.andExpect(jsonPath("$.description").value("구성원이 고친 설명\n둘째 줄"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(1));
		Map<String, Object> activity = this.jdbcTemplate
			.queryForMap("SELECT * FROM audit_logs WHERE action = 'DOCUMENT_DESCRIPTION_UPDATED'");
		assertThat(activity).containsEntry("target_type", "DOCUMENT")
			.containsEntry("target_id", documentId)
			.containsEntry("document_id", documentId)
			.containsEntry("actor_id", this.member.getId());
		assertThat(activity.get("version_number")).isNull();

		patchDescription(this.member, documentId, json("구성원이 고친 설명\n둘째 줄")).andExpect(status().isOk());
		assertThat(descriptionUpdates()).isEqualTo(1);

		patchDescription(this.owner, documentId, json("오너 설명")).andExpect(status().isOk())
			.andExpect(jsonPath("$.description").value("오너 설명"));
		patchDescription(this.owner, documentId, json("   ")).andExpect(status().isOk())
			.andExpect(jsonPath("$.description").doesNotExist());
		assertThat(documentRow(documentId).get("description")).isNull();
		patchDescription(this.member, documentId, "{\"description\":null}").andExpect(status().isOk());
		assertThat(descriptionUpdates()).isEqualTo(3);
		// description 키가 없으면 실수로 지우지 않도록 400 (키는 필수, 값 null은 허용)
		patchDescription(this.owner, documentId, json("되살린 설명")).andExpect(status().isOk());
		patchDescription(this.owner, documentId, "{}").andExpect(status().isBadRequest());
		patchDescription(this.owner, documentId, "{\"status\":\"FINALIZED\"}").andExpect(status().isBadRequest());
		assertThat(documentRow(documentId).get("description")).isEqualTo("되살린 설명");
		assertThat(documentRow(documentId).get("status")).isEqualTo("ACTIVE");

		this.mockMvc.perform(get("/api/projects/" + this.projectId + "/documents/" + documentId + "/activities")
			.with(as(this.member))).andExpect(status().isOk())
			.andExpect(jsonPath("$[0].action").value("DOCUMENT_DESCRIPTION_UPDATED"))
			.andExpect(jsonPath("$[0].versionNumber").doesNotExist());
	}

	// AC-META-08: 비구성원 404, 비로그인 401, CSRF 누락 403, 다른 프로젝트 문서 404, JSON 아님 415 — 모두 변화 없음
	@Test
	void descriptionUpdateRequiresMembership() throws Exception {
		Long documentId = idOf(createDocument(this.owner, "원래 설명", null));
		Long foreignProject = this.api.createProject(this.outsider, "P2");
		Long foreignDocument = this.api.createDocument(this.outsider, foreignProject);

		patchDescription(this.outsider, documentId, json("침입")).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PROJECT_NOT_FOUND"));
		this.mockMvc.perform(patch(documentUrl(this.projectId, documentId)).contentType(MediaType.APPLICATION_JSON)
			.content(json("익명")).with(csrf())).andExpect(status().isUnauthorized());
		this.mockMvc.perform(patch(documentUrl(this.projectId, documentId)).contentType(MediaType.APPLICATION_JSON)
			.content(json("CSRF 없음")).with(as(this.member))).andExpect(status().isForbidden());
		patchDescription(this.member, foreignDocument, json("다른 프로젝트")).andExpect(status().isNotFound());
		this.mockMvc.perform(patch(documentUrl(this.projectId, foreignDocument)).contentType(MediaType.APPLICATION_JSON)
			.content(json("경로 조작")).with(as(this.member)).with(csrf()))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("DOCUMENT_NOT_FOUND"));
		this.mockMvc.perform(patch(documentUrl(this.projectId, documentId)).contentType(MediaType.TEXT_PLAIN)
			.content("x").with(as(this.member)).with(csrf())).andExpect(status().is4xxClientError());

		assertThat(documentRow(documentId).get("description")).isEqualTo("원래 설명");
		assertThat(documentRow(foreignDocument).get("description")).isNull();
		assertThat(descriptionUpdates()).isZero();
	}

	// AC-META-09: FINALIZED 이후 설명 수정 409(오너·구성원), 기존 설명·버전 정보·다운로드는 계속 조회
	@Test
	void finalizedDocumentIsReadOnlyButReadable() throws Exception {
		Long documentId = idOf(createDocument(this.owner, "확정 전 설명", "초기 내용"));
		uploadVersion(this.member, documentId, "변경사항 v2").andExpect(status().isCreated());
		this.mockMvc.perform(post(documentUrl(this.projectId, documentId) + "/finalize")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"versionNumber\":1}")
			.with(as(this.owner))
			.with(csrf())).andExpect(status().isOk());

		for (User user : List.of(this.owner, this.member)) {
			patchDescription(user, documentId, json("확정 후 수정")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("DOCUMENT_ALREADY_FINALIZED"));
		}
		uploadVersion(this.member, documentId, "확정 후 버전").andExpect(status().isConflict());

		this.mockMvc.perform(get(documentUrl(this.projectId, documentId)).with(as(this.member)))
			.andExpect(jsonPath("$.status").value("FINALIZED"))
			.andExpect(jsonPath("$.description").value("확정 전 설명"))
			.andExpect(jsonPath("$.latestVersion.versionNote").value("변경사항 v2"));
		versions(this.member, documentId).andExpect(jsonPath("$[0].versionNote").value("변경사항 v2"))
			.andExpect(jsonPath("$[1].versionNote").value("초기 내용"));
		this.mockMvc.perform(get(documentUrl(this.projectId, documentId) + "/versions/1/download").with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(content().bytes(DocumentTestFiles.pdf()));
		assertThat(documentRow(documentId).get("description")).isEqualTo("확정 전 설명");
		assertThat(descriptionUpdates()).isZero();
	}

	// AC-META-10: 업로드 실패(형식 오류) 시 버전 정보를 포함한 어떤 행도 저장되지 않음
	@Test
	void failedUploadStoresNothing() throws Exception {
		Long documentId = idOf(createDocument(this.member, null, "v1"));
		MockMultipartFile broken = new MockMultipartFile("file", "a.pdf", "application/pdf", "%PDF-1.4 only".getBytes());
		this.mockMvc.perform(multipart(documentUrl(this.projectId, documentId) + "/versions").file(broken)
			.param("versionNote", "실패할 변경사항")
			.with(as(this.member))
			.with(csrf())).andExpect(status().isBadRequest());
		this.mockMvc.perform(multipart("/api/projects/" + this.projectId + "/documents").file(broken)
			.param("title", "실패 문서")
			.param("description", "실패할 설명")
			.param("versionNote", "실패할 초기 내용")
			.with(as(this.member))
			.with(csrf())).andExpect(status().isBadRequest());

		assertThat(count("documents")).isEqualTo(1);
		assertThat(count("document_versions")).isEqualTo(1);
		assertThat(DocumentTestSupport.storedFiles().size()).isEqualTo(1);
	}

	// AC-META-11: 하위 호환 — 기존 요청(새 필드 없음)·SPEC-09 이전 행(NULL)도 정상 조회, 다운로드 동일
	@Test
	void legacyRowsAndRequestsStillWork() throws Exception {
		Long documentId = this.api.createDocument(this.member, this.projectId);
		this.api.uploadVersion(this.owner, this.projectId, documentId).andExpect(status().isCreated());
		// 마이그레이션 이전 상태(설명·버전 정보 없음)를 그대로 재현한다.
		this.jdbcTemplate.update("UPDATE documents SET description = NULL WHERE id = ?", documentId);
		this.jdbcTemplate.update("UPDATE document_versions SET version_note = NULL WHERE document_id = ?", documentId);

		this.mockMvc.perform(get(documentUrl(this.projectId, documentId)).with(as(this.member)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.description").doesNotExist())
			.andExpect(jsonPath("$.latestVersion.versionNumber").value(2));
		versions(this.member, documentId).andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(2));
		for (int version = 1; version <= 2; version++) {
			this.mockMvc.perform(get(documentUrl(this.projectId, documentId) + "/versions/" + version + "/download")
				.with(as(this.owner))).andExpect(status().isOk()).andExpect(content().bytes(DocumentTestFiles.pdf()));
		}
	}

	// AC-META-12: 스키마 — 설명 VARCHAR(2000), 버전 정보 VARCHAR(1000) NULL 허용
	@Test
	void schemaMatchesSpec() {
		assertThat(columnLength("documents", "description")).isEqualTo(2000);
		assertThat(columnLength("document_versions", "version_note")).isEqualTo(1000);
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT is_nullable FROM information_schema.columns
				WHERE table_name = 'document_versions' AND column_name = 'version_note'""", String.class))
			.isEqualTo("YES");
	}

	private ResultActions createDocument(User user, String description, String versionNote) throws Exception {
		var request = multipart("/api/projects/" + this.projectId + "/documents").file(pdf()).param("title", "문서");
		if (description != null) {
			request.param("description", description);
		}
		if (versionNote != null) {
			request.param("versionNote", versionNote);
		}
		return this.mockMvc.perform(request.with(as(user)).with(csrf()));
	}

	private ResultActions uploadVersion(User user, Long documentId, String versionNote) throws Exception {
		var request = multipart(documentUrl(this.projectId, documentId) + "/versions").file(pdf());
		if (versionNote != null) {
			request.param("versionNote", versionNote);
		}
		return this.mockMvc.perform(request.with(as(user)).with(csrf()));
	}

	private ResultActions patchDescription(User user, Long documentId, String body) throws Exception {
		return this.mockMvc.perform(patch(documentUrl(this.projectId, documentId)).contentType(MediaType.APPLICATION_JSON)
			.content(body)
			.with(as(user))
			.with(csrf()));
	}

	private ResultActions versions(User user, Long documentId) throws Exception {
		return this.mockMvc.perform(get(documentUrl(this.projectId, documentId) + "/versions").with(as(user)));
	}

	private static String json(String description) {
		return "{\"description\":\"" + description.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
			.replace("\t", "\\t") + "\"}";
	}

	private Long idOf(ResultActions created) throws Exception {
		return idFromLocation(created.andReturn().getResponse().getHeader("Location"));
	}

	private Long idFromLocation(String location) {
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	private Map<String, Object> documentRow(Long documentId) {
		return this.jdbcTemplate.queryForMap("SELECT * FROM documents WHERE id = ?", documentId);
	}

	private Map<String, Object> versionRow(Long documentId, int versionNumber) {
		return this.jdbcTemplate.queryForMap(
				"SELECT * FROM document_versions WHERE document_id = ? AND version_number = ?", documentId,
				versionNumber);
	}

	private long count(String table) {
		return this.jdbcTemplate.queryForObject("SELECT count(*) FROM " + table, Long.class);
	}

	private long descriptionUpdates() {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM audit_logs WHERE action = 'DOCUMENT_DESCRIPTION_UPDATED'", Long.class);
	}

	private Integer columnLength(String table, String column) {
		return this.jdbcTemplate.queryForObject("""
				SELECT character_maximum_length FROM information_schema.columns
				WHERE table_name = ? AND column_name = ?""", Integer.class, table, column);
	}

}
