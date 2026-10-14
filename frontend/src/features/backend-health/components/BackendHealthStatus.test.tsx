import { render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";

import { BackendHealthStatus } from "./BackendHealthStatus";

function mockFetch(implementation: () => Promise<Response>) {
  vi.stubGlobal("fetch", vi.fn(implementation));
}

function jsonResponse(body: unknown, status: number) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

describe("BackendHealthStatus", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("응답 전에는 Loading 상태를 표시한다", () => {
    mockFetch(() => new Promise(() => {}));

    render(<BackendHealthStatus />);

    expect(screen.getByRole("status")).toHaveTextContent("확인하는 중");
  });

  it("Backend가 UP이면 Success 상태를 표시한다", async () => {
    mockFetch(async () => jsonResponse({ status: "UP" }, 200));

    render(<BackendHealthStatus />);

    expect(await screen.findByText(/Backend 정상 \(UP\)/)).toBeInTheDocument();
  });

  it("Backend가 DOWN이면 경고 상태를 표시한다", async () => {
    mockFetch(async () => jsonResponse({ status: "DOWN" }, 503));

    render(<BackendHealthStatus />);

    expect(await screen.findByRole("alert")).toHaveTextContent("DOWN");
  });

  it("네트워크 실패 시 Error 상태를 표시한다", async () => {
    mockFetch(async () => {
      throw new TypeError("Failed to fetch");
    });

    render(<BackendHealthStatus />);

    expect(await screen.findByRole("alert")).toHaveTextContent("연결할 수 없습니다");
  });

  it("해석할 수 없는 응답(프록시 오류 등)은 Error 상태로 표시한다", async () => {
    mockFetch(async () => new Response("Internal Server Error", { status: 500 }));

    render(<BackendHealthStatus />);

    expect(await screen.findByRole("alert")).toHaveTextContent("HTTP 500");
  });
});
