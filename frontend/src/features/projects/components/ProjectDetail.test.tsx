import { act, fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { ProjectDetail } from "./ProjectDetail";

const refresh = vi.fn();
const fetchProjectMock = vi.fn();
let params: { projectId: string };

vi.mock("next/navigation", () => ({
  useParams: () => params,
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchProject: (...args: unknown[]) => fetchProjectMock(...args),
}));

const project = {
  id: 7,
  name: "DocFlow 기획",
  description: "1행\n2행",
  owner: { id: 1, displayName: "Author" },
  createdAt: "2026-01-02T03:04:05Z",
  updatedAt: "2026-01-02T03:04:05Z",
};

describe("ProjectDetail", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchProjectMock.mockReset();
    params = { projectId: "7" };
  });

  it("로딩 후 이름·설명·Owner 표시 이름·생성일과 목록 링크를 표시한다", async () => {
    fetchProjectMock.mockResolvedValue(project);
    render(<ProjectDetail />);

    expect(screen.getByRole("status")).toHaveTextContent("불러오는 중...");
    expect(await screen.findByRole("heading", { name: "DocFlow 기획" })).toBeInTheDocument();
    expect(fetchProjectMock).toHaveBeenCalledWith("7");
    expect(screen.getByText("Author")).toBeInTheDocument();
    expect(screen.getByText(/1행/)).toHaveTextContent("1행 2행");
    expect(document.querySelector("time")).toHaveAttribute("dateTime", "2026-01-02T03:04:05Z");
    expect(screen.getByRole("link", { name: "프로젝트 목록으로" })).toHaveAttribute("href", "/projects");
  });

  it("설명이 없으면 안내 문구를 표시한다", async () => {
    fetchProjectMock.mockResolvedValue({ ...project, description: null });
    render(<ProjectDetail />);

    expect(await screen.findByText("설명이 없습니다.")).toBeInTheDocument();
  });

  // AC-PROJECT-21
  it("404와 400은 같은 '찾을 수 없습니다' 화면으로 표시한다", async () => {
    for (const error of [
      new ApiError("http", 404, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."),
      new ApiError("http", 400, "BAD_REQUEST", "요청이 올바르지 않습니다."),
    ]) {
      fetchProjectMock.mockRejectedValueOnce(error);
      const { unmount } = render(<ProjectDetail />);

      expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 찾을 수 없습니다.");
      expect(screen.getByRole("link", { name: "프로젝트 목록으로" })).toHaveAttribute("href", "/projects");
      expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
      unmount();
    }
  });

  it("숫자가 아닌 ID는 요청 없이 같은 404 화면을 표시한다", () => {
    params = { projectId: "abc" };
    render(<ProjectDetail />);

    expect(screen.getByRole("alert")).toHaveTextContent("프로젝트를 찾을 수 없습니다.");
    expect(fetchProjectMock).not.toHaveBeenCalled();
  });

  it("그 밖의 오류는 오류와 다시 시도를 표시한다", async () => {
    fetchProjectMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류"));
    fetchProjectMock.mockResolvedValueOnce(project);
    render(<ProjectDetail />);

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByRole("heading", { name: "DocFlow 기획" })).toBeInTheDocument();
    expect(fetchProjectMock).toHaveBeenCalledTimes(2);
  });

  // AC-PROJECT-22
  it("401이면 useAuth().refresh()를 호출한다", async () => {
    fetchProjectMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다."));
    render(<ProjectDetail />);

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("다른 프로젝트로 바뀌면 이전 프로젝트의 늦은 응답을 표시하지 않는다", async () => {
    let resolveFirst: (value: unknown) => void = () => {};
    fetchProjectMock.mockReturnValueOnce(new Promise((resolve) => (resolveFirst = resolve)));
    fetchProjectMock.mockResolvedValueOnce({ ...project, id: 8, name: "두 번째" });
    const { rerender } = render(<ProjectDetail />);

    params = { projectId: "8" };
    rerender(<ProjectDetail />);
    expect(await screen.findByRole("heading", { name: "두 번째" })).toBeInTheDocument();

    await act(async () => resolveFirst(project));
    expect(screen.getByRole("heading", { name: "두 번째" })).toBeInTheDocument();
  });
});
