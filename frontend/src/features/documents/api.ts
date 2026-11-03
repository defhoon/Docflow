import { apiRequest } from "@/lib/api-client";

import type { CreateDocumentInput, DocumentDetail, DocumentSummary } from "./types";

function documentsPath(projectId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/documents`;
}

/** 문서와 v1을 multipart 요청 하나로 등록한다 (SPEC-03 §6.3). */
export function createDocument(projectId: string, input: CreateDocumentInput): Promise<DocumentDetail> {
  const form = new FormData();
  form.append("title", input.title);
  if (input.description !== null) {
    form.append("description", input.description);
  }
  form.append("file", input.file);
  return apiRequest<DocumentDetail>(documentsPath(projectId), { method: "POST", body: form });
}

/** 서버 정렬(`created_at DESC, id DESC`)을 그대로 사용한다. */
export function fetchDocuments(projectId: string): Promise<DocumentSummary[]> {
  return apiRequest<DocumentSummary[]>(documentsPath(projectId));
}

export function fetchDocument(projectId: string, documentId: string): Promise<DocumentDetail> {
  return apiRequest<DocumentDetail>(`${documentsPath(projectId)}/${encodeURIComponent(documentId)}`);
}
