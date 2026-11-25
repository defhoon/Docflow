package com.docflow.analysis.service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.docflow.analysis.domain.AnalysisFailure;
import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisType;
import com.docflow.analysis.repository.AnalysisRepository;
import com.docflow.analysis.repository.ProjectAiSettingsRepository;

/**
 * 분석 작업자 (SPEC-11 §5). Spring 스케줄러가 주기적으로 실행하며 PostgreSQL을 대기열로 쓴다(새 인프라 없음).
 * <ol>
 * <li>AI가 켜진 프로젝트의 새 PDF 버전에 대기 작업 생성 (업로드 트랜잭션과 완전히 분리)</li>
 * <li>중단된 PROCESSING 작업 복구 (서버 재시작 포함)</li>
 * <li>대기 작업을 {@code FOR UPDATE SKIP LOCKED}로 하나씩 가져가 처리</li>
 * </ol>
 * 처리 중에는 DB 트랜잭션을 잡지 않는다. 작업마다 예외를 격리해 한 작업의 실패가 다른 작업·업로드에 영향을 주지 않는다. 문서
 * 내용은 로그에 남기지 않는다.
 */
@Component
public class AnalysisWorker {

	/** 한 번 실행에서 처리하는 최대 작업 수. */
	static final int BATCH = 5;

	private static final Logger log = LoggerFactory.getLogger(AnalysisWorker.class);

	private final AnalysisRepository repository;

	private final ProjectAiSettingsRepository settingsRepository;

	private final AiProperties properties;

	private final Map<AnalysisType, AnalysisProcessor> processors = new EnumMap<>(AnalysisType.class);

	public AnalysisWorker(AnalysisRepository repository, ProjectAiSettingsRepository settingsRepository,
			AiProperties properties, List<AnalysisProcessor> processors) {
		this.repository = repository;
		this.settingsRepository = settingsRepository;
		this.properties = properties;
		for (AnalysisProcessor processor : processors) {
			this.processors.put(processor.type(), processor);
		}
	}

	/** 단일 인스턴스 전제(ADR-0007): 시작 시점의 PROCESSING은 모두 이전 실행이 남긴 것이다. */
	@EventListener(ApplicationReadyEvent.class)
	public void recoverOnStartup() {
		int recovered = this.repository.recoverStale(Instant.now());
		if (recovered > 0) {
			log.info("Recovered interrupted analyses on startup: count={}", recovered);
		}
	}

	@Scheduled(fixedDelayString = "${docflow.ai.worker.poll-interval-ms:5000}",
			initialDelayString = "${docflow.ai.worker.initial-delay-ms:10000}")
	public void scheduledRun() {
		if (!this.properties.enabled()) {
			return;
		}
		try {
			runOnce();
		}
		catch (RuntimeException ex) {
			log.error("Analysis worker run failed", ex);
		}
	}

	/** 한 번 실행. 처리한 작업 수를 돌려준다(테스트에서 직접 호출). */
	public int runOnce() {
		this.repository.enqueueMissingSummaries(50);
		this.repository.recoverStale(Instant.now().minus(this.properties.staleAfter()));
		int processed = 0;
		for (int i = 0; i < BATCH; i++) {
			Optional<AnalysisRecord> next = this.repository.claimNext();
			if (next.isEmpty()) {
				break;
			}
			handle(next.get());
			processed++;
		}
		return processed;
	}

	private void handle(AnalysisRecord job) {
		long started = System.nanoTime();
		ProcessingOutcome outcome;
		try {
			outcome = execute(job);
		}
		catch (RuntimeException ex) {
			log.error("Analysis failed unexpectedly: analysisId={}, type={}", job.id(), job.type(), ex);
			outcome = new ProcessingOutcome.Failed(AnalysisFailure.AI_API_ERROR, true, AnalysisRepository.Usage.NONE);
		}
		switch (outcome) {
			case ProcessingOutcome.Completed completed ->
				this.repository.complete(job.id(), completed.model(), completed.resultJson(), completed.usage());
			case ProcessingOutcome.Failed failed when failed.transientFailure()
					&& job.attempts() < this.properties.maxAttempts() -> {
				long delay = this.properties.retryDelay().toMillis() << Math.min(job.attempts() - 1, 10);
				this.repository.retryLater(job.id(), Instant.now().plusMillis(delay), failed.failure().name(),
						failed.usage());
			}
			case ProcessingOutcome.Failed failed -> this.repository.fail(job.id(), failed.failure().name(), failed.usage());
		}
		log.info("Analysis processed: analysisId={}, type={}, attempt={}, outcome={}, calls={}, inputTokens={}, outputTokens={}, elapsedMs={}",
				job.id(), job.type(), job.attempts(), describe(outcome), outcome.usage().calls(),
				outcome.usage().inputTokens(), outcome.usage().outputTokens(), (System.nanoTime() - started) / 1_000_000);
	}

	private ProcessingOutcome execute(AnalysisRecord job) {
		if (!this.properties.available()) {
			return new ProcessingOutcome.Failed(AnalysisFailure.AI_NOT_CONFIGURED, false, AnalysisRepository.Usage.NONE);
		}
		if (!this.settingsRepository.find(job.projectId()).enabled()) {
			return new ProcessingOutcome.Failed(AnalysisFailure.AI_DISABLED, false, AnalysisRepository.Usage.NONE);
		}
		AnalysisProcessor processor = this.processors.get(job.type());
		if (processor == null) {
			return new ProcessingOutcome.Failed(AnalysisFailure.AI_API_ERROR, false, AnalysisRepository.Usage.NONE);
		}
		return processor.process(job);
	}

	private static String describe(ProcessingOutcome outcome) {
		return switch (outcome) {
			case ProcessingOutcome.Completed completed -> "COMPLETED";
			case ProcessingOutcome.Failed failed -> failed.failure().name() + (failed.transientFailure() ? "(transient)" : "");
		};
	}

}
