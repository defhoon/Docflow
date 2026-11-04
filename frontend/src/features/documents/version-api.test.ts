import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { createDocumentVersion } from "./api";

describe("새 버전 등록 API (SPEC-04 §6.2, §6.3)", () => {
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

  it("file 파트만 가진 multipart POST를 버전 경로로 CSRF 헤더와 함께 보낸다", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ id: 3 }), { status: 201, headers: { "Content-Type": "application/json" } }),
    );
    const file = new File(["%PDF"], "v2.pdf");

    await createDocumentVersion("7", "3", file);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/projects/7/documents/3/versions");
    expect(init.method).toBe("POST");
    const body = init.body as FormData;
    expect([...body.keys()]).toEqual(["file"]);
    expect(body.get("file")).toBe(file);
    expect(new Headers(init.headers).get("X-XSRF-TOKEN")).toBe("t");
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
  });

  it("네트워크 오류는 한 번만 요청하고 network 오류로 끝난다 (OQ-8, 자동 재시도 없음)", async () => {
    fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

    await expect(createDocumentVersion("7", "3", new File(["%PDF"], "v2.pdf"))).rejects.toMatchObject({
      kind: "network",
    });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });
});
