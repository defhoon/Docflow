package com.docflow.analysis.service;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** 분석 작업자용 스케줄러 (SPEC-11 §5). 스케줄 작업은 {@link AnalysisWorker} 하나다. */
@Configuration
@EnableScheduling
public class SchedulingConfig {

}
