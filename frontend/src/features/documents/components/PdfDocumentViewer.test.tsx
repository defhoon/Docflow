import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { DocumentVersionSummary } from "../version-files";
import { PdfDocumentViewer, VIEWER_MAX_WAIT_MS } from "./PdfDocumentViewer";

const refresh = vi.fn();
const previewMock = vi.fn();
const downloadMock = vi.fn();
const saveBlobMock = vi.fn();
const openPdfBytes = vi.fn();
const close = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../version-files", () => ({
  fetchVersionPreview: (...args: unknown[]) => previewMock(...args),
  downloadVersionFile: (...args: unknown[]) => downloadMock(...args),
  saveBlob: (...args: unknown[]) => saveBlobMock(...args),
}));
vi.mock("@/features/compare/pdf/pdfjs", () => ({ openPdfBytes: (...args: unknown[]) => openPdfBytes(...args) }));

/** PDF.js 페이지 흉내(2쪽, 600×800pt). */
function fakePdf(numPages = 2) {
  const page = (pageNumber: number) => ({
    pageNumber,
    rotate: 0,
    view: [0, 0, 600, 800],
    getViewport: ({ scale, rotation = 0 }: { scale: number; rotation?: number }) => {
      const swap = rotation % 180 !== 0;
      return { width: (swap ? 800 : 600) * scale, height: (swap ? 600 : 800) * scale, convertToViewportPoint: (x: number, y: number) => [x, y] };
    },
    render: () => ({ promise: Promise.resolve(), cancel: vi.fn() }),
  });
  return { pdf: { numPages, getPage: (n: number) => Promise.resolve(page(n)) }, close };
}

function version(versionNumber: number, name = `v${versionNumber}.pdf`): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: name,
    sizeBytes: 10,
    uploadedBy: { id: 1, displayName: "Owner" },
    uploadedAt: "2026-11-04T09:00:00Z",
    versionNote: null,
    latest: true,
    feedbackCount: 0,
  };
}

const ready = () => ({ status: "ready" as const, blob: new Blob(["%PDF"], { type: "application/pdf" }) });
const pending = (retryAfterMs = 10) => ({ status: "pending" as const, retryAfterMs });

