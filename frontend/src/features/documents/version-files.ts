import { apiRequest, ApiError, type FieldError } from "@/lib/api-client";

/** Backend `DocumentVersionResponse` (SPEC-05 §6.3). 저장 키·해시·Content Type은 포함되지 않는다. */
export type DocumentVersionSummary = {
  versionNumber: number;
  originalFilename: string;
  sizeBytes: number;
  uploadedBy: { id: number; displayName: string };
  uploadedAt: string;
  latest: boolean;
};

function versionsPath(projectId: string, documentId: string): string {
  return `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/versions`;
}

/** 버전 히스토리: 최신 먼저 (SPEC-05 §6.3). */
export function fetchDocumentVersions(projectId: string, documentId: string): Promise<DocumentVersionSummary[]> {
  return apiRequest<DocumentVersionSummary[]>(versionsPath(projectId, documentId));
}

export type PreviewResult = { status: "ready"; blob: Blob } | { status: "pending"; retryAfterMs: number };

const DEFAULT_RETRY_AFTER_MS = 2000;

/**
 * 원본 파일을 Blob으로 받는다 (SPEC-05 §9.3, 결정 Q2). 파일 응답은 `Accept: *\/*`로 요청한다.
 * 오류는 [기존] `api-client`와 같은 `ApiError`로 던진다.
 */
export async function downloadVersionFile(
  projectId: string,
  documentId: string,
  versionNumber: number,
  signal?: AbortSignal,
): Promise<Blob> {
  const response = await fetchFile(`${versionsPath(projectId, documentId)}/${versionNumber}/download`, signal);
  if (!response.ok) {
    throw await toHttpError(response);
  }
  return response.blob();
}

/**
 * 미리보기 PDF를 요청한다 (SPEC-05 §6.5). `202`면 변환 중이다. 표시용 Blob의 형식은 응답 헤더가 아니라 코드에서
 * `application/pdf`로 고정하고, 응답이 PDF가 아니면 표시하지 않는다 (§7.4).
 */
export async function fetchVersionPreview(
  projectId: string,
  documentId: string,
  versionNumber: number,
  signal?: AbortSignal,
): Promise<PreviewResult> {
  const response = await fetchFile(`${versionsPath(projectId, documentId)}/${versionNumber}/preview`, signal);
  if (response.status === 202) {
    await response.body?.cancel().catch(() => undefined);
    const seconds = Number(response.headers.get("Retry-After"));
    return { status: "pending", retryAfterMs: seconds > 0 ? seconds * 1000 : DEFAULT_RETRY_AFTER_MS };
  }
  if (!response.ok) {
    throw await toHttpError(response);
  }
  const contentType = response.headers.get("Content-Type") ?? "";
  if (!contentType.toLowerCase().startsWith("application/pdf")) {
    await response.body?.cancel().catch(() => undefined);
    throw new ApiError("http", response.status, "PREVIEW_NOT_PDF", "미리보기 응답이 PDF가 아닙니다.");
  }
  const bytes = await response.arrayBuffer();
  return { status: "ready", blob: new Blob([bytes], { type: "application/pdf" }) };
}

/** Blob을 원본 파일명으로 저장한다. 사용한 Blob URL은 바로 해제한다. */
export function saveBlob(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  try {
    const link = document.createElement("a");
    link.href = url;
    link.download = filename;
    link.rel = "noopener";
    link.style.display = "none";
    document.body.appendChild(link);
    link.click();
    link.remove();
  } finally {
    // 클릭이 다운로드를 시작한 뒤 해제한다.
    setTimeout(() => URL.revokeObjectURL(url), 0);
  }
}

async function fetchFile(path: string, signal?: AbortSignal): Promise<Response> {
  try {
    return await fetch(path, {
      method: "GET",
      headers: { Accept: "*/*" },
      credentials: "same-origin",
      cache: "no-store",
      signal,
    });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    throw new ApiError("network", null, null, "서버에 연결할 수 없습니다.");
  }
}

async function toHttpError(response: Response): Promise<ApiError> {
  const body = (await response.json().catch(() => null)) as {
    code?: unknown;
    message?: unknown;
    fieldErrors?: unknown;
  } | null;
  const code = typeof body?.code === "string" ? body.code : null;
  const message = typeof body?.message === "string" ? body.message : `요청이 실패했습니다. (HTTP ${response.status})`;
  const fieldErrors = Array.isArray(body?.fieldErrors) ? (body.fieldErrors as FieldError[]) : [];
  return new ApiError("http", response.status, code, message, fieldErrors);
}
