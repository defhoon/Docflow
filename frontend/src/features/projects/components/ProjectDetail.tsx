"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { DocumentList } from "@/features/documents/components/DocumentList";
import { ApiError } from "@/lib/api-client";

import { fetchProject } from "../api";
import { failureMessage, isUnauthenticated } from "../errors";
import { formatDateTime } from "../format";
import type { Project } from "../types";
import { ProjectMembers } from "./ProjectMembers";

type DetailResult =
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "success"; project: Project };

type DetailState = { kind: "loading" } | DetailResult;

const PROJECT_ID_PATTERN = /^[0-9]+$/;

export function ProjectDetail() {
  const { projectId } = useParams<{ projectId: string }>();
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  // 결과를 요청 키(projectId + attempt)와 함께 저장하여 다른 프로젝트·이전 시도의 응답을 표시하지 않는다.
  const requestKey = `${projectId}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: DetailResult } | null>(null);
  // 숫자가 아닌 ID는 서버도 400으로 거부하므로 요청 없이 같은 404 화면을 표시한다.
  const validId = PROJECT_ID_PATTERN.test(projectId);
  const state: DetailState = !validId
    ? { kind: "not-found" }
    : result?.key === requestKey
      ? result.value
      : { kind: "loading" };

  useEffect(() => {
    if (!validId) {
      return;
    }
    let active = true;
    fetchProject(projectId).then(
      (project) => {
        if (active) {
          setResult({ key: requestKey, value: { kind: "success", project } });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        // 다른 사용자의 프로젝트와 존재하지 않는 프로젝트를 구별하지 않는다.
        const value: DetailResult =
          error instanceof ApiError && (error.status === 404 || error.status === 400)
            ? { kind: "not-found" }
            : { kind: "error", message: failureMessage(error) };
        setResult({ key: requestKey, value });
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, requestKey, validId, refresh]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);

  switch (state.kind) {
    case "loading":
      return (
        <p role="status" className="text-zinc-500">
          불러오는 중...
        </p>
      );
    case "not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
          <ProjectsLink />
        </div>
      );
    case "error":
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
    case "success": {
      const { project } = state;
      return (
        <article className="flex flex-col gap-4">
          <h2 className="text-2xl font-semibold break-all">{project.name}</h2>
          <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-zinc-500">Owner</dt>
            <dd>{project.owner.displayName}</dd>
            <dt className="text-zinc-500">생성일</dt>
            <dd>
              <time dateTime={project.createdAt}>{formatDateTime(project.createdAt)}</time>
            </dd>
          </dl>
          <section>
            <h3 className="mb-1 text-sm font-semibold text-zinc-500">설명</h3>
            {project.description ? (
              <p className="whitespace-pre-wrap break-all">{project.description}</p>
            ) : (
              <p className="text-zinc-500">설명이 없습니다.</p>
            )}
          </section>
          <DocumentList projectId={projectId} />
          <ProjectMembers projectId={projectId} myRole={project.myRole} onProjectChanged={retry} />
          <ProjectsLink />
        </article>
      );
    }
  }
}

function ProjectsLink() {
  return (
    <Link href="/projects" className="text-sm text-zinc-700 underline">
      프로젝트 목록으로
    </Link>
  );
}
