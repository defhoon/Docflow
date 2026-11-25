"use client";

import { useCallback } from "react";

import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { SparkleIcon } from "@/components/ui/icons";
import { Skeleton } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { formatDateTime } from "@/features/projects/format";

import { fetchChangeSummary, requestChangeSummary } from "../api";
import { analysisFailureMessage, FACT_TYPE_LABELS } from "../messages";
import type { ChangeSummaryResult } from "../types";
import { useAnalysis } from "../useAnalysis";

type Props = {
  projectId: string;
  documentId: string;
  base: number;
  target: number;
  /** 변경 번호를 누르면 비교 결과의 해당 변경으로 이동한다. */
  onGoToChange: (changeId: number) => void;
};

const LEVEL_LABEL: Record<ChangeSummaryResult["significance"]["level"], { label: string; tone: "neutral" | "info" | "warning" | "danger" }> = {
  NONE: { label: "변경 없음", tone: "neutral" },
  LOW: { label: "영향 낮음(참고)", tone: "info" },
  MEDIUM: { label: "영향 보통(참고)", tone: "warning" },
  HIGH: { label: "영향 큼(참고)", tone: "danger" },
};

/**
 * AI 변경 요약 (SPEC-14 §6). 비교 알고리즘이 찾은 변경만 요약하며, 각 항목은 근거 변경 번호로 비교 결과와 연결된다. 비용 통제를
 * 위해 사용자가 요청할 때만 만든다.
 */
export function ChangeSummaryPanel({ projectId, documentId, base, target, onGoToChange }: Props) {
  const summary = useChangeSummary(projectId, documentId, base, target);
  return <ChangeSummaryView summary={summary} onGoToChange={onGoToChange} />;
}

/** AI 변경 요약 상태 조회·요청 (SPEC-14). 비교 화면이 탭 표시 판단에도 쓴다 (SPEC-17). */
export function useChangeSummary(projectId: string, documentId: string, base: number, target: number) {
  const load = useCallback(() => fetchChangeSummary(projectId, documentId, base, target), [projectId, documentId, base, target]);
  const submit = useCallback(() => requestChangeSummary(projectId, documentId, base, target), [projectId, documentId, base, target]);
  return useAnalysis<ChangeSummaryResult>(`${projectId}/${documentId}/${base}/${target}`, load, submit);
}

export type ChangeSummaryHandle = ReturnType<typeof useChangeSummary>;

