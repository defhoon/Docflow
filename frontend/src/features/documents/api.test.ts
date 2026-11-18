import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createDocument, createDocumentVersion, fetchDocument, fetchDocuments, updateDocumentDescription } from "./api";

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("문서 API (SPEC-03 §6)", () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  it("등록은 title·description·file 파트를 가진 multipart POST다", async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 3 }, 201));
    const file = new File(["%PDF"], "a.pdf");

    await createDocument("7", { title: "제목", description: "설명", versionNote: "초기 내용", file });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/projects/7/documents");
    expect(init.method).toBe("POST");
    const body = init.body as FormData;
    expect(body.get("title")).toBe("제목");
    expect(body.get("description")).toBe("설명");
    expect(body.get("file")).toBe(file);
    expect(body.get("versionNote")).toBe("초기 내용");
    expect([...body.keys()]).toEqual(["title", "description", "versionNote", "file"]);
  });

  it("설명이 없으면 description 파트를 보내지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 3 }, 201));

    await createDocument("7", { title: "제목", description: null, versionNote: null, file: new File(["x"], "a.pdf") });

    const body = (fetchMock.mock.calls[0][1] as RequestInit).body as FormData;
    expect(body.has("description")).toBe(false);
    expect(body.has("versionNote")).toBe(false);
  });

  it("SPEC-09: 새 버전 변경사항은 versionNote 파트로, 없으면 보내지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 3 }, 201)).mockResolvedValueOnce(json({ id: 3 }, 201));
    const file = new File(["%PDF"], "b.pdf");

    await createDocumentVersion("7", "3", file, "3장 수정");
    await createDocumentVersion("7", "3", file);

    const withNote = (fetchMock.mock.calls[0][1] as RequestInit).body as FormData;
    expect(withNote.get("versionNote")).toBe("3장 수정");
    expect(((fetchMock.mock.calls[1][1] as RequestInit).body as FormData).has("versionNote")).toBe(false);
  });

  it("SPEC-09: 설명 변경은 JSON PATCH, null이면 설명을 지운다", async () => {
    fetchMock.mockResolvedValueOnce(json({ id: 3 })).mockResolvedValueOnce(json({ id: 3 }));

    await updateDocumentDescription("7", "3", "새 설명");
    await updateDocumentDescription("7", "3", null);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/projects/7/documents/3");
    expect(init.method).toBe("PATCH");
    expect(JSON.parse(init.body as string)).toEqual({ description: "새 설명" });
    expect(JSON.parse((fetchMock.mock.calls[1][1] as RequestInit).body as string)).toEqual({ description: null });
  });

  it("목록·상세 경로", async () => {
    fetchMock.mockResolvedValueOnce(json([])).mockResolvedValueOnce(json({ id: 3 }));

    await fetchDocuments("7");
    await fetchDocument("7", "3");

    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(["/api/projects/7/documents", "/api/projects/7/documents/3"]);
  });
});
