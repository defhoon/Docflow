"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { FileIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { describeActivity, relativeTime } from "../format";
import type { Activity } from "../types";

type FeedState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "success"; items: Activity[] };

/**
 * 활동 이력 목록 (SPEC-06). {@code load}가 바뀌거나 {@code reloadKey}가 바뀌면 다시 불러온다.
 * {@code showProject}/{@code showDocument}로 문맥에 따라 표시할 링크를 고른다.
 */
export function ActivityFeed({ load, loadKey, showProject = false, showDocument = true, emptyText = "아직 활동이 없습니다." }: {
  load: () => Promise<Activity[]>;
  loadKey: string;
  showProject?: boolean;
  showDocument?: boolean;
  emptyText?: string;
}) {
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const key = `${loadKey}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: FeedState } | null>(null);
  const state: FeedState = result?.key === key ? result.value : (result?.value ?? { kind: "loading" });

  useEffect(() => {
    let active = true;
    load().then(
      (items) => active && setResult({ key, value: { kind: "success", items } }),
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({ key, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
    // load는 호출 측에서 loadKey로 식별한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [key, refresh]);

  if (state.kind === "loading") {
    return <LoadingBlock label="활동을 불러오는 중..." />;
  }
  if (state.kind === "error") {
    return <ErrorState role="status" title="활동을 불러오지 못했습니다." message={state.message} onRetry={() => setAttempt((n) => n + 1)} />;
  }
  if (state.items.length === 0) {
    return <EmptyState title={emptyText} />;
  }
  return <ActivityList items={state.items} showProject={showProject} showDocument={showDocument} />;
}

/** 활동 링크: 버전이 있으면 그 버전을 연 문서 화면, 없으면 문서 화면 (SPEC-18). */
export function activityHref(activity: Activity): string | null {
  if (!activity.document) {
    return null;
  }
  const base = `/projects/${activity.project.id}/documents/${activity.document.id}`;
  return activity.versionNumber ? `${base}?v=${activity.versionNumber}` : base;
}

/** 활동 목록 표시 (SPEC-06, SPEC-18). 문서명과 버전을 함께 보여 주고 누르면 해당 문서·버전으로 이동한다. */
export function ActivityList({ items, showProject = false, showDocument = true }: {
  items: Activity[];
  showProject?: boolean;
  showDocument?: boolean;
}) {
  return (
    <ol className="flex flex-col">
      {items.map((activity) => (
        <li key={activity.id} className="flex gap-3 border-b border-line py-3 last:border-b-0">
          <span aria-hidden="true" className="mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-full bg-subtle text-xs font-semibold text-ink-muted">
            {activity.actor.displayName.slice(0, 1)}
          </span>
          <div className="min-w-0 flex-1 text-sm">
            {/* 행동을 먼저, 대상 문서는 한 줄로 줄여 따로 보여 준다(긴 제목 반복으로 읽기 어려워지지 않게, #50). */}
            <p className="break-words text-ink">
              <span className="font-medium">{activity.actor.displayName}</span>님이 {describeActivity(activity)}
            </p>
            {showDocument && activity.document && (
              <Link
                href={activityHref(activity)!}
                title={activity.document.title}
                className="mt-0.5 flex min-w-0 items-center gap-1 text-xs font-medium text-brand-700 hover:underline"
              >
                <FileIcon className="size-3.5 shrink-0" />
                <span className="truncate">{activity.document.title}</span>
                {activity.versionNumber && <span className="shrink-0 text-ink-muted">· v{activity.versionNumber}</span>}
              </Link>
            )}
            <p className="mt-0.5 flex min-w-0 text-xs text-ink-faint">
              {showProject && (
                <>
                  <Link href={`/projects/${activity.project.id}`} title={activity.project.name} className="truncate hover:text-ink">
                    {activity.project.name}
                  </Link>
                  <span aria-hidden="true" className="shrink-0 px-1">·</span>
                </>
              )}
              <time dateTime={activity.createdAt} title={formatDateTime(activity.createdAt)} className="shrink-0">
                {relativeTime(activity.createdAt)}
              </time>
            </p>
          </div>
        </li>
      ))}
    </ol>
  );
}
