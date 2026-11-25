package com.docflow.analysis.repository;

import java.sql.Timestamp;
import java.time.Instant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 프로젝트 AI 설정 열({@code projects.ai_enabled*}, V12). 프로젝트 엔티티·응답 계약을 바꾸지 않기 위해 따로 읽고 쓴다.
 */
@Repository
public class ProjectAiSettingsRepository {

	private final JdbcTemplate jdbc;

	public ProjectAiSettingsRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public record Settings(boolean enabled, Instant enabledAt, Long enabledBy, String enabledByName) {
	}

	public Settings find(Long projectId) {
		return this.jdbc.queryForObject("""
				SELECT p.ai_enabled, p.ai_enabled_at, p.ai_enabled_by, u.display_name
				  FROM projects p LEFT JOIN users u ON u.id = p.ai_enabled_by
				 WHERE p.id = ?""", (rs, row) -> {
			Timestamp at = rs.getTimestamp(2);
			long by = rs.getLong(3);
			Long enabledBy = rs.wasNull() ? null : by;
			return new Settings(rs.getBoolean(1), (at != null) ? at.toInstant() : null, enabledBy, rs.getString(4));
		}, projectId);
	}

	/** 켤 때 시각·사용자(외부 전송 고지 동의 기록)를 남긴다. 끌 때는 마지막 동의 기록을 그대로 둔다. */
	public void update(Long projectId, boolean enabled, Long userId) {
		if (enabled) {
			this.jdbc.update("UPDATE projects SET ai_enabled = TRUE, ai_enabled_at = now(), ai_enabled_by = ? WHERE id = ?",
					userId, projectId);
		}
		else {
			this.jdbc.update("UPDATE projects SET ai_enabled = FALSE WHERE id = ?", projectId);
		}
	}

}
