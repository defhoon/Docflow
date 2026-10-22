import { fireEvent, render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RequireAuth } from "./RequireAuth";

const replace = vi.fn();
const refresh = vi.fn();
let authState: { status: string; sessionExpired: boolean };

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace }),
  usePathname: () => "/",
}));

vi.mock("../AuthProvider", () => ({
  useAuth: () => ({ ...authState, refresh }),
}));

describe("RequireAuth", () => {
  beforeEach(() => {
    replace.mockReset();
    refresh.mockReset();
  });

  it("확인 중에는 로딩 상태를 표시한다", () => {
    authState = { status: "loading", sessionExpired: false };
    render(<RequireAuth>보호된 내용</RequireAuth>);

    expect(screen.getByRole("status")).toHaveTextContent("확인하는 중");
    expect(screen.queryByText("보호된 내용")).not.toBeInTheDocument();
  });

  it("미인증이면 로그인 화면으로 이동한다", () => {
    authState = { status: "anonymous", sessionExpired: false };
    render(<RequireAuth>보호된 내용</RequireAuth>);

    expect(replace).toHaveBeenCalledWith("/login?next=%2F");
    expect(screen.queryByText("보호된 내용")).not.toBeInTheDocument();
  });

  it("세션이 만료되었으면 만료 사유를 함께 전달한다", () => {
    authState = { status: "anonymous", sessionExpired: true };
    render(<RequireAuth>보호된 내용</RequireAuth>);

    expect(replace).toHaveBeenCalledWith("/login?next=%2F&reason=expired");
  });

  it("인증되면 내용을 표시한다", () => {
    authState = { status: "authenticated", sessionExpired: false };
    render(<RequireAuth>보호된 내용</RequireAuth>);

    expect(screen.getByText("보호된 내용")).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });

  it("서버 오류면 오류와 재시도 버튼을 표시한다", () => {
    authState = { status: "error", sessionExpired: false };
    render(<RequireAuth>보호된 내용</RequireAuth>);

    expect(screen.getByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(refresh).toHaveBeenCalled();
  });
});
