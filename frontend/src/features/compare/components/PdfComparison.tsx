"use client";

import type { PDFPageProxy } from "pdfjs-dist";
import { type CSSProperties, type ReactNode, useCallback, useEffect, useMemo, useRef, useState } from "react";

import { cn } from "@/components/ui/cn";
import { IconButton } from "@/components/ui/IconButton";
import { FitWidthIcon, HighlightIcon, InfoIcon, LinkScrollIcon, MinusIcon, PlusIcon, RotateIcon } from "@/components/ui/icons";
import { PaneResizer } from "@/components/ui/PaneResizer";
import { ErrorState, Skeleton } from "@/components/ui/States";

import { highlightsFor, unlocatedCount, ZOOM_STEPS } from "../pdf/geometry";
import { openPdf, type OpenedPdf, type PDFDocumentProxy } from "../pdf/pdfjs";
import { anchorOf, centeredScrollTop, readPages, scrollTopFor } from "../pdf/sync";
import type { Change, CompareResponse } from "../types";
import { PdfViewer } from "./PdfViewer";

type Side = "base" | "target";

/** 변경 위치로 이동 요청. 같은 변경을 다시 눌러도 이동하도록 `nonce`를 둔다. */
export type NavRequest = { changeId: number; nonce: number };

type Props = {
  projectId: string;
  documentId: string;
  data: CompareResponse;
  activeChangeId: number | null;
  onSelectChange: (id: number) => void;
  navRequest?: NavRequest | null;
  /** 좁은 화면에서 보여 줄 쪽(한 번에 한 문서). 데스크톱에서는 무시한다. */
  mobileSide?: Side;
};

function downloadUrl(projectId: string, documentId: string, versionNumber: number): string {
  return `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/versions/${versionNumber}/download`;
}

type Loaded = { key: string; base: PDFDocumentProxy; target: PDFDocumentProxy } | { key: string; error: true } | null;

/**
 * 원본 PDF 위 변경 강조 (SPEC-13 §5, SPEC-17). 이전 버전에는 삭제·수정 전, 이후 버전에는 추가·수정 후를 표시한다.
 * 확대·축소·폭 맞춤·회전·강조 전환·동기 스크롤(기본 켜짐)을 지원하고, 변경을 고르면 양쪽에서 해당 위치를 가운데로 옮긴다.
 * 좌표는 매 렌더링마다 PDF.js 뷰포트로 다시 계산하므로 배율·회전·패널 크기가 바뀌어도 강조 위치가 맞는다.
 */
