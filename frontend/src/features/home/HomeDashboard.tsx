"use client";

import Link from "next/link";
import { useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { FolderIcon, PlusIcon } from "@/components/ui/icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/States";
import { Badge, StatusBadge } from "@/components/ui/StatusBadge";
import { ActivityList } from "@/features/activity/components/ActivityFeed";
import { relativeTime } from "@/features/activity/format";
import { useAuth } from "@/features/auth/AuthProvider";
import { FileTypeIcon } from "@/features/documents/components/FileTypeIcon";
import { fetchProjects } from "@/features/projects/api";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import type { ProjectSummary } from "@/features/projects/types";

import { fetchHome } from "./api";
import type { HomeSummary } from "./types";

type Loaded<T> = { kind: "loading" } | { kind: "error"; message: string } | { kind: "success"; value: T };

const HOME_PROJECT_LIMIT = 6;

/**
 * 홈 대시보드 (SPEC-18). 읽기 전용 통합 조회(`GET /api/home`)의 지표·최근 문서·최근 활동과 내 프로젝트 목록을 보여 준다.
 * 실제 데이터로 정확히 셀 수 있는 지표만 둔다(검토 요청·알림 등은 데이터가 없으므로 표시하지 않는다).
 */
export function HomeDashboard() {
  const { user, refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const [home, setHome] = useState<{ attempt: number; value: Loaded<HomeSummary> } | null>(null);
  const [projects, setProjects] = useState<{ attempt: number; value: Loaded<ProjectSummary[]> } | null>(null);
  const homeState: Loaded<HomeSummary> = home?.attempt === attempt ? home.value : { kind: "loading" };
  const projectState: Loaded<ProjectSummary[]> = projects?.attempt === attempt ? projects.value : { kind: "loading" };

  useEffect(() => {
    let active = true;
    const fail = (set: typeof setHome | typeof setProjects) => (error: unknown) => {
      if (!active) {
        return;
      }
      if (isUnauthenticated(error)) {
        void refresh();
      }
      set({ attempt, value: { kind: "error", message: failureMessage(error) } });
    };
    fetchHome().then((value) => active && setHome({ attempt, value: { kind: "success", value } }), fail(setHome));
    fetchProjects().then((value) => active && setProjects({ attempt, value: { kind: "success", value } }), fail(setProjects));
    return () => {
      active = false;
    };
  }, [attempt, refresh]);

  const retry = () => setAttempt((n) => n + 1);
  const summary = homeState.kind === "success" ? homeState.value : null;

  return (
    <>
      <PageHeader
        title={user ? `${user.displayName}님, 안녕하세요` : "홈"}
        description="참여 중인 프로젝트의 문서 진행 상황과 최근 활동입니다."
        actions={
          <ButtonLink href="/projects/new" variant="primary">
            <PlusIcon /> 새 프로젝트
          </ButtonLink>
        }
      />
      {homeState.kind === "error" && <ErrorState title="홈 정보를 불러오지 못했습니다." message={homeState.message} onRetry={retry} />}
      <section aria-label="요약" className="grid grid-cols-1 overflow-hidden rounded-[var(--radius-card)] border border-line bg-surface sm:grid-cols-3">
        <Stat label="참여 프로젝트" value={summary?.projectCount} href="/projects" />
        <Stat label="협업 중인 문서" value={summary?.activeDocumentCount} tone="info" />
        <Stat label="최종 확정 문서" value={summary?.finalizedDocumentCount} tone="success" />
      </section>
      <div className="grid grid-cols-1 items-start gap-6 xl:grid-cols-[minmax(0,1fr)_22rem]">
        <div className="flex min-w-0 flex-col gap-6">
          <Card aria-labelledby="home-recent-documents">
            <CardHeader title="최근 업데이트된 문서" titleId="home-recent-documents" description="최대 5개 · 새 버전·설명 변경·최종 확정 기준" />
            <CardBody className="px-0 py-0">
              {homeState.kind === "loading" && <ListSkeleton />}
              {summary && summary.recentDocuments.length === 0 && (
                <div className="p-5">
                  <EmptyState title="아직 등록된 문서가 없습니다." description="프로젝트에서 문서를 등록하면 여기에 최근 문서가 표시됩니다." />
                </div>
              )}
              {summary && summary.recentDocuments.length > 0 && (
                <ul className="divide-y divide-line">
                  {summary.recentDocuments.map((document) => (
                    <li key={document.id}>
                      <Link
                        href={`/projects/${document.project.id}/documents/${document.id}`}
                        className="flex items-center gap-3 px-5 py-3 transition-colors hover:bg-subtle/60"
                      >
                        <FileTypeIcon type={document.fileType} />
                        <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                          <span className="truncate text-sm font-medium text-ink" title={document.title}>
                            {document.title}
                          </span>
                          <span className="truncate text-xs text-ink-muted" title={document.project.name}>
                            {document.project.name}
                          </span>
                        </span>
                        <span className="hidden shrink-0 sm:block">
                          <StatusBadge status={document.status} />
                        </span>
                        <span className="w-20 shrink-0 text-right text-xs text-ink-muted tabular-nums">
                          v{document.latestVersionNumber}
                          {document.finalVersionNumber !== null && (
                            <span className="block text-success-700">최종 v{document.finalVersionNumber}</span>
                          )}
                        </span>
                        <span className="hidden w-32 shrink-0 text-right text-xs text-ink-muted md:block">
                          <span className="block truncate text-ink">{document.latestUploader.displayName}</span>
                          <time dateTime={document.updatedAt} title={formatDateTime(document.updatedAt)}>
                            {relativeTime(document.updatedAt)}
                          </time>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>
          <Card aria-labelledby="home-projects">
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
              {projectState.kind === "loading" && <ListSkeleton rows={2} />}
              {projectState.kind === "error" && <ErrorState title="프로젝트를 불러오지 못했습니다." message={projectState.message} onRetry={retry} />}
              {projectState.kind === "success" && projectState.value.length === 0 && (
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
              {projectState.kind === "success" && projectState.value.length > 0 && (
                <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                  {projectState.value.slice(0, HOME_PROJECT_LIMIT).map((project) => (
                    <li key={project.id}>
                      <Link
                        href={`/projects/${project.id}`}
                        className="flex h-full items-center gap-3 rounded-lg border border-line p-3 transition-colors hover:border-brand-500/40 hover:bg-brand-50/40"
                      >
                        <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-sm font-semibold text-brand-700">
                          {project.name.slice(0, 1)}
                        </span>
                        <span className="flex min-w-0 flex-1 flex-col gap-1">
                          <span className="truncate text-sm font-medium text-ink" title={project.name}>
                            {project.name}
                          </span>
                          <Badge tone={project.myRole === "OWNER" ? "brand" : "neutral"}>{project.myRole === "OWNER" ? "오너" : "구성원"}</Badge>
                        </span>
                      </Link>
                    </li>
                  ))}
                </ul>
              )}
            </CardBody>
          </Card>
        </div>
        <Card aria-labelledby="home-activity">
          <CardHeader
            title="최근 활동"
            titleId="home-activity"
            actions={
              <Link href="/activity" className="text-sm font-medium text-brand-700 hover:underline">
                전체 활동
              </Link>
            }
          />
          <CardBody className="py-1">
            {homeState.kind === "loading" && <ListSkeleton rows={3} />}
            {summary && summary.recentActivities.length === 0 && (
              <p className="py-6 text-center text-sm text-ink-muted">아직 문서 활동이 없습니다.</p>
            )}
            {summary && summary.recentActivities.length > 0 && <ActivityList items={summary.recentActivities} showProject />}
          </CardBody>
        </Card>
      </div>
    </>
  );
}

function Stat({ label, value, href, tone }: { label: string; value: number | undefined; href?: string; tone?: "info" | "success" }) {
  const body = (
    <>
      <span className="flex items-center gap-2 text-sm text-ink-muted">
        {tone && <span aria-hidden="true" className={tone === "info" ? "size-2 rounded-full bg-info-600" : "size-2 rounded-[2px] bg-success-600"} />}
        {label}
      </span>
      {value === undefined ? (
        <Skeleton className="mt-1 h-8 w-12" />
      ) : (
        <span className="text-3xl font-semibold tracking-tight text-ink tabular-nums">{value.toLocaleString("ko-KR")}</span>
      )}
    </>
  );
  const className = "flex flex-col gap-1 border-line px-5 py-4 not-last:border-b sm:not-last:border-r sm:not-last:border-b-0";
  return href ? (
    <Link href={href} className={`${className} transition-colors hover:bg-subtle/60`}>
      {body}
    </Link>
  ) : (
    <div className={className}>{body}</div>
  );
}

function ListSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="flex flex-col gap-3 p-5" aria-busy="true">
      <p role="status" className="sr-only">
        불러오는 중...
      </p>
      {Array.from({ length: rows }, (_, index) => (
        <Skeleton key={index} className="h-9 w-full" />
      ))}
    </div>
  );
}
