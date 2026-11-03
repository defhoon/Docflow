import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import DocumentDetailPage from "@/app/projects/[projectId]/documents/[documentId]/page";
import NewDocumentPage from "@/app/projects/[projectId]/documents/new/page";
import { AuthProvider } from "@/features/auth/AuthProvider";

/**
 * 실제 AuthProvider·RequireAuth·api-client를 사용하고 fetch만 대체하는 화면 수준 테스트 (SPEC-03 AC-DOC-27).
 */

const replace = vi.fn();
const push = vi.fn();
let pathname = "/";
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

/** /api/auth/me는 첫 호출만 인증 성공, 이후(세션 만료 확인)는 401 */
function sessionExpiresAfterFirstCheck(other: (url: string, init?: RequestInit) => Response) {
  let meCalls = 0;
  fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
    if (url === "/api/auth/me") {
      meCalls++;
      return meCalls === 1 ? json(200, user) : unauthorized();
    }
    return other(url, init);
  });
}

describe("문서 화면 인증 연동", () => {
  beforeEach(() => {
    replace.mockReset();
    push.mockReset();
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=t; path=/";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
  });

  const pages: [string, Record<string, string>, () => React.ReactNode][] = [
    ["/projects/7/documents/new", { projectId: "7" }, () => <NewDocumentPage />],
    ["/projects/7/documents/3", { projectId: "7", documentId: "3" }, () => <DocumentDetailPage />],
  ];

  for (const [path, routeParams, page] of pages) {
    it(`미인증 사용자는 로그인 화면으로 이동한다: ${path}`, async () => {
      pathname = path;
      params = routeParams;
      fetchMock.mockImplementation(async () => unauthorized());
      render(<AuthProvider>{page()}</AuthProvider>);

      await waitFor(() => expect(replace).toHaveBeenCalledWith(`/login?${new URLSearchParams({ next: path })}`));
      expect(fetchMock.mock.calls.map(([url]) => url)).toEqual(["/api/auth/me"]);
    });
  }

  it("상세 조회가 401이면 세션 만료 안내와 함께 로그인 화면으로 이동한다", async () => {
    pathname = "/projects/7/documents/3";
    params = { projectId: "7", documentId: "3" };
    sessionExpiresAfterFirstCheck(() => unauthorized());
    render(
      <AuthProvider>
        <DocumentDetailPage />
      </AuthProvider>,
    );

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/login?next=%2Fprojects%2F7%2Fdocuments%2F3&reason=expired"),
    );
  });

  it("등록 요청이 401이면 세션 만료 안내와 함께 로그인 화면으로 이동한다", async () => {
    pathname = "/projects/7/documents/new";
    params = { projectId: "7" };
    sessionExpiresAfterFirstCheck(() => unauthorized());
    render(
      <AuthProvider>
        <NewDocumentPage />
      </AuthProvider>,
    );

    fireEvent.change(await screen.findByLabelText("문서 제목"), { target: { value: "문서" } });
    fireEvent.change(screen.getByLabelText(/^파일/), {
      target: { files: [new File(["%PDF-1.4"], "a.pdf", { type: "application/pdf" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "문서 등록" }));

    await waitFor(() =>
      expect(replace).toHaveBeenCalledWith("/login?next=%2Fprojects%2F7%2Fdocuments%2Fnew&reason=expired"),
    );
    const upload = fetchMock.mock.calls.find(([url]) => url === "/api/projects/7/documents");
    expect(upload?.[1]?.body).toBeInstanceOf(FormData);
    expect((upload?.[1]?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("t");
  });

  it("등록 성공 시 실제 api-client로 multipart를 보내고 상세로 이동한다", async () => {
    pathname = "/projects/7/documents/new";
    params = { projectId: "7" };
    fetchMock.mockImplementation(async (url: string) =>
      url === "/api/auth/me" ? json(200, user) : json(201, { id: 12 }),
    );
    render(
      <AuthProvider>
        <NewDocumentPage />
      </AuthProvider>,
    );

    fireEvent.change(await screen.findByLabelText("문서 제목"), { target: { value: "문서" } });
    fireEvent.change(screen.getByLabelText(/^파일/), {
      target: { files: [new File(["%PDF-1.4"], "a.pdf", { type: "application/pdf" })] },
    });
    fireEvent.click(screen.getByRole("button", { name: "문서 등록" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/7/documents/12"));
    expect(screen.getByRole("link", { name: "프로젝트로 돌아가기" })).toHaveAttribute("href", "/projects/7");
  });
});
