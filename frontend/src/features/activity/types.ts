/** Backend `ActivityResponse` (SPEC-06 §6). */
export type ActivityAction =
  | "DOCUMENT_CREATED"
  | "VERSION_CREATED"
  | "FEEDBACK_CREATED"
  | "DOCUMENT_FINALIZED"
  /** SPEC-09 문서 설명 변경 (버전 번호 없음) */
  | "DOCUMENT_DESCRIPTION_UPDATED";

export type Activity = {
  id: number;
  action: ActivityAction;
  actor: { id: number; displayName: string };
  project: { id: number; name: string };
  document: { id: number; title: string } | null;
  versionNumber: number | null;
  createdAt: string;
};
