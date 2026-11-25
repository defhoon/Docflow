package com.docflow.analysis.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.springframework.stereotype.Component;

import com.docflow.analysis.repository.AnalysisRepository;

/**
 * 호출·비용 상한 (SPEC-11 §7). 하루·월 경계는 UTC 기준이다. 비용은 설정 단가로 계산한 추정치다.
 */
@Component
public class AnalysisBudget {

	private final AnalysisRepository repository;

	private final AiProperties properties;

	private final Clock clock;

	public AnalysisBudget(AnalysisRepository repository, AiProperties properties) {
		this.repository = repository;
		this.properties = properties;
		this.clock = Clock.systemUTC();
	}

	/**
	 * 앞으로 {@code calls}번 호출하고 최대 {@code estimatedCostMicros}만큼 쓸 수 있는지 확인한다.
	 */
	public boolean allows(Long projectId, int calls, long estimatedCostMicros) {
		LocalDate today = LocalDate.now(this.clock);
		Instant dayStart = today.atStartOfDay(ZoneOffset.UTC).toInstant();
		Instant monthStart = today.withDayOfMonth(1).atStartOfDay(ZoneOffset.UTC).toInstant();
		if (this.repository.projectCallsSince(projectId, dayStart) + calls > this.properties.projectDailyCalls()) {
			return false;
		}
		if (this.repository.globalCallsSince(dayStart) + calls > this.properties.globalDailyCalls()) {
			return false;
		}
		return this.repository.globalCostMicrosSince(monthStart) + estimatedCostMicros <= this.properties
			.monthlyBudgetMicros();
	}

}
