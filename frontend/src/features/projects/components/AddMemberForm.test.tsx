import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { Activity } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import { AddMemberForm } from "./AddMemberForm";

const refresh = vi.fn();
const addMemberMock = vi.fn();
const onAdded = vi.fn();
const onProjectChanged = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../api", () => ({
  addMember: (...args: unknown[]) => addMemberMock(...args),
}));

/** cacheComponents 사용 시 Next.js가 이전 경로를 <Activity mode="hidden">으로 보존하는 생명주기를 재현한다. */
function Route({ visible }: { visible: boolean }) {
  return (
    <Activity mode={visible ? "visible" : "hidden"}>
      <AddMemberForm projectId="7" onAdded={onAdded} onProjectChanged={onProjectChanged} />
    </Activity>
  );
}

function emailInput() {
  return screen.getByLabelText("구성원 이메일");
}

function submitButton() {
  return screen.getByRole("button", { name: /추가/ });
}

function submit(email: string) {
  fireEvent.change(emailInput(), { target: { value: email } });
  fireEvent.click(submitButton());
}

describe("AddMemberForm (AC-MEMBER-20)", () => {
  beforeEach(() => {
    refresh.mockReset();
    addMemberMock.mockReset();
    onAdded.mockReset();
    onProjectChanged.mockReset();
  });

  it("클라이언트 검증 오류는 요청 없이 표시한다", () => {
    render(<Route visible />);

    submit("   ");
    expect(screen.getByText("이메일을 입력해 주세요.")).toBeInTheDocument();
    submit("not-an-email");
    expect(screen.getByText("이메일 형식이 올바르지 않습니다.")).toBeInTheDocument();
    expect(emailInput()).toHaveAttribute("aria-invalid", "true");
    expect(addMemberMock).not.toHaveBeenCalled();
  });

  it("앞뒤 공백을 제거한 이메일만 보내고, 성공하면 입력을 비우고 목록 갱신을 요청한다", async () => {
    addMemberMock.mockResolvedValue({ userId: 2, displayName: "Reviewer", role: "MEMBER", addedAt: null });
    render(<Route visible />);

    submit("  reviewer@docflow.local ");

    await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
    expect(addMemberMock).toHaveBeenCalledWith("7", "reviewer@docflow.local");
    expect(emailInput()).toHaveValue("");
    expect(submitButton()).toHaveTextContent("추가");
    expect(submitButton()).toBeEnabled();
  });

  it("제출 중에는 버튼을 비활성화하고 중복 제출하지 않는다", async () => {
    let resolveAdd: (value: unknown) => void = () => {};
    addMemberMock.mockReturnValue(new Promise((resolve) => (resolveAdd = resolve)));
    render(<Route visible />);

    fireEvent.change(emailInput(), { target: { value: "reviewer@docflow.local" } });
    const form = submitButton().closest("form")!;
    fireEvent.submit(form);
    fireEvent.submit(form);

    expect(await screen.findByRole("button", { name: "추가 중..." })).toBeDisabled();
    expect(addMemberMock).toHaveBeenCalledTimes(1);
    await act(async () => resolveAdd({ userId: 2 }));
  });

  it("404 USER_NOT_FOUND, 409, 400 오류를 입력 필드에 표시하고 이메일은 다시 보여 주지 않는다", async () => {
    addMemberMock
      .mockRejectedValueOnce(new ApiError("http", 404, "USER_NOT_FOUND", "해당 이메일로 가입한 사용자를 찾을 수 없습니다."))
      .mockRejectedValueOnce(new ApiError("http", 409, "PROJECT_MEMBER_ALREADY_EXISTS", "이미 프로젝트 구성원입니다."))
      .mockRejectedValueOnce(
        new ApiError("http", 400, "VALIDATION_FAILED", "입력값을 확인해 주세요.", [
          { field: "email", message: "이메일은 255자 이하로 입력해 주세요." },
        ]),
      );
    render(<Route visible />);

    submit("nobody@docflow.local");
    expect(await screen.findByText("해당 이메일로 가입한 사용자를 찾을 수 없습니다.")).toBeInTheDocument();
    expect(document.body.textContent).not.toContain("nobody@docflow.local");

    submit("reviewer@docflow.local");
    expect(await screen.findByText("이미 프로젝트 구성원입니다.")).toBeInTheDocument();

    submit("long@docflow.local");
    expect(await screen.findByText("이메일은 255자 이하로 입력해 주세요.")).toBeInTheDocument();
    expect(submitButton()).toBeEnabled();
    expect(onAdded).not.toHaveBeenCalled();
  });

  it("403이면 권한 없음을 표시하고 상세를 다시 불러오며, 401이면 refresh()를 호출한다", async () => {
    addMemberMock
      .mockRejectedValueOnce(new ApiError("http", 403, "ACCESS_DENIED", "접근 권한이 없습니다."))
      .mockRejectedValueOnce(new ApiError("http", 401, "AUTH_REQUIRED", "로그인이 필요합니다."));
    render(<Route visible />);

    submit("reviewer@docflow.local");
    expect(await screen.findByRole("alert")).toHaveTextContent("권한이 없습니다.");
    expect(onProjectChanged).toHaveBeenCalledTimes(1);

    submit("reviewer@docflow.local");
    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
  });

  it("5xx·네트워크 오류는 일반 오류 문구를 표시한다", async () => {
    addMemberMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류"));
    render(<Route visible />);

    submit("reviewer@docflow.local");

    expect(await screen.findByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
    expect(screen.getByRole("alert")).not.toHaveTextContent("내부 오류");
  });

  describe("<Activity> 재진입", () => {
    it("추가 성공 후 화면을 떠났다 돌아오면 빈 폼이고 다시 제출할 수 있다", async () => {
      addMemberMock.mockResolvedValue({ userId: 2 });
      const { rerender } = render(<Route visible />);

      submit("reviewer@docflow.local");
      await waitFor(() => expect(onAdded).toHaveBeenCalledTimes(1));
      rerender(<Route visible={false} />);
      rerender(<Route visible />);

      expect(emailInput()).toHaveValue("");
      expect(submitButton()).toHaveTextContent("추가");
      expect(submitButton()).toBeEnabled();
      submit("outsider@docflow.local");
      await waitFor(() => expect(addMemberMock).toHaveBeenCalledTimes(2));
    });

    it("요청 중 화면을 떠났다가 성공한 뒤 돌아와도 제출 상태가 남지 않는다", async () => {
      let resolveAdd: (value: unknown) => void = () => {};
      addMemberMock.mockReturnValueOnce(new Promise((resolve) => (resolveAdd = resolve)));
      const { rerender } = render(<Route visible />);

      submit("reviewer@docflow.local");
      await screen.findByRole("button", { name: "추가 중..." });
      rerender(<Route visible={false} />);
      await act(async () => resolveAdd({ userId: 2 }));
      rerender(<Route visible />);

      expect(submitButton()).toHaveTextContent("추가");
      expect(submitButton()).toBeEnabled();
      expect(emailInput()).toHaveValue("");
    });

    it("요청 중 화면을 떠났다가 실패한 뒤 돌아오면 다시 제출할 수 있다", async () => {
      let rejectAdd: (reason: unknown) => void = () => {};
      addMemberMock.mockReturnValueOnce(new Promise((_, reject) => (rejectAdd = reject)));
      const { rerender } = render(<Route visible />);

      submit("reviewer@docflow.local");
      await screen.findByRole("button", { name: "추가 중..." });
      rerender(<Route visible={false} />);
      await act(async () => rejectAdd(new ApiError("http", 500, "INTERNAL_ERROR", "내부 오류")));
      rerender(<Route visible />);

      expect(submitButton()).toBeEnabled();
      expect(screen.getByRole("alert")).toHaveTextContent("서버 오류가 발생했습니다.");
    });
  });
});
