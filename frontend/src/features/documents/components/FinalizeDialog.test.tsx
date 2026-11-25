import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api-client";

import type { DocumentVersionSummary } from "../version-files";
import { FinalizeDialog } from "./FinalizeDialog";

const refresh = vi.fn();
const finalizeMock = vi.fn();
const onFinalized = vi.fn();
const onConflict = vi.fn();
const onClose = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api-finalize", () => ({ finalizeDocument: (...args: unknown[]) => finalizeMock(...args) }));

function version(versionNumber: number, latest = false): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: `spec-v${versionNumber}.pdf`,
    sizeBytes: 10,
    uploadedBy: { id: 1, displayName: "Owner" },
    uploadedAt: "2026-11-04T09:00:00Z",
    versionNote: null,
    latest,
    feedbackCount: 0,
  };
}

const VERSIONS = [version(3, true), version(2), version(1)];

function renderDialog(defaultVersion = 3) {
  return render(
    <ToastProvider>
      <FinalizeDialog
        open
        onClose={onClose}
        projectId="7"
        documentId="3"
        versions={VERSIONS}
        defaultVersion={defaultVersion}
        onFinalized={onFinalized}
        onConflict={onConflict}
      />
    </ToastProvider>,
  );
}

describe("FinalizeDialog (SPEC-07, SPEC-16 AC-WS-08)", () => {
  beforeEach(() => vi.clearAllMocks());

  it("확인 체크 전에는 확정할 수 없고, 최신 버전이면 경고가 없다", () => {
    renderDialog();
    const dialog = screen.getByRole("dialog", { name: "최종본 확정" });
    expect(within(dialog).getByRole("radio", { name: /v3/ })).toBeChecked();
    expect(within(dialog).queryByText(/최신 버전\(v3\)이 아닙니다/)).not.toBeInTheDocument();
    expect(within(dialog).getByText(/확정을 취소하거나 협업을 다시 열 수 없습니다/)).toBeInTheDocument();
    expect(within(dialog).getByRole("button", { name: "v3 최종 확정" })).toBeDisabled();
    fireEvent.click(within(dialog).getByRole("checkbox", { name: "되돌릴 수 없다는 것을 확인했습니다." }));
    expect(within(dialog).getByRole("button", { name: "v3 최종 확정" })).toBeEnabled();
  });

  it("최신이 아닌 버전을 고르면 경고하고, 그 버전으로 확정한다", async () => {
    finalizeMock.mockResolvedValue({ id: 3, status: "FINALIZED" });
    renderDialog();
    fireEvent.click(screen.getByRole("radio", { name: /v2/ }));
    expect(screen.getByText("최신 버전(v3)이 아닙니다.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "v2 최종 확정" }));
    expect(screen.getByRole("button", { name: "확정하는 중..." })).toBeDisabled();
    await waitFor(() => expect(onFinalized).toHaveBeenCalledWith({ id: 3, status: "FINALIZED" }));
    expect(finalizeMock).toHaveBeenCalledWith("7", "3", 2);
    expect(finalizeMock).toHaveBeenCalledTimes(1);
    expect(onClose).toHaveBeenCalled();
  });

  it("이미 확정되었으면(409) 안내하고 다시 조회를 요청한다", async () => {
    finalizeMock.mockRejectedValue(new ApiError("http", 409, "DOCUMENT_ALREADY_FINALIZED", "x"));
    renderDialog();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("이미 최종 확정된 문서입니다.");
    expect(onConflict).toHaveBeenCalled();
    expect(onFinalized).not.toHaveBeenCalled();
  });

  it("오너가 아니면(403) 서버 판정을 안내한다", async () => {
    finalizeMock.mockRejectedValue(new ApiError("http", 403, "ACCESS_DENIED", "x"));
    renderDialog();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트 오너만 최종본을 확정할 수 있습니다.");
  });

  it("없는 버전(404)이면 다시 선택하도록 안내한다", async () => {
    finalizeMock.mockRejectedValue(new ApiError("http", 404, "DOCUMENT_VERSION_NOT_FOUND", "x"));
    renderDialog();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "v3 최종 확정" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("선택한 버전을 찾을 수 없습니다.");
  });

  it("취소하면 선택·확인을 초기화하고 닫는다", () => {
    renderDialog();
    fireEvent.click(screen.getByRole("checkbox"));
    fireEvent.click(screen.getByRole("button", { name: "취소" }));
    expect(onClose).toHaveBeenCalled();
  });
});
