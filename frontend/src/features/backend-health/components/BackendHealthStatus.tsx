"use client";

import { useEffect, useState } from "react";

import { fetchBackendHealth } from "../api";
import type { BackendHealthState } from "../types";

export function BackendHealthStatus() {
  const [state, setState] = useState<BackendHealthState>({ kind: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    fetchBackendHealth(controller.signal)
      .then(setState)
      .catch(() => {
        // 언마운트로 요청이 취소된 경우 상태를 갱신하지 않는다.
      });
    return () => controller.abort();
  }, []);

  switch (state.kind) {
    case "loading":
      return (
        <p role="status" className="text-zinc-500">
          Backend 상태를 확인하는 중입니다...
        </p>
      );
    case "up":
      return (
        <p role="status" className="font-medium text-green-700">
          Backend 정상 (UP) — Backend와 Database 연결이 정상입니다.
        </p>
      );
    case "down":
      return (
        <p role="alert" className="font-medium text-amber-700">
          Backend 응답 상태: {state.status} — Database 연결 등을 확인해 주세요.
        </p>
      );
    case "error":
      return (
        <p role="alert" className="font-medium text-red-700">
          연결 오류 — {state.message}
        </p>
      );
  }
}
