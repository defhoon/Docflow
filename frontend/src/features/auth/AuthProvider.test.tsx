import { act, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AuthProvider, useAuth } from "./AuthProvider";

const fetchCurrentUserMock = vi.fn();
const logoutMock = vi.fn();

vi.mock("./api", () => ({
  fetchCurrentUser: () => fetchCurrentUserMock(),
  logout: () => logoutMock(),
}));

function Probe() {
  const { status, user, sessionExpired, signOut } = useAuth();
  return (
    <div>
      <p data-testid="status">{status}</p>
      <p data-testid="user">{user?.email ?? "-"}</p>
      <p data-testid="expired">{String(sessionExpired)}</p>
      <button type="button" onClick={() => void signOut()}>
        signOut
      </button>
    </div>
  );
}

const user = { id: 1, email: "author@docflow.local", displayName: "Author" };

describe("AuthProvider", () => {
  beforeEach(() => {
    fetchCurrentUserMock.mockReset();
    logoutMock.mockReset();
  });

  it("서버 응답으로 로그인 상태를 결정한다", async () => {
    fetchCurrentUserMock.mockResolvedValue(user);
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    expect(screen.getByTestId("status")).toHaveTextContent("loading");
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));
    expect(screen.getByTestId("user")).toHaveTextContent("author@docflow.local");
  });

  it("401(null)이면 anonymous로 처리한다", async () => {
    fetchCurrentUserMock.mockResolvedValue(null);
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(screen.getByTestId("expired")).toHaveTextContent("false");
  });

  it("로그인 상태에서 다시 확인했을 때 세션이 없으면 만료로 표시한다", async () => {
    fetchCurrentUserMock.mockResolvedValueOnce(user).mockResolvedValueOnce(null);
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    await act(async () => {
      window.dispatchEvent(new Event("focus"));
    });

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("anonymous"));
    expect(screen.getByTestId("expired")).toHaveTextContent("true");
  });

  it("서버 연결 실패는 error 상태로 처리한다", async () => {
    fetchCurrentUserMock.mockRejectedValue(new Error("network"));
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );

    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("error"));
  });

  it("로그아웃하면 anonymous가 되고 브라우저 저장소를 사용하지 않는다", async () => {
    fetchCurrentUserMock.mockResolvedValue(user);
    logoutMock.mockResolvedValue(undefined);
    render(
      <AuthProvider>
        <Probe />
      </AuthProvider>,
    );
    await waitFor(() => expect(screen.getByTestId("status")).toHaveTextContent("authenticated"));

    await act(async () => {
      screen.getByRole("button", { name: "signOut" }).click();
    });

    expect(screen.getByTestId("status")).toHaveTextContent("anonymous");
    expect(logoutMock).toHaveBeenCalled();
    expect(window.localStorage.length).toBe(0);
    expect(window.sessionStorage.length).toBe(0);
  });
});
