"use client";

import { useCallback, useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Card, CardBody } from "@/components/ui/Card";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";

import { fetchMyActivities } from "../api";
import type { Activity } from "../types";
import { ActivityList } from "./ActivityFeed";

export const ACTIVITY_PAGE_SIZE = 30;

/**
 * 전체 활동 (SPEC-18): 홈의 "전체 활동" 경로. 접근 가능한 모든 프로젝트의 활동을 최신순으로 보여 주고, 기존 커서 API
 * (`beforeId`)로 더 불러온다.
 */
export function ActivityPage() {
  const { refresh } = useAuth();
  const [items, setItems] = useState<Activity[]>([]);
  const [state, setState] = useState<"loading" | "idle" | "more" | "done">("loading");
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(
    async (beforeId: number | null) => {
      setError(null);
      setState(beforeId === null ? "loading" : "more");
      try {
        const page = await fetchMyActivities(ACTIVITY_PAGE_SIZE, beforeId ?? undefined);
        setItems((current) => (beforeId === null ? page : [...current, ...page]));
        setState(page.length < ACTIVITY_PAGE_SIZE ? "done" : "idle");
      } catch (failure) {
        if (isUnauthenticated(failure)) {
          void refresh();
        }
        setError(failureMessage(failure));
        setState("idle");
      }
    },
    [refresh],
  );

  useEffect(() => {
    // 첫 페이지는 화면이 열릴 때 한 번 불러온다(상태 갱신은 비동기 콜백 안에서만 일어난다).
    const timer = setTimeout(() => void load(null), 0);
    return () => clearTimeout(timer);
  }, [load]);

  const last = items[items.length - 1];
  return (
    <>
      <PageHeader breadcrumb={[{ label: "홈", href: "/" }, { label: "전체 활동" }]} title="전체 활동" description="참여 중인 프로젝트의 문서 활동을 최신순으로 보여 줍니다." />
      <Card className="max-w-3xl">
        <CardBody className="py-1">
          {state === "loading" && <LoadingBlock label="활동을 불러오는 중..." />}
          {state !== "loading" && items.length === 0 && !error && <EmptyState title="아직 문서 활동이 없습니다." />}
          {items.length > 0 && <ActivityList items={items} showProject />}
          {error && <ErrorState role="status" title="활동을 불러오지 못했습니다." message={error} onRetry={() => void load(last ? last.id : null)} />}
          {state !== "loading" && state !== "done" && items.length > 0 && !error && (
            <div className="flex justify-center py-3">
              <Button size="sm" onClick={() => void load(last.id)} disabled={state === "more"}>
                {state === "more" ? "불러오는 중..." : "더 보기"}
              </Button>
            </div>
          )}
        </CardBody>
      </Card>
    </>
  );
}
