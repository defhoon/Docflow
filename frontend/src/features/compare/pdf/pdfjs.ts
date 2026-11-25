"use client";

import type { PDFDocumentProxy } from "pdfjs-dist";

/**
 * PDF.js 지연 로드 (SPEC-13, ADR-0011). 서버 렌더링 중에는 불러오지 않고, 비교 화면에서 PDF 보기를 열 때만 내려받는다.
 * 작업자 스크립트는 같은 출처의 정적 파일로 제공된다.
 */
let loader: Promise<typeof import("pdfjs-dist")> | null = null;

function pdfjs() {
  if (!loader) {
    loader = import("pdfjs-dist").then((lib) => {
      lib.GlobalWorkerOptions.workerSrc = new URL("pdfjs-dist/build/pdf.worker.min.mjs", import.meta.url).toString();
      return lib;
    });
  }
  return loader;
}

export type OpenedPdf = { pdf: PDFDocumentProxy; close: () => void };

/** 세션 쿠키로 원본을 받아 PDF.js 문서를 연다. 다 쓰면 `close()`로 작업자 자원을 돌려준다. */
export async function openPdf(url: string, signal: AbortSignal): Promise<OpenedPdf> {
  const response = await fetch(url, { credentials: "same-origin", signal });
  if (!response.ok) {
    throw new Error(`PDF download failed: ${response.status}`);
  }
  const data = new Uint8Array(await response.arrayBuffer());
  const lib = await pdfjs();
  const task = lib.getDocument({ data });
  const pdf = await task.promise;
  return { pdf, close: () => void task.destroy() };
}

export type { PDFDocumentProxy };
