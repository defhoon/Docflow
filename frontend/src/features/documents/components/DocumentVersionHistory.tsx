"use client";

import { useCallback, useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/components/ui/cn";
import { DownloadIcon } from "@/components/ui/icons";
import { Badge } from "@/components/ui/StatusBadge";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fileFailureMessage } from "../errors";
import { formatFileSize } from "../format";
import { type DocumentVersionSummary, downloadVersionFile, fetchDocumentVersions, saveBlob } from "../version-files";
import { versionNoteEmptyText, versionNoteLabel } from "../version-note";

type Props = {
  projectId: string;
  documentId: string;
  /** 값이 바뀌면 목록을 다시 조회한다(새 버전 등록·상태 재조회, SPEC-05 §9.2). */
  reloadKey: number;
  selectedVersion: number | null;
  onPreview: (version: DocumentVersionSummary) => void;
  /** 버전 선택(피드백 대상, #28). 없으면 선택 버튼을 표시하지 않는다. */
  onSelect?: (version: DocumentVersionSummary) => void;
  /** 목록을 불러올 때마다 호출한다(선택 가능한 버전 목록 공유). */
  onLoaded?: (versions: DocumentVersionSummary[]) => void;
  /** 최종 확정 버전 번호 (SPEC-07). 해당 행에 '최종' 표시. */
  finalVersionNumber?: number | null;
  /** 제목을 숨긴다(카드 머리글이 대신할 때). */
  hideHeading?: boolean;
  /** 좁은 측면 패널용: 선택하지 않은 버전의 버전 정보를 두 줄로 줄인다. */
  compact?: boolean;
};

type ListState =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "success"; versions: DocumentVersionSummary[] };

/**
 * 버전 히스토리 (SPEC-05 §9.1): 최신 먼저, 버전별 미리보기·원본 다운로드, 버전 정보(SPEC-09: v1 초기 버전 내용, v2 이상
 * 변경사항). 버전 정보는 텍스트로만 표시하며 줄바꿈을 유지한다.
 */
