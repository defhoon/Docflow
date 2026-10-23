import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { ProjectList } from "./ProjectList";

const refresh = vi.fn();
const fetchProjectsMock = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchProjects: (...args: unknown[]) => fetchProjectsMock(...args),
}));

function deferred<T>() {
  let resolve: (value: T) => void = () => {};
  let reject: (reason: unknown) => void = () => {};
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

describe("ProjectList (AC-PROJECT-18)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchProjectsMock.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("불러오는 동안 로딩 상태를 표시하고 요청은 한 번만 보낸다", async () => {
    const pending = deferred<unknown[]>();
    fetchProjectsMock.mockReturnValue(pending.promise);
    render(<ProjectList />);

    expect(screen.getByRole("status")).toHaveTextContent("프로젝트를 불러오는 중...");
    await act(async () => pending.resolve([]));
    expect(fetchProjectsMock).toHaveBeenCalledTimes(1);
  });

  it("목록이 비어 있으면 빈 상태와 생성 버튼을 표시한다", async () => {
    fetchProjectsMock.mockResolvedValue([]);
    render(<ProjectList />);

    expect(await screen.findByText("아직 프로젝트가 없습니다.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "새 프로젝트" })).toHaveAttribute("href", "/projects/new");
  });

  it("서버 응답 순서를 그대로 표시하고 상세 링크와 생성일을 보여 준다", async () => {
    fetchProjectsMock.mockResolvedValue([
      { id: 3, name: "가장 최근", createdAt: "2026-01-03T00:00:00Z" },
      { id: 9, name: "중간", createdAt: "2026-01-02T00:00:00Z" },
      { id: 1, name: "가장 오래됨", createdAt: "2026-01-01T00:00:00Z" },
    ]);
    render(<ProjectList />);

    const links = await screen.findAllByRole("link");
    expect(links.map((link) => link.getAttribute("href"))).toEqual(["/projects/3", "/projects/9", "/projects/1"]);
    expect(links[0]).toHaveTextContent("가장 최근");
    expect(links[0].querySelector("time")).toHaveAttribute("dateTime", "2026-01-03T00:00:00Z");
  });

  it("오류가 나면 오류와 다시 시도 버튼을 표시하고, 다시 시도하면 목록을 다시 불러온다", async () => {
    fetchProjectsMock.mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."));
    fetchProjectsMock.mockResolvedValueOnce([{ id: 1, name: "DocFlow", createdAt: "2026-01-01T00:00:00Z" }]);
    render(<ProjectList />);

    expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 불러오지 못했습니다.");
    expect(screen.getByRole("alert")).toHaveTextContent("서버에 연결할 수 없습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(screen.getByRole("status")).toHaveTextContent("프로젝트를 불러오는 중...");
    expect(await screen.findByRole("link", { name: /DocFlow/ })).toBeInTheDocument();
    expect(fetchProjectsMock).toHaveBeenCalledTimes(2);
  });

  it("5xx 오류는 서버 오류 문구를 표시하고 refresh를 호출하지 않는다", async () => {
    fetchProjectsMock.mockRejectedValue(new ApiError("http", 500, "INTERNAL_ERROR", "서버 내부 오류"));
    render(<ProjectList />);

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
    expect(refresh).not.toHaveBeenCalled();
  });

  it("401이면 useAuth().refresh()로 세션 만료 흐름을 시작한다 (AC-PROJECT-22)", async () => {
    fetchProjectsMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다."));
    render(<ProjectList />);

    await screen.findByRole("alert");
    expect(refresh).toHaveBeenCalledTimes(1);
  });

  it("언마운트 후 도착한 응답은 상태에 반영하지 않는다", async () => {
    const pending = deferred<unknown[]>();
    fetchProjectsMock.mockReturnValue(pending.promise);
    const consoleError = vi.spyOn(console, "error");
    const { unmount } = render(<ProjectList />);

    unmount();
    await act(async () => pending.reject(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다.")));

    expect(refresh).not.toHaveBeenCalled();
    expect(consoleError).not.toHaveBeenCalled();
  });
});
