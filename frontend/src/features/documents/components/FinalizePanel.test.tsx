import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { FinalizePanel } from "./FinalizePanel";

const refresh = vi.fn();
const finalizeMock = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api-finalize", () => ({ finalizeDocument: (...args: unknown[]) => finalizeMock(...args) }));

const version = (versionNumber: number, latest: boolean) => ({
  versionNumber,
  originalFilename: `v${versionNumber}.pdf`,
  sizeBytes: 10,
  uploadedBy: { id: 1, displayName: "Owner" },
  uploadedAt: "2026-11-10T09:00:00Z",
  latest,
});

const versions = [version(3, true), version(2, false), version(1, false)];

function renderPanel(defaultVersion = 3) {
  const onFinalized = vi.fn();
  const onConflict = vi.fn();
  render(
    <FinalizePanel projectId="7" documentId="3" versions={versions} defaultVersion={defaultVersion} onFinalized={onFinalized} onConflict={onConflict} />,
  );
  return { onFinalized, onConflict };
}

describe("FinalizePanel (SPEC-07)", () => {
  beforeEach(() => {
    refresh.mockReset();
    finalizeMock.mockReset();
  });

  it("확인 대화상자에서 되돌릴 수 없음을 안내하고, 과거 버전이면 최신이 아님을 강조한다", () => {
    renderPanel();
    fireEvent.change(screen.getByLabelText("최종 버전으로 확정할 버전"), { target: { value: "2" } });
    fireEvent.click(screen.getByRole("button", { name: "v2 최종 확정…" }));

    const dialog = screen.getByRole("dialog", { name: "v2을 최종 버전으로 확정할까요?" });
    expect(dialog).toHaveTextContent("공식 최종본");
    expect(dialog).toHaveTextContent("최신 버전(v3)이 아닙니다.");
    expect(dialog).toHaveTextContent("새 버전 업로드와 피드백 작성이 모두 막힙니다.");
    expect(dialog).toHaveTextContent("확정을 취소하거나 협업을 다시 열 수 없습니다.");
    expect(screen.getByRole("button", { name: "취소" })).toHaveFocus();
    expect(finalizeMock).not.toHaveBeenCalled();
  });

  it("취소·Esc는 요청 없이 닫는다", () => {
    renderPanel();
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정…" }));
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정…" }));
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(finalizeMock).not.toHaveBeenCalled();
  });

  it("확정하면 선택한 버전으로 요청하고 결과를 상위에 전달한다 (최신 버전은 경고 없음)", async () => {
    const finalized = { id: 3, status: "FINALIZED" };
    finalizeMock.mockResolvedValue(finalized);
    const { onFinalized } = renderPanel();
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정…" }));
    expect(screen.getByRole("dialog")).not.toHaveTextContent("최신 버전(v3)이 아닙니다.");
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));

    await vi.waitFor(() => expect(onFinalized).toHaveBeenCalledWith(finalized));
    expect(finalizeMock).toHaveBeenCalledWith("7", "3", 3);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("이미 확정됨(409)·권한 없음(403)을 안내한다", async () => {
    finalizeMock.mockRejectedValueOnce(new ApiError("http", 409, "DOCUMENT_ALREADY_FINALIZED", "x"));
    const { onConflict } = renderPanel();
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정…" }));
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("이미 최종 확정된 문서입니다.");
    expect(onConflict).toHaveBeenCalled();

    finalizeMock.mockRejectedValueOnce(new ApiError("http", 403, "ACCESS_DENIED", "x"));
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));
    expect(await screen.findByText("프로젝트 오너만 최종 버전을 확정할 수 있습니다.")).toBeInTheDocument();
  });
});
