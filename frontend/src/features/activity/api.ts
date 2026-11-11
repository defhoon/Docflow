import { apiRequest } from "@/lib/api-client";

import type { Activity } from "./types";

function query(limit: number): string {
  return `?limit=${limit}`;
}

/** 접근 가능한 모든 프로젝트의 최근 활동 (SPEC-06). */
export function fetchMyActivities(limit = 20): Promise<Activity[]> {
  return apiRequest<Activity[]>(`/api/activities${query(limit)}`);
}

export function fetchProjectActivities(projectId: string, limit = 20): Promise<Activity[]> {
  return apiRequest<Activity[]>(`/api/projects/${encodeURIComponent(projectId)}/activities${query(limit)}`);
}

export function fetchDocumentActivities(projectId: string, documentId: string, limit = 20): Promise<Activity[]> {
  return apiRequest<Activity[]>(
    `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/activities${query(limit)}`,
  );
}
