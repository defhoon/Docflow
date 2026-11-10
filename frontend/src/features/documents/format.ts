import type { DocumentStatus } from "./types";

const STATUS_LABELS: Record<DocumentStatus, string> = {
  ACTIVE: "협업 중",
  FINALIZED: "최종 확정",
};

export function statusLabel(status: DocumentStatus): string {
  return STATUS_LABELS[status] ?? status;
}

/** 파일 크기 표시. 1KB = 1,024바이트. 정확한 바이트 수는 `title` 등으로 함께 제공한다. */
export function formatFileSize(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
