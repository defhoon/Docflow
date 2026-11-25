"use client";

import { useCallback } from "react";

import { Button } from "@/components/ui/Button";
import { SparkleIcon } from "@/components/ui/icons";
import { Skeleton } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { formatDateTime } from "@/features/projects/format";

import { fetchVersionSummary, requestVersionSummary } from "../api";
import { analysisFailureMessage, FACT_TYPE_LABELS } from "../messages";
import type { SummaryResult } from "../types";
import { useAnalysis } from "../useAnalysis";

type Props = {
  projectId: string;
  documentId: string;
  versionNumber: number;
};

/** 선택한 버전의 AI 요약 상태 조회·요청 (SPEC-11). 문서 워크스페이스가 탭 표시 여부 판단에도 쓴다 (SPEC-16). */
export function useVersionSummary(projectId: string, documentId: string, versionNumber: number) {
  const load = useCallback(() => fetchVersionSummary(projectId, documentId, versionNumber), [projectId, documentId, versionNumber]);
  const submit = useCallback(() => requestVersionSummary(projectId, documentId, versionNumber), [projectId, documentId, versionNumber]);
  return useAnalysis<SummaryResult>(`${projectId}/${documentId}/${versionNumber}`, load, submit);
}

export type VersionSummaryHandle = ReturnType<typeof useVersionSummary>;

/**
 * 선택한 버전의 AI 요약 (SPEC-11 §8). AI가 만든 결과임을 표시하고, 사용자가 쓴 버전 정보와 구분한다. 모든 값은 텍스트로만
 * 표시한다(HTML 해석 없음).
 */
export function VersionSummaryPanel({ projectId, documentId, versionNumber }: Props) {
  const summary = useVersionSummary(projectId, documentId, versionNumber);
  return <VersionSummaryView summary={summary} />;
}

/**
 * 요약 표시 (SPEC-11 §8, SPEC-16). `onGoToPage`가 있으면 쪽 번호(p.N)를 버튼으로 그려 문서 뷰어를 그 쪽으로 옮긴다.
 * `plain`이면 테두리 상자 없이 탭 패널 안에 바로 표시한다.
 */
