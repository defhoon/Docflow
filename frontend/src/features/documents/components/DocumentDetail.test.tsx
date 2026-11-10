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
  status: "ACTIVE",
  createdBy: { id: 1, displayName: "Author" },
  createdAt: "2026-10-27T09:00:00Z",
  updatedAt: "2026-10-27T09:00:00Z",
  finalization: null,
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

  // #28 협업 작업 공간: 머리글 메타데이터·빵부스러기·최신 버전 정보. 버전·피드백·활동은 각 하위 컴포넌트 테스트에서 검증한다.
  it("머리글에 제목·상태·작성자·설명을, 최신 버전 정보 카드에 파일 메타데이터를 표시한다", async () => {
    fetchDocumentMock.mockResolvedValue(detail);
    render(<DocumentDetail />);

    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중...");
    expect(await screen.findByRole("heading", { level: 1, name: "요구사항 명세서" })).toBeInTheDocument();
    expect(fetchDocumentMock).toHaveBeenCalledWith("7", "3");
    expect(screen.getByText("협업 중")).toBeInTheDocument();
    expect(screen.getByText("작성자 Author")).toBeInTheDocument();
    expect(screen.getByText("최신 버전 v1")).toBeInTheDocument();
    expect(screen.getByText("requirements.pdf")).toBeInTheDocument();
    expect(screen.getByText("1.5 MB")).toHaveAttribute("title", "1,572,864 바이트");
    expect(screen.getByText(/1행/)).toHaveTextContent("1행 2행");
    // 프로젝트 이름을 불러오지 못하면(목 미설정) 두 번째 빵부스러기도 "프로젝트"로 표시된다.
    expect(screen.getAllByRole("link", { name: "프로젝트" }).map((link) => link.getAttribute("href"))).toEqual(["/projects", "/projects/7"]);
    expect(screen.getByRole("heading", { name: "피드백 · v1" })).toBeInTheDocument();
    // FINALIZED가 아니므로 읽기 전용 안내가 없다
    expect(screen.queryByText(/종료되었습니다/)).not.toBeInTheDocument();
  });

  it("FINALIZED 문서는 확정 정보를 강조하고 업로드·피드백 작성 영역을 제거한다", async () => {
    fetchDocumentMock.mockResolvedValue({
      ...detail,
      status: "FINALIZED",
      finalization: { versionNumber: 1, finalizedBy: { id: 1, displayName: "Owner" }, finalizedAt: "2026-11-10T09:00:00Z" },
    });
    render(<DocumentDetail />);

    expect(await screen.findByText("v1이 최종 버전으로 확정되었습니다.")).toBeInTheDocument();
    expect(screen.getByText("최종 확정")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "새 문서 등록" })).toHaveAttribute("href", "/projects/7/documents/new");
    expect(screen.queryByRole("button", { name: "새 버전 등록" })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/피드백 남기기/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /최종 확정…/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /다시 열기|재개/ })).not.toBeInTheDocument();
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
