import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError, apiRequest, CSRF_HEADER_NAME } from "./api-client";

function setCookie(value: string | null) {
  document.cookie =
    value === null ? "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/" : `XSRF-TOKEN=${value}; path=/`;
}

function json(body: unknown, status: number) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function headersOf(call: unknown[]): Record<string, string> {
  return ((call[1] as RequestInit | undefined)?.headers ?? {}) as Record<string, string>;
}

describe("apiRequest", () => {
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
    setCookie("token-1");
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    setCookie(null);
  });

  it("변경 요청에 쿠키의 CSRF 토큰을 헤더로 붙인다", async () => {
    fetchMock.mockResolvedValueOnce(json({ ok: true }, 200));

    await apiRequest("/api/x", { method: "POST", body: { a: 1 } });

    expect(headersOf(fetchMock.mock.calls[0])[CSRF_HEADER_NAME]).toBe("token-1");
  });

  it("GET 요청에는 CSRF 헤더를 붙이지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ ok: true }, 200));

    await apiRequest("/api/x");

    expect(headersOf(fetchMock.mock.calls[0])[CSRF_HEADER_NAME]).toBeUndefined();
  });

  it("CSRF 쿠키가 없으면 먼저 토큰을 발급받는다", async () => {
    setCookie(null);
    fetchMock.mockImplementationOnce(async () => {
      setCookie("issued");
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await apiRequest("/api/x", { method: "POST" });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/csrf");
    expect(headersOf(fetchMock.mock.calls[1])[CSRF_HEADER_NAME]).toBe("issued");
  });

  it("CSRF_INVALID이면 토큰을 갱신하고 1회 재시도한다", async () => {
    fetchMock.mockResolvedValueOnce(json({ code: "CSRF_INVALID", message: "x" }, 403));
    fetchMock.mockImplementationOnce(async () => {
      setCookie("token-2");
      return new Response(null, { status: 204 });
    });
    fetchMock.mockResolvedValueOnce(json({ id: 1 }, 200));

    const result = await apiRequest<{ id: number }>("/api/x", { method: "POST" });

    expect(result).toEqual({ id: 1 });
    expect(fetchMock).toHaveBeenCalledTimes(3);
    expect(fetchMock.mock.calls[1][0]).toBe("/api/auth/csrf");
    expect(headersOf(fetchMock.mock.calls[2])[CSRF_HEADER_NAME]).toBe("token-2");
  });

  it("재시도 후에도 CSRF_INVALID이면 더 이상 재시도하지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ code: "CSRF_INVALID", message: "x" }, 403));
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    fetchMock.mockResolvedValueOnce(json({ code: "CSRF_INVALID", message: "x" }, 403));

    await expect(apiRequest("/api/x", { method: "POST" })).rejects.toMatchObject({
      status: 403,
      code: "CSRF_INVALID",
    });
    expect(fetchMock).toHaveBeenCalledTimes(3);
  });

  it("CSRF_INVALID가 아닌 403은 재시도하지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ code: "ACCESS_DENIED", message: "x" }, 403));

    await expect(apiRequest("/api/x", { method: "POST" })).rejects.toMatchObject({ code: "ACCESS_DENIED" });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("5xx 응답은 재시도하지 않는다", async () => {
    fetchMock.mockResolvedValueOnce(json({ code: "INTERNAL_ERROR", message: "x" }, 500));

    await expect(apiRequest("/api/x", { method: "POST" })).rejects.toMatchObject({ status: 500 });
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("네트워크 오류는 재시도하지 않는다", async () => {
    fetchMock.mockRejectedValueOnce(new TypeError("Failed to fetch"));

    const error = await apiRequest("/api/x", { method: "POST" }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).kind).toBe("network");
    expect(fetchMock).toHaveBeenCalledTimes(1);
  });

  it("오류 응답의 code와 fieldErrors를 파싱한다", async () => {
    fetchMock.mockResolvedValueOnce(
      json(
        {
          code: "VALIDATION_FAILED",
          message: "입력값을 확인해 주세요.",
          fieldErrors: [{ field: "email", message: "형식 오류" }],
        },
        400,
      ),
    );

    await expect(apiRequest("/api/x", { method: "POST" })).rejects.toMatchObject({
      status: 400,
      code: "VALIDATION_FAILED",
      fieldErrors: [{ field: "email", message: "형식 오류" }],
    });
  });

  // SPEC-03 §8.3, AC-DOC-23: FormData 본문
  describe("FormData 본문", () => {
    function formData() {
      const form = new FormData();
      form.append("title", "문서");
      form.append("file", new File(["%PDF-1.4"], "a.pdf", { type: "application/pdf" }));
      return form;
    }

    it("Content-Type을 지정하지 않고 FormData를 그대로 보내며 CSRF 헤더를 붙인다", async () => {
      fetchMock.mockResolvedValueOnce(json({ id: 1 }, 201));
      const form = formData();

      await apiRequest("/api/x", { method: "POST", body: form });

      const init = fetchMock.mock.calls[0][1] as RequestInit;
      expect(init.body).toBe(form);
      expect(headersOf(fetchMock.mock.calls[0])["Content-Type"]).toBeUndefined();
      expect(headersOf(fetchMock.mock.calls[0])[CSRF_HEADER_NAME]).toBe("token-1");
    });

    it("CSRF_INVALID 재시도 시 같은 FormData를 다시 보낸다", async () => {
      fetchMock.mockResolvedValueOnce(json({ code: "CSRF_INVALID", message: "x" }, 403));
      fetchMock.mockImplementationOnce(async () => {
        setCookie("token-2");
        return new Response(null, { status: 204 });
      });
      fetchMock.mockResolvedValueOnce(json({ id: 1 }, 201));
      const form = formData();

      await apiRequest("/api/x", { method: "POST", body: form });

      expect((fetchMock.mock.calls[2][1] as RequestInit).body).toBe(form);
      expect(headersOf(fetchMock.mock.calls[2])[CSRF_HEADER_NAME]).toBe("token-2");
      expect(headersOf(fetchMock.mock.calls[2])["Content-Type"]).toBeUndefined();
    });

    it("JSON 본문은 기존처럼 직렬화한다", async () => {
      fetchMock.mockResolvedValueOnce(json({ ok: true }, 200));

      await apiRequest("/api/x", { method: "POST", body: { a: 1 } });

      expect((fetchMock.mock.calls[0][1] as RequestInit).body).toBe('{"a":1}');
      expect(headersOf(fetchMock.mock.calls[0])["Content-Type"]).toBe("application/json");
    });

    it("공통 형식이 아닌 413 본문도 상태 코드로 오류를 만든다", async () => {
      fetchMock.mockResolvedValueOnce(json({ timestamp: "t", status: 413, error: "Content Too Large", path: "/api/x" }, 413));

      await expect(apiRequest("/api/x", { method: "POST", body: formData() })).rejects.toMatchObject({
        kind: "http",
        status: 413,
        code: null,
      });
    });
  });
});

describe("apiRequest 업로드 진행률 (SPEC-19)", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
    setCookie(null);
  });

  /** 진행 이벤트를 흉내 내는 XMLHttpRequest. */
  class FakeXhr {
    static last: FakeXhr | null = null;
    upload: { onprogress: ((event: { lengthComputable: boolean; loaded: number; total: number }) => void) | null } = { onprogress: null };
    status = 0;
    responseText = "";
    withCredentials = false;
    headers: Record<string, string> = {};
    method = "";
    url = "";
    body: unknown = null;
    onload: (() => void) | null = null;
    onerror: (() => void) | null = null;
    onabort: (() => void) | null = null;
    constructor() {
      FakeXhr.last = this;
    }
    open(method: string, url: string) {
      this.method = method;
      this.url = url;
    }
    setRequestHeader(name: string, value: string) {
      this.headers[name] = value;
    }
    getAllResponseHeaders() {
      return "content-type: application/json\r\n";
    }
    send(body: unknown) {
      this.body = body;
    }
  }

  it("진행률을 알리고 CSRF 헤더·쿠키 포함 요청을 보내며, 응답은 fetch와 같이 처리한다", async () => {
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    setCookie("token-x");
    const progress = vi.fn();
    const form = new FormData();
    form.append("title", "t");
    const promise = apiRequest<{ id: number }>("/api/projects/1/documents", { method: "POST", body: form, onUploadProgress: progress });
    await Promise.resolve();
    const xhr = FakeXhr.last!;
    expect(xhr.method).toBe("POST");
    expect(xhr.withCredentials).toBe(true);
    expect(xhr.headers[CSRF_HEADER_NAME]).toBe("token-x");
    expect(xhr.headers["Content-Type"]).toBeUndefined();
    expect(xhr.body).toBe(form);
    xhr.upload.onprogress?.({ lengthComputable: true, loaded: 30, total: 60 });
    expect(progress).toHaveBeenCalledWith({ loaded: 30, total: 60 });
    xhr.status = 201;
    xhr.responseText = JSON.stringify({ id: 5 });
    xhr.onload?.();
    await expect(promise).resolves.toEqual({ id: 5 });
  });

  it("오류 응답·네트워크 오류는 같은 ApiError로 바꾼다", async () => {
    vi.stubGlobal("XMLHttpRequest", FakeXhr);
    setCookie("token-x");
    const failed = apiRequest("/api/x", { method: "POST", body: new FormData(), onUploadProgress: vi.fn() });
    await Promise.resolve();
    FakeXhr.last!.status = 413;
    FakeXhr.last!.responseText = JSON.stringify({ code: "FILE_TOO_LARGE", message: "크다" });
    FakeXhr.last!.onload?.();
    await expect(failed).rejects.toMatchObject({ status: 413, code: "FILE_TOO_LARGE" });

    const offline = apiRequest("/api/x", { method: "POST", body: new FormData(), onUploadProgress: vi.fn() });
    await Promise.resolve();
    FakeXhr.last!.onerror?.();
    await expect(offline).rejects.toBeInstanceOf(ApiError);
  });
});

