import { ApiError } from "@/lib/api-client";

/** 프로젝트 API가 401을 반환했는지. 이때 화면은 `useAuth().refresh()`로 세션 만료 흐름에 맡긴다. */
export function isUnauthenticated(error: unknown): boolean {
  return error instanceof ApiError && error.status === 401;
}

/** 필드 오류·404 외의 실패를 사용자 문구로 바꾼다. 서버 내부 정보는 표시하지 않는다. */
export function failureMessage(error: unknown): string {
  if (error instanceof ApiError && error.kind === "http" && error.status !== null && error.status >= 500) {
    return "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.";
  }
  if (error instanceof ApiError && error.kind === "http") {
    return "요청을 처리하지 못했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.";
  }
  return "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
}

/** 서버 오류 코드. 상태 코드만으로 구분할 수 없는 404(프로젝트 없음·구성원 없음·사용자 없음)를 나눌 때 사용한다. */
export function errorCode(error: unknown): string | null {
  return error instanceof ApiError ? error.code : null;
}
