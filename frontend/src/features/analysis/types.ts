/** SPEC-11 AI 문서 요약·SPEC-14 변경 요약 공통 타입. */

export type AnalysisStatus = "NONE" | "PENDING" | "PROCESSING" | "COMPLETED" | "FAILED";

export type SummaryResult = {
  summary: string;
  keyPoints: { text: string; pages: number[] }[];
  keyFacts: { type: "DATE" | "NUMBER" | "REQUIREMENT" | "OTHER"; label: string; value: string; quote: string; page: number | null }[];
  meta: { chunks: number; droppedFacts: number; pageCount: number } | null;
};

export type AnalysisState<R> = {
  status: AnalysisStatus;
  errorCode: string | null;
  retryable: boolean;
  aiEnabled: boolean;
  supported: boolean;
  result: R | null;
  model: string | null;
  updatedAt: string | null;
  completedAt: string | null;
};

export type AiSettings = {
  enabled: boolean;
  available: boolean;
  canManage: boolean;
  enabledAt: string | null;
  enabledByName: string | null;
  provider: string;
  model: string;
};

export type ChangeSummaryResult = {
  overview: string;
  added: { text: string; changeIds: number[] }[];
  deleted: { text: string; changeIds: number[] }[];
  modified: { text: string; changeIds: number[] }[];
  keyChanges: { type: "DATE" | "NUMBER" | "REQUIREMENT" | "OTHER"; label: string; before: string; after: string; changeIds: number[] }[];
  significance: { level: "NONE" | "LOW" | "MEDIUM" | "HIGH"; reason: string };
  meta: { changeCount: number; droppedItems: number } | null;
};
