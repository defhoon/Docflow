import { apiRequest } from "@/lib/api-client";

import type { Activity } from "./types";

function query(limit: number, beforeId?: number): string {
  return beforeId ? `?limit=${limit}&beforeId=${beforeId}` : `?limit=${limit}`;
}

/** 접근 가능한 모든 프로젝트의 최근 활동 (SPEC-06). `beforeId`보다 오래된 활동만(커서, SPEC-18 전체 활동). */
export function fetchMyActivities(limit = 20, beforeId?: number): Promise<Activity[]> {
  return apiRequest<Activity[]>(`/api/activities${query(limit, beforeId)}`);
}

export function fetchProjectActivities(projectId: string, limit = 20): Promise<Activity[]> {
  return apiRequest<Activity[]>(`/api/projects/${encodeURIComponent(projectId)}/activities${query(limit)}`);
}

export function fetchDocumentActivities(projectId: string, documentId: string, limit = 20): Promise<Activity[]> {
  return apiRequest<Activity[]>(
    `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/activities${query(limit)}`,
  );
}
