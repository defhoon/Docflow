import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";

import type { AiSettings, AnalysisState, SummaryResult } from "../types";
import { AiSettingsCard } from "./AiSettingsCard";
import { VersionSummaryPanel } from "./VersionSummaryPanel";

const refresh = vi.fn();
const fetchSummary = vi.fn();
const requestSummary = vi.fn();
const fetchSettings = vi.fn();
const updateSettings = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api", () => ({
  fetchVersionSummary: (...args: unknown[]) => fetchSummary(...args),
  requestVersionSummary: (...args: unknown[]) => requestSummary(...args),
  fetchAiSettings: (...args: unknown[]) => fetchSettings(...args),
  updateAiSettings: (...args: unknown[]) => updateSettings(...args),
}));

function state(overrides: Partial<AnalysisState<SummaryResult>>): AnalysisState<SummaryResult> {
  return {
    status: "NONE",
    errorCode: null,
    retryable: false,
    aiEnabled: true,
    supported: true,
    result: null,
    model: null,
    updatedAt: null,
    completedAt: null,
    ...overrides,
  };
}

const completed = state({
  status: "COMPLETED",
  model: "claude-sonnet-5-5",
  completedAt: "2026-11-24T09:00:00Z",
  result: {
    summary: "<b>사업 계획</b> 요약입니다.",
    keyPoints: [{ text: "예산 정리", pages: [1, 2] }],
    keyFacts: [{ type: "NUMBER", label: "예산", value: "1,200만원", quote: "예산은 1,200만원", page: 1 }],
    meta: { chunks: 1, droppedFacts: 0, pageCount: 2 },
  },
});

describe("VersionSummaryPanel (SPEC-11)", () => {
  beforeEach(() => {
    fetchSummary.mockReset();
    requestSummary.mockReset();
  });
  afterEach(() => vi.useRealTimers());

  it("완료된 요약을 AI 생성 표시와 함께 텍스트로만 보여 준다", async () => {
    fetchSummary.mockResolvedValue(completed);
    render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={3} />);
    expect(await screen.findByText("<b>사업 계획</b> 요약입니다.")).toBeInTheDocument();
    expect(screen.getByText("AI 생성 · 검토 필요")).toBeInTheDocument();
    expect(screen.getByText("(p.1, 2)")).toBeInTheDocument();
    expect(screen.getByText("1,200만원")).toBeInTheDocument();
    expect(screen.getByText(/원문과 다를 수 있으니/)).toBeInTheDocument();
    expect(fetchSummary).toHaveBeenCalledWith("1", "2", 3);
  });

  it("AI가 꺼진 프로젝트에서 결과가 없으면 아무것도 표시하지 않는다", async () => {
    fetchSummary.mockResolvedValue(state({ aiEnabled: false }));
    const { container } = render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    await act(async () => {});
    expect(container).toBeEmptyDOMElement();
  });

  it("요약이 없으면 외부 전송을 안내하고 요청하면 진행 상태로 바뀐다", async () => {
    fetchSummary.mockResolvedValue(state({}));
    requestSummary.mockResolvedValue(state({ status: "PENDING" }));
    render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    expect(await screen.findByText(/외부 AI 서비스로 전송/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "AI 요약 만들기" }));
    expect(await screen.findByRole("status")).toHaveTextContent("기다리는 중");
    expect(requestSummary).toHaveBeenCalledTimes(1);
  });

  it("진행 중이면 주기적으로 다시 조회한다", async () => {
    vi.useFakeTimers();
    fetchSummary.mockResolvedValueOnce(state({ status: "PROCESSING" })).mockResolvedValue(completed);
    render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    await act(async () => {});
    expect(screen.getByRole("status")).toHaveTextContent("요약하고 있습니다");
    await act(async () => {
      vi.advanceTimersByTime(4000);
    });
    await act(async () => {});
    expect(screen.getByText("<b>사업 계획</b> 요약입니다.")).toBeInTheDocument();
    expect(fetchSummary).toHaveBeenCalledTimes(2);
  });

  it("실패 사유를 안내하고 다시 시도할 수 있는 경우에만 버튼을 둔다", async () => {
    fetchSummary.mockResolvedValue(state({ status: "FAILED", errorCode: "AI_API_ERROR", retryable: true }));
    const { unmount } = render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("일시적인 문제");
    expect(screen.getByRole("button", { name: "다시 시도" })).toBeInTheDocument();
    unmount();

    fetchSummary.mockResolvedValue(state({ status: "FAILED", errorCode: "TEXT_NO_TEXT", retryable: false }));
    render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    expect(await screen.findByRole("alert")).toHaveTextContent("스캔한 이미지 PDF");
    expect(screen.queryByRole("button", { name: "다시 시도" })).not.toBeInTheDocument();
  });

  it("PDF가 아니면 지원하지 않음을 안내한다", async () => {
    fetchSummary.mockResolvedValue(state({ supported: false }));
    render(<VersionSummaryPanel projectId="1" documentId="2" versionNumber={1} />);
    expect(await screen.findByText("AI 요약은 PDF 문서만 지원합니다.")).toBeInTheDocument();
  });
});

function settings(overrides: Partial<AiSettings>): AiSettings {
  return {
    enabled: false,
    available: true,
    canManage: true,
    enabledAt: null,
    enabledByName: null,
    provider: "Anthropic Claude API",
    model: "claude-sonnet-5-5",
    ...overrides,
  };
}

describe("AiSettingsCard (SPEC-11)", () => {
  beforeEach(() => {
    fetchSettings.mockReset();
    updateSettings.mockReset();
  });

  it("오너는 외부 전송 안내에 동의해야 켤 수 있다", async () => {
    fetchSettings.mockResolvedValue(settings({}));
    updateSettings.mockResolvedValue(settings({ enabled: true, enabledAt: "2026-11-24T09:00:00Z", enabledByName: "Owner" }));
    render(
      <ToastProvider>
        <AiSettingsCard projectId="1" />
      </ToastProvider>,
    );
    const enable = await screen.findByRole("button", { name: "AI 기능 켜기" });
    expect(screen.getByText(/외부 AI 서비스\(Anthropic Claude API/)).toBeInTheDocument();
    expect(enable).toBeDisabled();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(enable);
    expect(await screen.findByRole("button", { name: "AI 기능 끄기" })).toBeInTheDocument();
    expect(updateSettings).toHaveBeenCalledWith("1", true, true);
    expect(screen.getByText("켜짐")).toBeInTheDocument();
  });

  it("구성원은 상태만 보고 바꿀 수 없다", async () => {
    fetchSettings.mockResolvedValue(settings({ canManage: false, enabled: true }));
    render(
      <ToastProvider>
        <AiSettingsCard projectId="1" />
      </ToastProvider>,
    );
    expect(await screen.findByText("설정은 프로젝트 오너만 바꿀 수 있습니다.")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("서비스에서 AI를 쓸 수 없으면 카드를 표시하지 않는다", async () => {
    fetchSettings.mockResolvedValue(settings({ available: false }));
    render(
      <ToastProvider>
        <AiSettingsCard projectId="1" />
      </ToastProvider>,
    );
    await act(async () => {});
    expect(screen.queryByRole("heading")).not.toBeInTheDocument();
    expect(screen.queryByText("AI 문서 분석")).not.toBeInTheDocument();
  });
});
