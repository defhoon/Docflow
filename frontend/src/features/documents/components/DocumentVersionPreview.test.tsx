import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Activity } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { DocumentVersionSummary } from "../version-files";
import { DocumentVersionPreview, PREVIEW_MAX_WAIT_MS } from "./DocumentVersionPreview";

const refresh = vi.fn();
const previewMock = vi.fn();
const downloadMock = vi.fn();
const saveBlobMock = vi.fn();
const onClose = vi.fn();
const createObjectURL = vi.fn();
const revokeObjectURL = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../version-files", () => ({
  fetchVersionPreview: (...args: unknown[]) => previewMock(...args),
  downloadVersionFile: (...args: unknown[]) => downloadMock(...args),
  saveBlob: (...args: unknown[]) => saveBlobMock(...args),
}));

function version(versionNumber: number, name = `v${versionNumber}.docx`): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: name,
    sizeBytes: 10,
    uploadedBy: { id: 1, displayName: "Owner" },
    uploadedAt: "2026-11-04T09:00:00Z",
    latest: versionNumber === 2,
  };
}

const pdf = () => ({ status: "ready" as const, blob: new Blob(["%PDF"], { type: "application/pdf" }) });
const pending = (retryAfterMs = 10) => ({ status: "pending" as const, retryAfterMs });

function renderPreview(v = version(2)) {
  return render(<DocumentVersionPreview key={v.versionNumber} projectId="7" documentId="3" version={v} onClose={onClose} />);
}

describe("DocumentVersionPreview (SPEC-05 §9, AC-VDL-18)", () => {
  const original = { createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL };

  beforeEach(() => {
    refresh.mockReset();
    previewMock.mockReset();
    downloadMock.mockReset();
    saveBlobMock.mockReset();
    onClose.mockReset();
    createObjectURL.mockReset();
    revokeObjectURL.mockReset();
    let counter = 0;
    createObjectURL.mockImplementation(() => `blob:preview-${++counter}`);
    Object.assign(URL, { createObjectURL, revokeObjectURL });
  });

  afterEach(() => {
    Object.assign(URL, original);
    vi.useRealTimers();
  });

  it("PDF를 Blob URL로 iframe에 표시한다", async () => {
    previewMock.mockResolvedValue(pdf());
    renderPreview();

    const frame = await screen.findByTitle("v2 미리보기");
    expect(frame.tagName).toBe("IFRAME");
    expect(frame).toHaveAttribute("src", "blob:preview-1");
    expect(previewMock).toHaveBeenCalledWith("7", "3", 2, expect.any(AbortSignal));
    expect((createObjectURL.mock.calls[0][0] as Blob).type).toBe("application/pdf");
    expect(screen.getByText("미리보기는 참고용이며 원본 파일이 기준입니다.")).toBeInTheDocument();
  });

  it("변환 중에는 준비 문구를 보이며 다시 요청하고, 완료되면 표시한다", async () => {
    previewMock.mockResolvedValueOnce(pending()).mockResolvedValueOnce(pending()).mockResolvedValueOnce(pdf());
    renderPreview();

    expect(await screen.findByText(/미리보기를 준비하는 중입니다/)).toBeInTheDocument();
    expect(await screen.findByTitle("v2 미리보기")).toBeInTheDocument();
    expect(previewMock).toHaveBeenCalledTimes(3);
  });

  it(`최대 대기 시간(${PREVIEW_MAX_WAIT_MS}ms)을 넘기면 실패로 안내하고 원본 다운로드를 제공한다`, async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    previewMock.mockResolvedValue(pending(2000));
    renderPreview();

    await act(async () => {
      await vi.advanceTimersByTimeAsync(PREVIEW_MAX_WAIT_MS + 5000);
    });

    expect(screen.getByRole("alert")).toHaveTextContent("미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요.");
    expect(screen.getByRole("button", { name: "원본 다운로드" })).toBeInTheDocument();
  });

  it.each<[string, ApiError, string]>([
    ["422", new ApiError("http", 422, "DOCUMENT_PREVIEW_FAILED", "x"), "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요."],
    ["503", new ApiError("http", 503, "DOCUMENT_PREVIEW_UNAVAILABLE", "x"), "지금은 미리보기를 사용할 수 없습니다. 원본 파일을 내려받아 확인해 주세요."],
    ["PDF 아님", new ApiError("http", 200, "PREVIEW_NOT_PDF", "x"), "미리보기를 만들 수 없습니다. 원본 파일을 내려받아 확인해 주세요."],
    ["원본 없음", new ApiError("http", 500, "DOCUMENT_FILE_UNAVAILABLE", "x"), "파일을 불러올 수 없습니다. 관리자에게 문의해 주세요."],
  ])("%s 실패는 안내와 원본 다운로드·다시 시도를 제공한다", async (_label, error, message) => {
    previewMock.mockRejectedValueOnce(error).mockResolvedValueOnce(pdf());
    downloadMock.mockResolvedValue(new Blob(["orig"]));
    renderPreview(version(2, "보고서.docx"));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(screen.queryByTitle("v2 미리보기")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "원본 다운로드" }));
    await waitFor(() => expect(saveBlobMock).toHaveBeenCalledWith(expect.any(Blob), "보고서.docx"));

    fireEvent.click(screen.getByRole("button", { name: "미리보기 다시 시도" }));
    expect(await screen.findByTitle("v2 미리보기")).toBeInTheDocument();
  });

  it("401은 세션 만료 흐름에 맡긴다", async () => {
    previewMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    renderPreview();

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  });

  it("닫기를 누르면 onClose를 부르고, 언마운트 시 폴링을 멈추고 Blob URL을 해제한다", async () => {
    previewMock.mockResolvedValue(pdf());
    const view = renderPreview();
    await screen.findByTitle("v2 미리보기");

    fireEvent.click(screen.getByRole("button", { name: "미리보기 닫기" }));
    expect(onClose).toHaveBeenCalled();
    view.unmount();

    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1");
  });

  it("변환 대기 중 다른 버전으로 바꾸면 이전 요청을 중단한다", async () => {
    previewMock.mockResolvedValueOnce(pending(50)).mockResolvedValue(pdf());
    const view = renderPreview(version(1));
    await screen.findByText(/미리보기를 준비하는 중입니다/);
    const firstSignal = previewMock.mock.calls[0][3] as AbortSignal;

    view.rerender(<DocumentVersionPreview key={2} projectId="7" documentId="3" version={version(2)} onClose={onClose} />);

    expect(firstSignal.aborted).toBe(true);
    expect(await screen.findByTitle("v2 미리보기")).toBeInTheDocument();
    await new Promise((resolve) => setTimeout(resolve, 100));
    expect(previewMock.mock.calls.filter((call) => call[2] === 1)).toHaveLength(1);
  });

  it("화면이 숨겨지면(Activity) Blob URL을 해제하고, 다시 보이면 새로 불러온다", async () => {
    previewMock.mockResolvedValue(pdf());
    const tree = (visible: boolean) => (
      <Activity mode={visible ? "visible" : "hidden"}>
        <DocumentVersionPreview projectId="7" documentId="3" version={version(2)} onClose={onClose} />
      </Activity>
    );
    const view = render(tree(true));
    await screen.findByTitle("v2 미리보기");

    view.rerender(tree(false));
    expect(revokeObjectURL).toHaveBeenCalledWith("blob:preview-1");
    view.rerender(tree(true));

    await waitFor(() => expect(screen.getByTitle("v2 미리보기")).toHaveAttribute("src", "blob:preview-2"));
  });
});
