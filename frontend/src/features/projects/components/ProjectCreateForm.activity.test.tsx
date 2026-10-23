import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Activity } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { ProjectCreateForm } from "./ProjectCreateForm";

/**
 * cacheComponents 사용 시 Next.js는 이전 경로를 언마운트하지 않고 React <Activity mode="hidden">으로 숨긴다
 * (next.config.ts, Next.js 문서 "Preserving UI state"). 같은 폼 인스턴스가 재사용되는 생명주기를 재현한다.
 */

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

function NewProjectRoute({ visible }: { visible: boolean }) {
  return (
    <Activity mode={visible ? "visible" : "hidden"}>
      <ProjectCreateForm />
    </Activity>
  );
}

function nameInput() {
  return screen.getByLabelText("프로젝트 이름");
}

function submitButton() {
  // 숨겨진 Activity 내용은 접근성 트리에서 제외되므로 role 조회는 화면에 보이는 버튼만 찾는다.
  return screen.getByRole("button", { name: /프로젝트 생성|생성 중\.\.\./ });
}

describe("ProjectCreateForm — 경로 재방문 (Activity)", () => {
  beforeEach(() => {
    push.mockReset();
    refresh.mockReset();
    createProjectMock.mockReset();
  });

  it("생성 성공 → 상세 이동 → 생성 화면 재진입 시 새 폼을 제공하고 두 번째 생성도 된다", async () => {
    createProjectMock.mockResolvedValueOnce({ id: 1 }).mockResolvedValueOnce({ id: 2 });
    const { rerender } = render(<NewProjectRoute visible />);

    fireEvent.change(nameInput(), { target: { value: "첫 번째" } });
    fireEvent.change(screen.getByLabelText("설명 (선택)"), { target: { value: "설명" } });
    fireEvent.click(submitButton());
    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/1"));
    // 이동이 끝나기 전까지는 다시 제출할 수 없다.
    expect(submitButton()).toBeDisabled();

    // router.push로 상세 화면으로 이동 → 생성 화면은 숨겨진다.
    rerender(<NewProjectRoute visible={false} />);
    // "새 프로젝트" 링크로 다시 진입 → 같은 인스턴스가 다시 보인다.
    rerender(<NewProjectRoute visible />);

    expect(submitButton()).toHaveTextContent("프로젝트 생성");
    expect(submitButton()).toBeEnabled();
    expect(nameInput()).toHaveValue("");
    expect(screen.getByLabelText("설명 (선택)")).toHaveValue("");

    fireEvent.change(nameInput(), { target: { value: "두 번째" } });
    fireEvent.click(submitButton());
    await waitFor(() => expect(push).toHaveBeenCalledWith("/projects/2"));
    expect(createProjectMock).toHaveBeenNthCalledWith(2, { name: "두 번째", description: null });
  });

  it("제출하지 않고 다른 화면에 다녀오면 작성 중인 입력은 유지한다", () => {
    const { rerender } = render(<NewProjectRoute visible />);

    fireEvent.change(nameInput(), { target: { value: "작성 중" } });
    rerender(<NewProjectRoute visible={false} />);
    rerender(<NewProjectRoute visible />);

    expect(nameInput()).toHaveValue("작성 중");
    expect(createProjectMock).not.toHaveBeenCalled();
  });

  it("요청 중 화면을 떠났다가 실패한 뒤 돌아오면 다시 제출할 수 있다", async () => {
    let rejectCreate: (reason: unknown) => void = () => {};
    createProjectMock.mockReturnValueOnce(new Promise((_, reject) => (rejectCreate = reject)));
    const { rerender } = render(<NewProjectRoute visible />);

    fireEvent.change(nameInput(), { target: { value: "DocFlow" } });
    fireEvent.click(submitButton());
    await screen.findByRole("button", { name: "생성 중..." });

    rerender(<NewProjectRoute visible={false} />);
    await act(async () => rejectCreate(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류")));
    rerender(<NewProjectRoute visible />);

    expect(submitButton()).toHaveTextContent("프로젝트 생성");
    expect(submitButton()).toBeEnabled();
    expect(screen.getByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
  });

  it("요청 중 화면을 떠났다가 성공한 뒤 돌아와도 제출 상태가 남지 않는다", async () => {
    let resolveCreate: (value: unknown) => void = () => {};
    createProjectMock.mockReturnValueOnce(new Promise((resolve) => (resolveCreate = resolve)));
    const { rerender } = render(<NewProjectRoute visible />);

    fireEvent.change(nameInput(), { target: { value: "DocFlow" } });
    fireEvent.click(submitButton());
    await screen.findByRole("button", { name: "생성 중..." });

    rerender(<NewProjectRoute visible={false} />);
    await act(async () => resolveCreate({ id: 3 }));
    rerender(<NewProjectRoute visible />);

    expect(push).toHaveBeenCalledWith("/projects/3");
    expect(submitButton()).toHaveTextContent("프로젝트 생성");
    expect(submitButton()).toBeEnabled();
    expect(nameInput()).toHaveValue("");
  });

  it("언마운트된 뒤 도착한 실패 응답은 오류 없이 무시된다", async () => {
    let rejectCreate: (reason: unknown) => void = () => {};
    createProjectMock.mockReturnValueOnce(new Promise((_, reject) => (rejectCreate = reject)));
    const consoleError = vi.spyOn(console, "error");
    const { unmount } = render(<NewProjectRoute visible />);

    fireEvent.change(nameInput(), { target: { value: "DocFlow" } });
    fireEvent.click(submitButton());
    await screen.findByRole("button", { name: "생성 중..." });
    unmount();
    await act(async () => rejectCreate(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류")));

    expect(consoleError).not.toHaveBeenCalled();
    consoleError.mockRestore();
  });
});
