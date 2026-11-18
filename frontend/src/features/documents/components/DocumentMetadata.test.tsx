import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { describeActivity } from "@/features/activity/format";
import { ApiError } from "@/lib/api-client";

import type { DocumentDetail } from "../types";
import type { DocumentVersionSummary } from "../version-files";
import { DocumentCreateForm } from "./DocumentCreateForm";
import { DocumentDescription } from "./DocumentDescription";
import { DocumentVersionHistory } from "./DocumentVersionHistory";
import { DocumentVersionUploadForm } from "./DocumentVersionUploadForm";

const push = vi.fn();
const refresh = vi.fn();
const createDocumentMock = vi.fn();
const createDocumentVersionMock = vi.fn();
const updateDescriptionMock = vi.fn();
const fetchVersionsMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useParams: () => ({ projectId: "7" }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  createDocument: (...args: unknown[]) => createDocumentMock(...args),
  createDocumentVersion: (...args: unknown[]) => createDocumentVersionMock(...args),
  updateDocumentDescription: (...args: unknown[]) => updateDescriptionMock(...args),
}));

vi.mock("../version-files", () => ({
  fetchDocumentVersions: (...args: unknown[]) => fetchVersionsMock(...args),
  downloadVersionFile: vi.fn(),
  saveBlob: vi.fn(),
}));

function pdf(name = "a.pdf"): File {
  return new File(["%PDF-1.4"], name, { type: "application/pdf" });
}

function detail(overrides: Partial<DocumentDetail> = {}): DocumentDetail {
  return {
    id: 3,
    title: "요구사항",
    description: "기존 설명",
    status: "ACTIVE",
    createdBy: { id: 1, displayName: "Author" },
    createdAt: "2026-11-01T09:00:00Z",
    updatedAt: "2026-11-01T09:00:00Z",
    finalization: null,
    latestVersion: { versionNumber: 1, originalFilename: "a.pdf", sizeBytes: 10, uploadedAt: "2026-11-01T09:00:00Z", versionNote: null },
    ...overrides,
  };
}

function version(versionNumber: number, versionNote: string | null): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: `v${versionNumber}.pdf`,
    sizeBytes: 10,
    uploadedBy: { id: 1, displayName: "Author" },
    uploadedAt: "2026-11-01T09:00:00Z",
    latest: false,
    versionNote,
  };
}

