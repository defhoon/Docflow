package com.docflow.home.dto;

import java.util.List;

import com.docflow.audit.dto.ActivityResponse;

/**
 * 홈 통합 조회 응답 (SPEC-18). 모든 값은 현재 사용자가 Owner이거나 MEMBER인 프로젝트만 기준으로 한다.
 * @param projectCount 참여 프로젝트 수
 * @param activeDocumentCount 협업 중(ACTIVE) 문서 수
 * @param finalizedDocumentCount 최종 확정(FINALIZED) 문서 수
 * @param recentDocuments 최근 변경 문서(최대 {@code RECENT_DOCUMENT_LIMIT}개, 최신순)
 * @param recentActivities 최근 활동(최대 {@code RECENT_ACTIVITY_LIMIT}개, 최신순). 더 보기는 {@code GET /api/activities}
 */
public record HomeResponse(long projectCount, long activeDocumentCount, long finalizedDocumentCount,
		List<HomeDocumentResponse> recentDocuments, List<ActivityResponse> recentActivities) {

	public static final int RECENT_DOCUMENT_LIMIT = 5;

	public static final int RECENT_ACTIVITY_LIMIT = 5;

}