describe("PdfDocumentViewer (SPEC-16 AC-WS-06)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    openPdfBytes.mockImplementation(() => Promise.resolve(fakePdf()));
    Element.prototype.scrollIntoView = vi.fn();
  });
  afterEach(() => vi.useRealTimers());

  it("불러오는 중에는 상태만 보이고 PDF 전용 컨트롤을 숨긴다. 표시되면 폭 맞춤·쪽 이동·확대·회전 도구가 보인다", async () => {
    let resolve!: (value: unknown) => void;
    previewMock.mockReturnValue(new Promise((r) => (resolve = r)));
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(2)} />);
    expect(screen.getByRole("status")).toHaveTextContent("PDF를 불러오는 중...");
    expect(screen.queryByRole("toolbar", { name: "문서 보기 도구" })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "v2 원본 받기" })).toBeInTheDocument();

    await act(async () => resolve(ready()));
    const toolbar = await screen.findByRole("toolbar", { name: "문서 보기 도구" });
    expect(previewMock).toHaveBeenCalledWith("7", "3", 2, expect.any(AbortSignal));
    expect(screen.getByRole("button", { name: "폭 맞춤" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByLabelText(/쪽 번호/)).toHaveValue("1");
    expect(toolbar).toHaveTextContent("/ 2");
    await screen.findByLabelText("2쪽");
    fireEvent.click(screen.getByRole("button", { name: "확대" }));
    expect(screen.getByRole("button", { name: "폭 맞춤" })).toHaveAttribute("aria-pressed", "false");
    fireEvent.click(screen.getByRole("button", { name: /회전/ }));
    expect(screen.getByRole("button", { name: "회전 (현재 90도)" })).toBeInTheDocument();
  });

  it("쪽 번호를 입력하거나 외부에서 이동을 요청하면 해당 쪽으로 이동한다", async () => {
    previewMock.mockResolvedValue(ready());
    const { rerender } = render(<PdfDocumentViewer projectId="7" documentId="3" version={version(2)} />);
    await screen.findByLabelText("2쪽");
    const input = screen.getByLabelText(/쪽 번호/);
    fireEvent.change(input, { target: { value: "2" } });
    fireEvent.submit(input.closest("form")!);
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
    expect(input).toHaveValue("2");

    rerender(<PdfDocumentViewer projectId="7" documentId="3" version={version(2)} pageRequest={{ page: 1, nonce: 1 }} />);
    await waitFor(() => expect(screen.getByLabelText(/쪽 번호/)).toHaveValue("1"));
  });

  it("변환 중이면 준비 문구를 보이며 다시 요청하고, 오래 걸리면 실패로 바꾼다", async () => {
    vi.useFakeTimers();
    previewMock.mockResolvedValue(pending(1000));
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(1, "a.docx")} />);
    await act(async () => undefined);
    expect(screen.getByRole("status")).toHaveTextContent("미리보기를 준비하는 중입니다");
    await act(async () => {
      await vi.advanceTimersByTimeAsync(VIEWER_MAX_WAIT_MS + 1000);
    });
    expect(screen.getByRole("alert")).toHaveTextContent("문서를 표시하지 못했습니다.");
    expect(previewMock.mock.calls.length).toBeGreaterThan(2);
  });

  it("Office 미리보기를 지원하지 않는 환경이면 안내와 원본 받기를 보여 주고 PDF 도구는 없다", async () => {
    previewMock.mockRejectedValue(new ApiError("http", 503, "DOCUMENT_PREVIEW_UNAVAILABLE", "x"));
    downloadMock.mockResolvedValue(new Blob(["docx"]));
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(1, "가이드.docx")} />);
    expect(await screen.findByText("이 환경에서는 Office 문서 미리보기를 지원하지 않습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("toolbar", { name: "문서 보기 도구" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "확대" })).not.toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "v1 원본 받기" })[1]);
    await waitFor(() => expect(saveBlobMock).toHaveBeenCalledWith(expect.any(Blob), "가이드.docx"));
  });

  it("불러오기에 실패하면 오류 안내·다시 시도·원본 받기를 제공하고, 다시 시도하면 다시 요청한다", async () => {
    previewMock.mockRejectedValueOnce(new ApiError("http", 500, "DOCUMENT_FILE_UNAVAILABLE", "x"));
    previewMock.mockResolvedValueOnce(ready());
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(3)} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("문서를 표시하지 못했습니다.");
    expect(screen.queryByRole("toolbar", { name: "문서 보기 도구" })).not.toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: "v3 원본 받기" }).length).toBeGreaterThan(0);
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByRole("toolbar", { name: "문서 보기 도구" })).toBeInTheDocument();
    expect(previewMock).toHaveBeenCalledTimes(2);
  });

  it("PDF.js가 문서를 열지 못하면(손상 등) 실패 상태를 보여 준다", async () => {
    previewMock.mockResolvedValue(ready());
    openPdfBytes.mockRejectedValue(new Error("InvalidPDFException"));
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(1)} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("PDF를 표시하지 못했습니다.");
  });

  it("버전이 바뀌면 이전 요청을 취소하고 열린 PDF 자원을 해제한다", async () => {
    previewMock.mockResolvedValue(ready());
    const { rerender } = render(<PdfDocumentViewer key={1} projectId="7" documentId="3" version={version(1)} />);
    await screen.findByRole("toolbar", { name: "문서 보기 도구" });
    const signal = previewMock.mock.calls[0][3] as AbortSignal;
    rerender(<PdfDocumentViewer key={2} projectId="7" documentId="3" version={version(2)} />);
    expect(signal.aborted).toBe(true);
    expect(close).toHaveBeenCalled();
  });

  it("401이면 세션 만료 흐름을 시작한다", async () => {
    previewMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    render(<PdfDocumentViewer projectId="7" documentId="3" version={version(1)} />);
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });
});
