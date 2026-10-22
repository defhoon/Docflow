/**
 * 공통 API 클라이언트 (SPEC-AUTH §6.4, §9.4).
 *
 * - 브라우저는 Next.js(동일 출처)만 호출하고, `/api/*`는 rewrite로 Backend에 전달된다.
 * - 변경 요청에는 `XSRF-TOKEN` 쿠키 값을 `X-XSRF-TOKEN` 헤더로 보낸다.
 * - 자동 재시도는 서버가 `403 CSRF_INVALID`로 거부한 요청에 한해 토큰 갱신 후 최대 1회만 수행한다.
 *   그 밖의 403, 네트워크 오류, 5xx는 재시도하지 않는다.
 */

export const CSRF_COOKIE_NAME = "XSRF-TOKEN";
export const CSRF_HEADER_NAME = "X-XSRF-TOKEN";
export const CSRF_ENDPOINT = "/api/auth/csrf";

const SAFE_METHODS = new Set(["GET", "HEAD", "OPTIONS"]);

export type FieldError = { field: string; message: string };

export class ApiError extends Error {
  constructor(
    readonly kind: "http" | "network",
    readonly status: number | null,
    readonly code: string | null,
    message: string,
    readonly fieldErrors: FieldError[] = [],
  ) {
    super(message);
    this.name = "ApiError";
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
};

export function readCookie(name: string): string | null {
  if (typeof document === "undefined") {
    return null;
  }
  const prefix = `${name}=`;
  for (const part of document.cookie.split(";")) {
    const cookie = part.trim();
    if (cookie.startsWith(prefix)) {
      return decodeURIComponent(cookie.slice(prefix.length));
    }
  }
  return null;
}

let pendingCsrfRefresh: Promise<void> | null = null;

/** CSRF 토큰 쿠키를 발급·갱신한다. 동시에 여러 번 호출되면 요청 하나를 공유한다. */
export function refreshCsrfToken(): Promise<void> {
  if (!pendingCsrfRefresh) {
    pendingCsrfRefresh = fetch(CSRF_ENDPOINT, { credentials: "same-origin", cache: "no-store" })
      .then((response) => {
        if (!response.ok) {
          throw new ApiError("http", response.status, null, "CSRF 토큰을 발급받지 못했습니다.");
        }
      }, toNetworkError)
      .finally(() => {
        pendingCsrfRefresh = null;
      });
  }
  return pendingCsrfRefresh;
}

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? "GET";
  const unsafe = !SAFE_METHODS.has(method);

  if (unsafe && !readCookie(CSRF_COOKIE_NAME)) {
    await refreshCsrfToken();
  }

  let response = await send(path, method, options.body, unsafe);

  if (unsafe && response.status === 403) {
    const error = await toHttpError(response);
    if (error.code !== "CSRF_INVALID") {
      throw error;
    }
    await refreshCsrfToken();
    response = await send(path, method, options.body, unsafe);
  }

  if (!response.ok) {
    throw await toHttpError(response);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

async function send(path: string, method: string, body: unknown, unsafe: boolean): Promise<Response> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (body !== undefined) {
    headers["Content-Type"] = "application/json";
  }
  if (unsafe) {
    const token = readCookie(CSRF_COOKIE_NAME);
    if (token) {
      headers[CSRF_HEADER_NAME] = token;
    }
  }
  try {
    return await fetch(path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: "same-origin",
      cache: "no-store",
    });
  } catch {
    return toNetworkError();
  }
}

function toNetworkError(): never {
  throw new ApiError("network", null, null, "서버에 연결할 수 없습니다.");
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
