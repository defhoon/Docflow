"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Input, Select } from "@/components/ui/Field";
import { FolderIcon, PlusIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
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
    return <LoadingBlock label="프로젝트를 불러오는 중..." lines={4} />;
  }
  if (state.kind === "error") {
    return <ErrorState title="프로젝트를 불러오지 못했습니다." message={state.message} onRetry={retry} />;
  }
  if (state.projects.length === 0) {
    return (
      <EmptyState
        icon={<FolderIcon className="size-6" />}
        title="아직 프로젝트가 없습니다."
        description="프로젝트를 만들면 구성원을 추가하고 문서를 함께 검토할 수 있습니다."
        action={<NewProjectLink />}
      />
    );
  }
  return <ProjectGrid projects={state.projects} />;
}

type SortKey = "recent" | "name";

/** 불러온 목록 안에서만 검색·정렬한다(서버 검색 API 없음). */
function ProjectGrid({ projects }: { projects: ProjectSummary[] }) {
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortKey>("recent");
  const normalized = query.trim().toLocaleLowerCase("ko-KR");
  const visible = projects
    .filter((project) => project.name.toLocaleLowerCase("ko-KR").includes(normalized))
    .sort((a, b) => (sort === "name" ? a.name.localeCompare(b.name, "ko-KR") : 0));
  return (
    <div className="flex flex-col gap-4">
      <div className="flex flex-col gap-2 sm:flex-row sm:items-center">
        <label htmlFor="project-search" className="sr-only">
          프로젝트 검색
        </label>
        <Input
          id="project-search"
          type="search"
          placeholder="프로젝트 이름으로 찾기"
          value={query}
          onChange={(event) => setQuery(event.target.value)}
          className="sm:max-w-xs"
        />
        <label htmlFor="project-sort" className="sr-only">
          정렬
        </label>
        <Select id="project-sort" value={sort} onChange={(event) => setSort(event.target.value as SortKey)} className="sm:w-36">
          <option value="recent">최근 생성순</option>
          <option value="name">이름순</option>
        </Select>
      </div>
      {visible.length === 0 ? (
        <EmptyState title="검색 결과가 없습니다." description="다른 이름으로 찾아보세요." />
      ) : (
        <ul className="grid grid-cols-1 gap-3 md:grid-cols-2 xl:grid-cols-3">
          {visible.map((project) => (
            <li key={project.id}>
              <Link
                href={`/projects/${project.id}`}
                className="flex h-full flex-col gap-3 rounded-[var(--radius-card)] border border-line bg-surface p-4 shadow-[var(--shadow-card)] transition-colors hover:border-brand-500/40"
              >
                <span className="flex items-start justify-between gap-3">
                  <span className="flex min-w-0 items-center gap-2.5">
                    <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                      <FolderIcon />
                    </span>
                    <span className="font-medium break-all text-ink">{project.name}</span>
                  </span>
                  <Badge tone={project.myRole === "OWNER" ? "brand" : "neutral"}>
                    {project.myRole === "OWNER" ? "오너" : "구성원"}
                  </Badge>
                </span>
                <span className="text-xs text-ink-muted">
                  생성 <time dateTime={project.createdAt}>{formatDateTime(project.createdAt)}</time>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

export function NewProjectLink() {
  return (
    <ButtonLink href="/projects/new" variant="primary">
      <PlusIcon /> 새 프로젝트
    </ButtonLink>
  );
}
