import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { DocumentDetail as DocumentDetailData } from "../types";
import type { DocumentVersionSummary } from "../version-files";
import { DocumentDetail } from "./DocumentDetail";

const refresh = vi.fn();
const fetchDocumentMock = vi.fn();
const createDocumentVersionMock = vi.fn();
const fetchVersionsMock = vi.fn();
const previewMock = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => ({ projectId: "7", documentId: "3" }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchDocument: (...args: unknown[]) => fetchDocumentMock(...args),
  createDocumentVersion: (...args: unknown[]) => createDocumentVersionMock(...args),
}));

vi.mock("../version-files", () => ({
  fetchDocumentVersions: (...args: unknown[]) => fetchVersionsMock(...args),
  fetchVersionPreview: (...args: unknown[]) => previewMock(...args),
  downloadVersionFile: vi.fn(),
  saveBlob: vi.fn(),
}));

function detail(versionNumber: number): DocumentDetailData {
  return {
    id: 3,
    title: "요구사항 명세서",
    description: null,
    status: "ACTIVE",
    createdBy: { id: 1, displayName: "Author" },
    createdAt: "2026-10-27T09:00:00Z",
    updatedAt: "2026-10-27T09:00:00Z",
    finalization: null,
    latestVersion: { versionNumber, originalFilename: `v${versionNumber}.pdf`, sizeBytes: 10, uploadedAt: "2026-10-27T09:00:00Z" },
  };
}

function summary(versionNumber: number, latest: boolean): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: `v${versionNumber}.pdf`,
    sizeBytes: 10,
    uploadedBy: { id: 1, displayName: "Author" },
    uploadedAt: "2026-10-27T09:00:00Z",
    latest,
  };
}

describe("DocumentDetail — 버전 히스토리·미리보기 (SPEC-05 §9, AC-VDL-18·19)", () => {
  const original = { createObjectURL: URL.createObjectURL, revokeObjectURL: URL.revokeObjectURL };

  beforeEach(() => {
    refresh.mockReset();
    fetchDocumentMock.mockReset();
    createDocumentVersionMock.mockReset();
    fetchVersionsMock.mockReset();
    previewMock.mockReset();
    Object.assign(URL, { createObjectURL: vi.fn(() => "blob:detail-preview"), revokeObjectURL: vi.fn() });
  });

  afterEach(() => {
    Object.assign(URL, original);
  });

  it("히스토리는 문서 상세 안에서 메타데이터 아래, 새 버전 등록 위에 있고 미리보기를 연다", async () => {
    fetchDocumentMock.mockResolvedValue(detail(1));
    fetchVersionsMock.mockResolvedValue([summary(1, true)]);
    previewMock.mockResolvedValue({ status: "ready", blob: new Blob(["%PDF"], { type: "application/pdf" }) });
    render(<DocumentDetail />);

    const history = await screen.findByRole("region", { name: "버전 히스토리" });
    const upload = screen.getByRole("region", { name: "새 버전 등록" });
    expect(history.compareDocumentPosition(upload) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    fireEvent.click(await within(history).findByRole("button", { name: "v1 미리보기" }));

    expect(await screen.findByTitle("v1 미리보기")).toHaveAttribute("src", "blob:detail-preview#navpanes=0&view=FitH");
    fireEvent.click(screen.getByRole("button", { name: "미리보기 닫기" }));
    await waitFor(() => expect(screen.queryByTitle("v1 미리보기")).not.toBeInTheDocument());
  });

  it("새 버전 등록에 성공하면 히스토리를 다시 조회해 새 버전을 최신으로 보여 준다", async () => {
    fetchDocumentMock.mockResolvedValue(detail(1));
    fetchVersionsMock.mockResolvedValueOnce([summary(1, true)]).mockResolvedValueOnce([summary(2, true), summary(1, false)]);
    createDocumentVersionMock.mockResolvedValue(detail(2));
    render(<DocumentDetail />);
    const history = await screen.findByRole("region", { name: "버전 히스토리" });
    expect(await within(history).findAllByRole("listitem")).toHaveLength(1);

    const file = new File(["%PDF-1.4"], "v2.pdf", { type: "application/pdf" });
    fireEvent.change(screen.getByLabelText(/^새 버전 파일/), { target: { files: [file] } });
    fireEvent.click(screen.getByRole("button", { name: "새 버전 등록" }));

    await waitFor(() => expect(within(history).getAllByRole("listitem")).toHaveLength(2));
    expect(within(history).getAllByRole("listitem")[0]).toHaveTextContent("v2");
    expect(within(within(history).getAllByRole("listitem")[0]).getByText("최신")).toBeInTheDocument();
    expect(fetchVersionsMock).toHaveBeenCalledTimes(2);
  });
});