export function PdfComparison({ projectId, documentId, data, activeChangeId, onSelectChange, navRequest = null, mobileSide = "target" }: Props) {
  const [loaded, setLoaded] = useState<Loaded>(null);
  // null이면 폭 맞춤(기본). 숫자면 ZOOM_STEPS 색인.
  const [zoom, setZoom] = useState<number | null>(null);
  const [fitScale, setFitScale] = useState(1);
  const [rotation, setRotation] = useState(0);
  const [showHighlights, setShowHighlights] = useState(true);
  const [sync, setSync] = useState(true);
  const [pagesReady, setPagesReady] = useState({ base: false, target: false });
  const key = `${projectId}/${documentId}/${data.base.versionNumber}/${data.target.versionNumber}`;
  const gridRef = useRef<HTMLDivElement>(null);
  const baseScrollRef = useRef<HTMLDivElement>(null);
  const targetScrollRef = useRef<HTMLDivElement>(null);
  // 코드가 옮긴 스크롤은 동기화 대상에서 제외한다(서로 되먹임 방지).
  const suppress = useRef({ base: 0, target: 0 });
  const [baseWidth, setBaseWidth] = useState<number | null>(null);
  const [gridWidth, setGridWidth] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    const opened: OpenedPdf[] = [];
    Promise.all([
      openPdf(downloadUrl(projectId, documentId, data.base.versionNumber), controller.signal),
      openPdf(downloadUrl(projectId, documentId, data.target.versionNumber), controller.signal),
    ]).then(
      ([base, target]) => {
        opened.push(base, target);
        if (!controller.signal.aborted) {
          setLoaded({ key, base: base.pdf, target: target.pdf });
        } else {
          opened.forEach((item) => item.close());
        }
      },
      () => !controller.signal.aborted && setLoaded({ key, error: true }),
    );
    return () => {
      controller.abort();
      opened.forEach((item) => item.close());
    };
  }, [projectId, documentId, data.base.versionNumber, data.target.versionNumber, key]);

  const changes = data.result.changes;
  const baseHighlights = useMemo(() => highlightsFor(changes, "base"), [changes]);
  const targetHighlights = useMemo(() => highlightsFor(changes, "target"), [changes]);
  const missing = unlocatedCount(changes);
  const scale = zoom === null ? fitScale : ZOOM_STEPS[zoom];

  // 폭 맞춤: 두 열 중 좁은 쪽 안쪽 폭에 가장 넓은 페이지(회전 반영)가 들어가는 배율. 열 크기가 바뀌면 다시 계산한다.
  useEffect(() => {
    const grid = gridRef.current;
    if (!grid) {
      return;
    }
    const measure = () => {
      setGridWidth(grid.clientWidth);
      const widths = [baseScrollRef.current, targetScrollRef.current]
        .filter((element): element is HTMLDivElement => Boolean(element && element.clientWidth > 0))
        .map((element) => element.clientWidth);
      const width = (widths.length ? Math.min(...widths) : grid.clientWidth) - 32;
      const widest = Math.max(
        1,
        ...[...data.base.pages, ...data.target.pages].map((p) => ((p.rotation + rotation) % 180 === 0 ? p.width : p.height)),
      );
      setFitScale(Math.min(2, Math.max(0.3, width / widest)));
    };
    measure();
    if (typeof ResizeObserver === "undefined") {
      return;
    }
    const observer = new ResizeObserver(measure);
    observer.observe(grid);
    [baseScrollRef.current, targetScrollRef.current].forEach((element) => element && observer.observe(element));
    return () => observer.disconnect();
  }, [data.base.pages, data.target.pages, rotation, loaded]);

  const zoomOut = () => setZoom((z) => {
    const index = z ?? ZOOM_STEPS.findLastIndex((step) => step < fitScale);
    return Math.max(0, index === z ? index - 1 : index);
  });
  const zoomIn = () => setZoom((z) => {
    const index = z ?? ZOOM_STEPS.findIndex((step) => step > fitScale);
    return Math.min(ZOOM_STEPS.length - 1, index === z ? index + 1 : index < 0 ? ZOOM_STEPS.length - 1 : index);
  });

  /** 코드로 스크롤한다. 실제로 움직일 때만 다음 스크롤 이벤트를 동기화에서 뺀다. */
  const scrollSide = useCallback((side: Side, top: number) => {
    const element = side === "base" ? baseScrollRef.current : targetScrollRef.current;
    if (!element) {
      return;
    }
    const next = Math.max(0, Math.min(top, element.scrollHeight - element.clientHeight));
    if (Math.abs(element.scrollTop - next) > 1) {
      suppress.current[side] = Date.now() + 150;
      element.scrollTo({ top: next });
    }
  }, []);

  /** 한쪽의 현재 위치(페이지·비율)를 다른 쪽에 맞춘다. 페이지 수가 다르면 상대 문서 끝으로 보낸다. */
  const syncFrom = useCallback((side: Side) => {
    const from = side === "base" ? baseScrollRef.current : targetScrollRef.current;
    const other: Side = side === "base" ? "target" : "base";
    const to = other === "base" ? baseScrollRef.current : targetScrollRef.current;
    if (!from || !to) {
      return;
    }
    const anchor = anchorOf(from.scrollTop, readPages(from));
    scrollSide(other, scrollTopFor(anchor, readPages(to)));
  }, [scrollSide]);

  const onScroll = (side: Side) => {
    if (Date.now() < suppress.current[side]) {
      return;
    }
    if (sync) {
      syncFrom(side);
    }
  };

  // 변경 이동: 강조가 있는 쪽을 가운데로 옮기고, 없는 쪽은(동기 스크롤이 켜져 있으면) 같은 위치로 맞춘다.
  useEffect(() => {
    if (!navRequest || !pagesReady.base || !pagesReady.target) {
      return;
    }
    const frame = requestAnimationFrame(() => {
      const moved: Side[] = [];
      for (const side of ["base", "target"] as const) {
        const container = side === "base" ? baseScrollRef.current : targetScrollRef.current;
        const element = container?.querySelector<HTMLElement>(`#pdf-${side}-change-${navRequest.changeId}`);
        if (container && element) {
          scrollSide(side, centeredScrollTop(container, element));
          moved.push(side);
        }
      }
      if (sync && moved.length === 1) {
        syncFrom(moved[0]);
      }
    });
    return () => cancelAnimationFrame(frame);
    // 배율·회전·동기 스크롤 설정이 바뀐 뒤에는 다시 옮기지 않는다(사용자 위치 유지).
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [navRequest, pagesReady, scrollSide, syncFrom]);

  const current = loaded?.key === key ? loaded : null;
  const minColumn = 240;
  const resolvedBaseWidth = baseWidth === null ? null : Math.min(Math.max(minColumn, baseWidth), Math.max(minColumn, gridWidth - minColumn - 6));
  const markReady = (side: Side) => (pages: PDFPageProxy[]) => pages.length > 0 && setPagesReady((s) => (s[side] ? s : { ...s, [side]: true }));

  return (
    <div className="flex h-full min-h-0 flex-col">
      <div role="toolbar" aria-label="PDF 보기 도구" className="flex h-11 shrink-0 items-center gap-1 overflow-x-auto border-b border-line bg-surface px-2">
        <IconButton label="축소" icon={<MinusIcon />} onClick={zoomOut} disabled={zoom === 0} />
        <span className="w-11 shrink-0 text-center text-sm text-ink tabular-nums" aria-live="polite">
          {Math.round(scale * 100)}%
        </span>
        <IconButton label="확대" icon={<PlusIcon />} onClick={zoomIn} disabled={zoom === ZOOM_STEPS.length - 1} />
        <IconButton label="폭 맞춤" icon={<FitWidthIcon />} pressed={zoom === null} onClick={() => setZoom(null)} />
        <IconButton label={`회전 (현재 ${rotation}도)`} icon={<RotateIcon />} onClick={() => setRotation((r) => (r + 90) % 360)} />
        <span className="sr-only">(현재 {rotation}도)</span>
        <span aria-hidden="true" className="mx-1 h-5 w-px shrink-0 bg-line" />
        <IconButton label="동기 스크롤" icon={<LinkScrollIcon />} pressed={sync} onClick={() => setSync((v) => !v)} />
        <label className="flex shrink-0 cursor-pointer items-center gap-1.5 rounded-md px-2 py-1 text-[13px] text-ink-muted hover:bg-subtle">
          <input type="checkbox" checked={showHighlights} onChange={(event) => setShowHighlights(event.target.checked)} className="size-3.5 accent-brand-600" />
          <HighlightIcon className="size-3.5" aria-hidden="true" />
          변경 강조 표시
        </label>
        <span className="flex-1" />
        <Legend />
        <span className="group/tip relative ml-1 inline-flex shrink-0">
          <span tabIndex={0} aria-describedby="pdf-compare-limits" className="inline-flex size-8 items-center justify-center rounded-md text-ink-muted">
            <InfoIcon />
            <span className="sr-only">PDF 강조 지원 범위</span>
          </span>
          <span
            id="pdf-compare-limits"
            role="tooltip"
            className="pointer-events-none absolute top-full right-0 z-50 mt-1.5 hidden w-72 rounded-md bg-ink px-3 py-2 text-xs leading-relaxed text-white shadow-[var(--shadow-overlay)] group-hover/tip:block group-has-[:focus-visible]/tip:block"
          >
            일반 텍스트 PDF 기준입니다. 표·다단·회전된 글자는 위치가 정확하지 않을 수 있고, 스캔 PDF는 지원하지 않습니다.
          </span>
        </span>
      </div>
      {missing > 0 && (
        <p role="status" className="shrink-0 border-b border-warning-700/15 bg-warning-50 px-3 py-1.5 text-xs text-warning-700">
          위치를 찾지 못한 변경 {missing}개는 PDF에 표시하지 않았습니다(텍스트 비교에서 확인).
        </p>
      )}
      {!current && (
        <div className="grid min-h-0 flex-1 grid-cols-1 gap-4 bg-canvas-sunken p-6 lg:grid-cols-2" aria-busy="true">
          <p role="status" className="sr-only">
            PDF를 불러오는 중...
          </p>
          <Skeleton className="h-full bg-surface" />
          <Skeleton className="hidden h-full bg-surface lg:block" />
        </div>
      )}
      {current && "error" in current && (
        <div className="p-6">
          <ErrorState title="PDF를 불러오지 못했습니다." message="원본 파일을 내려받아 확인하거나 텍스트 비교로 확인해 주세요." />
        </div>
      )}
      {current && !("error" in current) && (
        <div ref={gridRef} className="flex min-h-0 flex-1 bg-canvas-sunken">
          <Column
            side="base"
            label={`이전 · v${data.base.versionNumber}`}
            description="삭제·수정 전"
            pageCount={data.base.pageCount}
            scrollRef={baseScrollRef}
            onScroll={() => onScroll("base")}
            className={cn(mobileSide !== "base" && "max-lg:hidden")}
            style={resolvedBaseWidth !== null ? ({ "--col-w": `${resolvedBaseWidth}px` } as CSSProperties) : undefined}
            fixed={resolvedBaseWidth !== null}
          >
            <PdfViewer pdf={current.base} side="base" highlights={baseHighlights} showHighlights={showHighlights} scale={scale} rotation={rotation} activeChangeId={activeChangeId} onSelectChange={onSelectChange} onPagesLoaded={markReady("base")} />
          </Column>
          <PaneResizer
            label="비교 열 너비"
            edge="left"
            value={resolvedBaseWidth ?? Math.round((gridWidth - 6) / 2)}
            min={minColumn}
            max={Math.max(minColumn, gridWidth - minColumn - 6)}
            onChange={setBaseWidth}
            className="max-lg:hidden"
          />
          <Column
            side="target"
            label={`이후 · v${data.target.versionNumber}`}
            description="추가·수정 후"
            pageCount={data.target.pageCount}
            scrollRef={targetScrollRef}
            onScroll={() => onScroll("target")}
            className={cn(mobileSide !== "target" && "max-lg:hidden")}
          >
            <PdfViewer pdf={current.target} side="target" highlights={targetHighlights} showHighlights={showHighlights} scale={scale} rotation={rotation} activeChangeId={activeChangeId} onSelectChange={onSelectChange} onPagesLoaded={markReady("target")} />
          </Column>
        </div>
      )}
    </div>
  );
}

