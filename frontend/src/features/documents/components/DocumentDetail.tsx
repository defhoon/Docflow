"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CheckCircleIcon, LockIcon } from "@/components/ui/icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorState, LoadingBlock } from "@/components/ui/States";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { fetchDocumentActivities } from "@/features/activity/api";
import { ActivityFeed } from "@/features/activity/components/ActivityFeed";
import { useAuth } from "@/features/auth/AuthProvider";
import { FeedbackPanel } from "@/features/feedback/FeedbackPanel";
import { fetchProject } from "@/features/projects/api";
import type { Project } from "@/features/projects/types";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import { ApiError } from "@/lib/api-client";

import { fetchDocument } from "../api";
import { formatFileSize } from "../format";
import type { DocumentDetail as DocumentDetailData } from "../types";
import type { DocumentVersionSummary } from "../version-files";
import { DocumentVersionHistory } from "./DocumentVersionHistory";
import { DocumentVersionPreview } from "./DocumentVersionPreview";
import { DocumentVersionUploadForm } from "./DocumentVersionUploadForm";
import { FinalizePanel } from "./FinalizePanel";

type DetailResult =
  | { kind: "project-not-found" }
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "success"; document: DocumentDetailData };

type DetailState = { kind: "loading" } | DetailResult;

const ID_PATTERN = /^[0-9]+$/;

/**
 * 문서 협업 작업 공간 (#28): 버전 미리보기·히스토리(SPEC-05), 선택 버전의 피드백(SPEC-08), 새 버전 등록(SPEC-04),
 * 오너의 최종 확정(SPEC-07), 활동 이력(SPEC-06). FINALIZED이면 읽기 전용으로 바뀐다.
 */
