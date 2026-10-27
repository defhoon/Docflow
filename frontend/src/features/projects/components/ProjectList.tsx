"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";

import { fetchProjects } from "../api";
import { failureMessage, isUnauthenticated } from "../errors";
import { formatDateTime } from "../format";
import type { ProjectSummary } from "../types";

type ListResult = { kind: "error"; message: string } | { kind: "success"; projects: ProjectSummary[] };

type ListState = { kind: "loading" } | ListResult;

export function ProjectList() {
  const { refresh } = useAuth();
  // 다시 시도할 때마다 증가한다. 결과는 요청한 attempt와 함께 저장하여 이전 요청의 응답을 무시한다.
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; value: ListResult } | null>(null);
  const state: ListState = result?.attempt === attempt ? result.value : { kind: "loading" };

  useEffect(() => {
    // 언마운트되거나 다시 시도하면 이 요청의 응답은 반영하지 않는다.
    let active = true;
    fetchProjects().then(
      (projects) => {
        if (active) {
          setResult({ attempt, value: { kind: "success", projects } });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({ attempt, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
  }, [attempt, refresh]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);

  if (state.kind === "loading") {
    return (
      <p role="status" className="text-zinc-500">
        프로젝트를 불러오는 중...
      </p>
    );
  }
  if (state.kind === "error") {
    return (
      <div role="alert" className="flex flex-col items-start gap-2 text-red-700">
        <p>프로젝트를 불러오지 못했습니다.</p>
        <p className="text-sm">{state.message}</p>
        <button
          type="button"
          onClick={retry}
          className="rounded border border-zinc-300 px-3 py-1 text-sm text-zinc-800"
        >
          다시 시도
        </button>
      </div>
    );
  }
  if (state.projects.length === 0) {
    return (
      <div className="flex flex-col items-start gap-3">
        <p className="text-zinc-600">아직 프로젝트가 없습니다.</p>
        <NewProjectLink />
      </div>
    );
  }
  return (
    <ul className="flex flex-col divide-y divide-zinc-200 rounded-lg border border-zinc-200">
      {state.projects.map((project) => (
        <li key={project.id}>
          <Link
            href={`/projects/${project.id}`}
            className="flex items-center justify-between gap-4 px-4 py-3 hover:bg-zinc-50"
          >
            <span className="break-all">
              <span className="font-medium">{project.name}</span>
              <span className="ml-2 text-sm text-zinc-500">{project.myRole === "OWNER" ? "Owner" : "Member"}</span>
            </span>
            <time dateTime={project.createdAt} className="shrink-0 text-sm text-zinc-500">
              {formatDateTime(project.createdAt)}
            </time>
          </Link>
        </li>
      ))}
    </ul>
  );
}

export function NewProjectLink() {
  return (
    <Link href="/projects/new" className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white">
      새 프로젝트
    </Link>
  );
}
