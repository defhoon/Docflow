import { existsSync } from "node:fs";
import { resolve } from "node:path";

import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { DocumentDetail as DocumentDetailData } from "../types";
import { DocumentDetail } from "./DocumentDetail";

const refresh = vi.fn();
const fetchDocumentMock = vi.fn();
const createDocumentVersionMock = vi.fn();

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

function detail(overrides: Partial<DocumentDetailData> = {}, versionNumber = 1): DocumentDetailData {
  return {
    id: 3,
    title: "요구사항 명세서",
    description: null,
    status: "ACTIVE",
    createdBy: { id: 1, displayName: "Author" },
    createdAt: "2026-10-27T09:00:00Z",
    updatedAt: "2026-10-27T09:00:00Z",
    finalization: null,
    latestVersion: {
      versionNumber,
      originalFilename: `v${versionNumber}.pdf`,
      sizeBytes: 1024,
      uploadedAt: "2026-10-27T09:00:00Z",
    },
    ...overrides,
  };
}

function choosePdf() {
  const file = new File(["%PDF-1.4"], "v2.pdf", { type: "application/pdf" });
  fireEvent.change(screen.getByLabelText(/^새 버전 파일/), { target: { files: [file] } });
}

describe("DocumentDetail — 새 버전 등록 영역 (SPEC-04 §9.1, AC-VER-16)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchDocumentMock.mockReset();
    createDocumentVersionMock.mockReset();
  });

  it("등록 영역은 문서 상세 화면 안에 있고 별도 경로가 없다 (OQ-6)", async () => {
    fetchDocumentMock.mockResolvedValue(detail());
    render(<DocumentDetail />);

    expect(await screen.findByRole("heading", { name: "새 버전 등록" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새 버전 등록" })).toBeEnabled();
    const detailRoute = resolve(__dirname, "../../../app/projects/[projectId]/documents/[documentId]");
    expect(existsSync(resolve(detailRoute, "page.tsx"))).toBe(true);
    expect(existsSync(resolve(detailRoute, "versions"))).toBe(false);
  });

  it("등록에 성공하면 로딩 화면 없이 최신 버전 정보가 응답으로 갱신된다", async () => {
    fetchDocumentMock.mockResolvedValue(detail());
    createDocumentVersionMock.mockResolvedValue(detail({ updatedAt: "2026-11-04T09:00:00Z" }, 2));
    render(<DocumentDetail />);
    expect(await screen.findByText("v1")).toBeInTheDocument();

    choosePdf();
    fireEvent.click(screen.getByRole("button", { name: "새 버전 등록" }));

    expect(await screen.findByText("v2")).toBeInTheDocument();
    expect(screen.getByText("v2.pdf")).toBeInTheDocument();
    expect(screen.getByText("새 버전을 등록했습니다. 최신 버전: v2")).toBeInTheDocument();
    expect(screen.queryByText("불러오는 중...")).not.toBeInTheDocument();
    expect(fetchDocumentMock).toHaveBeenCalledTimes(1);
  });

  it("허용되지 않은 상태의 문서는 등록 영역이 비활성화되고 안내 문구가 보인다 (OQ-7)", async () => {
    fetchDocumentMock.mockResolvedValue(detail({ status: "FINALIZED" }));
    render(<DocumentDetail />);

    expect(await screen.findByText("초안 또는 수정 요청 상태의 문서에만 새 버전을 등록할 수 있습니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새 버전 등록" })).toBeDisabled();
    expect(screen.getByLabelText(/^새 버전 파일/)).toBeDisabled();
  });

  it("409이면 오류 문구를 유지한 채 로딩 화면 없이 다시 조회해 상태를 반영한다 (I6)", async () => {
    fetchDocumentMock.mockResolvedValueOnce(detail()).mockResolvedValueOnce(detail({ status: "FINALIZED" }));
    createDocumentVersionMock.mockRejectedValue(new ApiError("http", 409, "DOCUMENT_STATUS_CONFLICT", "x"));
    render(<DocumentDetail />);
    await screen.findByText("협업 중");

    choosePdf();
    fireEvent.click(screen.getByRole("button", { name: "새 버전 등록" }));

    expect(await screen.findByText("최종 확정")).toBeInTheDocument();
    expect(fetchDocumentMock).toHaveBeenCalledTimes(2);
    expect(screen.getByRole("alert")).toHaveTextContent("현재 문서 상태에서는 새 버전을 등록할 수 없습니다.");
    expect(screen.getByRole("button", { name: "새 버전 등록" })).toBeDisabled();
    expect(screen.queryByText("불러오는 중...")).not.toBeInTheDocument();
  });

  it("409 후 다시 조회가 실패해도 화면과 오류 문구를 유지한다", async () => {
    fetchDocumentMock
      .mockResolvedValueOnce(detail())
      .mockRejectedValueOnce(new ApiError("network", null, null, "x"));
    createDocumentVersionMock.mockRejectedValue(new ApiError("http", 409, "DOCUMENT_STATUS_CONFLICT", "x"));
    render(<DocumentDetail />);
    await screen.findByText("협업 중");

    choosePdf();
    fireEvent.click(screen.getByRole("button", { name: "새 버전 등록" }));

    await waitFor(() => expect(fetchDocumentMock).toHaveBeenCalledTimes(2));
    expect(screen.getByRole("alert")).toHaveTextContent("현재 문서 상태에서는 새 버전을 등록할 수 없습니다.");
    expect(screen.getByRole("heading", { name: "요구사항 명세서" })).toBeInTheDocument();
  });
});