export function DocumentDetail() {
  const { projectId, documentId } = useParams<{ projectId: string; documentId: string }>();
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const requestKey = `${projectId}/${documentId}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: DetailResult } | null>(null);
  // 숫자가 아닌 ID는 서버도 400으로 거부하므로 요청 없이 같은 404 화면을 표시한다.
  const validIds = ID_PATTERN.test(projectId) && ID_PATTERN.test(documentId);
  const state: DetailState = !validIds
    ? { kind: "not-found" }
    : result?.key === requestKey
      ? result.value
      : { kind: "loading" };

  useEffect(() => {
    if (!validIds) {
      return;
    }
    let active = true;
    fetchDocument(projectId, documentId).then(
      (document) => {
        if (active) {
          setResult({ key: requestKey, value: { kind: "success", document } });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        let value: DetailResult;
        if (error instanceof ApiError && error.status === 404 && errorCode(error) === "PROJECT_NOT_FOUND") {
          value = { kind: "project-not-found" };
        } else if (error instanceof ApiError && (error.status === 404 || error.status === 400)) {
          // 다른 프로젝트의 문서와 존재하지 않는 문서를 구별하지 않는다.
          value = { kind: "not-found" };
        } else {
          value = { kind: "error", message: failureMessage(error) };
        }
        setResult({ key: requestKey, value });
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, documentId, requestKey, validIds, refresh]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);
  // 버전 히스토리 갱신 신호와 미리보기 중인 버전 (SPEC-05 §9.2).
  const [versionsReloadKey, setVersionsReloadKey] = useState(0);
  const [previewVersion, setPreviewVersion] = useState<DocumentVersionSummary | null>(null);
  const closePreview = useCallback(() => setPreviewVersion(null), []);
  // 피드백 대상 버전(기본: 최신)과 선택 가능한 버전 목록 (#28).
  const [selectedNumber, setSelectedNumber] = useState<number | null>(null);
  const [versions, setVersions] = useState<DocumentVersionSummary[]>([]);
  const [activityKey, setActivityKey] = useState(0);
  const bumpActivity = useCallback(() => setActivityKey((n) => n + 1), []);
  // 오너 여부·프로젝트 이름(빵부스러기). 실패해도 문서 화면은 표시하고 확정 영역만 숨긴다(권한은 Backend가 판정).
  const [project, setProject] = useState<{ key: string; value: Project | null } | null>(null);
  useEffect(() => {
    if (!validIds) {
      return;
    }
    let active = true;
    fetchProject(projectId).then(
      (value) => active && setProject({ key: projectId, value }),
      () => active && setProject({ key: projectId, value: null }),
    );
    return () => {
      active = false;
    };
  }, [projectId, validIds]);
  const projectInfo = project?.key === projectId ? project.value : null;
  const loadActivities = useCallback(
    () => fetchDocumentActivities(projectId, documentId, 15),
    [projectId, documentId],
  );

  // 새 버전 등록 성공: 응답(갱신된 상세)으로 바로 바꾼다. 로딩 화면을 거치지 않는다.
  const handleVersionUploaded = useCallback(
    (document: DocumentDetailData) => {
      setResult({ key: requestKey, value: { kind: "success", document } });
      setVersionsReloadKey((current) => current + 1);
      setSelectedNumber(document.latestVersion.versionNumber);
      bumpActivity();
    },
    [requestKey, bumpActivity],
  );

  // 최종 확정 성공: 응답으로 읽기 전용 화면으로 바꾼다.
  const handleFinalized = useCallback(
    (document: DocumentDetailData) => {
      setResult({ key: requestKey, value: { kind: "success", document } });
      setVersionsReloadKey((current) => current + 1);
      bumpActivity();
    },
    [requestKey, bumpActivity],
  );

  // 409(상태 충돌): 등록 영역의 오류 문구를 유지하도록 로딩 화면 없이 다시 조회한다 (결정 I6). 실패하면 현재 화면을 유지한다.
  const reloadInBackground = useCallback(() => {
    setVersionsReloadKey((current) => current + 1);
    fetchDocument(projectId, documentId).then(
      (document) =>
        setResult((current) =>
          current?.key === requestKey ? { key: requestKey, value: { kind: "success", document } } : current,
        ),
      () => undefined,
    );
  }, [projectId, documentId, requestKey]);

  switch (state.kind) {
    case "loading":
      return <LoadingBlock label="불러오는 중..." lines={6} />;
    case "project-not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
          <Link href="/projects" className="text-sm text-brand-700 underline">
            프로젝트 목록으로
          </Link>
        </div>
      );
    case "not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="font-medium">문서를 찾을 수 없습니다.</p>
          <ProjectLink projectId={projectId} />
        </div>
      );
    case "error":
      return <ErrorState title="문서를 불러오지 못했습니다." message={state.message} onRetry={retry} />;
    case "success": {
      const { document } = state;
      const latest = document.latestVersion;
      const finalized = document.status === "FINALIZED";
      const finalization = document.finalization;
      const selected = selectedNumber ?? finalization?.versionNumber ?? latest.versionNumber;
      const selectedSummary = versions.find((version) => version.versionNumber === selected) ?? null;
      const isOwner = projectInfo?.myRole === "OWNER";
      return (
        <>
          <PageHeader
            breadcrumb={[
              { label: "프로젝트", href: "/projects" },
              { label: projectInfo?.name ?? "프로젝트", href: `/projects/${projectId}` },
              { label: document.title },
            ]}
            title={document.title}
            description={document.description ? <span className="whitespace-pre-wrap break-words">{document.description}</span> : undefined}
            meta={
              <>
                <StatusBadge status={document.status} />
                <span>작성자 {document.createdBy.displayName}</span>
                <span aria-hidden="true">·</span>
                <span>
                  등록 <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
                </span>
                <span aria-hidden="true">·</span>
                <span>최신 버전 v{latest.versionNumber}</span>
                {finalization && (
                  <>
                    <span aria-hidden="true">·</span>
                    <span className="font-medium text-success-700">최종 버전 v{finalization.versionNumber}</span>
                  </>
                )}
              </>
            }
          />
          {finalized && finalization && (
            <div role="status" className="flex flex-col gap-3 rounded-[var(--radius-card)] border border-success-600/25 bg-success-50 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
              <div className="flex gap-3">
                <CheckCircleIcon className="mt-0.5 size-5 shrink-0 text-success-700" />
                <div className="text-sm">
                  <p className="font-semibold text-success-700">v{finalization.versionNumber}이 최종 버전으로 확정되었습니다.</p>
                  <p className="mt-0.5 text-ink-muted">
                    {finalization.finalizedBy.displayName} ·{" "}
                    <time dateTime={finalization.finalizedAt}>{formatDateTime(finalization.finalizedAt)}</time>. 이 문서는 읽기 전용이며
                    다시 열 수 없습니다. 변경이 필요하면 새 문서를 등록해 주세요.
                  </p>
                </div>
              </div>
              <ButtonLink href={`/projects/${projectId}/documents/new`} size="sm">
                새 문서 등록
              </ButtonLink>
            </div>
          )}
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-12">
            <div className="flex min-w-0 flex-col gap-6 lg:col-span-7">
              <Card aria-labelledby="preview-heading">
                <CardHeader
                  title={`미리보기 · v${selected}`}
                  titleId="preview-heading"
                  description={selectedSummary ? selectedSummary.originalFilename : undefined}
                />
                <CardBody>
                  {previewVersion ? (
                    <DocumentVersionPreview
                      key={previewVersion.versionNumber}
                      projectId={projectId}
                      documentId={documentId}
                      version={previewVersion}
                      onClose={closePreview}
                    />
                  ) : (
                    <div className="flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed border-line-strong bg-subtle/50 px-6 py-12 text-center">
                      <p className="text-sm text-ink-muted">
                        버전 목록에서 &lsquo;미리보기&rsquo;를 누르면 여기에 표시됩니다. Office 문서는 환경에 따라 미리보기가 제공되지 않을 수
                        있으며 원본을 내려받아 확인할 수 있습니다.
                      </p>
                      {selectedSummary && (
                        <button
                          type="button"
                          onClick={() => setPreviewVersion(selectedSummary)}
                          className="inline-flex h-8 items-center justify-center rounded-md border border-line-strong bg-surface px-3 text-sm text-ink hover:bg-subtle"
                        >
                          v{selected} 미리보기 열기
                        </button>
                      )}
                    </div>
                  )}
                </CardBody>
              </Card>
              <Card aria-labelledby="versions-card-heading">
                <CardHeader title="버전" titleId="versions-card-heading" description="버전을 선택하면 오른쪽에 해당 버전의 피드백이 표시됩니다." />
                <CardBody className="py-2">
                  <DocumentVersionHistory
                    projectId={projectId}
                    documentId={documentId}
                    reloadKey={versionsReloadKey}
                    selectedVersion={selected}
                    hideHeading
                    finalVersionNumber={finalization?.versionNumber ?? null}
                    onLoaded={setVersions}
                    onSelect={(version) => setSelectedNumber(version.versionNumber)}
                    onPreview={(version) => {
                      setSelectedNumber(version.versionNumber);
                      setPreviewVersion(version);
                    }}
                  />
                </CardBody>
              </Card>
            </div>
            <div className="flex min-w-0 flex-col gap-6 lg:col-span-5">
              <Card aria-labelledby="feedback-heading">
                <CardHeader
                  title={`피드백 · v${selected}`}
                  titleId="feedback-heading"
                  description={finalized ? "최종 확정된 문서의 피드백은 조회만 할 수 있습니다." : "모든 구성원이 각 버전에 피드백을 남길 수 있습니다."}
                />
                <CardBody>
                  <FeedbackPanel
                    projectId={projectId}
                    documentId={documentId}
                    versionNumber={selected}
                    canWrite={!finalized}
                    onFinalizedConflict={reloadInBackground}
                    onCreated={bumpActivity}
                  />
                </CardBody>
              </Card>
              {!finalized && (
                <Card>
                  <CardBody>
                    <DocumentVersionUploadForm
                      projectId={projectId}
                      documentId={documentId}
                      status={document.status}
                      onUploaded={handleVersionUploaded}
                      onConflict={reloadInBackground}
                    />
                  </CardBody>
                </Card>
              )}
              {!finalized && isOwner && (
                <Card className="border-warning-700/20">
                  <CardBody>
                    <FinalizePanel
                      projectId={projectId}
                      documentId={documentId}
                      versions={versions}
                      defaultVersion={selected}
                      onFinalized={handleFinalized}
                      onConflict={reloadInBackground}
                    />
                  </CardBody>
                </Card>
              )}
              {finalized && (
                <p className="flex items-center gap-2 text-xs text-ink-muted">
                  <LockIcon /> 새 버전 업로드와 피드백 작성이 종료되었습니다.
                </p>
              )}
              <Card aria-labelledby="document-info-heading">
                <CardHeader title="최신 버전 정보" titleId="document-info-heading" />
                <CardBody>
                  <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
                    <dt className="text-ink-muted">최신 버전</dt>
                    <dd>v{latest.versionNumber}</dd>
                    <dt className="text-ink-muted">파일</dt>
                    <dd className="break-all">{latest.originalFilename}</dd>
                    <dt className="text-ink-muted">크기</dt>
                    <dd title={`${latest.sizeBytes.toLocaleString("ko-KR")} 바이트`}>{formatFileSize(latest.sizeBytes)}</dd>
                    <dt className="text-ink-muted">업로드</dt>
                    <dd>
                      <time dateTime={latest.uploadedAt}>{formatDateTime(latest.uploadedAt)}</time>
                    </dd>
                  </dl>
                </CardBody>
              </Card>
              <Card aria-labelledby="document-activity-heading">
                <CardHeader title="활동 이력" titleId="document-activity-heading" />
                <CardBody className="py-1">
                  <ActivityFeed
                    load={loadActivities}
                    loadKey={`document-${projectId}-${documentId}-${activityKey}`}
                    showDocument={false}
                  />
                </CardBody>
              </Card>
            </div>
          </div>
        </>
      );
    }
  }
}

function ProjectLink({ projectId }: { projectId: string }) {
  return (
    <Link href={`/projects/${projectId}`} className="text-sm text-brand-700 underline">
      프로젝트로 돌아가기
    </Link>
  );
}
