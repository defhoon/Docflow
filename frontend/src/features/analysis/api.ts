import { apiRequest } from "@/lib/api-client";

import type { AiSettings, AnalysisState, SummaryResult } from "./types";

function projectPath(projectId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}`;
}

export function summaryPath(projectId: string, documentId: string, versionNumber: number): string {
  return `${projectPath(projectId)}/documents/${encodeURIComponent(documentId)}/versions/${versionNumber}/summary`;
}

export function fetchAiSettings(projectId: string): Promise<AiSettings> {
  return apiRequest<AiSettings>(`${projectPath(projectId)}/ai-settings`);
}

/** Owner만 가능. 켤 때는 외부 전송 안내 동의(`consent: true`)가 필요하다. */
export function updateAiSettings(projectId: string, enabled: boolean, consent: boolean): Promise<AiSettings> {
  return apiRequest<AiSettings>(`${projectPath(projectId)}/ai-settings`, { method: "PUT", body: { enabled, consent } });
}

export function fetchVersionSummary(projectId: string, documentId: string, versionNumber: number): Promise<AnalysisState<SummaryResult>> {
  return apiRequest<AnalysisState<SummaryResult>>(summaryPath(projectId, documentId, versionNumber));
}

/** 요약 요청·재시도. 이미 대기·진행·완료 중이면 현재 상태를 그대로 돌려준다(중복 호출 없음). */
export function requestVersionSummary(projectId: string, documentId: string, versionNumber: number): Promise<AnalysisState<SummaryResult>> {
  return apiRequest<AnalysisState<SummaryResult>>(summaryPath(projectId, documentId, versionNumber), { method: "POST" });
}
