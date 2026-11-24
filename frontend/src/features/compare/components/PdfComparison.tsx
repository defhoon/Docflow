"use client";

import { useEffect, useMemo, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { ErrorState, LoadingBlock } from "@/components/ui/States";

import { highlightsFor, unlocatedCount, ZOOM_STEPS } from "../pdf/geometry";
import { openPdf, type OpenedPdf, type PDFDocumentProxy } from "../pdf/pdfjs";
import type { Change, CompareResponse } from "../types";
import { PdfViewer } from "./PdfViewer";

type Props = {
  projectId: string;
  documentId: string;
  data: CompareResponse;
  activeChangeId: number | null;
  onSelectChange: (id: number) => void;
};

function downloadUrl(projectId: string, documentId: string, versionNumber: number): string {
  return `/api/projects/${encodeURIComponent(projectId)}/documents/${encodeURIComponent(documentId)}/versions/${versionNumber}/download`;
}

type Loaded = { key: string; base: PDFDocumentProxy; target: PDFDocumentProxy } | { key: string; error: true } | null;

/**
 * 원본 PDF 위 변경 강조 (SPEC-13 §5). 이전 버전에는 삭제·수정 전, 이후 버전에는 추가·수정 후를 표시한다. 확대·축소·회전·강조
 * 표시 전환을 지원한다. 원본은 기존 다운로드 API(권한 확인 포함)로 받는다.
 */
export function PdfComparison({ projectId, documentId, data, activeChangeId, onSelectChange }: Props) {
  const [loaded, setLoaded] = useState<Loaded>(null);
  // null이면 폭 맞춤(기본). 숫자면 ZOOM_STEPS 색인.
  const [zoom, setZoom] = useState<number | null>(null);
  const [fitScale, setFitScale] = useState(1);
  const gridRef = useRef<HTMLDivElement>(null);
  const [rotation, setRotation] = useState(0);
  const [showHighlights, setShowHighlights] = useState(true);
  const key = `${projectId}/${documentId}/${data.base.versionNumber}/${data.target.versionNumber}`;

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

  // 폭 맞춤: 한 열의 안쪽 폭에 가장 넓은 페이지(회전 반영)가 들어가는 배율.
  useEffect(() => {
    const grid = gridRef.current;
    if (!grid || typeof ResizeObserver === "undefined") {
      return;
    }
    const measure = () => {
      const column = grid.querySelector<HTMLElement>("[data-viewer-scroll]");
      const width = (column?.clientWidth ?? grid.clientWidth) - 24;
      const widest = Math.max(
        1,
        ...[...data.base.pages, ...data.target.pages].map((p) => ((p.rotation + rotation) % 180 === 0 ? p.width : p.height)),
      );
      setFitScale(Math.min(2, Math.max(0.3, width / widest)));
    };
    measure();
    const observer = new ResizeObserver(measure);
    observer.observe(grid);
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

  // 변경을 고르면 양쪽 PDF에서 해당 위치로 이동한다.
  useEffect(() => {
    if (activeChangeId === null) {
      return;
    }
    for (const side of ["base", "target"]) {
      window.document.getElementById(`pdf-${side}-change-${activeChangeId}`)?.scrollIntoView({ behavior: "smooth", block: "center", inline: "nearest" });
    }
  }, [activeChangeId, loaded, zoom, rotation]);

  const current = loaded?.key === key ? loaded : null;
  return (
    <div className="flex flex-col gap-3">
      <div className="flex flex-wrap items-center gap-2" role="toolbar" aria-label="PDF 보기 도구">
        <Button size="sm" aria-label="축소" disabled={zoom === 0} onClick={zoomOut}>
          −
        </Button>
        <span className="w-12 text-center text-sm tabular-nums" aria-live="polite">
          {Math.round(scale * 100)}%
        </span>
        <Button size="sm" aria-label="확대" disabled={zoom === ZOOM_STEPS.length - 1} onClick={zoomIn}>
          +
        </Button>
        <Button size="sm" disabled={zoom === null} onClick={() => setZoom(null)}>
          폭 맞춤
        </Button>
        <Button size="sm" onClick={() => setRotation((r) => (r + 90) % 360)}>
          회전 ⟳ <span className="sr-only">(현재 {rotation}도)</span>
        </Button>
        <label className="ml-1 flex items-center gap-1.5 text-sm">
          <input type="checkbox" checked={showHighlights} onChange={(event) => setShowHighlights(event.target.checked)} className="size-4 accent-brand-600" />
          변경 강조 표시
        </label>
        <Legend />
      </div>
      <p className="text-xs text-ink-muted">
        일반 텍스트 PDF 기준입니다. 표·다단·회전된 글자는 위치가 정확하지 않을 수 있고, 스캔 PDF는 지원하지 않습니다.
        {missing > 0 && ` 위치를 찾지 못한 변경 ${missing}개는 PDF에 표시하지 않았습니다(텍스트 비교에서 확인).`}
      </p>
      {!current && <LoadingBlock label="PDF를 불러오는 중..." lines={4} />}
      {current && "error" in current && <ErrorState title="PDF를 불러오지 못했습니다." message="원본 파일을 내려받아 확인해 주세요." />}
      {current && !("error" in current) && (
        <div ref={gridRef} className="grid grid-cols-1 gap-4 xl:grid-cols-2">
          <ViewerColumn label={`이전 · v${data.base.versionNumber}`} description="삭제·수정 전 내용 표시">
            <PdfViewer pdf={current.base} side="base" highlights={baseHighlights} showHighlights={showHighlights} scale={scale} rotation={rotation} activeChangeId={activeChangeId} onSelectChange={onSelectChange} />
          </ViewerColumn>
          <ViewerColumn label={`이후 · v${data.target.versionNumber}`} description="추가·수정 후 내용 표시">
            <PdfViewer pdf={current.target} side="target" highlights={targetHighlights} showHighlights={showHighlights} scale={scale} rotation={rotation} activeChangeId={activeChangeId} onSelectChange={onSelectChange} />
          </ViewerColumn>
        </div>
      )}
    </div>
  );
}

function ViewerColumn({ label, description, children }: { label: string; description: string; children: React.ReactNode }) {
  return (
    <section aria-label={label} className="min-w-0 rounded-[var(--radius-card)] border border-line bg-subtle/60">
      <p className="border-b border-line px-3 py-2 text-xs">
        <span className="font-medium text-ink">{label}</span> <span className="text-ink-muted">· {description}</span>
      </p>
      <div data-viewer-scroll className="max-h-[75vh] overflow-auto p-3">{children}</div>
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
    <ul className="ml-auto flex flex-wrap gap-3 text-xs text-ink-muted" aria-label="강조 범례">
      {items.map((item) => (
        <li key={item.type} className="flex items-center gap-1">
          <span aria-hidden="true" className={`inline-block h-3 w-5 rounded-[2px] ${item.className}`} />
          {item.label}
        </li>
      ))}
    </ul>
  );
}
