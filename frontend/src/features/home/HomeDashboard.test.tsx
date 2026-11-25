import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { HomeDashboard } from "./HomeDashboard";
import type { HomeSummary } from "./types";

const refresh = vi.fn();
const fetchHome = vi.fn();
const fetchProjects = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ user: { displayName: "정의훈" }, refresh }) }));
vi.mock("./api", () => ({ fetchHome: (...args: unknown[]) => fetchHome(...args) }));
vi.mock("@/features/projects/api", () => ({ fetchProjects: (...args: unknown[]) => fetchProjects(...args) }));

const summary: HomeSummary = {
  projectCount: 2,
  activeDocumentCount: 3,
  finalizedDocumentCount: 1,
  recentDocuments: [
    {
      id: 4,
      title: "11월 정기 회의록",
      status: "ACTIVE",
      project: { id: 1, name: "2026 상반기 서비스 기획" },
      latestVersionNumber: 3,
      finalVersionNumber: null,
      updatedAt: "2026-11-25T09:00:00Z",
      latestUploader: { id: 1, displayName: "Author" },
      fileType: "PDF",
    },
    {
      id: 3,
      title: "요구사항 명세서",
      status: "FINALIZED",
      project: { id: 1, name: "2026 상반기 서비스 기획" },
      latestVersionNumber: 3,
      finalVersionNumber: 2,
      updatedAt: "2026-11-24T09:00:00Z",
      latestUploader: { id: 2, displayName: "Reviewer" },
      fileType: "PDF",
    },
  ],
  recentActivities: [
    {
      id: 20,
      action: "FEEDBACK_CREATED",
      actor: { id: 2, displayName: "Reviewer" },
      project: { id: 1, name: "2026 상반기 서비스 기획" },
      document: { id: 4, title: "11월 정기 회의록" },
      versionNumber: 3,
      createdAt: "2026-11-25T09:30:00Z",
    },
  ],
};

describe("HomeDashboard (SPEC-18)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    fetchHome.mockResolvedValue(summary);
    fetchProjects.mockResolvedValue([{ id: 1, name: "2026 상반기 서비스 기획", createdAt: "2026-11-01T00:00:00Z", myRole: "OWNER" }]);
  });

  it("AC-HOME-UI-01: 서버가 센 지표(참여 프로젝트·협업 중·최종 확정)만 보여 준다", async () => {
    render(<HomeDashboard />);
    const stats = within(screen.getByRole("region", { name: "요약" }));
    expect(await stats.findByText("2")).toBeInTheDocument();
    expect(stats.getByText("참여 프로젝트").closest("a")).toHaveAttribute("href", "/projects");
    expect(stats.getByText("3")).toBeInTheDocument();
    expect(stats.getByText("1")).toBeInTheDocument();
    // 데이터가 없는 지표(검토 요청·알림 등)는 두지 않는다
    expect(screen.queryByText(/검토 대기|검토 요청|알림/)).not.toBeInTheDocument();
  });

  it("AC-HOME-UI-02: 최근 문서는 문서 화면으로, 최근 활동은 문서·버전으로 이동하고 전체 활동 경로가 있다", async () => {
    render(<HomeDashboard />);
    const docs = within(await screen.findByRole("region", { name: "최근 업데이트된 문서" }));
    const links = docs.getAllByRole("link");
    expect(links[0]).toHaveAttribute("href", "/projects/1/documents/4");
    expect(links[0]).toHaveTextContent("11월 정기 회의록");
    expect(links[1]).toHaveTextContent("최종 v2");
    const activity = within(screen.getByRole("region", { name: "최근 활동" }));
    expect(activity.getByTitle("11월 정기 회의록")).toHaveAttribute("href", "/projects/1/documents/4?v=3");
    expect(activity.getByRole("link", { name: "전체 활동" })).toHaveAttribute("href", "/activity");
    expect(within(screen.getByRole("region", { name: "내 프로젝트" })).getByRole("link", { name: /2026 상반기/ })).toHaveAttribute("href", "/projects/1");
  });

  it("비어 있으면 빈 상태를, 실패하면 다시 시도를 보여 준다", async () => {
    fetchHome.mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."));
    fetchHome.mockResolvedValueOnce({ ...summary, projectCount: 0, activeDocumentCount: 0, finalizedDocumentCount: 0, recentDocuments: [], recentActivities: [] });
    fetchProjects.mockResolvedValue([]);
    render(<HomeDashboard />);
    expect(await screen.findByText("홈 정보를 불러오지 못했습니다.")).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole("button", { name: "다시 시도" })[0]);
    expect(await screen.findByText("아직 등록된 문서가 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("아직 문서 활동이 없습니다.")).toBeInTheDocument();
    expect(screen.getByText("참여 중인 프로젝트가 없습니다.")).toBeInTheDocument();
  });

  it("401이면 세션 만료 흐름을 시작한다", async () => {
    fetchHome.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    render(<HomeDashboard />);
    await vi.waitFor(() => expect(refresh).toHaveBeenCalled());
  });
});
