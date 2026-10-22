import { ApiError, apiRequest, refreshCsrfToken } from "@/lib/api-client";

import type { AuthUser } from "./types";

export { refreshCsrfToken as fetchCsrf };

/** 로그인 후 서버가 CSRF 토큰을 교체하므로 새 토큰을 받아 둔다. */
export async function login(email: string, password: string): Promise<AuthUser> {
  const user = await apiRequest<AuthUser>("/api/auth/login", {
    method: "POST",
    body: { email, password },
  });
  await refreshCsrfToken();
  return user;
}

/** 세션이 없어도 204를 반환한다. 로그아웃 후에도 새 CSRF 토큰을 받아 둔다. */
export async function logout(): Promise<void> {
  await apiRequest<void>("/api/auth/logout", { method: "POST" });
  await refreshCsrfToken();
}

/** 로그인 상태가 아니면(401) null을 반환한다. 그 밖의 오류는 그대로 전달한다. */
export async function fetchCurrentUser(): Promise<AuthUser | null> {
  try {
    return await apiRequest<AuthUser>("/api/auth/me");
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}
