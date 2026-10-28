import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { DocumentDetail } from "./DocumentDetail";

const refresh = vi.fn();
const fetchDocumentMock = vi.fn();
let params: { projectId: string; documentId: string };

vi.mock("next/navigation", () => ({
  useParams: () => params,
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchDocument: (...args: unknown[]) => fetchDocumentMock(...args),
}));

const detail = {
  id: 3,
  title: "요구사항 명세서",
  description: "1행\n2행",
  status: "DRAFT",
  createdBy: { id: 1, displayName: "Author" },
  createdAt: "2026-10-27T09:00:00Z",
  updatedAt: "2026-10-27T09:00:00Z",
  latestVersion: {
    versionNumber: 1,
    originalFilename: "requirements.pdf",
    sizeBytes: 1_572_864,
    uploadedAt: "2026-10-27T09:00:00Z",
  },
};

describe("DocumentDetail (SPEC-03 §8.2, AC-DOC-26)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchDocumentMock.mockReset();
    params = { projectId: "7", documentId: "3" };
  });

  it("메타데이터를 표시하고 다운로드 UI는 없다", async () => {
    fetchDocumentMock.mockResolvedValue(detail);
    render(<DocumentDetail />);

    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중...");
    expect(await screen.findByRole("heading", { name: "요구사항 명세서" })).toBeInTheDocument();
    expect(fetchDocumentMock).toHaveBeenCalledWith("7", "3");
    expect(screen.getByText("초안")).toBeInTheDocument();
    expect(screen.getByText("Author")).toBeInTheDocument();
    expect(screen.getByText("v1")).toBeInTheDocument();
    expect(screen.getByText("requirements.pdf")).toBeInTheDocument();
    expect(screen.getByText("1.5 MB")).toHaveAttribute("title", "1,572,864 바이트");
    expect(screen.getByText(/1행/)).toHaveTextContent("1행 2행");
    expect(screen.queryByText(/다운로드/)).not.toBeInTheDocument();
    expect(screen.queryByText(/미리보기/)).not.toBeInTheDocument();
    expect(screen.getAllByRole("link").map((link) => link.getAttribute("href"))).toEqual(["/projects/7"]);
  });

  it("다른 프로젝트 문서·없는 문서(DOCUMENT_NOT_FOUND)는 문서 없음", async () => {
    fetchDocumentMock.mockRejectedValue(new ApiError("http", 404, "DOCUMENT_NOT_FOUND", "x"));
    render(<DocumentDetail />);

    expect(await screen.findByRole("alert")).toHaveTextContent("문서를 찾을 수 없습니다.");
  });

  it("구성원이 아닌 프로젝트(PROJECT_NOT_FOUND)는 프로젝트 없음", async () => {
    fetchDocumentMock.mockRejectedValue(new ApiError("http", 404, "PROJECT_NOT_FOUND", "x"));
    render(<DocumentDetail />);

    expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 찾을 수 없습니다.");
  });

  it("숫자가 아닌 ID는 요청 없이 문서 없음", () => {
    params = { projectId: "7", documentId: "abc" };
    render(<DocumentDetail />);

    expect(screen.getByRole("alert")).toHaveTextContent("문서를 찾을 수 없습니다.");
    expect(fetchDocumentMock).not.toHaveBeenCalled();
  });

  it("그 밖의 오류는 다시 시도할 수 있다", async () => {
    fetchDocumentMock.mockRejectedValueOnce(new ApiError("network", null, null, "x"));
    fetchDocumentMock.mockResolvedValueOnce(detail);
    render(<DocumentDetail />);

    expect(await screen.findByRole("alert")).toHaveTextContent("문서를 불러오지 못했습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByRole("heading", { name: "요구사항 명세서" })).toBeInTheDocument();
  });

  it("401이면 세션 만료 흐름을 시작한다", async () => {
    fetchDocumentMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    render(<DocumentDetail />);

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalled();
  });
});
