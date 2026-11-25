"use client";

import Link from "next/link";
import { useParams, usePathname, useRouter, useSearchParams } from "next/navigation";
import { type CSSProperties, useCallback, useEffect, useMemo, useState } from "react";

import { Button, ButtonLink } from "@/components/ui/Button";
import { cn } from "@/components/ui/cn";
import { Dialog } from "@/components/ui/Dialog";
import { IconButton, IconLink } from "@/components/ui/IconButton";
import {
  ArrowLeftIcon,
  CheckCircleIcon,
  CompareIcon,
  DownloadIcon,
  PanelLeftIcon,
  PanelRightIcon,
  UploadIcon,
} from "@/components/ui/icons";
import { PaneResizer } from "@/components/ui/PaneResizer";
import { ErrorState, LoadingBlock, Skeleton } from "@/components/ui/States";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Segmented, Tabs, tabPanelProps } from "@/components/ui/Tabs";
import { fetchDocumentActivities } from "@/features/activity/api";
import { ActivityFeed } from "@/features/activity/components/ActivityFeed";
import { fetchAiSettings } from "@/features/analysis/api";
import { useVersionSummary, VersionSummaryView } from "@/features/analysis/components/VersionSummaryPanel";
import type { AiSettings } from "@/features/analysis/types";
import { useAuth } from "@/features/auth/AuthProvider";
import { compareHref } from "@/features/compare/api";
import { FeedbackPanel } from "@/features/feedback/FeedbackPanel";
import { fetchProject } from "@/features/projects/api";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import type { Project } from "@/features/projects/types";
import { ApiError } from "@/lib/api-client";

import { fetchDocument } from "../api";
import { fileFailureMessage } from "../errors";
import { formatFileSize } from "../format";
import type { DocumentDetail as DocumentDetailData } from "../types";
import { type DocumentVersionSummary, downloadVersionFile, fetchDocumentVersions, saveBlob } from "../version-files";
import { versionNoteEmptyText, versionNoteLabel } from "../version-note";
import { DocumentDescription } from "./DocumentDescription";
import { DocumentVersionUploadForm } from "./DocumentVersionUploadForm";
import { FinalizeDialog } from "./FinalizeDialog";
import { type PageRequest, PdfDocumentViewer } from "./PdfDocumentViewer";
import { VersionList } from "./VersionList";

type DetailResult =
  | { kind: "project-not-found" }
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "success"; document: DocumentDetailData };

type DetailState = { kind: "loading" } | DetailResult;

type CollabTab = "feedback" | "ai" | "info" | "activity";
type MobileTab = "doc" | "versions" | "collab";

const ID_PATTERN = /^[0-9]+$/;
const LEFT = { min: 220, max: 400, initial: 264 };
const RIGHT = { min: 300, max: 560, initial: 368 };

function parseVersion(value: string | null): number | null {
  return value && ID_PATTERN.test(value) ? Number(value) : null;
}

