/** 분석 실패 사유 안내 (SPEC-11 §5.3, Backend `AnalysisFailure`). */
const FAILURE_MESSAGES: Record<string, string> = {
  AI_DISABLED: "분석 전에 프로젝트의 AI 기능이 꺼졌습니다.",
  AI_NOT_CONFIGURED: "서비스의 AI 설정이 완료되지 않았습니다. 관리자에게 문의해 주세요.",
  QUOTA_EXCEEDED: "AI 사용 한도에 도달했습니다. 잠시 후(다음 날) 다시 시도해 주세요.",
  AI_API_ERROR: "AI 서비스에 일시적인 문제가 있었습니다. 다시 시도해 주세요.",
  AI_OUTPUT_INVALID: "AI 응답을 처리하지 못했습니다. 다시 시도해 주세요.",
  AI_REFUSED: "AI가 이 문서의 분석을 거절했습니다.",
  FILE_UNAVAILABLE: "원본 파일을 읽지 못했습니다. 다시 시도해 주세요.",
  TEXT_TIMEOUT: "문서 텍스트를 읽는 데 시간이 너무 오래 걸렸습니다. 다시 시도해 주세요.",
  TEXT_NO_TEXT: "문서에서 텍스트를 찾을 수 없습니다. 스캔한 이미지 PDF는 지원하지 않습니다.",
  TEXT_ENCRYPTED: "암호가 걸려 있거나 텍스트 추출이 제한된 PDF입니다.",
  TEXT_CORRUPTED: "PDF 구조를 읽을 수 없습니다.",
  TEXT_TOO_LARGE: "문서가 너무 큽니다(최대 300쪽).",
  UNSUPPORTED_TYPE: "PDF 문서만 분석할 수 있습니다.",
  DOCUMENT_TOO_LARGE: "문서가 너무 길어 AI로 분석할 수 없습니다.",
  COMPARE_TOO_LARGE: "변경 내용이 너무 많아 AI로 요약할 수 없습니다.",
  VERSION_NOT_FOUND: "비교할 버전을 찾을 수 없습니다.",
};

export function analysisFailureMessage(code: string | null): string {
  return (code && FAILURE_MESSAGES[code]) || "분석하지 못했습니다.";
}

export const FACT_TYPE_LABELS: Record<string, string> = {
  DATE: "일정",
  NUMBER: "수치",
  REQUIREMENT: "요구사항",
  OTHER: "기타",
};

/** 대기·진행 중 상태를 다시 조회하는 간격(ms). */
export const ANALYSIS_POLL_INTERVAL_MS = 4000;
