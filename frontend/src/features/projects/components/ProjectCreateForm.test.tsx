import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { ProjectCreateForm } from "./ProjectCreateForm";

const push = vi.fn();
const refresh = vi.fn();
const createProjectMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ push }),
}));

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  createProject: (...args: unknown[]) => createProjectMock(...args),
}));

function fill(name: string, description = "") {
  fireEvent.change(screen.getByLabelText("프로젝트 이름"), { target: { value: name } });
  fireEvent.change(screen.getByLabelText("설명 (선택)"), { target: { value: description } });
}

function submit() {
  fireEvent.click(screen.getByRole("button", { name: "프로젝트 생성" }));
}

describe("ProjectCreateForm", () => {
  beforeEach(() => {
    push.mockReset();
    refresh.mockReset();
    createProjectMock.mockReset();
  });

  // AC-PROJECT-19
  it("잘못된 입력은 요청 없이 필드 오류를 표시한다", () => {
    render(<ProjectCreateForm />);

    fill(" 　 ", "설명\u0000");
    submit();

    expect(screen.getByText("프로젝트 이름을 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("설명에 사용할 수 없는 문자가 포함되어 있습니다.")).toBeInTheDocument();
    expect(screen.getByLabelText("프로젝트 이름")).toHaveAttribute("aria-invalid", "true");
    expect(createProjectMock).not.toHaveBeenCalled();
  });

  it("허용되지 않는 문자와 길이 초과를 서버와 같은 문구로 표시한다", () => {
    render(<ProjectCreateForm />);

    fill("﻿DocFlow", "d".repeat(1001));
    submit();
    expect(screen.getByText("프로젝트 이름에 사용할 수 없는 문자가 포함되어 있습니다.")).toBeInTheDocument();
    expect(screen.getByText("설명은 1000자 이하로 입력해 주세요.")).toBeInTheDocument();

    fill("😀".repeat(51));
    submit();
    expect(screen.getByText("프로젝트 이름은 100자 이하로 입력해 주세요.")).toBeInTheDocument();
    expect(createProjectMock).not.toHaveBeenCalled();
  });

  it("제출 중에는 버튼을 비활성화하고 중복 제출하지 않는다", async () => {
    let resolveCreate: (value: unknown) => void = () => {};
    createProjectMock.mockReturnValue(new Promise((resolve) => (resolveCreate = resolve)));
    render(<ProjectCreateForm />);

    fill("DocFlow");
    const form = screen.getByRole("button", { name: "프로젝트 생성" }).closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(await screen.findByRole("button", { name: "생성 중..." })).toBeDisabled();
    expect(createProjectMock).toHaveBeenCalledTimes(1);
    await act(async () => resolveCreate({ id: 1 }));
  });

  // AC-PROJECT-20
  it("정규화한 이름·설명만 보내고 성공하면 상세 화면으로 이동한다", async () => {
    createProjectMock.mockResolvedValue({ id: 42, name: "DocFlow 기획" });
    render(<ProjectCreateForm />);

    fill("  DocFlow 기획  ", "  \n ");
    submit();

    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/42"));
    expect(createProjectMock).toHaveBeenCalledWith({ name: "DocFlow 기획", description: null });
    expect(Object.keys(createProjectMock.mock.calls[0][0])).toEqual(["name", "description"]);
  });

  it("서버 fieldErrors를 해당 필드에 표시한다", async () => {
    createProjectMock.mockRejectedValue(
      new ApiError("http", 400, "VALIDATION_FAILED", "입력값이 올바르지 않습니다.", [
        { field: "name", message: "서버가 거부한 이름입니다." },
        { field: "description", message: "서버가 거부한 설명입니다." },
      ]),
    );
    render(<ProjectCreateForm />);

    fill("DocFlow", "설명");
    submit();

    expect(await screen.findByText("서버가 거부한 이름입니다.")).toBeInTheDocument();
    expect(screen.getByText("서버가 거부한 설명입니다.")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "프로젝트 생성" })).toBeEnabled();
    expect(push).not.toHaveBeenCalled();
  });

  it("5xx 오류는 서버 오류 문구를, 네트워크 오류는 연결 오류 문구를 표시한다", async () => {
    createProjectMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류"));
    createProjectMock.mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."));
    render(<ProjectCreateForm />);

    fill("DocFlow");
    submit();
    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
    expect(screen.getByRole("alert")).not.toHaveTextContent("내부 오류");

    submit();
    await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다."));
    expect(createProjectMock).toHaveBeenCalledTimes(2);
  });

  // AC-PROJECT-22
  it("401이면 useAuth().refresh()를 호출한다", async () => {
    createProjectMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다."));
    render(<ProjectCreateForm />);

    fill("DocFlow");
    submit();

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(push).not.toHaveBeenCalled();
  });
});
