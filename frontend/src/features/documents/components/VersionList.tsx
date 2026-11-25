"use client";

import { cn } from "@/components/ui/cn";
import { ChatIcon, CheckCircleIcon } from "@/components/ui/icons";
import { Badge } from "@/components/ui/StatusBadge";
import { formatDateTime } from "@/features/projects/format";

import { versionNoteEmptyText } from "../version-note";
import type { DocumentVersionSummary } from "../version-files";

/**
 * 워크스페이스 왼쪽 버전 목록 (SPEC-16). 최신 먼저. 항목을 누르면 그 버전이 뷰어·피드백·AI 요약·정보의 기준이 된다.
 * 최신·최종본·피드백 수·작성 중 초안을 표시한다. 버전 정보는 텍스트로만 표시한다.
 */
export function VersionList({ versions, selected, finalVersionNumber, drafts, onSelect }: {
  versions: DocumentVersionSummary[];
  selected: number;
  finalVersionNumber: number | null;
  /** 작성 중인 피드백이 있는 버전 번호 */
  drafts: Set<number>;
  onSelect: (versionNumber: number) => void;
}) {
  return (
    <ul aria-label="버전 목록" className="flex flex-col gap-1 p-2">
      {versions.map((version) => {
        const isSelected = version.versionNumber === selected;
        const isFinal = version.versionNumber === finalVersionNumber;
        return (
          <li key={version.versionNumber}>
            <button
              type="button"
              onClick={() => onSelect(version.versionNumber)}
              aria-current={isSelected ? "true" : undefined}
              aria-label={`v${version.versionNumber}${version.latest ? " 최신" : ""}${isFinal ? " 최종본" : ""} · ${version.originalFilename}`}
              className={cn(
                "relative flex w-full flex-col gap-0.5 rounded-[var(--radius-control)] border px-3 py-2.5 text-left transition-colors",
                isSelected ? "border-brand-600/30 bg-brand-50/70" : "border-transparent hover:bg-subtle",
              )}
            >
              {isSelected && <span aria-hidden="true" className="absolute top-2 bottom-2 left-0 w-0.5 rounded-full bg-brand-600" />}
              <span className="flex items-center gap-1.5 text-sm">
                <span className="font-semibold text-ink">v{version.versionNumber}</span>
                {version.latest && <Badge>최신</Badge>}
                {isFinal && (
                  <Badge tone="success">
                    <CheckCircleIcon className="size-3" /> 최종본
                  </Badge>
                )}
                <span className="ml-auto flex items-center gap-2 text-xs text-ink-muted">
                  {drafts.has(version.versionNumber) && <span className="font-medium text-warning-700">작성 중</span>}
                  {version.feedbackCount > 0 && (
                    <span className="flex items-center gap-0.5" title={`피드백 ${version.feedbackCount}개`}>
                      <ChatIcon className="size-3.5" />
                      <span className="sr-only">피드백</span>
                      {version.feedbackCount}
                    </span>
                  )}
                </span>
              </span>
              <span className="truncate text-[13px] text-ink" title={version.originalFilename}>
                {version.originalFilename}
              </span>
              <span className="text-xs text-ink-muted">
                {version.uploadedBy.displayName} · <time dateTime={version.uploadedAt}>{formatDateTime(version.uploadedAt)}</time>
              </span>
              {version.versionNote ? (
                <span className="mt-0.5 line-clamp-2 text-xs break-words text-ink-muted" title={version.versionNote}>
                  {version.versionNote}
                </span>
              ) : (
                <span className="mt-0.5 text-xs text-ink-decor">{versionNoteEmptyText(version.versionNumber)}</span>
              )}
            </button>
          </li>
        );
      })}
    </ul>
  );
}
