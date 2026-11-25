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
    description: "1장 개요와 일정",
    status: "ACTIVE",
    createdBy: { id: 2, displayName: "Member" },
    createdAt: "2026-10-27T10:00:00Z",
    latestVersionNumber: 2,
    finalVersionNumber: null,
    updatedAt: "2026-10-28T10:00:00Z",
    latestUploader: { id: 2, displayName: "Member" },
    fileType: "PDF",
  },
  {
    id: 1,
    title: "가이드",
    description: null,
    status: "FINALIZED",
    createdBy: { id: 1, displayName: "Owner" },
    createdAt: "2026-10-27T09:00:00Z",
    latestVersionNumber: 3,
    finalVersionNumber: 1,
    updatedAt: "2026-10-29T10:00:00Z",
    latestUploader: { id: 1, displayName: "Owner" },
    fileType: "DOCX",
  },
];

describe("DocumentList 문서 표 (SPEC-03 §8.2, SPEC-09, SPEC-18)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchDocumentsMock.mockReset();
  });

  it("표에 형식·제목·설명·상태·최신·최종본·최근 업로더를 표시하고 기본은 최근 업데이트순이다", async () => {
    fetchDocumentsMock.mockResolvedValue(documents);
    render(<DocumentList projectId="7" />);
    expect(screen.getByRole("status")).toHaveTextContent("문서를 불러오는 중...");
    const table = await screen.findByRole("table", { name: "문서 목록" });
    expect(fetchDocumentsMock).toHaveBeenCalledWith("7");
    expect(within(table).getAllByRole("columnheader").map((cell) => cell.textContent)).toEqual(["문서", "상태", "최신", "최종본", "업데이트", "열기"]);
    const rows = within(table).getAllByRole("row").slice(1);
    expect(rows).toHaveLength(2);
    // 최근 업데이트순: 가이드(10/29)가 먼저
    expect(within(rows[0]).getByRole("link", { name: "가이드" })).toHaveAttribute("href", "/projects/7/documents/1");
    expect(rows[0]).toHaveTextContent("DOCX");
    expect(rows[0]).toHaveTextContent("최종 확정");
    expect(rows[0]).toHaveTextContent("v3");
    expect(rows[0]).toHaveTextContent("v1");
    expect(rows[0]).toHaveTextContent("설명 없음");
    expect(within(rows[1]).getByTitle("1장 개요와 일정")).toBeInTheDocument();
    expect(rows[1]).toHaveTextContent("Member");
    expect(rows[1]).toHaveTextContent("협업 중");
    expect(within(rows[1]).getByLabelText("없음")).toBeInTheDocument();
  });

  it("제목으로 검색하고, 상태로 거르고, 정렬을 바꾼다", async () => {
    fetchDocumentsMock.mockResolvedValue(documents);
    render(<DocumentList projectId="7" />);
    const table = await screen.findByRole("table", { name: "문서 목록" });
    const titles = () => within(table).getAllByRole("link").map((link) => link.textContent);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "title" } });
    expect(titles()).toEqual(["가이드", "둘째 문서"]);
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "created" } });
    expect(titles()).toEqual(["둘째 문서", "가이드"]);
    fireEvent.click(screen.getByRole("button", { name: /최종 확정/ }));
    expect(titles()).toEqual(["가이드"]);
    fireEvent.click(screen.getByRole("button", { name: /전체/ }));
    fireEvent.change(screen.getByRole("searchbox", { name: "문서 제목 검색" }), { target: { value: "둘째" } });
    expect(titles()).toEqual(["둘째 문서"]);
    fireEvent.change(screen.getByRole("searchbox", { name: "문서 제목 검색" }), { target: { value: "없는 문서" } });
    expect(screen.getByText('"없는 문서"와 일치하는 문서가 없습니다.')).toBeInTheDocument();
  });

  it("문서가 없으면 빈 상태에서 등록을 안내한다", async () => {
    fetchDocumentsMock.mockResolvedValue([]);
    render(<DocumentList projectId="7" />);
    expect(await screen.findByText("아직 등록된 문서가 없습니다.")).toBeInTheDocument();
    expect(screen.getByText(/'문서 등록'으로/)).toBeInTheDocument();
  });

  it("실패하면 오류와 다시 시도를 표시하고 다시 요청한다", async () => {
    fetchDocumentsMock.mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."));
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
    await vi.waitFor(() => expect(refresh).toHaveBeenCalled());
  });
});
