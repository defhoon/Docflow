import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { SignupForm } from "./SignupForm";

const replace = vi.fn();
const signupMock = vi.fn();

vi.mock("next/navigation", () => ({ useRouter: () => ({ replace }) }));
vi.mock("../signup-api", () => ({ signup: (...args: unknown[]) => signupMock(...args) }));

function fill(values: Partial<Record<string, string>> = {}) {
  const all = {
    초대코드: "ABCD-EFGH",
    이메일: "new@example.test",
    이름: "새 사용자",
    비밀번호: "Strong-Passw0rd",
    "비밀번호 확인": "Strong-Passw0rd",
    ...values,
  };
  for (const [label, value] of Object.entries(all)) {
    fireEvent.change(screen.getByLabelText(new RegExp(`^${label}\\*?$`)), { target: { value } });
  }
}

describe("SignupForm (#26)", () => {
  beforeEach(() => {
    replace.mockReset();
    signupMock.mockReset();
  });

  it("기본 입력값이 비어 있다(개발 계정 정보 노출 없음)", () => {
    render(<SignupForm />);
    for (const label of ["초대코드", "이메일", "이름", "비밀번호", "비밀번호 확인"]) {
      expect(screen.getByLabelText(new RegExp(`^${label}\\*?$`))).toHaveValue("");
    }
  });

  it("가입 성공 시 로그인 화면으로 이동한다", async () => {
    signupMock.mockResolvedValue({ id: 1 });
    render(<SignupForm />);
    fill();
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));

    await vi.waitFor(() => expect(replace).toHaveBeenCalledWith("/login?signup=done"));
    expect(signupMock).toHaveBeenCalledWith({
      inviteCode: "ABCD-EFGH",
      email: "new@example.test",
      password: "Strong-Passw0rd",
      displayName: "새 사용자",
    });
  });

  it("비밀번호 확인 불일치·짧은 비밀번호는 요청하지 않는다", () => {
    render(<SignupForm />);
    fill({ "비밀번호 확인": "Different-1234" });
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));
    expect(screen.getByText("비밀번호가 일치하지 않습니다.")).toBeInTheDocument();

    fill({ 비밀번호: "short", "비밀번호 확인": "short" });
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));
    expect(screen.getByText("비밀번호는 10~64자로 입력해 주세요.")).toBeInTheDocument();
    expect(signupMock).not.toHaveBeenCalled();
  });

  it("초대코드 오류·중복 이메일·시도 제한을 각 위치에 안내한다", async () => {
    render(<SignupForm />);
    fill();
    signupMock.mockRejectedValueOnce(new ApiError("http", 400, "INVITATION_INVALID", "x"));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));
    expect(await screen.findByText(/초대코드가 올바르지 않거나 만료되었습니다/)).toBeInTheDocument();

    signupMock.mockRejectedValueOnce(new ApiError("http", 409, "EMAIL_ALREADY_REGISTERED", "x"));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));
    expect(await screen.findByText("이미 가입된 이메일입니다. 로그인해 주세요.")).toBeInTheDocument();

    signupMock.mockRejectedValueOnce(new ApiError("http", 429, "AUTH_TOO_MANY_ATTEMPTS", "x"));
    fireEvent.click(screen.getByRole("button", { name: "가입하기" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("시도 횟수를 초과했습니다.");
  });
});
