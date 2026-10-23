import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import ProjectDetailPage from "@/app/projects/[projectId]/page";
import NewProjectPage from "@/app/projects/new/page";
import ProjectsPage from "@/app/projects/page";
import { AuthProvider } from "@/features/auth/AuthProvider";

/**
 * 실제 AuthProvider·RequireAuth·api-client를 사용하고 fetch만 대체하는 화면 수준 테스트.
 * AC-PROJECT-22 (프로젝트 API 401 → 세션 만료 흐름), AC-PROJECT-23 (미인증 접근 차단)
 */

const replace = vi.fn();
const push = vi.fn();
let pathname = "/projects";
let params: Record<string, string> = {};
const fetchMock = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push }),
  usePathname: () => pathname,
  useParams: () => params,
}));

const user = { id: 1, email: "author@docflow.local", displayName: "Author" };

function json(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

const unauthorized = () => json(401, { code: "AUTH_REQUIRED", message: "로그인이 필요합니다." });

function renderWithAuth(page: React.ReactNode) {
  return render(<AuthProvider>{page}</AuthProvider>);
}

describe("프로젝트 화면 인증 연동", () => {
  beforeEach(() => {
    replace.mockReset();
    push.mockReset();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  describe("AC-PROJECT-23: 미인증 사용자는 로그인 화면으로 이동한다", () => {
    const cases: [string, Record<string, string>, () => React.ReactNode][] = [
      ["/projects", {}, () => <ProjectsPage />],
      ["/projects/new", {}, () => <NewProjectPage />],
      ["/projects/7", { projectId: "7" }, () => <ProjectDetailPage />],
    ];

    for (const [path, routeParams, page] of cases) {
      it(`${path}`, async () => {
        pathname = path;
        params = routeParams;
        fetchMock.mockImplementation(async () => unauthorized());
        renderWithAuth(page());

        await waitFor(() =>
          expect(replace).toHaveBeenCalledWith(`/login?${new URLSearchParams({ next: path }).toString()}`),
        );
        // 프로젝트 API는 호출하지 않는다 (/api/auth/me만 호출).
        expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(["/api/auth/me"]);
      });
    }
  });

  it("AC-PROJECT-22: 목록 조회가 401이면 세션 만료 안내와 함께 로그인 화면으로 이동한다", async () => {
    pathname = "/projects";
    let meCalls = 0;
    fetchMock.mockImplementation(async (url: string) => {
      if (url === "/api/auth/me") {
        meCalls++;
        return meCalls === 1 ? json(200, user) : unauthorized();
      }
      return unauthorized();
    });
    renderWithAuth(<ProjectsPage />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fprojects&reason=expired"));
    expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(["/api/auth/me", "/api/projects", "/api/auth/me"]);
  });

  it("AC-PROJECT-22: 상세 조회가 401이면 현재 경로를 next로 전달한다", async () => {
    pathname = "/projects/7";
    params = { projectId: "7" };
    let meCalls = 0;
    fetchMock.mockImplementation(async (url: string) => {
      if (url === "/api/auth/me") {
        meCalls++;
        return meCalls === 1 ? json(200, user) : unauthorized();
      }
      return unauthorized();
    });
    renderWithAuth(<ProjectDetailPage />);

    await waitFor(() => expect(replace).toHaveBeenCalledWith("/login?next=%2Fprojects%2F7&reason=expired"));
  });

  it("인증된 사용자는 목록을 본다", async () => {
    pathname = "/projects";
    fetchMock.mockImplementation(async (url: string) =>
      url === "/api/auth/me"
        ? json(200, user)
        : json(200, [{ id: 3, name: "DocFlow", createdAt: "2026-01-01T00:00:00Z" }]),
    );
    renderWithAuth(<ProjectsPage />);

    expect((await screen.findByText("DocFlow")).closest("a")).toHaveAttribute("href", "/projects/3");
    expect(screen.getByRole("heading", { name: "내 프로젝트" })).toBeInTheDocument();
    expect(replace).not.toHaveBeenCalled();
  });
});
