"use client";

import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { isUnauthenticated } from "@/features/projects/errors";

import { FILE_MESSAGES, fileFailureMessage } from "../errors";
import { type DocumentVersionSummary, downloadVersionFile, fetchVersionPreview, saveBlob } from "../version-files";

/** 변환 대기 최대 시간 (SPEC-05 §8.3). */
export const PREVIEW_MAX_WAIT_MS = 90_000;

/**
 * 미리보기 iframe의 sandbox 속성. 브라우저 내장 PDF 뷰어가 동작하는 가장 제한적인 값을 실제 브라우저 E2E로 정한다
 * (SPEC-05 §7.4). `null`이면 속성을 두지 않는다.
 */
export const PREVIEW_IFRAME_SANDBOX: string | null = null;

/**
 * 내장 PDF 뷰어 표시 옵션: 썸네일 패널을 닫고 폭에 맞춘다. 문서 상세의 좁은 영역(약 620px)에서 Chromium은 패널을 열어 두면
 * 본문 페이지를 그리지 못하는 것을 실제 브라우저 E2E로 확인했다(SPEC-05 §18). 지원하지 않는 브라우저는 무시한다.
 */
export const PREVIEW_VIEWER_FRAGMENT = "#navpanes=0&view=FitH";

type Props = {
  projectId: string;
  documentId: string;
  version: DocumentVersionSummary;
  onClose: () => void;
};

type PreviewState =
  | { kind: "loading" }
  | { kind: "converting" }
  | { kind: "ready"; url: string }
  | { kind: "failed"; message: string };

/**
 * 버전 미리보기 (SPEC-05 §9.1·§9.3). PDF Blob을 같은 화면의 iframe에 표시한다. 변환 중이면 `Retry-After` 간격으로 다시
 * 요청하고, 다른 버전 선택·닫기·화면 숨김·언마운트 시 요청을 중단하고 Blob URL을 해제한다.
 */
export function DocumentVersionPreview({ projectId, documentId, version, onClose }: Props) {
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [downloading, setDownloading] = useState(false);
  const versionNumber = version.versionNumber;
  // 요청마다 키를 두어, 다른 버전·다시 시도로 바뀌면 이전 결과 대신 불러오는 중으로 보이게 한다.
  const requestKey = `${projectId}/${documentId}/${versionNumber}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: PreviewState } | null>(null);
  const state: PreviewState = result?.key === requestKey ? result.value : { kind: "loading" };

  useEffect(() => {
    const controller = new AbortController();
    let objectUrl: string | null = null;
    const setState = (value: PreviewState) => setResult({ key: requestKey, value });

    async function load() {
      const startedAt = Date.now();
      for (;;) {
        const result = await fetchVersionPreview(projectId, documentId, versionNumber, controller.signal);
        if (result.status === "ready") {
          objectUrl = URL.createObjectURL(result.blob);
          setState({ kind: "ready", url: objectUrl });
          return;
        }
        if (Date.now() - startedAt >= PREVIEW_MAX_WAIT_MS) {
          setState({ kind: "failed", message: FILE_MESSAGES.previewFailed });
          return;
        }
        setState({ kind: "converting" });
        await wait(result.retryAfterMs, controller.signal);
      }
    }

    load().catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      if (isUnauthenticated(error)) {
        void refresh();
      }
      setState({ kind: "failed", message: fileFailureMessage(error) });
    });

    return () => {
      controller.abort();
      if (objectUrl) {
        URL.revokeObjectURL(objectUrl);
      }
    };
  }, [projectId, documentId, versionNumber, requestKey, refresh]);

  const download = useCallback(async () => {
    setDownloading(true);
    try {
      saveBlob(await downloadVersionFile(projectId, documentId, versionNumber), version.originalFilename);
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      } else {
        setResult({ key: requestKey, value: { kind: "failed", message: fileFailureMessage(error) } });
      }
    } finally {
      setDownloading(false);
    }
  }, [projectId, documentId, versionNumber, version.originalFilename, requestKey, refresh]);

  return (
    <section aria-labelledby="document-preview-heading" className="flex flex-col gap-2 border-t border-line pt-4">
      <div className="flex items-center justify-between gap-2">
        <h3 id="document-preview-heading" className="text-sm font-semibold text-ink-muted">
          미리보기 · v{versionNumber}
        </h3>
        <button type="button" onClick={onClose} className="inline-flex h-8 items-center justify-center rounded-md border border-line-strong bg-surface px-3 text-sm text-ink hover:bg-subtle disabled:opacity-50">
          미리보기 닫기
        </button>
      </div>
      {state.kind === "loading" && (
        <p role="status" className="text-sm text-ink-muted">
          미리보기를 불러오는 중...
        </p>
      )}
      {state.kind === "converting" && (
        <p role="status" className="text-sm text-ink-muted">
          미리보기를 준비하는 중입니다. 파일에 따라 최대 1분 정도 걸릴 수 있습니다.
        </p>
      )}
      {state.kind === "ready" && (
        <>
          <p className="text-xs text-ink-muted">미리보기는 참고용이며 원본 파일이 기준입니다.</p>
          <iframe
            title={`v${versionNumber} 미리보기`}
            src={`${state.url}${PREVIEW_VIEWER_FRAGMENT}`}
            {...(PREVIEW_IFRAME_SANDBOX === null ? {} : { sandbox: PREVIEW_IFRAME_SANDBOX })}
            className="h-[80vh] w-full rounded border border-line"
          />
        </>
      )}
      {state.kind === "failed" && (
        <div role="alert" className="flex flex-col items-start gap-2 text-sm">
          <p className="text-danger-700">{state.message}</p>
          <div className="flex gap-2">
            <button
              type="button"
              onClick={() => void download()}
              disabled={downloading}
              className="inline-flex h-8 items-center justify-center rounded-md bg-brand-600 px-3 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
            >
              {downloading ? "다운로드 중..." : "원본 다운로드"}
            </button>
            <button
              type="button"
              onClick={() => setAttempt((current) => current + 1)}
              className="inline-flex h-8 items-center justify-center rounded-md border border-line-strong bg-surface px-3 text-sm text-ink hover:bg-subtle disabled:opacity-50"
            >
              미리보기 다시 시도
            </button>
          </div>
        </div>
      )}
    </section>
  );
}

function wait(ms: number, signal: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal.aborted) {
      reject(new DOMException("Aborted", "AbortError"));
      return;
    }
    const timer = setTimeout(() => {
      signal.removeEventListener("abort", onAbort);
      resolve();
    }, ms);
    function onAbort() {
      clearTimeout(timer);
      reject(new DOMException("Aborted", "AbortError"));
    }
    signal.addEventListener("abort", onAbort, { once: true });
  });
}
