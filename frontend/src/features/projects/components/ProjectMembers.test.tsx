import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { Member } from "../types";
import { ProjectMembers } from "./ProjectMembers";

const refresh = vi.fn();
const fetchMembersMock = vi.fn();
const removeMemberMock = vi.fn();
const addMemberMock = vi.fn();
const onProjectChanged = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  fetchMembers: (...args: unknown[]) => fetchMembersMock(...args),
  removeMember: (...args: unknown[]) => removeMemberMock(...args),
  addMember: (...args: unknown[]) => addMemberMock(...args),
}));

const owner: Member = { userId: 1, displayName: "Author", role: "OWNER", addedAt: null };
const twinA: Member = { userId: 2, displayName: "Same Name", role: "MEMBER", addedAt: "2026-01-02T03:04:05Z" };
const twinB: Member = { userId: 3, displayName: "Same Name", role: "MEMBER", addedAt: "2026-01-03T03:04:05Z" };

function renderMembers(myRole: "OWNER" | "MEMBER" = "OWNER") {
  return render(<ProjectMembers projectId="7" myRole={myRole} onProjectChanged={onProjectChanged} />);
}

describe("ProjectMembers", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchMembersMock.mockReset();
    removeMemberMock.mockReset();
    addMemberMock.mockReset();
    onProjectChanged.mockReset();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  // AC-MEMBER-18
  it("표시 이름과 역할만 보여 주고 이메일·추가 시각은 표시하지 않는다", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    renderMembers();

    expect(screen.getByRole("status")).toHaveTextContent("구성원을 불러오는 중...");
    const list = await screen.findByRole("list", { name: "구성원 목록" });
    const rows = within(list).getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("Author");
    expect(rows[0]).toHaveTextContent("Owner");
    expect(rows[1]).toHaveTextContent("Same Name");
    expect(rows[1]).toHaveTextContent("Member");
    expect(list.textContent).not.toMatch(/@|2026/);
    expect(list.querySelector("time")).toBeNull();
    expect(fetchMembersMock).toHaveBeenCalledWith("7");
  });

  // AC-MEMBER-19
  it("MEMBER에게는 목록만 보이고 추가 폼과 제거 버튼은 보이지 않는다", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    renderMembers("MEMBER");

    await screen.findByRole("list", { name: "구성원 목록" });
    expect(screen.queryByLabelText("구성원 이메일")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /제거/ })).not.toBeInTheDocument();
  });

  it("OWNER에게는 추가 폼과 MEMBER 행의 제거 버튼만 보인다 (Owner 행 제외)", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    renderMembers();

    await screen.findByRole("list", { name: "구성원 목록" });
    expect(screen.getByLabelText("구성원 이메일")).toBeInTheDocument();
    expect(screen.getAllByRole("button", { name: /제거/ })).toHaveLength(1);
    expect(screen.queryByRole("button", { name: "Author 제거" })).not.toBeInTheDocument();
  });

  it("제거는 확인 대화상자를 거치며, 취소하면 요청하지 않는다", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    const confirm = vi.spyOn(window, "confirm").mockReturnValue(false);
    renderMembers();

    fireEvent.click(await screen.findByRole("button", { name: "Same Name 제거" }));

    expect(confirm).toHaveBeenCalledWith("Same Name님을 프로젝트에서 제거할까요?");
    expect(removeMemberMock).not.toHaveBeenCalled();
  });

  it("동명이인 중 선택한 행의 userId로 제거하고 목록을 다시 불러온다", async () => {
    fetchMembersMock.mockResolvedValueOnce([owner, twinA, twinB]).mockResolvedValueOnce([owner, twinA]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    removeMemberMock.mockResolvedValue(undefined);
    renderMembers();

    const list = await screen.findByRole("list", { name: "구성원 목록" });
    const secondRow = within(list).getAllByRole("listitem")[2];
    fireEvent.click(within(secondRow).getByRole("button", { name: "Same Name 제거" }));

    await waitFor(() => expect(fetchMembersMock).toHaveBeenCalledTimes(2));
    expect(removeMemberMock).toHaveBeenCalledWith("7", 3);
    await waitFor(() =>
      expect(within(screen.getByRole("list", { name: "구성원 목록" })).getAllByRole("listitem")).toHaveLength(2),
    );
  });

  it("이미 제거된 구성원이면 안내 후 목록을 다시 불러온다", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    removeMemberMock.mockRejectedValue(
      new ApiError("http", 404, "PROJECT_MEMBER_NOT_FOUND", "프로젝트 구성원을 찾을 수 없습니다."),
    );
    renderMembers();

    fireEvent.click(await screen.findByRole("button", { name: "Same Name 제거" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("이미 제거된 구성원입니다.");
    await waitFor(() => expect(fetchMembersMock).toHaveBeenCalledTimes(2));
    expect(onProjectChanged).not.toHaveBeenCalled();
  });

  it("403이면 권한 없음을 표시하고 상세를 다시 불러온다", async () => {
    fetchMembersMock.mockResolvedValue([owner, twinA]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    removeMemberMock.mockRejectedValue(new ApiError("http", 403, "ACCESS_DENIED", "접근 권한이 없습니다."));
    renderMembers();

    fireEvent.click(await screen.findByRole("button", { name: "Same Name 제거" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    expect(onProjectChanged).toHaveBeenCalledTimes(1);
  });

  it("프로젝트에 접근할 수 없게 되면(404 PROJECT_NOT_FOUND) 상세를 다시 불러온다", async () => {
    fetchMembersMock.mockRejectedValue(new ApiError("http", 404, "PROJECT_NOT_FOUND", "프로젝트를 찾을 수 없습니다."));
    renderMembers();

    await waitFor(() => expect(onProjectChanged).toHaveBeenCalledTimes(1));
  });

  // AC-MEMBER-21
  it("구성원 API가 401이면 useAuth().refresh()를 호출한다", async () => {
    fetchMembersMock.mockResolvedValueOnce([owner, twinA]);
    vi.spyOn(window, "confirm").mockReturnValue(true);
    removeMemberMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다."));
    renderMembers();

    fireEvent.click(await screen.findByRole("button", { name: "Same Name 제거" }));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  });

  it("목록 조회 오류는 다시 시도할 수 있다", async () => {
    fetchMembersMock
      .mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."))
      .mockResolvedValueOnce([owner]);
    renderMembers();

    expect(await screen.findByRole("alert")).toHaveTextContent("구성원을 불러오지 못했습니다.");
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));

    expect(await screen.findByRole("list", { name: "구성원 목록" })).toHaveTextContent("Author");
  });

  it("추가에 성공하면 목록을 다시 불러온다", async () => {
    fetchMembersMock.mockResolvedValueOnce([owner]).mockResolvedValueOnce([owner, twinA]);
    addMemberMock.mockResolvedValue(twinA);
    renderMembers();
    await screen.findByRole("list", { name: "구성원 목록" });

    fireEvent.change(screen.getByLabelText("구성원 이메일"), { target: { value: "twin-a@docflow.local" } });
    fireEvent.click(screen.getByRole("button", { name: "추가" }));

    await waitFor(() => expect(fetchMembersMock).toHaveBeenCalledTimes(2));
    expect(await screen.findByRole("list", { name: "구성원 목록" })).toHaveTextContent("Same Name");
    expect(screen.getByRole("list", { name: "구성원 목록" }).textContent).not.toContain("twin-a@");
  });

  it("언마운트된 뒤 도착한 목록 응답은 반영하지 않는다", async () => {
    let resolveMembers: (value: Member[]) => void = () => {};
    fetchMembersMock.mockReturnValue(new Promise((resolve) => (resolveMembers = resolve)));
    const consoleError = vi.spyOn(console, "error");
    const { unmount } = renderMembers();

    unmount();
    await act(async () => resolveMembers([owner]));

    expect(consoleError).not.toHaveBeenCalled();
  });
});
