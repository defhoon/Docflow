import { ApiError } from "@/lib/api-client";

import { failureMessage } from "@/features/projects/errors";

const TEXT_STATUS: Record<string, string> = {
  NO_TEXT: "텍스트가 없는 PDF입니다(스캔 이미지 PDF는 지원하지 않습니다).",
  ENCRYPTED: "암호가 걸려 있거나 텍스트 추출이 제한된 PDF입니다.",
  CORRUPTED: "PDF 구조를 읽을 수 없습니다.",
  TOO_LARGE: "문서가 너무 큽니다(최대 300쪽).",
  TIMEOUT: "문서를 읽는 데 시간이 너무 오래 걸렸습니다. 잠시 후 다시 시도해 주세요.",
  UNSUPPORTED_TYPE: "PDF 문서가 아닙니다.",
};

/** 비교 실패 안내 (SPEC-12 §6). */
export function compareFailureMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case "COMPARE_UNSUPPORTED_TYPE":
        return "버전 비교는 PDF 문서만 지원합니다. Office 문서는 원본을 내려받아 확인해 주세요.";
      case "COMPARE_SAME_VERSION":
        return "서로 다른 두 버전을 선택해 주세요.";
      case "COMPARE_TEXT_UNAVAILABLE": {
        const detail = error.fieldErrors[0];
        const side = detail?.field === "base" ? "이전 버전" : "이후 버전";
        return `${side}에서 비교할 텍스트를 읽을 수 없습니다. ${TEXT_STATUS[detail?.message ?? ""] ?? ""}`.trim();
      }
      case "DOCUMENT_VERSION_NOT_FOUND":
        return "선택한 버전을 찾을 수 없습니다.";
    }
  }
  return failureMessage(error);
}

export const CHANGE_LABELS = {
  ADDED: { symbol: "+", label: "추가" },
  DELETED: { symbol: "−", label: "삭제" },
  MODIFIED: { symbol: "~", label: "수정" },
} as const;
