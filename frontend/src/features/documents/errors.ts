import { failureMessage } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { MESSAGES } from "./validation";

/**
 * 문서 등록 실패를 사용자 문구로 바꾼다 (SPEC-03 §8.3, §9). 필드 오류(400 `fieldErrors`)·401·404는 화면에서 따로
 * 처리한다. 서버 내부 정보는 표시하지 않는다.
 */
export function uploadFailureMessage(error: unknown): string {
  if (error instanceof ApiError && error.kind === "network") {
    // 업로드 중 연결이 끊긴 경우(서버 조기 응답 후 연결 종료, 프록시 타임아웃 등)도 여기에 해당한다.
    return "업로드 중 서버와의 연결이 끊어졌습니다. 파일 크기(20MB 이하)와 네트워크 상태를 확인한 뒤 다시 시도해 주세요.";
  }
  if (error instanceof ApiError && error.status === 413) {
    return MESSAGES.fileTooLarge;
  }
  if (error instanceof ApiError && error.status === 403) {
    return "요청을 처리할 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.";
  }
  if (error instanceof ApiError && error.status === 415) {
    return "지원하지 않는 요청 형식입니다. 페이지를 새로고침한 뒤 다시 시도해 주세요.";
  }
  return failureMessage(error);
}
