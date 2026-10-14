import type { BackendHealthState } from "./types";

export const BACKEND_HEALTH_PATH = "/backend/health";

type HealthResponse = { status?: unknown };

/**
 * Backend Actuator health 결과를 화면 상태로 변환한다.
 * - 200 + UP: Backend와 DB 모두 정상
 * - 503 + DOWN 등: Backend는 응답했지만 DB 등 구성 요소가 비정상
 * - 그 외(네트워크 실패, 프록시 오류, 형식 불일치): 연결 오류
 */
export async function fetchBackendHealth(
  signal?: AbortSignal,
): Promise<Exclude<BackendHealthState, { kind: "loading" }>> {
  let response: Response;
  try {
    response = await fetch(BACKEND_HEALTH_PATH, { cache: "no-store", signal });
  } catch (error) {
    if (error instanceof DOMException && error.name === "AbortError") {
      throw error;
    }
    return { kind: "error", message: "Backend에 연결할 수 없습니다." };
  }

  const body = (await response.json().catch(() => null)) as HealthResponse | null;
  const status = typeof body?.status === "string" ? body.status : null;

  if (status === null) {
    return {
      kind: "error",
      message: response.ok
        ? `Backend 응답을 해석할 수 없습니다. (HTTP ${response.status})`
        : `Backend에 연결할 수 없습니다. (HTTP ${response.status})`,
    };
  }
  if (response.ok && status === "UP") {
    return { kind: "up" };
  }
  return { kind: "down", status };
}
