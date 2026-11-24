package com.docflow.analysis.domain;

/** 분석 종류 (SPEC-11 §4). DB CHECK {@code ck_document_analyses_type}과 같은 집합이다. */
public enum AnalysisType {

	/** 한 버전의 문서 요약 (SPEC-11). */
	SUMMARY,
	/** 두 버전 비교 결과의 변경 요약 (SPEC-14). */
	CHANGE_SUMMARY

}