export function VersionSummaryView({ summary, onGoToPage, plain = false }: {
  summary: VersionSummaryHandle;
  onGoToPage?: (page: number) => void;
  plain?: boolean;
}) {
  const { state, request, requesting, requestError, reload } = summary;
  const SummaryFrame = plain ? PlainFrame : BoxFrame;
  const pageRef = (page: number) =>
    onGoToPage ? (
      <button
        key={page}
        type="button"
        onClick={() => onGoToPage(page)}
        className="rounded px-0.5 text-brand-700 underline-offset-2 hover:underline"
        aria-label={`${page}쪽으로 이동`}
      >
        p.{page}
      </button>
    ) : (
      <span key={page}>p.{page}</span>
    );
  const pageRefs = (pages: number[]) => !onGoToPage ? (
    <span className="ml-1 text-xs text-ink-muted">(p.{pages.join(", ")})</span>
  ) : (
    <span className="ml-1 inline-flex flex-wrap gap-x-1 text-xs text-ink-muted">
      ({pages.map((page, index) => (
        <span key={page}>
          {index > 0 && ", "}
          {pageRef(page)}
        </span>
      ))})
    </span>
  );

  if (state.kind === "loading") {
    return (
      <SummaryFrame>
        <Skeleton className="h-4 w-2/3" />
      </SummaryFrame>
    );
  }
  if (state.kind === "error") {
    return (
      <SummaryFrame>
        <p className="text-sm text-danger-700" role="alert">
          AI 요약을 불러오지 못했습니다. {state.message}
        </p>
        <Button size="sm" onClick={reload} className="self-start">
          다시 불러오기
        </Button>
      </SummaryFrame>
    );
  }
  const analysis = state.value;
  // AI가 꺼진 프로젝트이고 결과도 없으면 영역을 표시하지 않는다(미구현 기능처럼 보이지 않게).
  if (!analysis.aiEnabled && analysis.status !== "COMPLETED") {
    return null;
  }
  if (!analysis.supported) {
    return (
      <SummaryFrame>
        <p className="text-sm text-ink-muted">AI 요약은 PDF 문서만 지원합니다.</p>
      </SummaryFrame>
    );
  }
  const errorLine = requestError && (
    <p role="alert" className="text-sm text-danger-700">
      {requestError}
    </p>
  );
  switch (analysis.status) {
    case "NONE":
      return (
        <SummaryFrame>
          <p className="text-sm text-ink-muted">이 버전의 AI 요약이 아직 없습니다. 요청하면 문서 내용이 외부 AI 서비스로 전송됩니다.</p>
          <Button size="sm" variant="primary" onClick={request} disabled={requesting} className="self-start">
            {requesting ? "요청 중..." : "AI 요약 만들기"}
          </Button>
          {errorLine}
        </SummaryFrame>
      );
    case "PENDING":
    case "PROCESSING":
      return (
        <SummaryFrame>
          <p className="flex items-center gap-2 text-sm text-ink-muted" role="status">
            <span className="inline-block size-3 animate-pulse rounded-full bg-brand-500" aria-hidden="true" />
            {analysis.status === "PENDING" ? "AI 요약을 기다리는 중입니다." : "AI가 문서를 요약하고 있습니다."} 잠시 후 자동으로 표시됩니다.
          </p>
        </SummaryFrame>
      );
    case "FAILED":
      return (
        <SummaryFrame>
          <p className="text-sm text-danger-700" role="alert">
            AI 요약을 만들지 못했습니다. {analysisFailureMessage(analysis.errorCode)}
          </p>
          {analysis.retryable && analysis.aiEnabled && (
            <Button size="sm" onClick={request} disabled={requesting} className="self-start">
              {requesting ? "요청 중..." : "다시 시도"}
            </Button>
          )}
          {errorLine}
        </SummaryFrame>
      );
    case "COMPLETED": {
      const result = analysis.result;
      if (!result) {
        return null;
      }
      return (
        <SummaryFrame
          footer={
            <p className="text-xs text-ink-muted">
              AI가 자동으로 만든 요약입니다. 원문과 다를 수 있으니 중요한 내용은 원본에서 확인해 주세요.
              {analysis.completedAt && (
                <>
                  {" "}
                  · <time dateTime={analysis.completedAt}>{formatDateTime(analysis.completedAt)}</time>
                </>
              )}
              {analysis.model && <> · {analysis.model}</>}
            </p>
          }
        >
          <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink">{result.summary}</p>
          {result.keyPoints.length > 0 && (
            <div>
              <h4 className="text-xs font-medium text-ink-muted">주요 내용</h4>
              <ul className="mt-1 flex list-disc flex-col gap-1 pl-5 text-sm text-ink">
                {result.keyPoints.map((point, index) => (
                  <li key={index} className="break-words">
                    {point.text}
                    {point.pages.length > 0 && pageRefs(point.pages)}
                  </li>
                ))}
              </ul>
            </div>
          )}
          {result.keyFacts.length > 0 && (
            <div>
              <h4 className="text-xs font-medium text-ink-muted">주요 일정·수치</h4>
              <dl className="mt-1 grid grid-cols-1 gap-1.5 text-sm sm:grid-cols-[max-content_minmax(0,1fr)] sm:gap-x-3">
                {result.keyFacts.map((fact, index) => (
                  <div key={index} className="contents">
                    <dt className="flex items-center gap-1.5 text-ink-muted">
                      <Badge>{FACT_TYPE_LABELS[fact.type] ?? "기타"}</Badge>
                      <span className="break-words">{fact.label}</span>
                    </dt>
                    <dd className="break-words text-ink">
                      {fact.value}
                      <span className="block text-xs text-ink-muted" title="원문 인용">
                        “{fact.quote}”{fact.page !== null && <> · {pageRef(fact.page)}</>}
                      </span>
                    </dd>
                  </div>
                ))}
              </dl>
            </div>
          )}
        </SummaryFrame>
      );
    }
  }
}

function PlainFrame({ children, footer }: { children: React.ReactNode; footer?: React.ReactNode }) {
  return (
    <section aria-label="AI 요약" className="flex flex-col gap-3">
      <p className="flex items-center gap-2">
        <Badge tone="brand">
          <SparkleIcon className="size-3.5" /> AI 생성 · 검토 필요
        </Badge>
      </p>
      {children}
      {footer}
    </section>
  );
}

function BoxFrame({ children, footer }: { children: React.ReactNode; footer?: React.ReactNode }) {
  return (
    <section aria-labelledby="ai-summary-heading" className="flex flex-col gap-3 rounded-[var(--radius-control)] border border-brand-500/20 bg-brand-50/40 px-4 py-3">
      <h3 id="ai-summary-heading" className="flex items-center gap-2 text-xs font-medium text-brand-700">
        <SparkleIcon className="size-4" /> AI 요약
        <Badge tone="brand">AI 생성 · 검토 필요</Badge>
      </h3>
      {children}
      {footer}
    </section>
  );
}
