"use client";

import Link from "next/link";
import { usePathname, useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { PlusIcon } from "@/components/ui/icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorState, LoadingBlock } from "@/components/ui/States";
import { Tabs, tabPanelProps } from "@/components/ui/Tabs";
import { Badge } from "@/components/ui/StatusBadge";
import { fetchProjectActivities } from "@/features/activity/api";
import { ActivityFeed } from "@/features/activity/components/ActivityFeed";
import { useAuth } from "@/features/auth/AuthProvider";
import { DocumentList } from "@/features/documents/components/DocumentList";
import { ApiError } from "@/lib/api-client";

import { fetchProject } from "../api";
import { failureMessage, isUnauthenticated } from "../errors";
import { formatDateTime } from "../format";
import type { Project } from "../types";
import { ProjectMembers } from "./ProjectMembers";
import { AiSettingsCard } from "@/features/analysis/components/AiSettingsCard";

type DetailResult =
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "success"; project: Project };

type DetailState = { kind: "loading" } | DetailResult;

const PROJECT_ID_PATTERN = /^[0-9]+$/;

type ProjectTab = "docs" | "members" | "activity" | "settings";
const TABS: ProjectTab[] = ["docs", "members", "activity", "settings"];

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

  const loadActivities = useCallback(() => fetchProjectActivities(projectId, 50), [projectId]);
  // 탭은 URL(?tab=)에 둔다 (SPEC-18). 기본은 문서.
  const searchParams = useSearchParams();
  const router = useRouter();
  const pathname = usePathname();
  const requestedTab = searchParams.get("tab") as ProjectTab | null;
  const tab: ProjectTab = requestedTab && TABS.includes(requestedTab) ? requestedTab : "docs";
  const selectTab = (next: ProjectTab) => router.replace(next === "docs" ? pathname : `${pathname}?tab=${next}`, { scroll: false });

  switch (state.kind) {
    case "loading":
      return <LoadingBlock label="불러오는 중..." lines={5} />;
    case "not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
          <ProjectsLink />
        </div>
      );
    case "error":
      return <ErrorState title="프로젝트를 불러오지 못했습니다." message={state.message} onRetry={retry} />;
    case "success": {
      const { project } = state;
      return (
        <>
          <PageHeader
            breadcrumb={[{ label: "프로젝트", href: "/projects" }, { label: project.name }]}
            title={project.name}
            description={project.description ? <span className="whitespace-pre-wrap break-words">{project.description}</span> : "설명이 없습니다."}
            actions={
              <ButtonLink href={`/projects/${projectId}/documents/new`} variant="primary">
                <PlusIcon /> 문서 등록
              </ButtonLink>
            }
            meta={
              <>
                <Badge tone={project.myRole === "OWNER" ? "brand" : "neutral"}>{project.myRole === "OWNER" ? "내가 오너" : "구성원"}</Badge>
                <span>오너 {project.owner.displayName}</span>
                <span aria-hidden="true">·</span>
                <span>
                  생성 <time dateTime={project.createdAt}>{formatDateTime(project.createdAt)}</time>
                </span>
              </>
            }
          />
          <div className="flex flex-col gap-5">
            <Tabs<ProjectTab>
              label="프로젝트"
              idBase="project"
              className="border-b border-line"
              value={tab}
              onChange={selectTab}
              items={[
                { key: "docs", label: "문서" },
                { key: "members", label: "구성원" },
                { key: "activity", label: "활동 이력" },
                { key: "settings", label: "설정" },
              ]}
            />
            <div {...tabPanelProps("project", tab)} className="min-w-0 outline-none">
              {tab === "docs" && <DocumentList projectId={projectId} />}
              {tab === "members" && (
                <div className="max-w-3xl">
                  <ProjectMembers projectId={projectId} myRole={project.myRole} onProjectChanged={retry} />
                </div>
              )}
              {tab === "activity" && (
                <Card aria-labelledby="project-activity" className="max-w-3xl">
                  <CardHeader title="활동 이력" titleId="project-activity" description="최근 50개" />
                  <CardBody className="py-1">
                    <ActivityFeed load={loadActivities} loadKey={`project-${projectId}`} emptyText="아직 문서 활동이 없습니다." />
                  </CardBody>
                </Card>
              )}
              {tab === "settings" && (
                <div className="flex max-w-3xl flex-col gap-6">
                  <Card aria-labelledby="project-info">
                    <CardHeader title="프로젝트 정보" titleId="project-info" />
                    <CardBody>
                      <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-4 gap-y-2 text-sm">
                        <dt className="text-ink-muted">이름</dt>
                        <dd className="break-words">{project.name}</dd>
                        <dt className="text-ink-muted">설명</dt>
                        <dd className="whitespace-pre-wrap break-words">{project.description ?? "설명이 없습니다."}</dd>
                        <dt className="text-ink-muted">오너</dt>
                        <dd>{project.owner.displayName}</dd>
                        <dt className="text-ink-muted">생성</dt>
                        <dd>
                          <time dateTime={project.createdAt}>{formatDateTime(project.createdAt)}</time>
                        </dd>
                      </dl>
                    </CardBody>
                  </Card>
                  <AiSettingsCard projectId={projectId} />
                </div>
              )}
            </div>
          </div>
        </>
      );
    }
  }
}

function ProjectsLink() {
  return (
    <Link href="/projects" className="text-sm text-brand-700 underline">
      프로젝트 목록으로
    </Link>
  );
}
