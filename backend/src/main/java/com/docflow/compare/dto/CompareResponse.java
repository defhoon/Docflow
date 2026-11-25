package com.docflow.compare.dto;

import java.util.List;

import com.docflow.compare.diff.CompareResult;

/**
 * 버전 비교 응답 (SPEC-12 §3).
 *
 * @param base 이전 버전 정보
 * @param target 이후 버전 정보
 * @param result 비교 결과
 */
public record CompareResponse(VersionInfo base, VersionInfo target, CompareResult result) {

	/** @param pages 페이지 크기·회전 (CropBox 기준, SPEC-10) */
	public record VersionInfo(int versionNumber, String originalFilename, int pageCount, List<Page> pages) {
	}

	public record Page(float width, float height, int rotation) {
	}

}
