"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useEffect, useState, type ReactNode } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { LogoutButton } from "@/features/auth/components/LogoutButton";
import { RequireAuth } from "@/features/auth/components/RequireAuth";

import { cn } from "../ui/cn";
import { CloseIcon, FolderIcon, HomeIcon, MenuIcon, TicketIcon } from "../ui/icons";
import { ToastProvider } from "../ui/Toast";

type NavItem = { href: string; label: string; icon: ReactNode; match: (path: string) => boolean; adminOnly?: boolean };

/** 실제로 동작하는 화면만 메뉴에 둔다 (미구현 기능을 메뉴로 표시하지 않음). */
const NAV: NavItem[] = [
  { href: "/", label: "내 작업", icon: <HomeIcon />, match: (path) => path === "/" },
  { href: "/projects", label: "프로젝트", icon: <FolderIcon />, match: (path) => path.startsWith("/projects") },
  {
    href: "/admin/invitations",
    label: "초대 관리",
    icon: <TicketIcon />,
    match: (path) => path.startsWith("/admin"),
    adminOnly: true,
  },
];

/**
 * 로그인 사용자 화면의 공통 틀: 데스크톱은 고정 사이드바, 모바일은 상단 바 + 서랍 메뉴.
 * 화면 보호(RequireAuth)는 사용성 목적이며 실제 접근 제어는 Backend가 한다.
 */
export function AppShell({ children }: { children: ReactNode }) {
  return (
    <RequireAuth>
      <ToastProvider>
        <ShellFrame>{children}</ShellFrame>
      </ToastProvider>
    </RequireAuth>
  );
}

function ShellFrame({ children }: { children: ReactNode }) {
  const pathname = usePathname() || "/";
  const [menuOpen, setMenuOpen] = useState(false);
  const [menuPath, setMenuPath] = useState(pathname);
  // 화면이 바뀌면 모바일 메뉴를 닫는다.
  if (menuPath !== pathname) {
    setMenuPath(pathname);
    setMenuOpen(false);
  }

  useEffect(() => {
    if (!menuOpen) {
      return;
    }
    const onKeyDown = (event: KeyboardEvent) => event.key === "Escape" && setMenuOpen(false);
    document.addEventListener("keydown", onKeyDown);
    return () => document.removeEventListener("keydown", onKeyDown);
  }, [menuOpen]);

  return (
    <div className="flex min-h-dvh w-full">
      <div className="hidden w-60 shrink-0 border-r border-line bg-surface lg:block">
        <aside className="sticky top-0 flex h-dvh w-60">
          <Sidebar pathname={pathname} />
        </aside>
      </div>
      {menuOpen && (
        <div className="fixed inset-0 z-40 lg:hidden">
          <div aria-hidden="true" className="absolute inset-0 bg-ink/40" onClick={() => setMenuOpen(false)} />
          <div id="mobile-navigation" className="relative flex h-full w-72 max-w-[85vw] bg-surface shadow-[var(--shadow-overlay)]">
            <Sidebar pathname={pathname} />
            <button
              type="button"
              onClick={() => setMenuOpen(false)}
              aria-label="메뉴 닫기"
              className="absolute top-3 right-3 rounded-md p-1.5 text-ink-muted hover:bg-subtle"
            >
              <CloseIcon className="size-5" />
            </button>
          </div>
        </div>
      )}
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-30 flex h-14 items-center gap-3 border-b border-line bg-surface/95 px-4 backdrop-blur lg:hidden">
          <button
            type="button"
            onClick={() => setMenuOpen(true)}
            aria-label="메뉴 열기"
            aria-expanded={menuOpen}
            aria-controls="mobile-navigation"
            className="rounded-md p-1.5 text-ink-muted hover:bg-subtle"
          >
            <MenuIcon className="size-5" />
          </button>
          <Logo />
        </header>
        <main className="mx-auto flex w-full max-w-7xl flex-1 flex-col gap-6 px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
          {children}
        </main>
      </div>
    </div>
  );
}

function Logo() {
  return (
    <Link href="/" className="flex items-center gap-2 font-semibold tracking-tight text-ink">
      <span aria-hidden="true" className="flex size-7 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
        D
      </span>
      DocFlow
    </Link>
  );
}

function Sidebar({ pathname }: { pathname: string }) {
  const { user } = useAuth();
  const items = NAV.filter((item) => !item.adminOnly || user?.admin);
  return (
    <div className="flex h-full w-full flex-col">
      <div className="flex h-14 items-center px-5">
        <Logo />
      </div>
      <nav aria-label="주 메뉴" className="flex-1 px-3 py-2">
        <ul className="flex flex-col gap-0.5">
          {items.map((item) => {
            const active = item.match(pathname);
            return (
              <li key={item.href}>
                <Link
                  href={item.href}
                  aria-current={active ? "page" : undefined}
                  className={cn(
                    "flex items-center gap-2.5 rounded-md px-2.5 py-2 text-sm font-medium transition-colors",
                    active ? "bg-brand-50 text-brand-700" : "text-ink-muted hover:bg-subtle hover:text-ink",
                  )}
                >
                  {item.icon}
                  {item.label}
                </Link>
              </li>
            );
          })}
        </ul>
      </nav>
      <div className="border-t border-line p-3">
        {user && (
          <div className="mb-1 flex items-center gap-2.5 px-2.5 py-1.5">
            <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-full bg-subtle text-sm font-semibold text-ink-muted">
              {user.displayName.slice(0, 1)}
            </span>
            <div className="min-w-0">
              <p className="truncate text-sm font-medium text-ink">{user.displayName}</p>
              <p className="truncate text-xs text-ink-faint">{user.email}</p>
            </div>
          </div>
        )}
        <LogoutButton />
      </div>
    </div>
  );
}
