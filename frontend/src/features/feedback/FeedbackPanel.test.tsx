import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { FeedbackPanel } from "./FeedbackPanel";

const refresh = vi.fn();
const fetchFeedbacksMock = vi.fn();
const createFeedbackMock = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("./api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./api")>()),
  fetchFeedbacks: (...args: unknown[]) => fetchFeedbacksMock(...args),
  createFeedback: (...args: unknown[]) => createFeedbackMock(...args),
}));

const item = (id: number, content: string) => ({
  id,
  versionNumber: 2,
  author: { id: 1, displayName: "Member" },
  content,
  createdAt: "2026-11-10T09:00:00Z",
});

function renderPanel(canWrite = true, onFinalizedConflict = vi.fn()) {
  render(
    <FeedbackPanel projectId="7" documentId="3" versionNumber={2} canWrite={canWrite} onFinalizedConflict={onFinalizedConflict} />,
  );
  return onFinalizedConflict;
}

describe("FeedbackPanel (SPEC-08)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchFeedbacksMock.mockReset();
    createFeedbackMock.mockReset();
  });

  it("선택한 버전의 피드백을 작성 순으로 텍스트로만 표시한다 (AC-FB-02, 09)", async () => {
    fetchFeedbacksMock.mockResolvedValue([item(1, "첫 의견"), item(2, "<b>굵게</b>\n둘째 줄")]);
    renderPanel();

    const list = await screen.findByRole("list", { name: "v2 피드백" });
    expect(fetchFeedbacksMock).toHaveBeenCalledWith("7", "3", 2);
    expect(list.querySelectorAll("li")).toHaveLength(2);
    expect(screen.getByText(/<b>굵게<\/b>/)).toBeInTheDocument();
    expect(list.querySelector("b")).toBeNull();
  });

  it("작성하면 목록 끝에 추가하고 입력을 비운다 (AC-FB-01)", async () => {
    fetchFeedbacksMock.mockResolvedValue([]);
    createFeedbackMock.mockResolvedValue(item(5, "새 의견"));
    renderPanel();

    expect(await screen.findByText("v2에 남긴 피드백이 없습니다.")).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText("v2에 피드백 남기기"), { target: { value: "새 의견" } });
    fireEvent.click(screen.getByRole("button", { name: "피드백 등록" }));

    expect(await screen.findByText("새 의견")).toBeInTheDocument();
    expect(createFeedbackMock).toHaveBeenCalledWith("7", "3", 2, "새 의견");
    expect(screen.getByLabelText("v2에 피드백 남기기")).toHaveValue("");
  });

  it("빈 내용·2,000자 초과는 요청하지 않는다", async () => {
    fetchFeedbacksMock.mockResolvedValue([]);
    renderPanel();
    await screen.findByText("v2에 남긴 피드백이 없습니다.");

    fireEvent.click(screen.getByRole("button", { name: "피드백 등록" }));
    expect(screen.getByRole("alert")).toHaveTextContent("피드백 내용을 입력해 주세요.");
    fireEvent.change(screen.getByLabelText("v2에 피드백 남기기"), { target: { value: "가".repeat(2001) } });
    fireEvent.click(screen.getByRole("button", { name: "피드백 등록" }));
    expect(screen.getByRole("alert")).toHaveTextContent("2,000자 이하");
    expect(createFeedbackMock).not.toHaveBeenCalled();
  });

  it("409 DOCUMENT_ALREADY_FINALIZED이면 안내하고 상위에 알린다 (AC-FB-06)", async () => {
    fetchFeedbacksMock.mockResolvedValue([]);
    createFeedbackMock.mockRejectedValue(new ApiError("http", 409, "DOCUMENT_ALREADY_FINALIZED", "x"));
    const onConflict = renderPanel();
    await screen.findByText("v2에 남긴 피드백이 없습니다.");

    fireEvent.change(screen.getByLabelText("v2에 피드백 남기기"), { target: { value: "늦은 의견" } });
    fireEvent.click(screen.getByRole("button", { name: "피드백 등록" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("최종 확정되어");
    expect(onConflict).toHaveBeenCalled();
  });

  it("작성 권한이 없으면(FINALIZED) 폼 없이 조회만 한다", async () => {
    fetchFeedbacksMock.mockResolvedValue([item(1, "기존 의견")]);
    renderPanel(false);

    expect(await screen.findByText("기존 의견")).toBeInTheDocument();
    expect(screen.queryByLabelText("v2에 피드백 남기기")).not.toBeInTheDocument();
  });

  it("목록 조회 실패는 다시 시도할 수 있다", async () => {
    fetchFeedbacksMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "x")).mockResolvedValueOnce([]);
    renderPanel();

    expect(await screen.findByText("피드백을 불러오지 못했습니다.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(fetchFeedbacksMock).toHaveBeenCalledTimes(2));
  });
});