/** 요약 표시. `plain`이면 카드 없이 탭 패널 안에 표시한다 (SPEC-17). */
export function ChangeSummaryView({ summary, onGoToChange, plain = false }: {
  summary: ChangeSummaryHandle;
  onGoToChange: (changeId: number) => void;
  plain?: boolean;
}) {
  const { state, request, requesting, requestError, reload } = summary;
  const Frame = plain ? PlainFrame : CardFrame;

  if (state.kind === "error") {
    // 카드(보조 영역)는 숨기고, 탭(사용자가 직접 연 화면)에서는 실패를 알린다.
    return plain ? (
      <Frame>
        <p role="alert" className="text-sm text-danger-700">
          AI 변경 요약을 불러오지 못했습니다. {state.message}
        </p>
        <Button size="sm" onClick={reload} className="self-start">
          다시 불러오기
        </Button>
      </Frame>
    ) : null;
  }
  if (state.kind === "loading") {
    return (
      <Frame>
        <Skeleton className="h-4 w-1/2" />
      </Frame>
    );
  }
  const analysis = state.value;
  if ((!analysis.aiEnabled && analysis.status !== "COMPLETED") || !analysis.supported) {
    return null;
  }
  const errorLine = requestError && (
    <p role="alert" className="text-sm text-danger-700">
      {requestError}
    </p>
  );
  if (analysis.status === "NONE") {
    return (
      <Frame>
        <p className="text-sm text-ink-muted">비교 결과의 변경 목록을 AI가 문장으로 요약합니다. 요청하면 변경된 텍스트가 외부 AI 서비스로 전송됩니다.</p>
        <Button size="sm" variant="primary" onClick={request} disabled={requesting} className="self-start">
          {requesting ? "요청 중..." : "AI 변경 요약 만들기"}
        </Button>
        {errorLine}
      </Frame>
    );
  }
  if (analysis.status === "PENDING" || analysis.status === "PROCESSING") {
    return (
      <Frame>
        <p role="status" className="flex items-center gap-2 text-sm text-ink-muted">
          <span className="inline-block size-3 animate-pulse rounded-full bg-brand-500" aria-hidden="true" />
          AI가 변경 내용을 요약하고 있습니다. 잠시 후 자동으로 표시됩니다.
        </p>
      </Frame>
    );
  }
  if (analysis.status === "FAILED") {
    return (
      <Frame>
        <p role="alert" className="text-sm text-danger-700">
          AI 변경 요약을 만들지 못했습니다. {analysisFailureMessage(analysis.errorCode)}
        </p>
        {analysis.retryable && analysis.aiEnabled && (
          <Button size="sm" onClick={request} disabled={requesting} className="self-start">
            {requesting ? "요청 중..." : "다시 시도"}
          </Button>
        )}
        {errorLine}
      </Frame>
    );
  }
  const result = analysis.result;
  if (!result) {
    return null;
  }
  const level = LEVEL_LABEL[result.significance.level] ?? LEVEL_LABEL.MEDIUM;
  const refs = (ids: number[]) => (
    <span className="ml-1 inline-flex flex-wrap gap-1 align-middle">
      {ids.map((id) => (
        <button key={id} type="button" onClick={() => onGoToChange(id)} className="rounded bg-subtle px-1.5 text-xs text-brand-700 hover:underline" aria-label={`변경 ${id + 1}로 이동`}>
          #{id + 1}
        </button>
      ))}
    </span>
  );
  const section = (title: string, items: { text: string; changeIds: number[] }[]) =>
    items.length > 0 && (
      <div>
        <h4 className="text-xs font-medium text-ink-muted">{title}</h4>
        <ul className="mt-1 flex list-disc flex-col gap-1 pl-5 text-sm text-ink">
          {items.map((item, index) => (
            <li key={index} className="break-words">
              {item.text}
              {refs(item.changeIds)}
            </li>
          ))}
        </ul>
      </div>
    );
  return (
    <Frame
      footer={
        <p className="text-xs text-ink-muted">
          AI가 비교 결과를 바탕으로 만든 요약입니다. 변경 위치는 비교 알고리즘이 정한 것이며, 중요도는 참고용입니다.
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
      <div className="flex flex-wrap items-center gap-2">
        <Badge tone={level.tone}>{level.label}</Badge>
        {result.significance.reason && <span className="text-xs text-ink-muted">{result.significance.reason}</span>}
      </div>
      <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink">{result.overview}</p>
      {result.keyChanges.length > 0 && (
        <div>
          <h4 className="text-xs font-medium text-ink-muted">주요 일정·수치 변경</h4>
          <ul className="mt-1 flex flex-col gap-1 text-sm">
            {result.keyChanges.map((key, index) => (
              <li key={index} className="flex flex-wrap items-center gap-1.5 break-words">
                <Badge>{FACT_TYPE_LABELS[key.type] ?? "기타"}</Badge>
                <span className="font-medium">{key.label}</span>
                <span className="text-danger-700 line-through">{key.before || "(없음)"}</span>
                <span aria-hidden="true">→</span>
                <span className="sr-only">에서</span>
                <span className="text-success-700">{key.after || "(삭제)"}</span>
                {refs(key.changeIds)}
              </li>
            ))}
          </ul>
        </div>
      )}
      {section("추가된 내용", result.added)}
      {section("삭제된 내용", result.deleted)}
      {section("수정된 내용", result.modified)}
    </Frame>
  );
}

function PlainFrame({ children, footer }: { children: React.ReactNode; footer?: React.ReactNode }) {
  return (
    <section aria-label="AI 변경 요약" className="flex flex-col gap-3">
      <p>
        <Badge tone="brand">
          <SparkleIcon className="size-3.5" /> AI 생성 · 검토 필요
        </Badge>
      </p>
      {children}
      {footer}
    </section>
  );
}

function CardFrame({ children, footer }: { children: React.ReactNode; footer?: React.ReactNode }) {
  return (
    <Card aria-labelledby="change-summary-heading" className="border-brand-500/25">
      <CardHeader
        title={
          <span className="flex items-center gap-2">
            <SparkleIcon className="size-4 text-brand-700" /> AI 변경 요약 <Badge tone="brand">AI 생성 · 검토 필요</Badge>
          </span>
        }
        titleId="change-summary-heading"
      />
      <CardBody className="flex flex-col gap-3">
        {children}
        {footer}
      </CardBody>
    </Card>
  );
}
