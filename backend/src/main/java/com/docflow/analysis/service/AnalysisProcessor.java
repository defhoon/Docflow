package com.docflow.analysis.service;

import com.docflow.analysis.domain.AnalysisRecord;
import com.docflow.analysis.domain.AnalysisType;

/**
 * 분석 종류별 실행기. 작업자({@link AnalysisWorker})가 가져간 작업을 처리하고 결과를 돌려준다. 예외를 던지지 않고 결과로
 * 성공·실패·재시도를 알린다.
 */
public interface AnalysisProcessor {

	AnalysisType type();

	ProcessingOutcome process(AnalysisRecord job);

}
