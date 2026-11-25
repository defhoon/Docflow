import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Activity } from "../types";
import { ACTIVITY_PAGE_SIZE, ActivityPage } from "./ActivityPage";

const fetchMyActivities = vi.fn();
// 실제 AuthProvider처럼 refresh는 같은 함수를 유지한다.
const refresh = vi.fn();
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api", () => ({ fetchMyActivities: (...args: unknown[]) => fetchMyActivities(...args) }));

function activity(id: number): Activity {
  return {
    id,
    action: "VERSION_CREATED",
    actor: { id: 1, displayName: "Author" },
    project: { id: 1, name: "P" },
    document: { id: 4, title: `문서 ${id}` },
    versionNumber: 2,
    createdAt: "2026-11-25T09:00:00Z",
  };
}

describe("ActivityPage 전체 활동 (SPEC-18)", () => {
  beforeEach(() => fetchMyActivities.mockReset());

  it("최신순 첫 페이지를 보여 주고, 더 보기는 마지막 항목 이전(beforeId)을 불러온다", async () => {
    const first = Array.from({ length: ACTIVITY_PAGE_SIZE }, (_, i) => activity(100 - i));
    fetchMyActivities.mockResolvedValueOnce(first).mockResolvedValueOnce([activity(10)]);
    render(<ActivityPage />);
    expect(await screen.findByTitle("문서 100")).toBeInTheDocument();
    expect(fetchMyActivities).toHaveBeenCalledWith(ACTIVITY_PAGE_SIZE, undefined);
    fireEvent.click(screen.getByRole("button", { name: "더 보기" }));
    expect(await screen.findByTitle("문서 10")).toBeInTheDocument();
    expect(fetchMyActivities).toHaveBeenLastCalledWith(ACTIVITY_PAGE_SIZE, 100 - ACTIVITY_PAGE_SIZE + 1);
    // 마지막 페이지(개수 미만)면 더 보기를 숨긴다
    expect(screen.queryByRole("button", { name: "더 보기" })).not.toBeInTheDocument();
  });

  it("활동이 없으면 빈 상태를 보여 준다", async () => {
    fetchMyActivities.mockResolvedValue([]);
    render(<ActivityPage />);
    expect(await screen.findByText("아직 문서 활동이 없습니다.")).toBeInTheDocument();
  });
});
