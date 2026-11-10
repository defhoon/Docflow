"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { FolderIcon, PlusIcon } from "@/components/ui/icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { fetchMyActivities } from "@/features/activity/api";
import { ActivityFeed } from "@/features/activity/components/ActivityFeed";
import { useAuth } from "@/features/auth/AuthProvider";
import { fetchProjects } from "@/features/projects/api";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import type { ProjectSummary } from "@/features/projects/types";

type ProjectsState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "success"; projects: ProjectSummary[] };

const HOME_PROJECT_LIMIT = 6;

/**
 * '내 작업' 홈 (#28). 실제 API가 제공하는 데이터만 표시한다: 내 프로젝트(SPEC-01), 접근 가능한 프로젝트의 최근 활동(SPEC-06).
 * 통계 Dashboard는 Post-MVP이므로 두지 않는다.
 */
export function HomeDashboard() {
  const { user, refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; value: ProjectsState } | null>(null);
  const state: ProjectsState = result?.attempt === attempt ? result.value : { kind: "loading" };

  useEffect(() => {
    let active = true;
    fetchProjects().then(
      (projects) => active && setResult({ attempt, value: { kind: "success", projects } }),
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

  const loadActivities = useCallback(() => fetchMyActivities(15), []);

  return (
    <>
      <PageHeader
        title={user ? `${user.displayName}님, 안녕하세요` : "내 작업"}
        description="참여 중인 프로젝트와 최근 문서 활동을 확인하세요."
        actions={
          <ButtonLink href="/projects/new" variant="primary">
            <PlusIcon /> 새 프로젝트
          </ButtonLink>
        }
      />
      <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-5">
        <Card className="xl:col-span-3" aria-labelledby="home-projects">
          <CardHeader
            title="내 프로젝트"
            titleId="home-projects"
            actions={
              <Link href="/projects" className="text-sm font-medium text-brand-700 hover:underline">
                전체 보기
              </Link>
            }
          />
          <CardBody>
            {state.kind === "loading" && <LoadingBlock label="프로젝트를 불러오는 중..." />}
            {state.kind === "error" && (
              <ErrorState title="프로젝트를 불러오지 못했습니다." message={state.message} onRetry={() => setAttempt((n) => n + 1)} />
            )}
            {state.kind === "success" && state.projects.length === 0 && (
              <EmptyState
                icon={<FolderIcon className="size-6" />}
                title="참여 중인 프로젝트가 없습니다."
                description="프로젝트를 만들고 구성원을 추가해 문서 협업을 시작하세요."
                action={
                  <ButtonLink href="/projects/new" variant="primary" size="sm">
                    프로젝트 만들기
                  </ButtonLink>
                }
              />
            )}
            {state.kind === "success" && state.projects.length > 0 && (
              <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                {state.projects.slice(0, HOME_PROJECT_LIMIT).map((project) => (
                  <li key={project.id}>
                    <Link
                      href={`/projects/${project.id}`}
                      className="flex h-full items-start gap-3 rounded-lg border border-line p-3 transition-colors hover:border-brand-500/40 hover:bg-brand-50/40"
                    >
                      <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                        <FolderIcon />
                      </span>
                      <span className="flex min-w-0 flex-1 flex-col gap-1">
                        <span className="truncate text-sm font-medium text-ink">{project.name}</span>
                        <Badge tone={project.myRole === "OWNER" ? "brand" : "neutral"}>
                          {project.myRole === "OWNER" ? "오너" : "구성원"}
                        </Badge>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
        <Card className="xl:col-span-2" aria-labelledby="home-activity">
          <CardHeader title="최근 활동" titleId="home-activity" description="내가 참여한 프로젝트의 문서 활동" />
          <CardBody className="py-1">
            <ActivityFeed load={loadActivities} loadKey="mine" showProject emptyText="아직 문서 활동이 없습니다." />
          </CardBody>
        </Card>
      </div>
    </>
  );
}
