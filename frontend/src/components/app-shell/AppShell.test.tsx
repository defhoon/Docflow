import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AppShell } from "./AppShell";

let authState: { status: string; user: { id: number; email: string; displayName: string; admin?: boolean } | null };

vi.mock("next/navigation", () => ({
  usePathname: () => "/projects/3",
  useRouter: () => ({ replace: vi.fn() }),
}));
vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ ...authState, sessionExpired: false, refresh: vi.fn(), signOut: vi.fn() }),
}));

describe("AppShell (#28)", () => {
  beforeEach(() => {
    authState = { status: "authenticated", user: { id: 1, email: "u@example.test", displayName: "사용자" } };
  });

  it("실제 화면만 메뉴에 두고 현재 위치를 표시한다. 초대 관리는 관리자에게만 보인다", () => {
    render(<AppShell>본문</AppShell>);
    const nav = screen.getByRole("navigation", { name: "주 메뉴" });
    expect(within(nav).getAllByRole("link").map((link) => link.textContent)).toEqual(["내 작업", "프로젝트"]);
    expect(within(nav).getByRole("link", { name: "프로젝트" })).toHaveAttribute("aria-current", "page");
    expect(screen.getByText("본문")).toBeInTheDocument();
  });

  it("관리자에게는 초대 관리 메뉴가 보인다", () => {
    authState.user = { ...authState.user!, admin: true };
    render(<AppShell>본문</AppShell>);
    expect(within(screen.getByRole("navigation", { name: "주 메뉴" })).getByRole("link", { name: "초대 관리" })).toHaveAttribute(
      "href",
      "/admin/invitations",
    );
  });

  it("모바일 메뉴 버튼으로 서랍 메뉴를 열고 Esc로 닫는다", () => {
    render(<AppShell>본문</AppShell>);
    fireEvent.click(screen.getByRole("button", { name: "메뉴 열기" }));
    expect(screen.getAllByRole("navigation", { name: "주 메뉴" })).toHaveLength(2);
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.getAllByRole("navigation", { name: "주 메뉴" })).toHaveLength(1);
  });

  it("로그인하지 않았으면 본문을 표시하지 않는다", () => {
    authState = { status: "loading", user: null };
    render(<AppShell>본문</AppShell>);
    expect(screen.queryByText("본문")).not.toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("로그인 상태를 확인하는 중입니다...");
  });
});