export function DocumentVersionHistory({
  projectId,
  documentId,
  reloadKey,
  selectedVersion,
  onPreview,
  onSelect,
  onLoaded,
  finalVersionNumber = null,
  hideHeading = false,
  compact = false,
}: Props) {
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const requestKey = `${projectId}/${documentId}#${reloadKey}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: ListState } | null>(null);
  const [downloading, setDownloading] = useState<number | null>(null);
  const [downloadError, setDownloadError] = useState<string | null>(null);
  // 새로 조회하는 동안에는 이전 목록을 유지해 화면이 깜박이지 않게 한다.
  const state: ListState = result?.key === requestKey ? result.value : (result?.value ?? { kind: "loading" });

  useEffect(() => {
    let active = true;
    fetchDocumentVersions(projectId, documentId).then(
      (versions) => {
        if (active) {
          setResult({ key: requestKey, value: { kind: "success", versions } });
          onLoaded?.(versions);
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({ key: requestKey, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
    // onLoaded는 알림용 콜백이라 다시 조회할 이유가 아니다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [projectId, documentId, requestKey, refresh]);

  const download = useCallback(
    async (version: DocumentVersionSummary) => {
      setDownloading(version.versionNumber);
      setDownloadError(null);
      try {
        const blob = await downloadVersionFile(projectId, documentId, version.versionNumber);
        saveBlob(blob, version.originalFilename);
      } catch (error) {
        if (isUnauthenticated(error)) {
          void refresh();
        } else {
          setDownloadError(fileFailureMessage(error));
        }
      } finally {
        setDownloading(null);
      }
    },
    [projectId, documentId, refresh],
  );

  return (
    <section aria-labelledby="document-versions-heading" className="flex flex-col gap-2">
      <h3 id="document-versions-heading" className={hideHeading ? "sr-only" : "text-sm font-semibold text-ink-muted"}>
        버전 히스토리
      </h3>
      {state.kind === "loading" && (
        <p role="status" className="text-sm text-ink-muted">
          버전 목록을 불러오는 중...
        </p>
      )}
      {state.kind === "error" && (
        // 사용자 동작의 결과가 아닌 배경 조회 실패이므로 정중한 알림(status)으로 알린다. 다운로드 실패는 alert.
        <div role="status" className="flex flex-col items-start gap-2 text-sm text-danger-700">
          <p>버전 목록을 불러오지 못했습니다. {state.message}</p>
          <Button size="sm" onClick={() => setAttempt((current) => current + 1)}>
            목록 다시 불러오기
          </Button>
        </div>
      )}
      {state.kind === "success" && (
        <ul className={cn("flex flex-col text-sm", compact ? "gap-1.5" : "gap-2")}>
          {state.versions.map((version) => {
            const isSelected = selectedVersion === version.versionNumber;
            const isFinal = finalVersionNumber === version.versionNumber;
            const heading = (
              <>
                <span className="flex flex-wrap items-center gap-1.5">
                  <span className="font-semibold text-ink">v{version.versionNumber}</span>
                  {version.latest && <Badge>최신</Badge>}
                  {isFinal && <Badge tone="success">최종</Badge>}
                </span>
                <span className="w-full truncate text-ink" title={version.originalFilename}>
                  {version.originalFilename}
                </span>
                <span className="text-xs text-ink-muted">
                  {version.uploadedBy.displayName} · <time dateTime={version.uploadedAt}>{formatDateTime(version.uploadedAt)}</time> ·{" "}
                  <span title={`${version.sizeBytes.toLocaleString("ko-KR")} 바이트`}>{formatFileSize(version.sizeBytes)}</span>
                </span>
              </>
            );
            return (
              <li
                key={version.versionNumber}
                aria-current={isSelected ? "true" : undefined}
                className={cn(
                  "flex flex-col gap-2 rounded-[var(--radius-control)] border px-3 py-2.5 transition-colors",
                  isSelected ? "border-brand-600/40 bg-brand-50/60" : "border-transparent hover:bg-subtle/70",
                )}
              >
                {onSelect ? (
                  <button
                    type="button"
                    onClick={() => onSelect(version)}
                    aria-pressed={isSelected}
                    aria-label={`v${version.versionNumber} 선택`}
                    className="flex min-w-0 flex-col items-start gap-0.5 rounded-md text-left"
                  >
                    {heading}
                  </button>
                ) : (
                  <div className="flex min-w-0 flex-col items-start gap-0.5">{heading}</div>
                )}
                {/* 측면 패널(compact)에서는 선택한 버전의 정보를 본문 카드가 보여 주므로 반복하지 않는다. */}
                {!(compact && isSelected) && (
                <div className="text-xs">
                  <span className="font-medium text-ink-muted">{versionNoteLabel(version.versionNumber)}</span>
                  {version.versionNote ? (
                    <p
                      className={cn("mt-0.5 break-words text-sm text-ink", compact ? "line-clamp-1" : "whitespace-pre-wrap")}
                      title={compact ? version.versionNote : undefined}
                    >
                      {version.versionNote}
                    </p>
                  ) : (
                    <p className="mt-0.5 text-ink-muted">{versionNoteEmptyText(version.versionNumber)}</p>
                  )}
                </div>
                )}
                <span className="flex gap-2">
                  <Button
                    size="sm"
                    onClick={() => onPreview(version)}
                    aria-label={`v${version.versionNumber} 미리보기`}
                  >
                    미리보기
                  </Button>
                  <Button
                    size="sm"
                    onClick={() => void download(version)}
                    disabled={downloading === version.versionNumber}
                    aria-label={`v${version.versionNumber} 다운로드`}
                  >
                    <DownloadIcon className="size-3.5" />
                    {downloading === version.versionNumber ? "다운로드 중..." : "다운로드"}
                  </Button>
                </span>
              </li>
            );
          })}
        </ul>
      )}
      {downloadError && (
        <p role="alert" className="text-sm text-danger-700">
          {downloadError}
        </p>
      )}
    </section>
  );
}
