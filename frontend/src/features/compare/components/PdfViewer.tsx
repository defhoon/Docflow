"use client";

import type { PDFDocumentProxy, PDFPageProxy } from "pdfjs-dist";
import { useEffect, useRef, useState } from "react";

import { cn } from "@/components/ui/cn";

import { CHANGE_LABELS } from "../messages";
import { type PageHighlight, toScreenRect } from "../pdf/geometry";

type Props = {
  pdf: PDFDocumentProxy;
  side: "base" | "target";
  highlights: PageHighlight[];
  showHighlights: boolean;
  scale: number;
  /** 사용자가 더한 회전(0·90·180·270). 페이지 자체 회전(`/Rotate`)에 더한다. */
  rotation: number;
  activeChangeId: number | null;
  onSelectChange: (id: number) => void;
};

const HIGHLIGHT_STYLE: Record<PageHighlight["type"], string> = {
  ADDED: "border-2 border-success-600 bg-success-600/20",
  DELETED: "border-2 border-dashed border-danger-600 bg-[repeating-linear-gradient(135deg,rgb(220_38_38/0.22)_0_4px,transparent_4px_8px)]",
  MODIFIED: "border-2 border-dotted border-warning-700 bg-warning-500/25",
};

/**
 * PDF.js로 페이지를 그리고 변경 위치를 겹쳐 표시한다 (SPEC-13 §5). 페이지는 화면에 가까워질 때 그린다. 강조는 색 외에 테두리
 * 모양(실선·점선·점)과 이름으로도 구분한다.
 */
export function PdfViewer({ pdf, side, highlights, showHighlights, scale, rotation, activeChangeId, onSelectChange }: Props) {
  const [pages, setPages] = useState<PDFPageProxy[]>([]);

  useEffect(() => {
    let active = true;
    Promise.all(Array.from({ length: pdf.numPages }, (_, i) => pdf.getPage(i + 1))).then(
      (loaded) => active && setPages(loaded),
      () => undefined,
    );
    return () => {
      active = false;
    };
  }, [pdf]);

  return (
    <div className="flex flex-col items-center gap-4">
      {pages.map((page) => (
        <PdfPage
          key={page.pageNumber}
          page={page}
          side={side}
          highlights={highlights.filter((h) => h.page === page.pageNumber)}
          showHighlights={showHighlights}
          scale={scale}
          rotation={rotation}
          activeChangeId={activeChangeId}
          onSelectChange={onSelectChange}
        />
      ))}
    </div>
  );
}

function PdfPage({ page, side, highlights, showHighlights, scale, rotation, activeChangeId, onSelectChange }: Omit<Props, "pdf"> & { page: PDFPageProxy }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const containerRef = useRef<HTMLDivElement>(null);
  const [visible, setVisible] = useState(false);
  const viewport = page.getViewport({ scale, rotation: (page.rotate + rotation) % 360 });

  useEffect(() => {
    const element = containerRef.current;
    if (!element || typeof IntersectionObserver === "undefined") {
      setVisible(true);
      return;
    }
    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting)) {
        setVisible(true);
        observer.disconnect();
      }
    }, { rootMargin: "600px" });
    observer.observe(element);
    return () => observer.disconnect();
  }, []);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!visible || !canvas) {
      return;
    }
    const ratio = window.devicePixelRatio || 1;
    const renderViewport = page.getViewport({ scale: scale * ratio, rotation: (page.rotate + rotation) % 360 });
    canvas.width = Math.floor(renderViewport.width);
    canvas.height = Math.floor(renderViewport.height);
    const task = page.render({ canvas, viewport: renderViewport });
    task.promise.catch(() => undefined);
    return () => task.cancel();
  }, [visible, page, scale, rotation]);

  return (
    <div
      ref={containerRef}
      data-page={page.pageNumber}
      id={`pdf-${side}-page-${page.pageNumber}`}
      className="relative shrink-0 bg-white shadow-[var(--shadow-card)] ring-1 ring-line"
      style={{ width: viewport.width, height: viewport.height }}
    >
      <canvas ref={canvasRef} aria-label={`${page.pageNumber}쪽`} style={{ width: viewport.width, height: viewport.height }} />
      {showHighlights &&
        highlights.map((highlight, index) => {
          const rect = toScreenRect(highlight.rect, page.view, (x, y) => viewport.convertToViewportPoint(x, y));
          const label = `${CHANGE_LABELS[highlight.type].label} (${page.pageNumber}쪽)`;
          return (
            <button
              key={index}
              type="button"
              id={index === highlights.findIndex((h) => h.changeId === highlight.changeId) ? `pdf-${side}-change-${highlight.changeId}` : undefined}
              aria-label={label}
              title={label}
              onClick={() => onSelectChange(highlight.changeId)}
              className={cn(
                "absolute cursor-pointer rounded-[2px] scroll-mt-32",
                HIGHLIGHT_STYLE[highlight.type],
                activeChangeId === highlight.changeId && "ring-2 ring-brand-600 ring-offset-1",
              )}
              style={{ left: rect.left - 1, top: rect.top - 1, width: rect.width + 2, height: rect.height + 2 }}
            />
          );
        })}
      <span className="absolute right-1 bottom-1 rounded bg-surface/80 px-1 text-[10px] text-ink-muted">{page.pageNumber}</span>
    </div>
  );
}
