import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { AnalysisState, ChangeSummaryResult } from "../types";
import { ChangeSummaryPanel } from "./ChangeSummaryPanel";

const fetchChange = vi.fn();
const requestChange = vi.fn();
const refresh = vi.fn();
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api", () => ({
  fetchChangeSummary: (...args: unknown[]) => fetchChange(...args),
  requestChangeSummary: (...args: unknown[]) => requestChange(...args),
}));

function state(overrides: Partial<AnalysisState<ChangeSummaryResult>>): AnalysisState<ChangeSummaryResult> {
  return { status: "NONE", errorCode: null, retryable: false, aiEnabled: true, supported: true, result: null, model: null, updatedAt: null, completedAt: null, ...overrides };
}

const completed = state({
  status: "COMPLETED",
  model: "claude-sonnet-5-5",
  result: {
    overview: "예산이 늘었습니다.",
    added: [{ text: "보안 점검 추가", changeIds: [1] }],
    deleted: [],
    modified: [{ text: "예산 증액", changeIds: [0] }],
    keyChanges: [{ type: "NUMBER", label: "예산", before: "1,200만원", after: "1,500만원", changeIds: [0] }],
    significance: { level: "MEDIUM", reason: "예산 변경" },
    meta: { changeCount: 2, droppedItems: 0 },
  },
});

describe("ChangeSummaryPanel (SPEC-14)", () => {
  beforeEach(() => {
    fetchChange.mockReset();
    requestChange.mockReset();
  });

  it("요약·핵심 변경·중요도(참고)를 보여 주고 변경 번호로 비교 결과에 연결한다", async () => {
    fetchChange.mockResolvedValue(completed);
    const onGo = vi.fn();
    render(<ChangeSummaryPanel projectId="7" documentId="3" base={1} target={2} onGoToChange={onGo} />);
    expect(await screen.findByText("예산이 늘었습니다.")).toBeInTheDocument();
    expect(screen.getByText("AI 생성 · 검토 필요")).toBeInTheDocument();
    expect(screen.getByText("영향 보통(참고)")).toBeInTheDocument();
    expect(screen.getByText("1,200만원")).toHaveClass("line-through");
    expect(screen.getByText(/변경 위치는 비교 알고리즘이 정한 것/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "변경 2로 이동" }));
    expect(onGo).toHaveBeenCalledWith(1);
    expect(fetchChange).toHaveBeenCalledWith("7", "3", 1, 2);
  });

  it("요약이 없으면 외부 전송을 안내하고 요청할 수 있다", async () => {
    fetchChange.mockResolvedValue(state({}));
    requestChange.mockResolvedValue(state({ status: "PENDING" }));
    render(<ChangeSummaryPanel projectId="7" documentId="3" base={1} target={2} onGoToChange={vi.fn()} />);
    fireEvent.click(await screen.findByRole("button", { name: "AI 변경 요약 만들기" }));
    expect(await screen.findByText(/요약하고 있습니다/)).toBeInTheDocument();
    expect(requestChange).toHaveBeenCalledWith("7", "3", 1, 2);
  });

  it("AI가 꺼진 프로젝트면 표시하지 않는다", async () => {
    fetchChange.mockResolvedValue(state({ aiEnabled: false }));
    render(<ChangeSummaryPanel projectId="7" documentId="3" base={1} target={2} onGoToChange={vi.fn()} />);
    await screen.findByText(() => true).catch(() => null);
    expect(screen.queryByText("AI 변경 요약")).not.toBeInTheDocument();
  });
});
