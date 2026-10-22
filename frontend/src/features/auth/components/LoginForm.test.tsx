import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { LoginForm } from "./LoginForm";

const replace = vi.fn();
let searchParams = new URLSearchParams();
const signIn = vi.fn();
const loginMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  useSearchParams: () => searchParams,
}));

vi.mock("../AuthProvider", () => ({
  useAuth: () => ({ status: "anonymous", signIn }),
}));

vi.mock("../api", () => ({
  login: (...args: unknown[]) => loginMock(...args),
}));

function fillAndSubmit(email: string, password: string) {
  fireEvent.change(screen.getByLabelText("이메일"), { target: { value: email } });
  fireEvent.change(screen.getByLabelText("비밀번호"), { target: { value: password } });
  fireEvent.click(screen.getByRole("button", { name: "로그인" }));
}

describe("LoginForm", () => {
  beforeEach(() => {
    replace.mockReset();
    signIn.mockReset();
    loginMock.mockReset();
    searchParams = new URLSearchParams();
  });

  it("빈 입력은 서버 요청 없이 필드 오류를 표시한다", () => {
    render(<LoginForm />);

    fireEvent.click(screen.getByRole("button", { name: "로그인" }));

    expect(screen.getByText("이메일을 입력해 주세요.")).toBeInTheDocument();
    expect(screen.getByText("비밀번호를 입력해 주세요.")).toBeInTheDocument();
    expect(loginMock).not.toHaveBeenCalled();
  });

  it("제출 중에는 버튼을 비활성화한다", async () => {
    let resolveLogin: (value: unknown) => void = () => {};
    loginMock.mockReturnValue(new Promise((resolve) => (resolveLogin = resolve)));
    render(<LoginForm />);

    fillAndSubmit("author@docflow.local", "pw");

    expect(await screen.findByRole("button", { name: "로그인 중..." })).toBeDisabled();
    await act(async () => resolveLogin({ id: 1, email: "author@docflow.local", displayName: "Author" }));
  });

  it("성공하면 사용자 정보를 저장하고 next 경로로 이동한다", async () => {
    searchParams = new URLSearchParams({ next: "/projects/1" });
    const user = { id: 1, email: "author@docflow.local", displayName: "Author" };
    loginMock.mockResolvedValue(user);
    render(<LoginForm />);

    fillAndSubmit(" author@docflow.local ", "pw");

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/projects/1"));
    expect(loginMock).toHaveBeenCalledWith("author@docflow.local", "pw");
    expect(signIn).toHaveBeenCalledWith(user);
  });

  it("외부 next 값은 무시하고 /로 이동한다", async () => {
    searchParams = new URLSearchParams({ next: "https://evil.example" });
    loginMock.mockResolvedValue({ id: 1, email: "a@b.c", displayName: "A" });
    render(<LoginForm />);

    fillAndSubmit("a@b.c", "pw");

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/"));
  });

  it("401이면 계정 존재 여부를 드러내지 않는 공통 문구를 표시한다", async () => {
    loginMock.mockRejectedValue(new ApiError("http", 401, "AUTH_INVALID_CREDENTIALS", "x"));
    render(<LoginForm />);

    fillAndSubmit("author@docflow.local", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("이메일 또는 비밀번호가 올바르지 않습니다.");
    expect(replace).not.toHaveBeenCalled();
  });

  it("400 응답의 필드 오류를 표시한다", async () => {
    loginMock.mockRejectedValue(
      new ApiError("http", 400, "VALIDATION_FAILED", "x", [{ field: "email", message: "서버 검증 오류" }]),
    );
    render(<LoginForm />);

    fillAndSubmit("author@docflow.local", "pw");

    expect(await screen.findByText("서버 검증 오류")).toBeInTheDocument();
  });

  it("네트워크 오류를 표시한다", async () => {
    loginMock.mockRejectedValue(new ApiError("network", null, null, "x"));
    render(<LoginForm />);

    fillAndSubmit("author@docflow.local", "pw");

    expect(await screen.findByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다");
  });

  it("세션 만료로 이동한 경우 안내를 표시한다", () => {
    searchParams = new URLSearchParams({ reason: "expired" });
    render(<LoginForm />);

    expect(screen.getByText("세션이 만료되었습니다. 다시 로그인해 주세요.")).toBeInTheDocument();
  });
});