/**
 * 문서 상세 3단 워크스페이스 (SPEC-16): 버전 | 문서 뷰어 | 협업(피드백·AI 요약·정보·활동). 선택 버전 하나가 모든 패널의
 * 기준이며 URL `?v=N`과 동기화한다. FINALIZED이면 읽기 전용으로 바뀐다. 권한·상태 판정은 Backend가 한다.
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
      (document) => active && setResult({ key: requestKey, value: { kind: "success", document } }),
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
  const setDocument = useCallback(
    (document: DocumentDetailData) => setResult({ key: requestKey, value: { kind: "success", document } }),
    [requestKey],
  );
  // 409(상태 충돌): 오류 문구를 유지하도록 로딩 화면 없이 다시 조회한다. 실패하면 현재 화면을 유지한다.
  const reloadInBackground = useCallback(() => {
    fetchDocument(projectId, documentId).then(
      (document) =>
        setResult((current) => (current?.key === requestKey ? { key: requestKey, value: { kind: "success", document } } : current)),
      () => undefined,
    );
  }, [projectId, documentId, requestKey]);

  switch (state.kind) {
    case "loading":
      return (
        <div className="p-6">
          <LoadingBlock label="불러오는 중..." lines={6} />
        </div>
      );
    case "project-not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3 p-6">
          <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
          <Link href="/projects" className="text-sm text-brand-700 underline">
            프로젝트 목록으로
          </Link>
        </div>
      );
    case "not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3 p-6">
          <p className="font-medium">문서를 찾을 수 없습니다.</p>
          <Link href={`/projects/${projectId}`} className="text-sm text-brand-700 underline">
            프로젝트로 돌아가기
          </Link>
        </div>
      );
    case "error":
      return (
        <div className="p-6">
          <ErrorState title="문서를 불러오지 못했습니다." message={state.message} onRetry={retry} />
        </div>
      );
    case "success":
      return (
        <Workspace
          key={`${projectId}/${documentId}`}
          projectId={projectId}
          documentId={documentId}
          document={state.document}
          onDocument={setDocument}
          onConflict={reloadInBackground}
        />
      );
  }
}

function Workspace({ projectId, documentId, document, onDocument, onConflict }: {
  projectId: string;
  documentId: string;
  document: DocumentDetailData;
  onDocument: (document: DocumentDetailData) => void;
  onConflict: () => void;
}) {
  const { refresh } = useAuth();
  const router = useRouter();
  const pathname = usePathname();
  const searchParams = useSearchParams();
  const finalized = document.status === "FINALIZED";
  const finalization = document.finalization;
  const latestNumber = document.latestVersion.versionNumber;

  // 프로젝트(오너 여부·이름). 실패해도 문서 화면은 표시하고 확정 버튼만 숨긴다(권한은 Backend가 판정).
  const [project, setProject] = useState<Project | null>(null);
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null);
  useEffect(() => {
    let active = true;
    fetchProject(projectId).then((value) => active && setProject(value), () => undefined);
    fetchAiSettings(projectId).then((value) => active && setAiSettings(value), () => undefined);
    return () => {
      active = false;
    };
  }, [projectId]);
  const isOwner = project?.myRole === "OWNER";

  // 버전 목록. 다시 불러오는 동안 이전 목록을 유지한다.
  const [versionsKey, setVersionsKey] = useState(0);
  const [versionsState, setVersionsState] = useState<{ key: number; versions: DocumentVersionSummary[] } | { key: number; error: string } | null>(null);
  useEffect(() => {
    let active = true;
    fetchDocumentVersions(projectId, documentId).then(
      (versions) => active && setVersionsState({ key: versionsKey, versions }),
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setVersionsState((current) => (current && "versions" in current ? current : { key: versionsKey, error: failureMessage(error) }));
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, documentId, versionsKey, refresh]);
  const versions = useMemo(() => (versionsState && "versions" in versionsState ? versionsState.versions : []), [versionsState]);
  const reloadVersions = useCallback(() => setVersionsKey((n) => n + 1), []);

  // 선택 버전: URL ?v=N(있는 버전일 때) → 확정 문서는 확정 버전, 협업 중이면 최신 버전.
  const defaultVersion = finalization?.versionNumber ?? latestNumber;
  const requested = parseVersion(searchParams.get("v"));
  const selected =
    requested !== null && (versions.length === 0 ? requested <= latestNumber : versions.some((v) => v.versionNumber === requested))
      ? requested
      : defaultVersion;
  const selectedSummary = versions.find((version) => version.versionNumber === selected) ?? null;
  const [mobileTab, setMobileTab] = useState<MobileTab>("doc");
  function selectVersion(versionNumber: number) {
    const params = new URLSearchParams(searchParams.toString());
    params.set("v", String(versionNumber));
    router.replace(`${pathname}?${params.toString()}`, { scroll: false });
    setMobileTab("doc");
  }

  // 버전별 피드백 초안 (SPEC-16 §3.2)
  const [drafts, setDrafts] = useState<Record<number, string>>({});
  const setDraft = useCallback((versionNumber: number, value: string) => {
    setDrafts((current) => ({ ...current, [versionNumber]: value }));
  }, []);
  const draftVersions = useMemo(
    () => new Set(Object.entries(drafts).filter(([, value]) => value.trim().length > 0).map(([key]) => Number(key))),
    [drafts],
  );
  useEffect(() => {
    if (draftVersions.size === 0) {
      return;
    }
    const warn = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", warn);
    return () => window.removeEventListener("beforeunload", warn);
  }, [draftVersions]);

  // AI 요약 (선택 버전). 탭 표시: (프로젝트 AI 켜짐 그리고 서버 사용 가능) 또는 완료된 요약이 있음.
  const summary = useVersionSummary(projectId, documentId, selected);
  const summaryCompleted = summary.state.kind === "ready" && summary.state.value.status === "COMPLETED";
  const aiVisible = Boolean(aiSettings?.enabled && aiSettings.available) || summaryCompleted;
  const [tab, setTab] = useState<CollabTab>("feedback");
  const activeTab: CollabTab = tab === "ai" && !aiVisible ? "feedback" : tab;
  const [pageRequest, setPageRequest] = useState<PageRequest | null>(null);
  function goToPage(page: number) {
    setPageRequest((current) => ({ page, nonce: (current?.nonce ?? 0) + 1 }));
    setMobileTab("doc");
  }

  const [activityKey, setActivityKey] = useState(0);
  const bumpActivity = useCallback(() => setActivityKey((n) => n + 1), []);
  const loadActivities = useCallback(() => fetchDocumentActivities(projectId, documentId, 30), [projectId, documentId]);

  // 패널 접기·너비 (저장하지 않음)
  const [leftOpen, setLeftOpen] = useState(true);
  const [rightOpen, setRightOpen] = useState(true);
  const [leftWidth, setLeftWidth] = useState(LEFT.initial);
  const [rightWidth, setRightWidth] = useState(RIGHT.initial);

  const [modal, setModal] = useState<"upload" | "finalize" | null>(null);
  const [uploading, setUploading] = useState(false);

  // 최종본 받기 (확정 버전)
  const [finalDownloading, setFinalDownloading] = useState(false);
  const [finalDownloadError, setFinalDownloadError] = useState<string | null>(null);
  async function downloadFinal() {
    if (!finalization) {
      return;
    }
    const finalSummary = versions.find((version) => version.versionNumber === finalization.versionNumber);
    setFinalDownloading(true);
    setFinalDownloadError(null);
    try {
      const blob = await downloadVersionFile(projectId, documentId, finalization.versionNumber);
      saveBlob(blob, finalSummary?.originalFilename ?? `${document.title}-v${finalization.versionNumber}`);
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      } else {
        setFinalDownloadError(fileFailureMessage(error));
      }
    } finally {
      setFinalDownloading(false);
    }
  }

  const compareLink = useMemo(() => {
    if (versions.length < 2) {
      return null;
    }
    // 목록은 최신 먼저. 선택 버전과 그 직전 버전을 비교한다(가장 오래된 버전이면 다음 버전과).
    const index = versions.findIndex((version) => version.versionNumber === selected);
    if (index < 0) {
      return compareHref(projectId, documentId, versions[1].versionNumber, versions[0].versionNumber);
    }
    if (index === versions.length - 1) {
      return compareHref(projectId, documentId, versions[index].versionNumber, versions[index - 1].versionNumber);
    }
    return compareHref(projectId, documentId, versions[index + 1].versionNumber, versions[index].versionNumber);
  }, [versions, selected, projectId, documentId]);

  function handleUploaded(updated: DocumentDetailData) {
    onDocument(updated);
    reloadVersions();
    bumpActivity();
    setModal(null);
    selectVersion(updated.latestVersion.versionNumber);
  }
  function handleFinalized(updated: DocumentDetailData) {
    onDocument(updated);
    reloadVersions();
    bumpActivity();
    if (updated.finalization) {
      selectVersion(updated.finalization.versionNumber);
    }
  }
  function handleConflict() {
    onConflict();
    reloadVersions();
  }

  const versionsError = versionsState && "error" in versionsState ? versionsState.error : null;
  const feedbackCount = selectedSummary?.feedbackCount;

  const versionsPane = (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex h-11 shrink-0 items-center gap-2 border-b border-line px-3">
        <h2 className="text-sm font-semibold text-ink">버전</h2>
        <span className="text-xs text-ink-muted tabular-nums">{versions.length || ""}</span>
        <span className="flex-1" />
        <span className="max-lg:hidden">
          <IconButton label="버전 패널 접기" icon={<PanelLeftIcon />} onClick={() => setLeftOpen(false)} />
        </span>
      </div>
      <div className="min-h-0 flex-1 overflow-y-auto">
        {versionsError ? (
          <div className="p-3">
            <ErrorState role="status" title="버전 목록을 불러오지 못했습니다." message={versionsError} onRetry={reloadVersions} />
          </div>
        ) : versions.length === 0 ? (
          <div className="flex flex-col gap-2 p-3">
            <p role="status" className="sr-only">
              버전 목록을 불러오는 중...
            </p>
            <Skeleton className="h-16 w-full" />
            <Skeleton className="h-16 w-full" />
          </div>
        ) : (
          <VersionList
            versions={versions}
            selected={selected}
            finalVersionNumber={finalization?.versionNumber ?? null}
            drafts={draftVersions}
            onSelect={selectVersion}
          />
        )}
      </div>
    </div>
  );

  const collabPane = (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-end border-b border-line px-2">
        <Tabs<CollabTab>
          label="협업"
          idBase="collab"
          size="sm"
          className="min-w-0 flex-1"
          value={activeTab}
          onChange={setTab}
          items={[
            { key: "feedback", label: "피드백", count: feedbackCount },
            { key: "ai", label: "AI 요약", hidden: !aiVisible },
            { key: "info", label: "정보" },
            { key: "activity", label: "활동" },
          ]}
        />
        <span className="mb-1.5 max-lg:hidden">
          <IconButton label="협업 패널 접기" icon={<PanelRightIcon />} onClick={() => setRightOpen(false)} tooltipSide="left" />
        </span>
      </div>
      <div {...tabPanelProps("collab", activeTab)} tabIndex={-1} className="flex min-h-0 flex-1 flex-col outline-none">
        {activeTab === "feedback" && (
          <FeedbackPanel
            key={selected}
            layout="pane"
            projectId={projectId}
            documentId={documentId}
            versionNumber={selected}
            canWrite={!finalized}
            draft={drafts[selected] ?? ""}
            onDraftChange={setDraft}
            onFinalizedConflict={handleConflict}
            onCreated={() => {
              bumpActivity();
              reloadVersions();
            }}
          />
        )}
        {activeTab === "ai" && (
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
            <p className="mb-3 text-xs text-ink-muted">v{selected} 요약</p>
            <VersionSummaryView summary={summary} onGoToPage={goToPage} plain />
          </div>
        )}
        {activeTab === "info" && (
          <div className="flex min-h-0 flex-1 flex-col gap-6 overflow-y-auto px-4 py-4">
            <section aria-labelledby="info-description" className="flex flex-col gap-2">
              <h3 id="info-description" className="text-xs font-medium text-ink-muted">
                문서 설명
              </h3>
              <DocumentDescription
                projectId={projectId}
                documentId={documentId}
                description={document.description}
                editable={!finalized}
                onUpdated={(updated) => {
                  onDocument(updated);
                  bumpActivity();
                }}
                onFinalizedConflict={handleConflict}
              />
            </section>
            <section aria-labelledby="info-version" className="flex flex-col gap-2">
              <h3 id="info-version" className="text-xs font-medium text-ink-muted">
                선택한 버전 · v{selected}
              </h3>
              {selectedSummary ? (
                <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                  <dt className="text-ink-muted">파일</dt>
                  <dd className="break-all">{selectedSummary.originalFilename}</dd>
                  <dt className="text-ink-muted">크기</dt>
                  <dd title={`${selectedSummary.sizeBytes.toLocaleString("ko-KR")} 바이트`}>{formatFileSize(selectedSummary.sizeBytes)}</dd>
                  <dt className="text-ink-muted">업로드</dt>
                  <dd>
                    {selectedSummary.uploadedBy.displayName} · <time dateTime={selectedSummary.uploadedAt}>{formatDateTime(selectedSummary.uploadedAt)}</time>
                  </dd>
                  <dt className="text-ink-muted">{versionNoteLabel(selected)}</dt>
                  <dd className={selectedSummary.versionNote ? "whitespace-pre-wrap break-words" : "text-ink-muted"}>
                    {selectedSummary.versionNote ?? versionNoteEmptyText(selected)}
                  </dd>
                </dl>
              ) : (
                <p className="text-sm text-ink-muted">버전 정보를 불러오는 중...</p>
              )}
            </section>
            <section aria-labelledby="info-document" className="flex flex-col gap-2">
              <h3 id="info-document" className="text-xs font-medium text-ink-muted">
                문서
              </h3>
              <dl className="grid grid-cols-[max-content_minmax(0,1fr)] gap-x-3 gap-y-1.5 text-sm">
                <dt className="text-ink-muted">상태</dt>
                <dd>
                  <StatusBadge status={document.status} />
                </dd>
                <dt className="text-ink-muted">작성자</dt>
                <dd>{document.createdBy.displayName}</dd>
                <dt className="text-ink-muted">등록</dt>
                <dd>
                  <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
                </dd>
                <dt className="text-ink-muted">최신 버전</dt>
                <dd>v{latestNumber}</dd>
                {finalization && (
                  <>
                    <dt className="text-ink-muted">최종본</dt>
                    <dd>
                      v{finalization.versionNumber} · {finalization.finalizedBy.displayName} ·{" "}
                      <time dateTime={finalization.finalizedAt}>{formatDateTime(finalization.finalizedAt)}</time>
                    </dd>
                  </>
                )}
              </dl>
            </section>
          </div>
        )}
        {activeTab === "activity" && (
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-2">
            <ActivityFeed load={loadActivities} loadKey={`document-${projectId}-${documentId}-${activityKey}`} showDocument={false} />
          </div>
        )}
      </div>
    </div>
  );

  return (
    <div className="flex h-full min-h-0 flex-col bg-surface">
      {/* 머리글 */}
      <header className="flex shrink-0 flex-wrap items-center gap-x-3 gap-y-2 border-b border-line px-3 py-2 sm:px-4 lg:h-14 lg:flex-nowrap lg:py-0">
        <IconLink href={`/projects/${projectId}`} label="프로젝트로 돌아가기" icon={<ArrowLeftIcon />} />
        <div className="flex min-w-0 flex-1 flex-col">
          <nav aria-label="현재 위치" className="hidden min-w-0 text-xs text-ink-muted sm:block">
            <ol className="flex min-w-0 items-center gap-1">
              <li className="min-w-0 truncate">
                <Link href={`/projects/${projectId}`} className="hover:text-ink" title={project?.name}>
                  {project?.name ?? "프로젝트"}
                </Link>
              </li>
              <li aria-hidden="true">/</li>
              <li className="shrink-0">문서</li>
            </ol>
          </nav>
          <div className="flex min-w-0 items-center gap-2">
            <h1 className="min-w-0 truncate text-base font-semibold text-ink" title={document.title}>
              {document.title}
            </h1>
            <StatusBadge status={document.status} />
            <span className="hidden shrink-0 rounded-full border border-line px-2 py-0.5 text-xs text-ink-muted sm:inline">v{selected} 보는 중</span>
          </div>
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {compareLink && (
            <ButtonLink href={compareLink} size="sm">
              <CompareIcon /> <span className="max-sm:sr-only">버전 비교</span>
            </ButtonLink>
          )}
          {!finalized && isOwner && (
            <Button size="sm" onClick={() => setModal("finalize")} disabled={versions.length === 0}>
              <CheckCircleIcon /> <span className="max-sm:sr-only">최종본 확정</span>
            </Button>
          )}
          {!finalized && (
            <Button size="sm" variant="primary" onClick={() => setModal("upload")}>
              <UploadIcon /> 새 버전
            </Button>
          )}
          {finalized && finalization && (
            <Button size="sm" variant="primary" onClick={() => void downloadFinal()} disabled={finalDownloading}>
              <DownloadIcon /> {finalDownloading ? "내려받는 중..." : `최종본 v${finalization.versionNumber} 받기`}
            </Button>
          )}
        </div>
      </header>
      {finalDownloadError && (
        <p role="alert" className="shrink-0 border-b border-danger-600/20 bg-danger-50 px-4 py-2 text-sm text-danger-700">
          {finalDownloadError}
        </p>
      )}
      {finalized && finalization && (
        <div role="status" className="flex shrink-0 items-start gap-2 border-b border-success-600/20 bg-success-50 px-4 py-2 text-sm">
          <CheckCircleIcon className="mt-0.5 size-4 shrink-0 text-success-700" />
          <p className="min-w-0">
            <strong className="font-semibold text-success-700">v{finalization.versionNumber}이 최종본으로 확정되었습니다.</strong>{" "}
            <span className="text-ink-muted">
              {finalization.finalizedBy.displayName} · <time dateTime={finalization.finalizedAt}>{formatDateTime(finalization.finalizedAt)}</time> ·
              읽기 전용이며 다시 열 수 없습니다.
              {latestNumber !== finalization.versionNumber && ` 최신 버전(v${latestNumber})은 최종본이 아닙니다.`}
            </span>{" "}
            <Link href={`/projects/${projectId}/documents/new`} className="font-medium whitespace-nowrap text-brand-700 hover:underline">
              새 문서 등록
            </Link>
          </p>
        </div>
      )}
      {/* 모바일: 한 패널씩 */}
      <div className="shrink-0 border-b border-line px-3 py-2 lg:hidden">
        <Segmented<MobileTab>
          label="화면"
          className="w-full [&>button]:flex-1 [&>button]:justify-center"
          value={mobileTab}
          onChange={setMobileTab}
          items={[
            { key: "doc", label: "문서" },
            { key: "versions", label: `버전${versions.length ? ` ${versions.length}` : ""}` },
            { key: "collab", label: "협업" },
          ]}
        />
      </div>
      <div className="flex min-h-0 flex-1">
        {/* 왼쪽: 버전 */}
        {leftOpen ? (
          <>
            <aside
              aria-label="버전"
              style={{ "--pane-w": `${leftWidth}px` } as CSSProperties}
              className={cn("min-h-0 w-full shrink-0 bg-surface lg:w-[var(--pane-w)]", mobileTab !== "versions" && "max-lg:hidden")}
            >
              {versionsPane}
            </aside>
            <PaneResizer label="버전 패널 너비" edge="left" value={leftWidth} min={LEFT.min} max={LEFT.max} onChange={setLeftWidth} className="max-lg:hidden" />
          </>
        ) : (
          <div className="hidden w-11 shrink-0 flex-col items-center border-r border-line bg-surface py-2 lg:flex">
            <IconButton label="버전 패널 펼치기" icon={<PanelLeftIcon />} onClick={() => setLeftOpen(true)} tooltipSide="right" />
            <span className="mt-3 text-xs font-medium text-ink-muted [writing-mode:vertical-rl]">버전 {versions.length}</span>
          </div>
        )}
        {/* 가운데: 뷰어 */}
        <main
          aria-label="문서"
          className={cn("min-h-0 min-w-0 flex-1 border-line lg:border-x", mobileTab !== "doc" && "max-lg:hidden")}
        >
          {selectedSummary ? (
            <PdfDocumentViewer key={selected} projectId={projectId} documentId={documentId} version={selectedSummary} pageRequest={pageRequest} />
          ) : (
            <div className="flex h-full flex-col gap-3 bg-canvas-sunken p-6">
              <p role="status" className="sr-only">
                문서를 불러오는 중...
              </p>
              <Skeleton className="mx-auto h-full w-full max-w-2xl bg-surface" />
            </div>
          )}
        </main>
        {/* 오른쪽: 협업 */}
        {rightOpen ? (
          <>
            <PaneResizer label="협업 패널 너비" edge="right" value={rightWidth} min={RIGHT.min} max={RIGHT.max} onChange={setRightWidth} className="max-lg:hidden" />
            <aside
              aria-label="협업"
              style={{ "--pane-w": `${rightWidth}px` } as CSSProperties}
              className={cn("min-h-0 w-full shrink-0 bg-surface lg:w-[var(--pane-w)]", mobileTab !== "collab" && "max-lg:hidden")}
            >
              {collabPane}
            </aside>
          </>
        ) : (
          <div className="hidden w-11 shrink-0 flex-col items-center border-l border-line bg-surface py-2 lg:flex">
            <IconButton label="협업 패널 펼치기" icon={<PanelRightIcon />} onClick={() => setRightOpen(true)} tooltipSide="left" />
            <span className="mt-3 text-xs font-medium text-ink-muted [writing-mode:vertical-rl]">
              피드백{feedbackCount ? ` ${feedbackCount}` : ""}
            </span>
          </div>
        )}
      </div>

      <Dialog
        open={modal === "upload"}
        busy={uploading}
        onClose={() => setModal(null)}
        title="새 버전 등록"
        description={`v${latestNumber + 1}로 추가됩니다. 이전 버전과 피드백은 그대로 남습니다.`}
      >
        <DocumentVersionUploadForm
          bare
          projectId={projectId}
          documentId={documentId}
          status={document.status}
          onUploaded={handleUploaded}
          onConflict={handleConflict}
          onSubmittingChange={setUploading}
          onCancel={() => setModal(null)}
        />
      </Dialog>
      {!finalized && isOwner && (
        <FinalizeDialog
          open={modal === "finalize"}
          onClose={() => setModal(null)}
          projectId={projectId}
          documentId={documentId}
          versions={versions}
          defaultVersion={selected}
          onFinalized={handleFinalized}
          onConflict={handleConflict}
        />
      )}
    </div>
  );
}
