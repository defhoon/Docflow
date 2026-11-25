"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";

import { ANALYSIS_POLL_INTERVAL_MS } from "./messages";
import type { AnalysisState } from "./types";

type LoadState<R> =
  | { kind: "loading" }
  | { kind: "error"; message: string }
  | { kind: "ready"; value: AnalysisState<R> };

/**
 * 분석 상태 조회와 요청 (SPEC-11·14). 대기·진행 중이면 일정 간격으로 다시 조회하고, 키가 바뀌거나 화면을 떠나면 멈춘다.
 */
export function useAnalysis<R>(key: string, load: () => Promise<AnalysisState<R>>, submit: () => Promise<AnalysisState<R>>) {
  const { refresh } = useAuth();
  const [state, setState] = useState<{ key: string; value: LoadState<R> } | null>(null);
  const [requesting, setRequesting] = useState(false);
  const [requestError, setRequestError] = useState<string | null>(null);
  const requestingRef = useRef(false);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let active = true;
    load().then(
      (value) => active && setState({ key, value: { kind: "ready", value } }),
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setState({ key, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
  }, [key, load, refresh, tick]);

  const current: LoadState<R> = state?.key === key ? state.value : { kind: "loading" };
  const inProgress = current.kind === "ready" && (current.value.status === "PENDING" || current.value.status === "PROCESSING");

  useEffect(() => {
    if (!inProgress) {
      return;
    }
    const timer = window.setTimeout(() => setTick((n) => n + 1), ANALYSIS_POLL_INTERVAL_MS);
    return () => window.clearTimeout(timer);
  }, [inProgress, state]);

  const request = useCallback(async () => {
    if (requestingRef.current) {
      return;
    }
    requestingRef.current = true;
    setRequesting(true);
    setRequestError(null);
    try {
      const value = await submit();
      setState({ key, value: { kind: "ready", value } });
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      }
      setRequestError(failureMessage(error));
    } finally {
      requestingRef.current = false;
      setRequesting(false);
    }
  }, [key, submit, refresh]);

  const reload = useCallback(() => setTick((n) => n + 1), []);

  return { state: current, request, requesting, requestError, reload };
}
