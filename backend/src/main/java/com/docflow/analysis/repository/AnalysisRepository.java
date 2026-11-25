package com.docflow.analysis.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisStatus;
import com.docflow.analysis.domain.AnalysisType;

/**
 * 분석 작업 저장소 (SPEC-11 §5). 대기열 연산(가져가기·되돌리기)은 PostgreSQL 기능({@code FOR UPDATE SKIP LOCKED},
 * {@code ON CONFLICT})이 필요해 SQL로 직접 둔다. 모든 메서드는 호출 측 트랜잭션(없으면 자동 커밋)에서 실행된다.
 */
@Repository
public class AnalysisRepository {

	private static final String COLUMNS = """
			id, project_id, document_id, version_id, base_version_id, analysis_type, status, attempts, model,
			result::text AS result, error_code, created_at, updated_at, completed_at""";

	private static final RowMapper<AnalysisRecord> MAPPER = AnalysisRepository::map;

	private final JdbcTemplate jdbc;

	public AnalysisRepository(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public Optional<AnalysisRecord> find(Long versionId, AnalysisType type, Long baseVersionId) {
		List<AnalysisRecord> rows = this.jdbc.query("SELECT " + COLUMNS + """
				 FROM document_analyses
				WHERE version_id = ? AND analysis_type = ? AND COALESCE(base_version_id, 0) = COALESCE(?, 0)""", MAPPER,
				versionId, type.name(), baseVersionId);
		return rows.stream().findFirst();
	}

	public Optional<AnalysisRecord> findById(Long id) {
		return this.jdbc.query("SELECT " + COLUMNS + " FROM document_analyses WHERE id = ?", MAPPER, id)
			.stream()
			.findFirst();
	}

	/** 대기 작업을 만든다. 같은 분석이 이미 있으면 아무것도 하지 않는다(중복 생성 방지). 만들었으면 true. */
	public boolean insertPending(Long projectId, Long documentId, Long versionId, Long baseVersionId,
			AnalysisType type, Long requestedBy) {
		return this.jdbc.update("""
				INSERT INTO document_analyses
				    (project_id, document_id, version_id, base_version_id, analysis_type, status, requested_by)
				VALUES (?, ?, ?, ?, ?, 'PENDING', ?)
				ON CONFLICT (version_id, analysis_type, COALESCE(base_version_id, 0)) DO NOTHING""", projectId,
				documentId, versionId, baseVersionId, type.name(), requestedBy) == 1;
	}

	/** 실패한 작업을 다시 대기로 돌린다(시도 횟수 초기화). FAILED였을 때만 바뀐다. */
	public boolean resetFailed(Long id, Long requestedBy) {
		return this.jdbc.update("""
				UPDATE document_analyses
				   SET status = 'PENDING', attempts = 0, next_attempt_at = now(), error_code = NULL,
				       requested_by = ?, updated_at = now()
				 WHERE id = ? AND status = 'FAILED'""", requestedBy, id) == 1;
	}

	/**
	 * AI가 켜진 프로젝트에서 켠 뒤 올라온 PDF 버전 중 요약 작업이 없는 버전에 대기 작업을 만든다. 업로드 트랜잭션과 분리하기
	 * 위해 작업자가 주기적으로 실행한다.
	 */
	public int enqueueMissingSummaries(int limit) {
		return this.jdbc.update("""
				INSERT INTO document_analyses (project_id, document_id, version_id, analysis_type, status)
				SELECT d.project_id, d.id, v.id, 'SUMMARY', 'PENDING'
				  FROM document_versions v
				  JOIN documents d ON d.id = v.document_id
				  JOIN projects p ON p.id = d.project_id
				 WHERE p.ai_enabled AND v.created_at >= p.ai_enabled_at AND v.content_type = 'application/pdf'
				   AND NOT EXISTS (SELECT 1 FROM document_analyses a
				                    WHERE a.version_id = v.id AND a.analysis_type = 'SUMMARY')
				 ORDER BY v.id
				 LIMIT ?
				ON CONFLICT (version_id, analysis_type, COALESCE(base_version_id, 0)) DO NOTHING""", limit);
	}

	/** 오래 멈춘 PROCESSING 작업(서버 재시작·중단)을 다시 대기로 돌린다. */
	public int recoverStale(Instant startedBefore) {
		return this.jdbc.update("""
				UPDATE document_analyses SET status = 'PENDING', next_attempt_at = now(), updated_at = now()
				 WHERE status = 'PROCESSING' AND started_at < ?""", Timestamp.from(startedBefore));
	}

	/** 실행할 작업 하나를 가져가 PROCESSING으로 바꾼다. 다른 작업자가 잡은 행은 건너뛴다. */
	public Optional<AnalysisRecord> claimNext() {
		List<AnalysisRecord> rows = this.jdbc.query("""
				UPDATE document_analyses SET status = 'PROCESSING', attempts = attempts + 1, started_at = now(),
				       updated_at = now()
				 WHERE id = (SELECT id FROM document_analyses
				              WHERE status = 'PENDING' AND next_attempt_at <= now()
				              ORDER BY next_attempt_at, id
				              FOR UPDATE SKIP LOCKED LIMIT 1)
				RETURNING """ + " " + COLUMNS, MAPPER);
		return rows.stream().findFirst();
	}

	public void complete(Long id, String model, String resultJson, Usage usage) {
		this.jdbc.update("""
				UPDATE document_analyses
				   SET status = 'COMPLETED', result = ?::jsonb, model = ?, error_code = NULL, completed_at = now(),
				       updated_at = now(), api_calls = api_calls + ?, input_tokens = input_tokens + ?,
				       output_tokens = output_tokens + ?, cost_micros = cost_micros + ?
				 WHERE id = ? AND status = 'PROCESSING'""", resultJson, model, usage.calls(), usage.inputTokens(),
				usage.outputTokens(), usage.costMicros(), id);
	}

	public void fail(Long id, String errorCode, Usage usage) {
		this.jdbc.update("""
				UPDATE document_analyses
				   SET status = 'FAILED', error_code = ?, updated_at = now(), api_calls = api_calls + ?,
				       input_tokens = input_tokens + ?, output_tokens = output_tokens + ?, cost_micros = cost_micros + ?
				 WHERE id = ? AND status = 'PROCESSING'""", errorCode, usage.calls(), usage.inputTokens(),
				usage.outputTokens(), usage.costMicros(), id);
	}

	/** 일시적 오류: 다시 대기로 두고 다음 시도 시각을 정한다. */
	public void retryLater(Long id, Instant nextAttemptAt, String errorCode, Usage usage) {
		this.jdbc.update("""
				UPDATE document_analyses
				   SET status = 'PENDING', next_attempt_at = ?, error_code = ?, updated_at = now(),
				       api_calls = api_calls + ?, input_tokens = input_tokens + ?, output_tokens = output_tokens + ?,
				       cost_micros = cost_micros + ?
				 WHERE id = ? AND status = 'PROCESSING'""", Timestamp.from(nextAttemptAt), errorCode, usage.calls(),
				usage.inputTokens(), usage.outputTokens(), usage.costMicros(), id);
	}

	/** {@code since} 이후 프로젝트의 API 호출 수. */
	public long projectCallsSince(Long projectId, Instant since) {
		return this.jdbc.queryForObject(
				"SELECT COALESCE(SUM(api_calls), 0) FROM document_analyses WHERE project_id = ? AND updated_at >= ?",
				Long.class, projectId, Timestamp.from(since));
	}

	public long globalCallsSince(Instant since) {
		return this.jdbc.queryForObject(
				"SELECT COALESCE(SUM(api_calls), 0) FROM document_analyses WHERE updated_at >= ?", Long.class,
				Timestamp.from(since));
	}

	public long globalCostMicrosSince(Instant since) {
		return this.jdbc.queryForObject(
				"SELECT COALESCE(SUM(cost_micros), 0) FROM document_analyses WHERE updated_at >= ?", Long.class,
				Timestamp.from(since));
	}

	/** 한 작업에서 쓴 API 호출·토큰·추정 비용. */
	public record Usage(int calls, long inputTokens, long outputTokens, long costMicros) {

		public static final Usage NONE = new Usage(0, 0, 0, 0);

		public Usage plus(Usage other) {
			return new Usage(this.calls + other.calls, this.inputTokens + other.inputTokens,
					this.outputTokens + other.outputTokens, this.costMicros + other.costMicros);
		}

	}

	private static AnalysisRecord map(ResultSet rs, int row) throws SQLException {
		long baseValue = rs.getLong("base_version_id");
		Long base = rs.wasNull() ? null : baseValue;
		return new AnalysisRecord(rs.getLong("id"), rs.getLong("project_id"), rs.getLong("document_id"),
				rs.getLong("version_id"), base, AnalysisType.valueOf(rs.getString("analysis_type")),
				AnalysisStatus.valueOf(rs.getString("status")), rs.getInt("attempts"), rs.getString("model"),
				rs.getString("result"), rs.getString("error_code"), instant(rs, "created_at"), instant(rs, "updated_at"),
				instant(rs, "completed_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		Timestamp value = rs.getTimestamp(column);
		return (value != null) ? value.toInstant() : null;
	}

}
