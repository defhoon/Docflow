package com.docflow.document;

import static com.docflow.document.DocumentTestSupport.as;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.docflow.user.domain.User;

/** SPEC-06~08 통합 테스트 공통: 프로젝트·구성원 준비와 실제 API를 통한 문서·버전 등록. */
public final class DocumentApiHelper {

	private final MockMvc mockMvc;

	private final JdbcTemplate jdbcTemplate;

	public DocumentApiHelper(MockMvc mockMvc, JdbcTemplate jdbcTemplate) {
		this.mockMvc = mockMvc;
		this.jdbcTemplate = jdbcTemplate;
	}

	public Long createProject(User owner, String name) {
		return this.jdbcTemplate.queryForObject("INSERT INTO projects (name, owner_id) VALUES (?, ?) RETURNING id",
				Long.class, name, owner.getId());
	}

	public void addMember(Long projectId, User member) {
		this.jdbcTemplate.update("INSERT INTO project_members (project_id, user_id) VALUES (?, ?)", projectId,
				member.getId());
	}

	public Long createDocument(User user, Long projectId) throws Exception {
		String location = createDocumentRequest(user, projectId).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getHeader("Location");
		return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
	}

	public ResultActions createDocumentRequest(User user, Long projectId) throws Exception {
		return this.mockMvc.perform(multipart("/api/projects/" + projectId + "/documents").file(pdf())
			.param("title", "문서")
			.with(as(user))
			.with(csrf()));
	}

	public ResultActions uploadVersion(User user, Long projectId, Long documentId) throws Exception {
		return this.mockMvc.perform(multipart(documentUrl(projectId, documentId) + "/versions").file(pdf())
			.with(as(user))
			.with(csrf()));
	}

	public static String documentUrl(Long projectId, Long documentId) {
		return "/api/projects/" + projectId + "/documents/" + documentId;
	}

	public static MockMultipartFile pdf() {
		return new MockMultipartFile("file", "file.pdf", "application/octet-stream", DocumentTestFiles.pdf());
	}

}