function Column({ side, label, description, pageCount, scrollRef, onScroll, children, className, style, fixed = false }: {
  side: Side;
  label: string;
  description: string;
  pageCount: number;
  scrollRef: React.RefObject<HTMLDivElement | null>;
  onScroll: () => void;
  children: ReactNode;
  className?: string;
  style?: CSSProperties;
  fixed?: boolean;
}) {
  return (
    <section
      aria-label={label}
      data-side={side}
      style={style}
      className={cn("flex min-h-0 min-w-0 flex-col", fixed ? "w-full shrink-0 lg:w-[var(--col-w)]" : "flex-1", className)}
    >
      <p className="flex h-8 shrink-0 items-center gap-1.5 border-b border-line bg-surface px-3 text-xs">
        <span className="font-semibold text-ink">{label}</span>
        <span className="text-ink-muted">· {description}</span>
        <span className="ml-auto text-ink-muted tabular-nums">{pageCount}쪽</span>
      </p>
      <div ref={scrollRef} data-viewer-scroll data-pdf-viewport onScroll={onScroll} className="relative min-h-0 flex-1 overflow-auto p-4">
        {children}
      </div>
    </section>
  );
}

function Legend() {
  const items: { type: Change["type"]; className: string; label: string }[] = [
    { type: "ADDED", className: "border-2 border-success-600 bg-success-600/20", label: "추가(실선)" },
    { type: "DELETED", className: "border-2 border-dashed border-danger-600 bg-danger-600/20", label: "삭제(파선)" },
    { type: "MODIFIED", className: "border-2 border-dotted border-warning-700 bg-warning-500/25", label: "수정(점선)" },
  ];
  return (
    <ul className="hidden shrink-0 gap-3 text-xs text-ink-muted xl:flex" aria-label="강조 범례">
      {items.map((item) => (
        <li key={item.type} className="flex items-center gap-1">
          <span aria-hidden="true" className={`inline-block h-3 w-5 rounded-[2px] ${item.className}`} />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
