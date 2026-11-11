"use client";

import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fileFailureMessage } from "../errors";
import { formatFileSize } from "../format";
import { type DocumentVersionSummary, downloadVersionFile, fetchDocumentVersions, saveBlob } from "../version-files";

type Props = {
  projectId: string;
  documentId: string;
  /** 값이 바뀌면 목록을 다시 조회한다(새 버전 등록·상태 재조회, SPEC-05 §9.2). */
  reloadKey: number;
  selectedVersion: number | null;
  onPreview: (version: DocumentVersionSummary) => void;
};

type ListState =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "success"; versions: DocumentVersionSummary[] };

/** 버전 히스토리 (SPEC-05 §9.1): 최신 먼저, 버전별 미리보기·원본 다운로드. */
export function DocumentVersionHistory({ projectId, documentId, reloadKey, selectedVersion, onPreview }: Props) {
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
    <section aria-labelledby="document-versions-heading" className="flex flex-col gap-2 border-t border-zinc-200 pt-4">
      <h3 id="document-versions-heading" className="text-sm font-semibold text-zinc-500">
        버전 히스토리
      </h3>
      {state.kind === "loading" && (
        <p role="status" className="text-sm text-zinc-500">
          버전 목록을 불러오는 중...
        </p>
      )}
      {state.kind === "error" && (
        // 사용자 동작의 결과가 아닌 배경 조회 실패이므로 정중한 알림(status)으로 알린다. 다운로드 실패는 alert.
        <div role="status" className="flex flex-col items-start gap-2 text-sm text-red-700">
          <p>버전 목록을 불러오지 못했습니다. {state.message}</p>
          <button
            type="button"
            onClick={() => setAttempt((current) => current + 1)}
            className="rounded border border-zinc-300 px-3 py-1 text-zinc-800"
          >
            목록 다시 불러오기
          </button>
        </div>
      )}
      {state.kind === "success" && (
        <ul className="flex flex-col divide-y divide-zinc-100 text-sm">
          {state.versions.map((version) => (
            <li
              key={version.versionNumber}
              aria-current={selectedVersion === version.versionNumber ? "true" : undefined}
              className="flex flex-wrap items-center gap-x-3 gap-y-1 py-2"
            >
              <span className="font-medium">
                v{version.versionNumber}
                {version.latest && <span className="ml-1 rounded bg-zinc-100 px-1 text-xs text-zinc-600">최신</span>}
              </span>
              <span className="min-w-0 flex-1 break-all">{version.originalFilename}</span>
              <span className="text-zinc-500">
                {version.uploadedBy.displayName} ·{" "}
                <time dateTime={version.uploadedAt}>{formatDateTime(version.uploadedAt)}</time> ·{" "}
                <span title={`${version.sizeBytes.toLocaleString("ko-KR")} 바이트`}>
                  {formatFileSize(version.sizeBytes)}
                </span>
              </span>
              <span className="flex gap-2">
                <button
                  type="button"
                  onClick={() => onPreview(version)}
                  aria-label={`v${version.versionNumber} 미리보기`}
                  className="rounded border border-zinc-300 px-2 py-0.5 text-zinc-800"
                >
                  미리보기
                </button>
                <button
                  type="button"
                  onClick={() => void download(version)}
                  disabled={downloading === version.versionNumber}
                  aria-label={`v${version.versionNumber} 다운로드`}
                  className="rounded border border-zinc-300 px-2 py-0.5 text-zinc-800 disabled:opacity-50"
                >
                  {downloading === version.versionNumber ? "다운로드 중..." : "다운로드"}
                </button>
              </span>
            </li>
          ))}
        </ul>
      )}
      {downloadError && (
        <p role="alert" className="text-sm text-red-700">
          {downloadError}
        </p>
      )}
    </section>
  );
}