function httpError(status: number, code: string | null, fieldErrors: Array<{ field: string; message: string }> = []) {
  return new ApiError("http", status, code, "x", fieldErrors);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("문서 등록 — 설명·초기 버전 내용 (SPEC-09 AC-META-UI-01)", () => {
  function fillAndSubmit(description: string, note: string) {
    fireEvent.change(screen.getByLabelText("문서 제목"), { target: { value: "요구사항" } });
    fireEvent.change(screen.getByLabelText("문서 설명 (선택)"), { target: { value: description } });
    fireEvent.change(screen.getByLabelText("초기 버전 내용 (선택)"), { target: { value: note } });
    fireEvent.change(screen.getByLabelText(/파일/), { target: { files: [pdf()] } });
    fireEvent.click(screen.getByRole("button", { name: "문서 등록" }));
  }

  it("정규화한 설명과 초기 버전 내용을 함께 보낸다", async () => {
    createDocumentMock.mockResolvedValueOnce({ id: 11 });
    render(<DocumentCreateForm />);

    fillAndSubmit("  목적\n범위 ", " 초안 ");

    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/11"));
    expect(createDocumentMock).toHaveBeenCalledWith("7", expect.objectContaining({ description: "목적\n범위", versionNote: "초안" }));
  });

  it("1000자를 넘는 초기 버전 내용은 보내지 않고 오류를 표시하며 글자 수를 안내한다", () => {
    render(<DocumentCreateForm />);

    fillAndSubmit("", "가".repeat(1001));

    expect(screen.getByText("초기 버전 내용은 1000자 이하로 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("1,001 / 1,000자")).toBeInTheDocument();
    expect(createDocumentMock).not.toHaveBeenCalled();
  });

  it("서버 검증 오류(versionNote)는 해당 입력란에 표시하고 입력값을 유지한다", async () => {
    createDocumentMock.mockRejectedValueOnce(httpError(400, "VALIDATION_FAILED", [{ field: "versionNote", message: "서버 오류 문구" }]));
    render(<DocumentCreateForm />);

    fillAndSubmit("설명", "내용");

    expect(await screen.findByText("서버 오류 문구")).toBeInTheDocument();
    expect(screen.getByLabelText("문서 설명 (선택)")).toHaveValue("설명");
    expect(screen.getByLabelText("초기 버전 내용 (선택)")).toHaveValue("내용");
  });
});

describe("새 버전 — 변경사항 (SPEC-09 AC-META-UI-02)", () => {
  function renderForm() {
    render(
      <DocumentVersionUploadForm projectId="7" documentId="3" status="ACTIVE" onUploaded={vi.fn()} onConflict={vi.fn()} />,
    );
  }

  function submit(note: string) {
    fireEvent.change(screen.getByLabelText(/새 버전 파일/), { target: { files: [pdf("v2.pdf")] } });
    fireEvent.change(screen.getByLabelText("직전 버전 대비 변경사항 (선택)"), { target: { value: note } });
    fireEvent.click(screen.getByRole("button", { name: "새 버전 등록" }));
  }

  it("변경사항을 보내고 성공하면 비운다", async () => {
    createDocumentVersionMock.mockResolvedValueOnce(detail({ latestVersion: { ...detail().latestVersion, versionNumber: 2 } }));
    renderForm();

    submit("  3장 수정 ");

    expect(await screen.findByText(/새 버전을 등록했습니다/)).toBeInTheDocument();
    expect(createDocumentVersionMock).toHaveBeenCalledWith("7", "3", expect.any(File), "3장 수정");
    expect(screen.getByLabelText("직전 버전 대비 변경사항 (선택)")).toHaveValue("");
  });

  it("업로드가 실패하면 변경사항을 유지한다", async () => {
    createDocumentVersionMock.mockRejectedValueOnce(new ApiError("network", null, null, "Failed to fetch"));
    renderForm();

    submit("유지할 변경사항");

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByLabelText("직전 버전 대비 변경사항 (선택)")).toHaveValue("유지할 변경사항");
  });

  it("1000자를 넘으면 보내지 않는다", () => {
    renderForm();

    submit("가".repeat(1001));

    expect(screen.getByText("변경사항은 1000자 이하로 입력해 주세요.")).toBeInTheDocument();
    expect(createDocumentVersionMock).not.toHaveBeenCalled();
  });
});

describe("문서 설명 보기·수정 (SPEC-09 AC-META-UI-03)", () => {
  function renderDescription(props: Partial<Parameters<typeof DocumentDescription>[0]> = {}) {
    const onUpdated = vi.fn();
    const onFinalizedConflict = vi.fn();
    render(
      <DocumentDescription
        projectId="7"
        documentId="3"
        description="기존 설명"
        editable
        onUpdated={onUpdated}
        onFinalizedConflict={onFinalizedConflict}
        {...props}
      />,
    );
    return { onUpdated, onFinalizedConflict };
  }

  it("설명을 줄바꿈 그대로 텍스트로 표시하고, 수정해 저장한다", async () => {
    const saved = detail({ description: "새 설명" });
    updateDescriptionMock.mockResolvedValueOnce(saved);
    const { onUpdated } = renderDescription({ description: "<b>첫 줄</b>\n둘째 줄" });
    expect(screen.getByText(/<b>첫 줄<\/b>/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole("button", { name: "설명 수정" }));
    fireEvent.change(screen.getByLabelText("문서 설명"), { target: { value: "  새 설명 " } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("문서 설명을 저장했습니다.")).toBeInTheDocument();
    expect(updateDescriptionMock).toHaveBeenCalledWith("7", "3", "새 설명");
    expect(onUpdated).toHaveBeenCalledWith(saved);
  });

  it("빈 값으로 저장하면 설명을 지운다(null)", async () => {
    updateDescriptionMock.mockResolvedValueOnce(detail({ description: null }));
    renderDescription();

    fireEvent.click(screen.getByRole("button", { name: "설명 수정" }));
    fireEvent.change(screen.getByLabelText("문서 설명"), { target: { value: "   " } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    await waitFor(() => expect(updateDescriptionMock).toHaveBeenCalledWith("7", "3", null));
  });

  it("설명이 없으면 빈 상태와 '설명 추가'를 보여 준다", () => {
    renderDescription({ description: null });
    expect(screen.getByText(/아직 문서 설명이 없습니다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "설명 추가" })).toBeInTheDocument();
  });

  it("FINALIZED(수정 불가)면 읽기 전용이다", () => {
    renderDescription({ editable: false });
    expect(screen.getByText("기존 설명")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /설명/ })).not.toBeInTheDocument();
  });

  it("2000자를 넘으면 보내지 않는다", () => {
    renderDescription();
    fireEvent.click(screen.getByRole("button", { name: "설명 수정" }));
    fireEvent.change(screen.getByLabelText("문서 설명"), { target: { value: "가".repeat(2001) } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByRole("alert")).toHaveTextContent("설명은 2000자 이하로 입력해 주세요.");
    expect(updateDescriptionMock).not.toHaveBeenCalled();
  });

  it("저장 중에는 중복 제출하지 않는다", async () => {
    let resolve: (value: DocumentDetail) => void = () => {};
    updateDescriptionMock.mockReturnValueOnce(new Promise((r) => (resolve = r)));
    renderDescription();
    fireEvent.click(screen.getByRole("button", { name: "설명 수정" }));
    fireEvent.change(screen.getByLabelText("문서 설명"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));
    expect(screen.getByRole("button", { name: "저장 중..." })).toBeDisabled();
    fireEvent.submit(screen.getByLabelText("문서 설명").closest("form")!);
    expect(updateDescriptionMock).toHaveBeenCalledTimes(1);
    resolve(detail({ description: "x" }));
    expect(await screen.findByText("문서 설명을 저장했습니다.")).toBeInTheDocument();
  });

  it("화면 표시 후 확정되었으면(409) 안내하고 상세를 다시 조회하며 입력값을 유지한다", async () => {
    updateDescriptionMock.mockRejectedValueOnce(httpError(409, "DOCUMENT_ALREADY_FINALIZED"));
    const { onFinalizedConflict } = renderDescription();

    fireEvent.click(screen.getByRole("button", { name: "설명 수정" }));
    fireEvent.change(screen.getByLabelText("문서 설명"), { target: { value: "늦은 수정" } });
    fireEvent.click(screen.getByRole("button", { name: "저장" }));

    expect(await screen.findByText("이미 최종 확정된 문서라 설명을 수정할 수 없습니다.")).toBeInTheDocument();
    expect(onFinalizedConflict).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("문서 설명")).toHaveValue("늦은 수정");
  });
});

describe("버전 히스토리 — 버전 정보 (SPEC-09 AC-META-UI-04)", () => {
  it("v1은 '초기 버전 내용', v2 이상은 '변경사항', 없으면 빈 상태 문구", async () => {
    fetchVersionsMock.mockResolvedValueOnce([version(3, null), version(2, "3장 추가\n표 수정"), version(1, "초안")]);
    render(<DocumentVersionHistory projectId="7" documentId="3" reloadKey={0} selectedVersion={3} onPreview={vi.fn()} />);

    const items = await screen.findAllByRole("listitem");
    expect(within(items[0]).getByText("변경사항")).toBeInTheDocument();
    expect(within(items[0]).getByText("작성된 변경사항이 없습니다.")).toBeInTheDocument();
    expect(within(items[1]).getByText(/3장 추가/)).toBeInTheDocument();
    expect(within(items[2]).getByText("초기 버전 내용")).toBeInTheDocument();
    expect(within(items[2]).getByText("초안")).toBeInTheDocument();
  });
});

describe("활동 이력 문구 (SPEC-09)", () => {
  it("DOCUMENT_DESCRIPTION_UPDATED", () => {
    expect(
      describeActivity({
        id: 1,
        action: "DOCUMENT_DESCRIPTION_UPDATED",
        actor: { id: 1, displayName: "A" },
        project: { id: 1, name: "P" },
        document: { id: 3, title: "D" },
        versionNumber: null,
        createdAt: "2026-11-01T09:00:00Z",
      }),
    ).toBe("문서 설명을 수정했습니다");
  });
});
