import { apiRequest } from "@/lib/api-client";

import type { CompareResponse } from "./types";

export function fetchComparison(projectId: string, documentId: string, base: number, target: number): Promise<CompareResponse> {
  const path = `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/compare`;
  return apiRequest<CompareResponse>(`${path}?base=${base}&target=${target}`);
}

export function compareHref(projectId: string, documentId: string, base: number, target: number): string {
  return `/projects/${projectId}/documents/${documentId}/compare?base=${base}&target=${target}`;
}
