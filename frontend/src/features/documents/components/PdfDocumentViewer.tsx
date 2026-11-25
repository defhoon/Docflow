"use client";

import type { PDFPageProxy } from "pdfjs-dist";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/components/ui/cn";
import { IconButton } from "@/components/ui/IconButton";
import { AlertIcon, DownloadIcon, FileIcon, FitWidthIcon, MinusIcon, PlusIcon, RefreshIcon, RotateIcon } from "@/components/ui/icons";
import { Skeleton } from "@/components/ui/States";
import { useAuth } from "@/features/auth/AuthProvider";
import { PdfViewer } from "@/features/compare/components/PdfViewer";
import { ZOOM_STEPS } from "@/features/compare/pdf/geometry";
import { openPdfBytes, type PDFDocumentProxy } from "@/features/compare/pdf/pdfjs";
import { isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { FILE_MESSAGES, fileFailureMessage } from "../errors";
import { type DocumentVersionSummary, downloadVersionFile, fetchVersionPreview, saveBlob } from "../version-files";

/** 변환 대기 최대 시간 (SPEC-05 §8.3). */
export const VIEWER_MAX_WAIT_MS = 90_000;

/** 화면 이동 요청. `nonce`가 바뀔 때마다 이동한다(같은 쪽을 다시 눌러도 이동). */
export type PageRequest = { page: number; nonce: number };

type ViewerState =
  | { kind: "loading" }
  | { kind: "converting" }
  | { kind: "ready"; pdf: PDFDocumentProxy }
  | { kind: "unsupported" }
  | { kind: "failed"; message: string };

type Props = {
  projectId: string;
  documentId: string;
  version: DocumentVersionSummary;
  pageRequest?: PageRequest | null;
};

const SIDE = "doc";

/**
 * 문서 상세의 공용 PDF.js 뷰어 (SPEC-16 §3.3). 미리보기 API(PDF는 원본, Office는 변환본)로 받아 그리며, 기본은 폭 맞춤이다.
 * 버전이 바뀌면 진행 중인 요청을 취소하고 PDF.js 자원을 해제한다. PDF 전용 컨트롤은 문서를 표시할 때만 보인다.
 */
export function PdfDocumentViewer({ projectId, documentId, version, pageRequest }: Props) {
  const { refresh } = useAuth();
  const versionNumber = version.versionNumber;
  const [attempt, setAttempt] = useState(0);
  const requestKey = `${projectId}/${documentId}/${versionNumber}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: ViewerState } | null>(null);
  const state: ViewerState = result?.key === requestKey ? result.value : { kind: "loading" };
  const [downloading, setDownloading] = useState(false);
  const [downloadError, setDownloadError] = useState<string | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    let close: (() => void) | null = null;
    const set = (value: ViewerState) => !controller.signal.aborted && setResult({ key: requestKey, value });

    async function load() {
      const startedAt = Date.now();
      for (;;) {
        const preview = await fetchVersionPreview(projectId, documentId, versionNumber, controller.signal);
        if (preview.status === "ready") {
          const opened = await openPdfBytes(new Uint8Array(await preview.blob.arrayBuffer()));
          if (controller.signal.aborted) {
            opened.close();
            return;
          }
          close = opened.close;
          set({ kind: "ready", pdf: opened.pdf });
          return;
        }
        if (Date.now() - startedAt >= VIEWER_MAX_WAIT_MS) {
          set({ kind: "failed", message: FILE_MESSAGES.previewFailed });
          return;
        }
        set({ kind: "converting" });
        await wait(preview.retryAfterMs, controller.signal);
      }
    }

    load().catch((error: unknown) => {
      if (controller.signal.aborted) {
        return;
      }
      if (isUnauthenticated(error)) {
        void refresh();
      }
      if (error instanceof ApiError && error.code === "DOCUMENT_PREVIEW_UNAVAILABLE") {
        set({ kind: "unsupported" });
        return;
      }
      set({ kind: "failed", message: error instanceof ApiError ? fileFailureMessage(error) : "PDF를 표시하지 못했습니다. 원본 파일을 내려받아 확인해 주세요." });
    });

    return () => {
      controller.abort();
      close?.();
    };
  }, [projectId, documentId, versionNumber, requestKey, refresh]);

  const download = useCallback(async () => {
    setDownloading(true);
    setDownloadError(null);
    try {
      saveBlob(await downloadVersionFile(projectId, documentId, versionNumber), version.originalFilename);
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      } else {
        setDownloadError(fileFailureMessage(error));
      }
    } finally {
      setDownloading(false);
    }
  }, [projectId, documentId, versionNumber, version.originalFilename, refresh]);

  const downloadLabel = downloading ? "내려받는 중..." : `v${versionNumber} 원본 받기`;

  return (
    <section aria-label={`v${versionNumber} 문서 보기`} className="flex h-full min-h-0 flex-col bg-canvas-sunken">
      {state.kind === "ready" ? (
        <ReadyViewer
          key={requestKey}
          pdf={state.pdf}
          filename={version.originalFilename}
          pageRequest={pageRequest ?? null}
          download={download}
          downloadLabel={downloadLabel}
          downloading={downloading}
        />
      ) : (
        <>
          <div className="flex h-11 shrink-0 items-center gap-2 border-b border-line bg-surface px-3">
            <FileIcon className="size-4 shrink-0 text-ink-decor" />
            <span className="min-w-0 flex-1 truncate text-sm text-ink" title={version.originalFilename}>
              {version.originalFilename}
            </span>
            <Button size="sm" variant="ghost" onClick={() => void download()} disabled={downloading}>
              <DownloadIcon /> {downloadLabel}
            </Button>
          </div>
          <div className="flex min-h-0 flex-1 items-start justify-center overflow-auto p-6">
            {(state.kind === "loading" || state.kind === "converting") && (
              <div className="flex w-full max-w-2xl flex-col gap-3 rounded-sm bg-surface p-10 shadow-[var(--shadow-card)]" aria-busy="true">
                <p role="status" className="text-sm text-ink-muted">
                  {state.kind === "loading" ? "PDF를 불러오는 중..." : "미리보기를 준비하는 중입니다. 파일에 따라 최대 1분 정도 걸릴 수 있습니다."}
                </p>
                <Skeleton className="h-6 w-1/2" />
                <Skeleton className="h-4 w-full" />
                <Skeleton className="h-4 w-11/12" />
                <Skeleton className="h-4 w-3/4" />
                <Skeleton className="mt-4 h-4 w-full" />
                <Skeleton className="h-4 w-5/6" />
              </div>
            )}
            {state.kind === "unsupported" && (
              <Notice
                title="이 환경에서는 Office 문서 미리보기를 지원하지 않습니다."
                message="원본 파일을 내려받아 확인해 주세요. 피드백과 버전 정보는 그대로 사용할 수 있습니다."
              >
                <Button variant="primary" size="sm" onClick={() => void download()} disabled={downloading}>
                  <DownloadIcon /> {downloadLabel}
                </Button>
              </Notice>
            )}
            {state.kind === "failed" && (
              <Notice tone="danger" title="문서를 표시하지 못했습니다." message={state.message}>
                <Button size="sm" onClick={() => setAttempt((n) => n + 1)}>
                  <RefreshIcon /> 다시 시도
                </Button>
                <Button variant="primary" size="sm" onClick={() => void download()} disabled={downloading}>
                  <DownloadIcon /> {downloadLabel}
                </Button>
              </Notice>
            )}
          </div>
        </>
      )}
      {downloadError && (
        <p role="alert" className="shrink-0 border-t border-danger-600/20 bg-danger-50 px-3 py-2 text-sm text-danger-700">
          {downloadError}
        </p>
      )}
    </section>
  );
}

function Notice({ title, message, tone = "neutral", children }: { title: string; message: string; tone?: "neutral" | "danger"; children: React.ReactNode }) {
  return (
    <div
      role={tone === "danger" ? "alert" : "status"}
      className="mt-10 flex w-full max-w-md flex-col items-center gap-3 rounded-[var(--radius-card)] border border-line bg-surface px-6 py-8 text-center"
    >
      {tone === "danger" ? <AlertIcon className="size-6 text-danger-600" /> : <FileIcon className="size-6 text-ink-decor" />}
      <p className="text-sm font-medium text-ink">{title}</p>
      <p className="text-sm text-ink-muted">{message}</p>
      <div className="mt-1 flex flex-wrap justify-center gap-2">{children}</div>
    </div>
  );
}

/** 문서를 표시할 때의 도구 막대와 페이지 영역. 버전마다 새로 마운트되어 배율·회전·쪽이 초기화된다. */
function ReadyViewer({ pdf, filename, pageRequest, download, downloadLabel, downloading }: {
  pdf: PDFDocumentProxy;
  filename: string;
  pageRequest: PageRequest | null;
  download: () => Promise<void>;
  downloadLabel: string;
  downloading: boolean;
}) {
  const scrollRef = useRef<HTMLDivElement>(null);
  const [pages, setPages] = useState<PDFPageProxy[]>([]);
  // null이면 폭 맞춤(기본). 숫자면 ZOOM_STEPS 색인.
  const [zoom, setZoom] = useState<number | null>(null);
  const [rotation, setRotation] = useState(0);
  const [fitScale, setFitScale] = useState(1);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageInput, setPageInput] = useState("1");
  const total = pdf.numPages;
  const scale = zoom === null ? fitScale : ZOOM_STEPS[zoom];

  // 폭 맞춤: 뷰어 안쪽 폭에 가장 넓은 페이지(회전 반영)가 들어가는 배율. 화면·패널 크기가 바뀌면 다시 계산한다.
  const widest = useMemo(
    () => Math.max(1, ...pages.map((page) => page.getViewport({ scale: 1, rotation: (page.rotate + rotation) % 360 }).width)),
    [pages, rotation],
  );
  useEffect(() => {
    const element = scrollRef.current;
    if (!element) {
      return;
    }
    const measure = () => setFitScale(Math.min(2, Math.max(0.3, (element.clientWidth - 48) / widest)));
    measure();
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(element);
    return () => observer.disconnect();
  }, [widest]);

  const goToPage = useCallback((page: number) => {
    const target = Math.min(Math.max(1, page), total);
    const element = scrollRef.current?.querySelector<HTMLElement>(`#pdf-${SIDE}-page-${target}`);
    element?.scrollIntoView({ block: "start", behavior: "smooth" });
    setCurrentPage(target);
    setPageInput(String(target));
  }, [total]);

  // AI 요약의 쪽 번호 등 외부 요청으로 이동한다.
  useEffect(() => {
    if (!pageRequest || pages.length === 0) {
      return;
    }
    // 페이지가 그려진 다음 프레임에 이동한다.
    const frame = requestAnimationFrame(() => goToPage(pageRequest.page));
    return () => cancelAnimationFrame(frame);
  }, [pageRequest, pages.length, goToPage]);

  // 스크롤 위치로 현재 쪽을 정한다(뷰어 위쪽 1/3 지점이 걸친 페이지).
  const onScroll = useCallback(() => {
    const element = scrollRef.current;
    if (!element) {
      return;
    }
    const probe = element.getBoundingClientRect().top + element.clientHeight / 3;
    let page = 1;
    for (const node of element.querySelectorAll<HTMLElement>("[data-page]")) {
      if (node.getBoundingClientRect().top <= probe) {
        page = Number(node.dataset.page);
      }
    }
    setCurrentPage(page);
    setPageInput(String(page));
  }, []);

  const zoomOut = () => setZoom((z) => {
    const index = z ?? ZOOM_STEPS.findLastIndex((step) => step < fitScale);
    return Math.max(0, index === z ? index - 1 : index);
  });
  const zoomIn = () => setZoom((z) => {
    const index = z ?? ZOOM_STEPS.findIndex((step) => step > fitScale);
    return Math.min(ZOOM_STEPS.length - 1, index === z ? index + 1 : index < 0 ? ZOOM_STEPS.length - 1 : index);
  });

  return (
    <>
      <div role="toolbar" aria-label="문서 보기 도구" className="flex h-11 shrink-0 items-center gap-1 overflow-x-auto border-b border-line bg-surface px-2">
        <span className="hidden min-w-0 max-w-[16rem] truncate px-1 text-sm text-ink xl:block" title={filename}>
          {filename}
        </span>
        <form
          className="flex shrink-0 items-center gap-1 text-sm text-ink-muted"
          onSubmit={(event) => {
            event.preventDefault();
            const value = Number(pageInput);
            if (Number.isFinite(value)) {
              goToPage(value);
            }
          }}
        >
          <label htmlFor="viewer-page" className="sr-only">
            쪽 번호 (전체 {total}쪽)
          </label>
          <input
            id="viewer-page"
            inputMode="numeric"
            value={pageInput}
            onChange={(event) => setPageInput(event.target.value.replace(/[^0-9]/g, ""))}
            onBlur={() => setPageInput(String(currentPage))}
            className="h-7 w-10 rounded-md border border-line-strong bg-surface text-center text-sm text-ink tabular-nums"
          />
          <span aria-hidden="true" className="tabular-nums">
            / {total}
          </span>
        </form>
        <span aria-hidden="true" className="mx-1 h-5 w-px shrink-0 bg-line" />
        <IconButton label="축소" icon={<MinusIcon />} onClick={zoomOut} disabled={zoom === 0} />
        <span className="w-11 shrink-0 text-center text-sm text-ink tabular-nums" aria-live="polite" aria-label={`배율 ${Math.round(scale * 100)}%`}>
          {Math.round(scale * 100)}%
        </span>
        <IconButton label="확대" icon={<PlusIcon />} onClick={zoomIn} disabled={zoom === ZOOM_STEPS.length - 1} />
        <IconButton label="폭 맞춤" icon={<FitWidthIcon />} pressed={zoom === null} onClick={() => setZoom(null)} />
        <IconButton label={`회전 (현재 ${rotation}도)`} icon={<RotateIcon />} onClick={() => setRotation((r) => (r + 90) % 360)} />
        <span className="flex-1" />
        <Button size="sm" variant="ghost" onClick={() => void download()} disabled={downloading} className="shrink-0">
          <DownloadIcon /> {downloadLabel}
        </Button>
      </div>
      <div ref={scrollRef} data-pdf-viewport onScroll={onScroll} className={cn("min-h-0 flex-1 overflow-auto px-6 py-6")}>
        <PdfViewer
          pdf={pdf}
          side={SIDE}
          highlights={[]}
          showHighlights={false}
          scale={scale}
          rotation={rotation}
          activeChangeId={null}
          onSelectChange={() => undefined}
          onPagesLoaded={setPages}
        />
      </div>
    </>
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
