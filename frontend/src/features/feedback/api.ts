import { apiRequest } from "@/lib/api-client";

import type { Feedback } from "./types";

export const FEEDBACK_MAX_LENGTH = 2000;

function feedbackPath(projectId: string, documentId: string, versionNumber: number): string {
  return `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/versions/${versionNumber}/feedbacks`;
}

/** 버전의 피드백을 작성 순으로 조회한다 (SPEC-08). */
export function fetchFeedbacks(projectId: string, documentId: string, versionNumber: number): Promise<Feedback[]> {
  return apiRequest<Feedback[]>(feedbackPath(projectId, documentId, versionNumber));
}

export function createFeedback(projectId: string, documentId: string, versionNumber: number, content: string): Promise<Feedback> {
  return apiRequest<Feedback>(feedbackPath(projectId, documentId, versionNumber), { method: "POST", body: { content } });
}

/** 서버와 같은 기준(앞뒤 공백 제거 후 코드 포인트 수)으로 길이를 센다. */
export function feedbackLength(content: string): number {
  return Array.from(content.trim()).length;
}
