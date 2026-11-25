import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { Activity } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { DocumentCreateForm, titleFromFilename } from "./DocumentCreateForm";

const push = vi.fn();
const refresh = vi.fn();
const createDocumentMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
  useParams: () => ({ projectId: "7" }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  createDocument: (...args: unknown[]) => createDocumentMock(...args),
}));

function pdf(name = "a.pdf", size = 10): File {
  const file = new File(["%PDF-1.4"], name, { type: "application/pdf" });
  Object.defineProperty(file, "size", { value: size });
  return file;
}

function fill({ title = "요구사항", description, file = pdf() }: { title?: string; description?: string; file?: File | null }) {
  fireEvent.change(screen.getByLabelText("문서 제목"), { target: { value: title } });
  if (description !== undefined) {
    fireEvent.change(screen.getByLabelText("문서 설명 (선택)"), { target: { value: description } });
  }
  if (file) {
    fireEvent.change(fileInput(), { target: { files: [file] } });
  }
}

function fileInput(): HTMLInputElement {
  return screen.getByLabelText(/^파일/) as HTMLInputElement;
}

function submitButton() {
  return screen.getByRole("button", { name: /문서 등록|등록 중\.\.\./ });
}

function httpError(status: number, code: string | null, fieldErrors: { field: string; message: string }[] = []) {
  return new ApiError("http", status, code, "x", fieldErrors);
}

