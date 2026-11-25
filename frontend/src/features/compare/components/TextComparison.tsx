"use client";

import { cn } from "@/components/ui/cn";

import type { Block, Segment } from "../types";

type Props = {
  blocks: Block[];
  baseLabel: string;
  targetLabel: string;
  activeChangeId: number | null;
  onSelectChange: (id: number) => void;
};

const SEGMENT_STYLE: Record<Segment["op"], string> = {
  EQUAL: "",
  INSERT: "rounded-sm bg-success-50 px-0.5 text-success-700 underline decoration-success-600/60 decoration-2 underline-offset-2",
  DELETE: "rounded-sm bg-danger-50 px-0.5 text-danger-700 line-through decoration-danger-600/70",
  MODIFY: "rounded-sm bg-warning-50 px-0.5 text-warning-700 underline decoration-dotted decoration-warning-700/70 underline-offset-2",
};

/** 화면 읽기 프로그램용 변경 종류 안내(색 외 구분). */
const SEGMENT_SR: Record<Segment["op"], { base: string; target: string }> = {
  EQUAL: { base: "", target: "" },
  INSERT: { base: "추가: ", target: "추가: " },
  DELETE: { base: "삭제: ", target: "삭제: " },
  MODIFY: { base: "수정 전: ", target: "수정 후: " },
};

const SEGMENT_MARK: Record<Segment["op"], string> = { EQUAL: "", INSERT: "+", DELETE: "−", MODIFY: "~" };

/**
 * 텍스트 나란히 보기 (SPEC-12 §7). 추가는 녹색·밑줄·＋, 삭제는 빨간색·취소선·−, 수정은 노란색·점선·~로 색 외에도 구분한다.
 * 모든 텍스트는 그대로 표시한다(HTML 해석 없음).
 */
export function TextComparison({ blocks, baseLabel, targetLabel, activeChangeId, onSelectChange }: Props) {
  // 변경 구간 순서(1부터). 원본 줄 번호가 아니다 (SPEC-17 §3.5).
  const ordinals = new Map<number, number>();
  blocks.forEach((block, index) => block.type === "CHANGED" && ordinals.set(index, ordinals.size + 1));
  return (
    <div className="flex flex-col gap-3">
      <p className="text-xs text-ink-muted">
        본문을 문단 단위로 비교합니다. 왼쪽의 번호는 원본 PDF의 줄 번호가 아니라 <strong className="font-medium">변경 구간 순서</strong>입니다.
      </p>
      <div className="hidden grid-cols-[3rem_1fr_1fr] gap-4 text-xs font-medium text-ink-muted md:grid">
        <span aria-hidden="true" />
        <span>{baseLabel}</span>
        <span>{targetLabel}</span>
      </div>
      {blocks.map((block, index) =>
        block.type === "EQUAL" ? (
          <p key={index} className="rounded-[var(--radius-control)] border border-dashed border-line bg-subtle/50 px-3 py-2 text-xs text-ink-muted md:ml-16">
            변경 없는 문단 {block.paragraphCount}개
            {block.firstText && <span className="mt-0.5 block truncate" title={block.firstText}>“{block.firstText}”</span>}
          </p>
        ) : (
          <div key={index} className="grid grid-cols-1 gap-2 md:grid-cols-[3rem_1fr_1fr] md:gap-4">
            <span
              className="pt-2 text-xs font-medium text-ink-muted tabular-nums md:text-right"
              title="원본 줄 번호가 아니라 변경 구간 순서입니다"
            >
              구간 {ordinals.get(index)}
            </span>
            <Column label={baseLabel} side="base" segments={block.base} activeChangeId={activeChangeId} onSelectChange={onSelectChange} />
            <Column label={targetLabel} side="target" segments={block.target} activeChangeId={activeChangeId} onSelectChange={onSelectChange} />
          </div>
        ),
      )}
    </div>
  );
}

function Column({ label, side, segments, activeChangeId, onSelectChange }: {
  label: string;
  side: "base" | "target";
  segments: Segment[];
  activeChangeId: number | null;
  onSelectChange: (id: number) => void;
}) {
  return (
    <div className="min-w-0 rounded-[var(--radius-control)] border border-line bg-surface px-3 py-2">
      <span className="mb-1 block text-xs font-medium text-ink-muted md:hidden">{label}</span>
      {segments.length === 0 ? (
        <p className="text-sm text-ink-muted">{side === "base" ? "(이전 버전에 없음)" : "(이후 버전에 없음)"}</p>
      ) : (
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink">
          {segments.map((segment, index) =>
            segment.changeId === null ? (
              <span key={index}>{segment.text}</span>
            ) : (
              <button
                key={index}
                type="button"
                id={`change-${segment.changeId}-${side}`}
                data-change-id={segment.changeId}
                onClick={() => onSelectChange(segment.changeId as number)}
                className={cn(
                  "inline cursor-pointer text-left align-baseline scroll-mt-28",
                  SEGMENT_STYLE[segment.op],
                  activeChangeId === segment.changeId && "outline-2 outline-offset-1 outline-brand-600",
                )}
              >
                <span className="sr-only">{side === "base" ? SEGMENT_SR[segment.op].base : SEGMENT_SR[segment.op].target}</span>
                <span aria-hidden="true" className="mr-0.5 text-[0.7em] font-bold">
                  {SEGMENT_MARK[segment.op]}
                </span>
                {segment.text}
              </button>
            ),
          )}
        </p>
      )}
    </div>
  );
}
