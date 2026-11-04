import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { downloadVersionFile, fetchDocumentVersions, fetchVersionPreview, saveBlob } from "./version-files";

function response(body: BodyInit | null, status: number, headers: Record<string, string> = {}) {
  return new Response(body, { status, headers });
}

describe("버전 파일 API (SPEC-05 §6, §9.3)", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("버전 목록은 GET /versions다", async () => {
    fetchMock.mockResolvedValueOnce(response("[]", 200, { "Content-Type": "application/json" }));

    await fetchDocumentVersions("7", "3");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/projects/7/documents/3/versions");
  });

  it("다운로드는 Accept */*, 같은 출처 쿠키, 캐시 없이 요청하고 Blob을 돌려준다", async () => {
    fetchMock.mockResolvedValueOnce(response(new Uint8Array([1, 2, 3]), 200, { "Content-Type": "application/pdf" }));

    const blob = await downloadVersionFile("7", "3", 2);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/projects/7/documents/3/versions/2/download");
    expect(init).toMatchObject({ method: "GET", credentials: "same-origin", cache: "no-store" });
    expect(new Headers(init.headers).get("Accept")).toBe("*/*");
    expect(new Uint8Array(await blob.arrayBuffer())).toEqual(new Uint8Array([1, 2, 3]));
  });

  it("오류 응답은 code를 가진 ApiError, 연결 실패는 network ApiError다", async () => {
    fetchMock.mockResolvedValueOnce(
      response(JSON.stringify({ code: "DOCUMENT_FILE_UNAVAILABLE", message: "m" }), 500, {
        "Content-Type": "application/json",
      }),
    );
    await expect(downloadVersionFile("7", "3", 1)).rejects.toMatchObject({
      kind: "http",
      status: 500,
      code: "DOCUMENT_FILE_UNAVAILABLE",
    });

    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));
    await expect(downloadVersionFile("7", "3", 1)).rejects.toMatchObject({ kind: "network" });
  });

  it("미리보기 202는 Retry-After 간격의 대기 상태다 (없으면 2초)", async () => {
    fetchMock
      .mockResolvedValueOnce(response(JSON.stringify({ status: "PENDING" }), 202, { "Retry-After": "3" }))
      .mockResolvedValueOnce(response(JSON.stringify({ status: "PENDING" }), 202));

    expect(await fetchVersionPreview("7", "3", 1)).toEqual({ status: "pending", retryAfterMs: 3000 });
    expect(await fetchVersionPreview("7", "3", 1)).toEqual({ status: "pending", retryAfterMs: 2000 });
    expect(fetchMock.mock.calls[0][0]).toBe("/api/projects/7/documents/3/versions/1/preview");
  });

  it("미리보기 PDF는 형식을 application/pdf로 고정한 Blob이다", async () => {
    fetchMock.mockResolvedValueOnce(response("%PDF-1.4", 200, { "Content-Type": "application/pdf" }));

    const result = await fetchVersionPreview("7", "3", 1);

    expect(result.status).toBe("ready");
    expect(result.status === "ready" && result.blob.type).toBe("application/pdf");
  });

  it("PDF가 아닌 미리보기 응답은 표시하지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(response("<script>alert(1)</script>", 200, { "Content-Type": "text/html" }));

    await expect(fetchVersionPreview("7", "3", 1)).rejects.toSatisfy(
      (error) => error instanceof ApiError && error.code === "PREVIEW_NOT_PDF",
    );
  });

  it("중단 신호는 그대로 전달된다", async () => {
    const controller = new AbortController();
    fetchMock.mockImplementationOnce(() => Promise.reject(new DOMException("Aborted", "AbortError")));
    controller.abort();

    await expect(fetchVersionPreview("7", "3", 1, controller.signal)).rejects.toMatchObject({ name: "AbortError" });
    expect((fetchMock.mock.calls[0][1] as RequestInit).signal).toBe(controller.signal);
  });

  it("저장은 원본 파일명으로 링크를 눌러 내려받고 Blob URL을 해제한다", async () => {
    vi.useFakeTimers();
    const create = vi.fn(() => "blob:test-url");
    const revoke = vi.fn();
    const original = { createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL };
    Object.assign(URL, { createObjectURL: create, revokeObjectURL: revoke });
    const click = vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);

    saveBlob(new Blob(["x"]), "요구사항 v2.docx");

    const anchor = click.mock.contexts[0] as HTMLAnchorElement;
    expect(anchor.download).toBe("요구사항 v2.docx");
    expect(anchor.href).toBe("blob:test-url");
    expect(anchor.isConnected).toBe(false);
    vi.runAllTimers();
    expect(revoke).toHaveBeenCalledWith("blob:test-url");
    click.mockRestore();
    Object.assign(URL, original);
    vi.useRealTimers();
  });
});
