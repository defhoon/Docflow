import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { DocumentList } from "./DocumentList";

const refresh = vi.fn();
const fetchDocumentsMock = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchDocuments: (...args: unknown[]) => fetchDocumentsMock(...args),
}));

const documents = [
  {
    id: 2,
    title: "둘째 문서",
    status: "DRAFT",
    createdBy: { id: 2, displayName: "Member" },
    createdAt: "2026-10-27T10:00:00Z",
    latestVersionNumber: 2,
  },
  {
    id: 1,
    title: "첫째 문서",
    status: "APPROVED",
    createdBy: { id: 1, displayName: "Owner" },
    createdAt: "2026-10-27T09:00:00Z",
    latestVersionNumber: 1,
  },
];

describe("DocumentList (SPEC-03 §8.2, AC-DOC-26)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchDocumentsMock.mockReset();
  });

  it("로딩 후 서버 순서대로 제목·상태·작성자·최신 버전·상세 링크를 표시한다", async () => {
    fetchDocumentsMock.mockResolvedValue(documents);
    render(<DocumentList projectId="7" />);

    expect(screen.getByRole("status")).toHaveTextContent("문서를 불러오는 중...");
    const items = await screen.findAllByRole("listitem");
    expect(fetchDocumentsMock).toHaveBeenCalledWith("7");
    expect(items).toHaveLength(2);
    expect(within(items[0]).getByRole("link")).toHaveAttribute("href", "/projects/7/documents/2");
    expect(items[0]).toHaveTextContent("둘째 문서");
    expect(items[0]).toHaveTextContent("초안");
    expect(items[0]).toHaveTextContent("Member · v2");
    expect(items[1]).toHaveTextContent("승인");
    expect(screen.getByRole("link", { name: "문서 등록" })).toHaveAttribute("href", "/projects/7/documents/new");
  });

  it("문서가 없으면 빈 상태와 등록 링크를 표시한다", async () => {
    fetchDocumentsMock.mockResolvedValue([]);
    render(<DocumentList projectId="7" />);

    expect(await screen.findByText("아직 등록된 문서가 없습니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "문서 등록" })).toBeInTheDocument();
  });

  it("실패하면 오류와 다시 시도를 표시하고 다시 요청한다", async () => {
    fetchDocumentsMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "x"));
    fetchDocumentsMock.mockResolvedValueOnce([]);
    render(<DocumentList projectId="7" />);

    expect(await screen.findByRole("alert")).toHaveTextContent("문서 목록을 불러오지 못했습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByText("아직 등록된 문서가 없습니다.")).toBeInTheDocument();
    expect(fetchDocumentsMock).toHaveBeenCalledTimes(2);
  });

  it("401이면 세션 만료 흐름을 시작한다", async () => {
    fetchDocumentsMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    render(<DocumentList projectId="7" />);

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalled();
  });
});