describe("DocumentCreateForm (SPEC-03 §8.3, AC-DOC-24)", () => {
  beforeEach(() => {
    push.mockReset();
    refresh.mockReset();
    createDocumentMock.mockReset();
  });

  it("정규화한 제목·설명과 파일을 보내고 성공하면 상세로 이동한다", async () => {
    createDocumentMock.mockResolvedValueOnce({ id: 11 });
    render(<DocumentCreateForm />);
    const file = pdf();

    fill({ title: "  요구사항  ", description: "  1차  ", file });
    fireEvent.click(submitButton());

    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/11"));
    expect(createDocumentMock).toHaveBeenCalledWith("7", { title: "요구사항", description: "1차", versionNote: null, file }, expect.any(Function));
  });

  it("클라이언트 검증 실패 시 요청하지 않는다", () => {
    render(<DocumentCreateForm />);

    fireEvent.click(submitButton());

    expect(screen.getByText("문서 제목을 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("파일을 선택해 주세요.")).toBeInTheDocument();
    expect(createDocumentMock).not.toHaveBeenCalled();
  });

  it("20MB 초과·허용되지 않는 형식·빈 파일은 선택 즉시 알리고 보내지 않는다", () => {
    render(<DocumentCreateForm />);

    fill({ file: pdf("big.pdf", 20_971_521) });
    expect(screen.getByText("파일은 20MB 이하만 업로드할 수 있습니다.")).toBeInTheDocument();
    fireEvent.change(fileInput(), { target: { files: [pdf("a.exe")] } });
    expect(screen.getByText("PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.")).toBeInTheDocument();
    fireEvent.change(fileInput(), { target: { files: [pdf("a.pdf", 0)] } });
    expect(screen.getByText("빈 파일은 업로드할 수 없습니다.")).toBeInTheDocument();

    fireEvent.click(submitButton());
    expect(createDocumentMock).not.toHaveBeenCalled();
  });

  it("20,971,520바이트 파일은 보낸다", async () => {
    createDocumentMock.mockResolvedValueOnce({ id: 1 });
    render(<DocumentCreateForm />);

    fill({ file: pdf("max.pdf", 20_971_520) });
    fireEvent.click(submitButton());

    await waitFor(() => expect(createDocumentMock).toHaveBeenCalledTimes(1));
  });

  it("제출 중에는 버튼·입력을 비활성화하고 연속 제출을 막는다", async () => {
    let resolve: (value: unknown) => void = () => {};
    createDocumentMock.mockReturnValueOnce(new Promise((r) => (resolve = r)));
    render(<DocumentCreateForm />);
    fill({});

    fireEvent.click(submitButton());
    fireEvent.click(submitButton());
    fireEvent.submit(submitButton().closest("form")!);

    expect(submitButton()).toBeDisabled();
    expect(submitButton()).toHaveTextContent("등록 중...");
    expect(screen.getByLabelText("문서 제목")).toBeDisabled();
    expect(fileInput()).toBeDisabled();
    expect(screen.getByRole("status")).toHaveTextContent("업로드하는 중");
    expect(createDocumentMock).toHaveBeenCalledTimes(1);

    await act(async () => resolve({ id: 5 }));
    expect(push).toHaveBeenCalledWith("/projects/7/documents/5");
  });

  it("서버 fieldErrors를 각 필드에 표시하고 다시 제출할 수 있다", async () => {
    createDocumentMock.mockRejectedValueOnce(
      httpError(400, "VALIDATION_FAILED", [
        { field: "file", message: "파일 확장자와 실제 문서 형식이 일치하지 않습니다." },
        { field: "title", message: "문서 제목에 사용할 수 없는 문자가 포함되어 있습니다." },
      ]),
    );
    render(<DocumentCreateForm />);
    fill({});

    fireEvent.click(submitButton());

    expect(await screen.findByText("파일 확장자와 실제 문서 형식이 일치하지 않습니다.")).toBeInTheDocument();
    expect(screen.getByText("문서 제목에 사용할 수 없는 문자가 포함되어 있습니다.")).toBeInTheDocument();
    expect(fileInput()).toHaveAttribute("aria-invalid", "true");
    expect(submitButton()).toBeEnabled();
  });

  it("413은 파일 필드에 크기 오류로 표시한다 (공통 형식이 아닌 본문 포함)", async () => {
    createDocumentMock.mockRejectedValueOnce(httpError(413, null));
    render(<DocumentCreateForm />);
    fill({});

    fireEvent.click(submitButton());

    expect(await screen.findByText("파일은 20MB 이하만 업로드할 수 있습니다.")).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
  });

  it("404는 프로젝트 없음 화면을 표시한다", async () => {
    createDocumentMock.mockRejectedValueOnce(httpError(404, "PROJECT_NOT_FOUND"));
    render(<DocumentCreateForm />);
    fill({});

    fireEvent.click(submitButton());

    expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 찾을 수 없습니다.");
    expect(screen.queryByLabelText("문서 제목")).not.toBeInTheDocument();
  });

  it("401은 세션 만료 흐름(refresh)에 맡긴다", async () => {
    createDocumentMock.mockRejectedValueOnce(httpError(401, "AUTH_REQUIRED"));
    render(<DocumentCreateForm />);
    fill({});

    fireEvent.click(submitButton());

    await waitFor(() => expect(refresh).toHaveBeenCalled());
    expect(push).not.toHaveBeenCalled();
  });

  const formErrors: [string, ApiError, string][] = [
    ["403", httpError(403, "CSRF_INVALID"), "요청을 처리할 수 없습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."],
    ["415", httpError(415, "UNSUPPORTED_MEDIA_TYPE"), "지원하지 않는 요청 형식입니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."],
    ["500", httpError(500, "INTERNAL_ERROR"), "서버 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."],
    ["400 (필드 없음)", httpError(400, "BAD_REQUEST"), "요청을 처리하지 못했습니다. 페이지를 새로고침한 뒤 다시 시도해 주세요."],
    [
      "네트워크 연결 종료",
      new ApiError("network", null, null, "x"),
      "업로드 중 서버와의 연결이 끊어졌습니다. 문서가 등록되었는지 프로젝트의 문서 목록에서 확인한 뒤, 등록되지 않았다면 파일 크기(20MB 이하)와 네트워크 상태를 확인하고 다시 시도해 주세요.",
    ],
  ];

  for (const [label, error, message] of formErrors) {
    it(`${label} 오류 문구를 표시하고 다시 제출할 수 있다`, async () => {
      createDocumentMock.mockRejectedValueOnce(error);
      render(<DocumentCreateForm />);
      fill({});

      fireEvent.click(submitButton());

      expect(await screen.findByRole("alert")).toHaveTextContent(message);
      expect(submitButton()).toBeEnabled();
    });
  }
});

function NewDocumentRoute({ visible }: { visible: boolean }) {
  return (
    <Activity mode={visible ? "visible" : "hidden"}>
      <DocumentCreateForm />
    </Activity>
  );
}

describe("DocumentCreateForm — 경로 재방문 (Activity, AC-DOC-25)", () => {
  beforeEach(() => {
    push.mockReset();
    createDocumentMock.mockReset();
  });

  it("등록 성공 후 다시 진입하면 제목·설명·파일 입력이 비어 있고 두 번째 등록도 된다", async () => {
    createDocumentMock.mockResolvedValueOnce({ id: 1 }).mockResolvedValueOnce({ id: 2 });
    const { rerender } = render(<NewDocumentRoute visible />);
    fill({ title: "첫 번째", description: "설명" });
    const firstInput = fileInput();
    expect(firstInput.files).toHaveLength(1);

    fireEvent.click(submitButton());
    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/1"));
    expect(submitButton()).toBeDisabled();

    rerender(<NewDocumentRoute visible={false} />);
    rerender(<NewDocumentRoute visible />);

    expect(submitButton()).toBeEnabled();
    expect(screen.getByLabelText("문서 제목")).toHaveValue("");
    expect(screen.getByLabelText("문서 설명 (선택)")).toHaveValue("");
    // 파일 입력은 다시 마운트되어 선택된 파일이 없다.
    expect(fileInput()).not.toBe(firstInput);
    expect(fileInput().files ?? []).toHaveLength(0);

    fireEvent.click(submitButton());
    expect(screen.getByText("파일을 선택해 주세요.")).toBeInTheDocument();
    expect(createDocumentMock).toHaveBeenCalledTimes(1);

    fill({ title: "두 번째" });
    fireEvent.click(submitButton());
    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/2"));
  });

  it("요청 중 화면을 떠났다가 실패해도 다시 진입하면 제출할 수 있다", async () => {
    let reject: (error: unknown) => void = () => {};
    createDocumentMock.mockReturnValueOnce(new Promise((_, r) => (reject = r)));
    const { rerender } = render(<NewDocumentRoute visible />);
    fill({});
    fireEvent.click(submitButton());

    rerender(<NewDocumentRoute visible={false} />);
    await act(async () => reject(new ApiError("http", 500, "INTERNAL_ERROR", "x")));
    rerender(<NewDocumentRoute visible />);

    expect(submitButton()).toBeEnabled();
    expect(screen.getByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
  });

  it("요청 중 화면을 떠났다가 성공하면 다시 진입했을 때 빈 폼이다", async () => {
    let resolve: (value: unknown) => void = () => {};
    createDocumentMock.mockReturnValueOnce(new Promise((r) => (resolve = r)));
    const { rerender } = render(<NewDocumentRoute visible />);
    fill({ title: "떠난 뒤 성공" });
    fireEvent.click(submitButton());

    rerender(<NewDocumentRoute visible={false} />);
    await act(async () => resolve({ id: 9 }));
    rerender(<NewDocumentRoute visible />);

    expect(submitButton()).toBeEnabled();
    expect(screen.getByLabelText("문서 제목")).toHaveValue("");
  });
});

describe("DocumentCreateForm 파일 우선 등록 (SPEC-19)", () => {
  beforeEach(() => {
    push.mockReset();
    refresh.mockReset();
    createDocumentMock.mockReset();
  });

  it("AC-CA-01: 파일을 먼저 고르면 확장자를 뺀 파일 이름으로 제목을 채우고, 파일을 바꾸면 따라 바뀌며 빼면 비운다", () => {
    render(<DocumentCreateForm />);
    expect(screen.getAllByLabelText(/^파일/)[0]).toBeInTheDocument();
    fireEvent.change(fileInput(), { target: { files: [pdf("12월 정기 회의록.pdf")] } });
    expect(screen.getByLabelText("문서 제목")).toHaveValue("12월 정기 회의록");
    expect(screen.getByText("파일 이름으로 채웠습니다. 바꿀 수 있어요.")).toBeInTheDocument();
    fireEvent.change(fileInput(), { target: { files: [pdf("요구사항_명세서_v2.pdf")] } });
    expect(screen.getByLabelText("문서 제목")).toHaveValue("요구사항 명세서 v2");
  });

  it("AC-CA-02: 직접 고친 제목은 파일을 바꿔도 유지한다", () => {
    render(<DocumentCreateForm />);
    fireEvent.change(fileInput(), { target: { files: [pdf("a.pdf")] } });
    fireEvent.change(screen.getByLabelText("문서 제목"), { target: { value: "내가 정한 제목" } });
    fireEvent.change(fileInput(), { target: { files: [pdf("다른 파일.pdf")] } });
    expect(screen.getByLabelText("문서 제목")).toHaveValue("내가 정한 제목");
    expect(screen.queryByText("파일 이름으로 채웠습니다. 바꿀 수 있어요.")).not.toBeInTheDocument();
  });

  it("파일 이름 제목은 200자로 자르고 확장자 없는 이름도 처리한다", () => {
    expect(titleFromFilename(`${"가".repeat(250)}.pdf`)).toHaveLength(200);
    expect(titleFromFilename("README")).toBe("README");
    expect(titleFromFilename(".hidden.pdf")).toBe(".hidden");
  });

  it("AC-CA-03: 선택 입력은 접혀 있고, 그 안의 값이 잘못되면 펼쳐서 오류를 보여 준다", () => {
    render(<DocumentCreateForm />);
    const details = screen.getByText(/선택 입력/).closest("details")!;
    expect(details).not.toHaveAttribute("open");
    fireEvent.click(screen.getByText(/선택 입력/));
    fireEvent.change(screen.getByLabelText("문서 설명 (선택)"), { target: { value: "가".repeat(2001) } });
    fireEvent.click(screen.getByText(/선택 입력/));
    fill({ title: "제목" });
    fireEvent.submit(submitButton().closest("form")!);
    expect(createDocumentMock).not.toHaveBeenCalled();
    expect(details).toHaveAttribute("open");
  });

  it("AC-CA-04: 업로드 진행률을 보여 주고, 실패하면 입력을 유지한 채 다시 시도할 수 있다", async () => {
    let report: ((progress: { loaded: number; total: number }) => void) | undefined;
    let fail!: (error: unknown) => void;
    createDocumentMock.mockImplementationOnce((_p: string, _i: unknown, onProgress: typeof report) => {
      report = onProgress;
      return new Promise((_resolve, reject) => (fail = reject));
    });
    createDocumentMock.mockResolvedValueOnce({ id: 9 });
    render(<DocumentCreateForm />);
    fill({ title: "제목" });
    fireEvent.click(submitButton());
    await act(async () => report?.({ loaded: 50, total: 100 }));
    expect(screen.getByRole("progressbar", { name: "업로드 진행률" })).toHaveAttribute("aria-valuenow", "50");
    expect(screen.getByText("파일을 업로드하는 중입니다. 50%")).toBeInTheDocument();
    await act(async () => fail(new ApiError("network", null, null, "서버에 연결할 수 없습니다.")));
    const alert = await screen.findByRole("alert");
    expect(screen.getByLabelText("문서 제목")).toHaveValue("제목");
    fireEvent.click(within(alert).getByRole("button", { name: "다시 시도" }));
    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/9"));
    expect(createDocumentMock).toHaveBeenCalledTimes(2);
  });
});

