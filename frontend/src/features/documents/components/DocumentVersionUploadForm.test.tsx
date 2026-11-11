import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Activity } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { DocumentDetail, DocumentStatus } from "../types";
import { DocumentVersionUploadForm } from "./DocumentVersionUploadForm";

const refresh = vi.fn();
const createDocumentVersionMock = vi.fn();
const onUploaded = vi.fn();
const onConflict = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  createDocumentVersion: (...args: unknown[]) => createDocumentVersionMock(...args),
}));

const NETWORK_MESSAGE =
  "새 버전 등록 결과를 확인하지 못했습니다. 문서 상세 정보를 새로고침하여 최신 버전을 확인한 뒤 다시 시도해 주세요.";
const GUIDE = "최종 확정된 문서는 읽기 전용입니다. 변경이 필요하면 새 문서를 등록해 주세요.";

function updated(versionNumber: number): DocumentDetail {
  return {
    id: 3,
    title: "요구사항",
    description: null,
    status: "ACTIVE",
    createdBy: { id: 1, displayName: "Author" },
    createdAt: "2026-10-27T09:00:00Z",
    updatedAt: "2026-11-04T09:00:00Z",
    finalization: null,
    latestVersion: { versionNumber, originalFilename: "v.pdf", sizeBytes: 10, uploadedAt: "2026-11-04T09:00:00Z" },
  };
}

