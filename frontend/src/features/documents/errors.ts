import { errorCode, failureMessage } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { MESSAGES } from "./validation";

/**
 * 문서 등록 실패를 사용자 문구로 바꾼다 (SPEC-03 §8.3, §9). 필드 오류(400 `fieldErrors`)·401·404는 화면에서 따로
 * 처리한다. 서버 내부 정보는 표시하지 않는다.
 */
export function uploadFailureMessage(error: unknown): string {
  if (error instanceof ApiError && error.kind === "network") {
    // 업로드 중 연결이 끊긴 경우(서버 조기 응답 후 연결 종료, 프록시 타임아웃 등)도 여기에 해당한다.
    return "업로드 중 서버와의 연결이 끊어졌습니다. 문서가 등록되었는지 프로젝트의 문서 목록에서 확인한 뒤, 등록되지 않았다면 파일 크기(20MB 이하)와 네트워크 상태를 확인하고 다시 시도해 주세요.";
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

/** 새 버전 등록 문구 (SPEC-04 §9.2, §9.3). */
export const VERSION_MESSAGES = {
  statusConflict: "최종 확정된 문서에는 새 버전을 등록할 수 없습니다. 변경이 필요하면 새 문서를 등록해 주세요.",
  statusGuide: "최종 확정된 문서는 읽기 전용입니다. 변경이 필요하면 새 문서를 등록해 주세요.",
  // 결정 OQ-8: 결과가 불명확하므로 자동으로 다시 보내지 않고 사용자가 최신 버전을 확인하게 한다.
  network:
    "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요.",
  documentNotFound: "문서를 찾을 수 없습니다.",
  projectNotFound: "프로젝트를 찾을 수 없습니다.",
} as const;

/**
 * 새 버전 등록 실패를 사용자 문구로 바꾼다 (SPEC-04 §9.3). 필드 오류(400 `fieldErrors`)·413·401은 화면에서 따로
 * 처리한다. 403·415·5xx는 SPEC-03 문구를 그대로 쓴다.
 */
export function versionUploadFailureMessage(error: unknown): string {
  if (error instanceof ApiError && error.kind === "network") {
    return VERSION_MESSAGES.network;
  }
  if (error instanceof ApiError && error.status === 409) {
    return VERSION_MESSAGES.statusConflict;
  }
  if (error instanceof ApiError && error.status === 404) {
    return errorCode(error) === "PROJECT_NOT_FOUND" ? VERSION_MESSAGES.projectNotFound : VERSION_MESSAGES.documentNotFound;
  }
  return uploadFailureMessage(error);
}

/** 버전 다운로드·미리보기 문구 (SPEC-05 §9.4). */
export const FILE_MESSAGES = {
  fileUnavailable: "파일을 불러올 수 없습니다. 관리자에게 문의해 주세요.",
  previewFailed: "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요.",
  previewUnavailable: "지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요.",
  network: "파일을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.",
  versionNotFound: "버전을 찾을 수 없습니다.",
} as const;

/** 버전 다운로드·미리보기 실패 문구. 401은 화면에서 세션 만료 흐름으로 처리한다. */
export function fileFailureMessage(error: unknown): string {
  if (error instanceof ApiError && error.kind === "network") {
    return FILE_MESSAGES.network;
  }
  if (error instanceof ApiError && error.status === 404) {
    const code = errorCode(error);
    if (code === "PROJECT_NOT_FOUND") {
      return VERSION_MESSAGES.projectNotFound;
    }
    if (code === "DOCUMENT_VERSION_NOT_FOUND") {
      return FILE_MESSAGES.versionNotFound;
    }
    return VERSION_MESSAGES.documentNotFound;
  }
  if (error instanceof ApiError && error.code === "DOCUMENT_FILE_UNAVAILABLE") {
    return FILE_MESSAGES.fileUnavailable;
  }
  if (error instanceof ApiError && (error.status === 422 || error.code === "PREVIEW_NOT_PDF")) {
    return FILE_MESSAGES.previewFailed;
  }
  if (error instanceof ApiError && error.status === 503) {
    return FILE_MESSAGES.previewUnavailable;
  }
  return failureMessage(error);
}
