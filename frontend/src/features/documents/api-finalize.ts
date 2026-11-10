import { apiRequest } from "@/lib/api-client";

import type { DocumentDetail } from "./types";

/** 최종 버전 확정 (SPEC-07 §6). 오너만 가능하며 되돌릴 수 없다. */
export function finalizeDocument(projectId: string, documentId: string, versionNumber: number): Promise<DocumentDetail> {
  return apiRequest<DocumentDetail>(
    `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/finalize`,
    { method: "POST", body: { versionNumber } },
  );
}
