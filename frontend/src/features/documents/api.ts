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

/** 기존 문서에 새 버전을 등록한다. 파일만 보낸다 (SPEC-04 §6.3). 응답은 갱신된 문서 상세다. */
export function createDocumentVersion(projectId: string, documentId: string, file: File): Promise<DocumentDetail> {
  const form = new FormData();
  form.append("file", file);
  return apiRequest<DocumentDetail>(`${documentsPath(projectId)}/${encodeURIComponent(documentId)}/versions`, {
    method: "POST",
    body: form,
  });
}

export function fetchDocument(projectId: string, documentId: string): Promise<DocumentDetail> {
  return apiRequest<DocumentDetail>(`${documentsPath(projectId)}/${encodeURIComponent(documentId)}`);
}