function pdf(name = "v2.pdf", size = 10): File {
  const file = new File(["%PDF-1.4"], name, { type: "application/pdf" });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

function renderForm(status: DocumentStatus = "ACTIVE") {
  return render(
    <DocumentVersionUploadForm
      projectId="7"
      documentId="3"
      status={status}
      onUploaded={onUploaded}
      onConflict={onConflict}
    />,
  );
}

function fileInput(): HTMLInputElement {
  return screen.getByLabelText(/^새 버전 파일/) as HTMLInputElement;
}

function submitButton(): HTMLButtonElement {
  return screen.getByRole("button", { name: /새 버전 등록|등록 중\.\.\./ }) as HTMLButtonElement;
}

function choose(file: File) {
  fireEvent.change(fileInput(), { target: { files: [file] } });
}

function httpError(status: number, code: string | null, fieldErrors: { field: string; message: string }[] = []) {
  return new ApiError("http", status, code, "x", fieldErrors);
}

describe("DocumentVersionUploadForm (SPEC-04 §9, AC-VER-16)", () => {
  beforeEach(() => {
    refresh.mockReset();
    createDocumentVersionMock.mockReset();
    onUploaded.mockReset();
    onConflict.mockReset();
  });

  it("파일만 보내고 성공하면 상세를 갱신하고 입력을 비운 뒤 성공 문구를 표시한다", async () => {
    const file = pdf();
    createDocumentVersionMock.mockResolvedValue(updated(2));
    renderForm();

    choose(file);
    fireEvent.click(submitButton());

    expect(await screen.findByText("새 버전을 등록했습니다. 최신 버전: v2")).toHaveAttribute("role", "status");
    expect(createDocumentVersionMock).toHaveBeenCalledWith("7", "3", file);
    expect(onUploaded).toHaveBeenCalledWith(updated(2));
    expect(fileInput().files).toHaveLength(0);
    expect(submitButton()).toBeEnabled();
  });

  it("파일 없이 제출하거나 사전 검사에 실패하면 요청하지 않는다", () => {
    renderForm();

    fireEvent.click(submitButton());
    expect(screen.getByText("파일을 선택해 주세요.")).toBeInTheDocument();

    choose(pdf("a.txt"));
    expect(screen.getByText("PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.")).toBeInTheDocument();
    choose(pdf("a.pdf", 0));
    expect(screen.getByText("빈 파일은 업로드할 수 없습니다.")).toBeInTheDocument();
    choose(pdf("a.pdf", 20_971_521));
    expect(screen.getByText("파일은 20MB 이하만 업로드할 수 있습니다.")).toBeInTheDocument();
    fireEvent.click(submitButton());

    expect(createDocumentVersionMock).not.toHaveBeenCalled();
  });

  it("20,971,520바이트 파일은 보낸다", async () => {
    createDocumentVersionMock.mockResolvedValue(updated(2));
    renderForm();

    choose(pdf("a.pdf", 20_971_520));
    fireEvent.click(submitButton());

    await waitFor(() => expect(createDocumentVersionMock).toHaveBeenCalledTimes(1));
  });

  it("제출 중에는 입력·버튼을 비활성화하고 연속 제출을 막는다", async () => {
    let resolve: (value: DocumentDetail) => void = () => undefined;
    createDocumentVersionMock.mockReturnValue(new Promise<DocumentDetail>((r) => (resolve = r)));
    renderForm();

    choose(pdf());
    const form = submitButton().closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(createDocumentVersionMock).toHaveBeenCalledTimes(1);
    expect(submitButton()).toBeDisabled();
    expect(submitButton()).toHaveTextContent("등록 중...");
    expect(fileInput()).toBeDisabled();
    expect(screen.getByText("파일을 업로드하는 중입니다. 화면을 닫지 마세요.")).toBeInTheDocument();

    await act(async () => resolve(updated(2)));
    expect(submitButton()).toBeEnabled();
  });

  it.each<[DocumentStatus]>([["ACTIVE"]])("%s 상태에서는 등록 영역이 활성화된다", (status) => {
    renderForm(status);

    expect(fileInput()).toBeEnabled();
    expect(submitButton()).toBeEnabled();
    expect(screen.queryByText(GUIDE)).not.toBeInTheDocument();
  });

  it.each<[DocumentStatus]>([["FINALIZED"]])(
    "%s 상태에서는 파일 입력·버튼을 비활성화하고 안내 문구를 표시한다 (OQ-7)",
    (status) => {
      renderForm(status);

      expect(fileInput()).toBeDisabled();
      expect(submitButton()).toBeDisabled();
      expect(screen.getByText(GUIDE)).toBeInTheDocument();
      fireEvent.submit(submitButton().closest("form")!);
      expect(createDocumentVersionMock).not.toHaveBeenCalled();
    },
  );

  it("400 fieldErrors[file]은 파일 입력 아래에 서버 메시지로 표시한다", async () => {
    createDocumentVersionMock.mockRejectedValue(
      httpError(400, "VALIDATION_FAILED", [{ field: "file", message: "파일 확장자와 실제 문서 형식이 일치하지 않습니다." }]),
    );
    renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    expect(await screen.findByText("파일 확장자와 실제 문서 형식이 일치하지 않습니다.")).toHaveAttribute(
      "id",
      "document-version-file-error",
    );
    expect(fileInput()).toHaveAttribute("aria-invalid", "true");
  });

  it("413은 파일 입력 아래에 크기 오류로 표시한다", async () => {
    createDocumentVersionMock.mockRejectedValue(httpError(413, null));
    renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    expect(await screen.findByText("파일은 20MB 이하만 업로드할 수 있습니다.")).toHaveAttribute(
      "id",
      "document-version-file-error",
    );
  });

  it("409 DOCUMENT_STATUS_CONFLICT는 문구를 표시하고 상세를 다시 조회하게 한다 (I6)", async () => {
    createDocumentVersionMock.mockRejectedValue(httpError(409, "DOCUMENT_STATUS_CONFLICT"));
    const view = renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    expect(await screen.findByRole("alert")).toHaveTextContent("최종 확정된 문서에는 새 버전을 등록할 수 없습니다. 변경이 필요하면 새 문서를 등록해 주세요.");
    expect(onConflict).toHaveBeenCalledTimes(1);

    // 다시 조회한 상태가 반영되어도 오류 문구는 유지되고 등록 영역은 비활성화된다.
    view.rerender(
      <DocumentVersionUploadForm projectId="7" documentId="3" status="FINALIZED" onUploaded={onUploaded} onConflict={onConflict} />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("최종 확정된 문서에는 새 버전을 등록할 수 없습니다. 변경이 필요하면 새 문서를 등록해 주세요.");
    expect(screen.getByText(GUIDE)).toBeInTheDocument();
    expect(submitButton()).toBeDisabled();
  });

  it.each<[string, ApiError, string]>([
    ["404 문서 없음", httpError(404, "DOCUMENT_NOT_FOUND"), "문서를 찾을 수 없습니다."],
    ["404 프로젝트 없음", httpError(404, "PROJECT_NOT_FOUND"), "프로젝트를 찾을 수 없습니다."],
    ["403", httpError(403, "CSRF_INVALID"), "요청을 처리할 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."],
    ["415", httpError(415, "UNSUPPORTED_MEDIA_TYPE"), "지원하지 않는 요청 형식입니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."],
    ["5xx", httpError(500, "INTERNAL_ERROR"), "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."],
  ])("%s 오류 문구를 표시하고 다시 제출할 수 있다", async (_label, error, message) => {
    createDocumentVersionMock.mockRejectedValue(error);
    renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(onConflict).not.toHaveBeenCalled();
    expect(submitButton()).toBeEnabled();
  });

  it("네트워크 오류는 결과 불명확 문구를 표시하고 자동으로 다시 보내지 않는다 (OQ-8)", async () => {
    createDocumentVersionMock.mockRejectedValue(new ApiError("network", null, null, "Failed to fetch"));
    renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    expect(await screen.findByRole("alert")).toHaveTextContent(NETWORK_MESSAGE);
    // 잠시 기다려도 추가 호출이 없다
    await new Promise((resolve) => setTimeout(resolve, 50));
    expect(createDocumentVersionMock).toHaveBeenCalledTimes(1);
    expect(submitButton()).toBeEnabled();
  });

  it("401은 세션 만료 흐름(refresh)에 맡긴다", async () => {
    createDocumentVersionMock.mockRejectedValue(httpError(401, "AUTH_REQUIRED"));
    renderForm();

    choose(pdf());
    fireEvent.click(submitButton());

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

function Toggle({ visible }: { visible: boolean }) {
  return (
    <Activity mode={visible ? "visible" : "hidden"}>
      <DocumentVersionUploadForm projectId="7" documentId="3" status="ACTIVE" onUploaded={onUploaded} onConflict={onConflict} />
    </Activity>
  );
}

describe("DocumentVersionUploadForm — 화면 재진입 (Activity, §9.2)", () => {
  beforeEach(() => {
    createDocumentVersionMock.mockReset();
    onUploaded.mockReset();
  });

  it("성공 후 숨겼다가 다시 표시하면 입력·성공 문구가 초기화된다", async () => {
    createDocumentVersionMock.mockResolvedValue(updated(2));
    const view = render(<Toggle visible />);
    choose(pdf());
    fireEvent.click(submitButton());
    expect(await screen.findByText(/새 버전을 등록했습니다/)).toBeInTheDocument();

    view.rerender(<Toggle visible={false} />);
    view.rerender(<Toggle visible />);

    expect(screen.queryByText(/새 버전을 등록했습니다/)).not.toBeInTheDocument();
    expect(fileInput().files).toHaveLength(0);
    expect(submitButton()).toBeEnabled();
  });

  it("실패 후 숨겼다가 다시 표시하면 오류 문구가 초기화된다", async () => {
    createDocumentVersionMock.mockRejectedValue(httpError(500, "INTERNAL_ERROR"));
    const view = render(<Toggle visible />);
    choose(pdf());
    fireEvent.click(submitButton());
    expect(await screen.findByRole("alert")).toBeInTheDocument();

    view.rerender(<Toggle visible={false} />);
    view.rerender(<Toggle visible />);

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("요청 중 숨겼다가 요청이 끝난 뒤 다시 표시하면 제출할 수 있는 빈 폼이다", async () => {
    let reject: (reason: unknown) => void = () => undefined;
    createDocumentVersionMock.mockReturnValue(new Promise<DocumentDetail>((_resolve, r) => (reject = r)));
    const view = render(<Toggle visible />);
    choose(pdf());
    fireEvent.click(submitButton());

    view.rerender(<Toggle visible={false} />);
    await act(async () => reject(httpError(500, "INTERNAL_ERROR")));
    view.rerender(<Toggle visible />);

    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
    expect(fileInput().files).toHaveLength(0);
  });
});
