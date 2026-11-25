"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { type CSSProperties, useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/components/ui/cn";
import { IconButton, IconLink } from "@/components/ui/IconButton";
import { AlertIcon, ArrowLeftIcon, CheckCircleIcon, ChevronDownIcon, ChevronUpIcon, PanelRightIcon, SwapIcon } from "@/components/ui/icons";
import { PaneResizer } from "@/components/ui/PaneResizer";
import { ErrorState, LoadingBlock } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { Segmented, Tabs, tabPanelProps } from "@/components/ui/Tabs";
import { fetchAiSettings } from "@/features/analysis/api";
import { ChangeSummaryView, useChangeSummary } from "@/features/analysis/components/ChangeSummaryPanel";
import type { AiSettings } from "@/features/analysis/types";
import { useAuth } from "@/features/auth/AuthProvider";
import { fetchDocument } from "@/features/documents/api";
import type { DocumentDetail } from "@/features/documents/types";
import { fetchDocumentVersions, type DocumentVersionSummary } from "@/features/documents/version-files";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";

import { compareHref, fetchComparison } from "../api";
import { CHANGE_LABELS, compareFailureMessage } from "../messages";
import type { Change, ChangeType, CompareResponse } from "../types";
import { type NavRequest, PdfComparison } from "./PdfComparison";
import { TextComparison } from "./TextComparison";

type Loaded<T> = { key: string; value: { ok: true; data: T } | { ok: false; message: string } } | null;
type Mode = "pdf" | "text";
type Filter = "ALL" | ChangeType;
type PanelTab = "changes" | "ai";
type MobileTab = "base" | "target" | "changes";

const PANEL = { min: 280, max: 480, initial: 320 };

function parseVersion(value: string | null): number | null {
  return value && /^[0-9]+$/.test(value) ? Number(value) : null;
}

/** 변경이 PDF 어느 쪽에도 위치가 없는지(강조할 수 없음). */
function isUnlocated(change: Change): boolean {
  const sides = change.type === "ADDED" ? [change.target] : change.type === "DELETED" ? [change.base] : [change.base, change.target];
  return sides.every((side) => side.boxes.length === 0);
}

function changeText(change: Change): string {
  return change.type === "DELETED" ? change.base.text : change.target.text;
}

function changePages(change: Change): number[] {
  return change.type === "DELETED" ? change.base.pages : change.target.pages;
}

/**
 * 버전 비교 집중 모드 (SPEC-12 §7, SPEC-17). 결정론적 비교 결과를 PDF(기본) 또는 텍스트로 보여 주고, 오른쪽 패널의
 * 변경사항·AI 변경 요약에서 위치로 이동한다. 버전·방향·보기 방식은 URL(`base`·`target`·`mode`)에 둔다.
 */
export function VersionCompare() {
  const { projectId, documentId } = useParams<{ projectId: string; documentId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const { refresh } = useAuth();
  const [doc, setDoc] = useState<Loaded<{ document: DocumentDetail; versions: DocumentVersionSummary[] }>>(null);
  const [comparison, setComparison] = useState<Loaded<CompareResponse>>(null);
  const docKey = `${projectId}/${documentId}`;

  useEffect(() => {
    let active = true;
    Promise.all([fetchDocument(projectId, documentId), fetchDocumentVersions(projectId, documentId)]).then(
      ([document, versions]) => active && setDoc({ key: docKey, value: { ok: true, data: { document, versions } } }),
      (error: unknown) => {
        if (isUnauthenticated(error)) {
          void refresh();
        }
        if (active) {
          setDoc({ key: docKey, value: { ok: false, message: failureMessage(error) } });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, documentId, docKey, refresh]);

  const loadedDoc = doc?.key === docKey && doc.value.ok ? doc.value.data : null;
  const versions = useMemo(() => loadedDoc?.versions ?? [], [loadedDoc]);
  // 기본: 최신 버전과 그 직전 버전. URL에 있으면 사용자가 고른 버전·방향을 그대로 쓴다.
  const latest = versions[0]?.versionNumber ?? null;
  const target = parseVersion(searchParams.get("target")) ?? latest;
  const targetIndex = versions.findIndex((version) => version.versionNumber === target);
  const fallbackBase = targetIndex >= 0 ? (versions[targetIndex + 1]?.versionNumber ?? versions[targetIndex - 1]?.versionNumber ?? null) : null;
  const base = parseVersion(searchParams.get("base")) ?? fallbackBase;
  const mode: Mode = searchParams.get("mode") === "text" ? "text" : "pdf";
  const compareKey = `${docKey}:${base}:${target}`;
  const canCompare = base !== null && target !== null && base !== target;

  useEffect(() => {
    if (!canCompare) {
      return;
    }
    let active = true;
    fetchComparison(projectId, documentId, base, target).then(
      (data) => active && setComparison({ key: compareKey, value: { ok: true, data } }),
      (error: unknown) => {
        if (isUnauthenticated(error)) {
          void refresh();
        }
        if (active) {
          setComparison({ key: compareKey, value: { ok: false, message: compareFailureMessage(error) } });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, documentId, base, target, compareKey, canCompare, refresh]);

  function navigate(next: { base?: number; target?: number; mode?: Mode }) {
    const nextBase = next.base ?? base;
    const nextTarget = next.target ?? target;
    if (nextBase === null || nextTarget === null) {
      return;
    }
    const nextMode = next.mode ?? mode;
    const href = compareHref(projectId, documentId, nextBase, nextTarget);
    router.replace(nextMode === "text" ? `${href}&mode=text` : href);
  }

  const backHref = `/projects/${projectId}/documents/${documentId}${target !== null ? `?v=${target}` : ""}`;

  if (doc?.key === docKey && !doc.value.ok) {
    return (
      <div className="p-6">
        <ErrorState title="문서를 불러오지 못했습니다." message={doc.value.message} />
      </div>
    );
  }
  if (!loadedDoc) {
    return (
      <div className="p-6">
        <LoadingBlock label="불러오는 중..." lines={5} />
      </div>
    );
  }
  const current = comparison?.key === compareKey ? comparison.value : null;
  const data = current?.ok ? current.data : null;

  return (
    <div className="flex h-full min-h-0 flex-col bg-surface">
      <header className="flex shrink-0 flex-wrap items-center gap-x-3 gap-y-2 border-b border-line px-3 py-2 sm:px-4 xl:h-14 xl:flex-nowrap xl:py-0">
        <IconLink href={backHref} label="문서로 돌아가기" icon={<ArrowLeftIcon />} />
        <div className="flex min-w-0 flex-col">
          <span className="text-xs text-ink-muted">버전 비교</span>
          <h1 className="min-w-0 truncate text-base font-semibold text-ink" title={loadedDoc.document.title}>
            {loadedDoc.document.title}
          </h1>
        </div>
        <div className="flex flex-wrap items-center gap-2 xl:ml-4">
          {versions.length >= 2 && (
            <>
              <VersionSelect id="compare-base" label="이전 버전" value={base} versions={versions} onChange={(value) => navigate({ base: value })} />
              <IconButton label="서로 바꾸기" icon={<SwapIcon />} onClick={() => base !== null && target !== null && navigate({ base: target, target: base })} />
              <VersionSelect id="compare-target" label="이후 버전" value={target} versions={versions} onChange={(value) => navigate({ target: value })} />
            </>
          )}
          <Segmented<Mode>
            label="보기 방식"
            value={mode}
            onChange={(next) => navigate({ mode: next })}
            items={[
              { key: "pdf", label: "PDF" },
              { key: "text", label: "텍스트" },
            ]}
          />
        </div>
        {data && !data.result.identical && <ChangeCounts counts={data.result.counts} />}
      </header>
      {versions.length < 2 && <Centered><p className="text-sm text-ink-muted">비교하려면 버전이 2개 이상 있어야 합니다.</p></Centered>}
      {versions.length >= 2 && base === target && (
        <Centered>
          <p role="alert" className="text-sm text-danger-700">
            서로 다른 두 버전을 선택해 주세요.
          </p>
        </Centered>
      )}
      {canCompare && !current && (
        <div className="p-6">
          <LoadingBlock label="비교하는 중..." lines={4} />
        </div>
      )}
      {current && !current.ok && (
        <Centered>
          <ErrorState title="비교하지 못했습니다." message={current.message} />
          <Link href={backHref} className="text-sm text-brand-700 underline">
            문서로 돌아가기
          </Link>
        </Centered>
      )}
      {data && data.result.identical && (
        <Centered>
          <p role="status" className="flex items-center gap-2 text-sm text-success-700">
            <CheckCircleIcon className="size-5" /> 두 버전의 본문 텍스트가 같습니다. (공백·줄바꿈·쪽 번호 차이는 무시합니다)
          </p>
        </Centered>
      )}
      {data && !data.result.identical && (
        <CompareBody
          key={compareKey}
          projectId={projectId}
          documentId={documentId}
          data={data}
          mode={mode}
          onMode={(next) => navigate({ mode: next })}
        />
      )}
    </div>
  );
}

function Centered({ children }: { children: React.ReactNode }) {
  return <div className="flex flex-1 flex-col items-center justify-center gap-3 p-6 text-center">{children}</div>;
}

/** 비교 결과가 있을 때의 본문: 보기 영역 + 오른쪽 패널(변경사항·AI 변경 요약). 버전 조합이 바뀌면 새로 마운트한다. */
function CompareBody({ projectId, documentId, data, mode, onMode }: {
  projectId: string;
  documentId: string;
  data: CompareResponse;
  mode: Mode;
  onMode: (mode: Mode) => void;
}) {
  const changes = data.result.changes;
  const [activeChange, setActiveChange] = useState<number | null>(null);
  const [navRequest, setNavRequest] = useState<NavRequest | null>(null);
  const [filter, setFilter] = useState<Filter>("ALL");
  const [panelTab, setPanelTab] = useState<PanelTab>("changes");
  const [panelOpen, setPanelOpen] = useState(true);
  const [panelWidth, setPanelWidth] = useState(PANEL.initial);
  const [mobileTab, setMobileTab] = useState<MobileTab>("target");
  const [aiSettings, setAiSettings] = useState<AiSettings | null>(null);
  const summary = useChangeSummary(projectId, documentId, data.base.versionNumber, data.target.versionNumber);

  useEffect(() => {
    let active = true;
    fetchAiSettings(projectId).then((value) => active && setAiSettings(value), () => undefined);
    return () => {
      active = false;
    };
  }, [projectId]);

  const summaryCompleted = summary.state.kind === "ready" && summary.state.value.status === "COMPLETED";
  const summarySupported = summary.state.kind !== "ready" || summary.state.value.supported;
  const aiVisible = summarySupported && (Boolean(aiSettings?.enabled && aiSettings.available) || summaryCompleted);
  const activeTab: PanelTab = panelTab === "ai" && !aiVisible ? "changes" : panelTab;

  const filtered = filter === "ALL" ? changes : changes.filter((change) => change.type === filter);
  const activeIndex = changes.findIndex((change) => change.id === activeChange);
  const active = activeIndex >= 0 ? changes[activeIndex] : null;
  const unlocatedActive = mode === "pdf" && active !== null && isUnlocated(active);

  function goToChange(change: Change) {
    setActiveChange(change.id);
    setNavRequest((current) => ({ changeId: change.id, nonce: (current?.nonce ?? 0) + 1 }));
    if (mode === "text") {
      const side = change.type === "DELETED" ? "base" : "target";
      requestAnimationFrame(() => {
        const element = window.document.getElementById(`change-${change.id}-${side}`);
        element?.scrollIntoView({ behavior: "smooth", block: "center" });
        element?.focus({ preventScroll: true });
      });
    }
    // 좁은 화면: 위치가 있는 쪽 문서로 전환한다.
    setMobileTab((tab) => (tab === "changes" ? (change.type === "DELETED" ? "base" : "target") : tab));
  }

  const go = (offset: number) => {
    const next = changes[activeIndex < 0 ? (offset > 0 ? 0 : changes.length - 1) : activeIndex + offset];
    if (next) {
      goToChange(next);
    }
  };

  const panel = (
    <div className="flex h-full min-h-0 flex-col">
      <div className="flex shrink-0 items-end border-b border-line px-2">
        <Tabs<PanelTab>
          label="비교 패널"
          idBase="compare-panel"
          size="sm"
          className="min-w-0 flex-1"
          value={activeTab}
          onChange={setPanelTab}
          items={[
            { key: "changes", label: "변경사항", count: changes.length },
            { key: "ai", label: "AI 변경 요약", hidden: !aiVisible },
          ]}
        />
        <span className="mb-1.5 max-lg:hidden">
          <IconButton label="변경 패널 접기" icon={<PanelRightIcon />} onClick={() => setPanelOpen(false)} tooltipSide="left" />
        </span>
      </div>
      <div {...tabPanelProps("compare-panel", activeTab)} tabIndex={-1} className="flex min-h-0 flex-1 flex-col outline-none">
        {activeTab === "changes" ? (
          <>
            <div className="shrink-0 border-b border-line px-3 py-2">
              <Segmented<Filter>
                label="변경 종류"
                value={filter}
                onChange={setFilter}
                className="w-full [&>button]:flex-1 [&>button]:justify-center"
                items={[
                  { key: "ALL", label: `전체 ${changes.length}` },
                  { key: "ADDED", label: `추가 ${data.result.counts.added}` },
                  { key: "DELETED", label: `삭제 ${data.result.counts.deleted}` },
                  { key: "MODIFIED", label: `수정 ${data.result.counts.modified}` },
                ]}
              />
            </div>
            {unlocatedActive && (
              <div role="status" className="flex shrink-0 flex-col gap-2 border-b border-warning-700/15 bg-warning-50 px-3 py-2.5 text-sm text-warning-700">
                <p className="flex gap-2">
                  <AlertIcon className="mt-0.5 size-4 shrink-0" />
                  <span>변경 #{activeChange! + 1}은 PDF에서 위치를 찾지 못해 표시하지 않았습니다.</span>
                </p>
                <Button size="sm" className="self-start" onClick={() => onMode("text")}>
                  텍스트 비교에서 보기
                </Button>
              </div>
            )}
            <ol aria-label="변경 목록" className="min-h-0 flex-1 overflow-y-auto p-2">
              {filtered.length === 0 && <li className="px-2 py-6 text-center text-sm text-ink-muted">이 종류의 변경이 없습니다.</li>}
              {filtered.map((change) => {
                const unlocated = isUnlocated(change);
                return (
                  <li key={change.id}>
                    <button
                      type="button"
                      onClick={() => goToChange(change)}
                      aria-current={change.id === activeChange ? "true" : undefined}
                      className={cn(
                        "flex w-full items-start gap-2 rounded-md px-2 py-2 text-left text-sm hover:bg-subtle",
                        change.id === activeChange && "bg-brand-50 ring-1 ring-brand-500/30",
                      )}
                    >
                      <span className="w-6 shrink-0 pt-0.5 text-xs text-ink-muted tabular-nums">#{change.id + 1}</span>
                      <ChangeBadge type={change.type} />
                      <span className="min-w-0 flex-1">
                        <span className="line-clamp-3 break-words text-ink">{changeText(change) || "(빈 문단)"}</span>
                        <span className="mt-0.5 flex flex-wrap gap-x-2 text-xs text-ink-muted">
                          {changePages(change).length > 0 && <span>p.{changePages(change).join(", ")}</span>}
                          {unlocated && <span className="text-warning-700">PDF 위치 없음</span>}
                        </span>
                      </span>
                    </button>
                  </li>
                );
              })}
            </ol>
          </>
        ) : (
          <div className="min-h-0 flex-1 overflow-y-auto px-4 py-4">
            <p className="mb-3 text-xs text-ink-muted">
              v{data.base.versionNumber} → v{data.target.versionNumber} 변경 요약
            </p>
            <ChangeSummaryView
              plain
              summary={summary}
              onGoToChange={(id) => {
                const change = changes.find((item) => item.id === id);
                if (change) {
                  goToChange(change);
                }
              }}
            />
          </div>
        )}
      </div>
    </div>
  );

  return (
    <>
      <div className="flex shrink-0 flex-wrap items-center gap-2 border-b border-line px-3 py-1.5">
        <span className="text-xs text-ink-muted">
          v{data.base.versionNumber} <span aria-hidden="true">→</span>
          <span className="sr-only">에서</span> v{data.target.versionNumber}
        </span>
        <span className="flex-1" />
        <span className="text-xs text-ink-muted tabular-nums" aria-live="polite">
          {activeIndex >= 0 ? `${activeIndex + 1} / ${changes.length}` : `변경 ${changes.length}개`}
        </span>
        <IconButton label="이전 변경" icon={<ChevronUpIcon />} disabled={activeIndex === 0} onClick={() => go(-1)} />
        <IconButton label="다음 변경" icon={<ChevronDownIcon />} disabled={activeIndex === changes.length - 1} onClick={() => go(1)} />
        {!panelOpen && (
          <Button size="sm" onClick={() => setPanelOpen(true)} className="max-lg:hidden">
            <PanelRightIcon /> 변경 패널
          </Button>
        )}
      </div>
      <div className="shrink-0 border-b border-line px-3 py-2 lg:hidden">
        <Segmented<MobileTab>
          label="화면"
          className="w-full [&>button]:flex-1 [&>button]:justify-center"
          value={mobileTab}
          onChange={setMobileTab}
          items={[
            { key: "base", label: `이전 v${data.base.versionNumber}`, disabled: mode === "text" },
            { key: "target", label: mode === "text" ? "비교 내용" : `이후 v${data.target.versionNumber}` },
            { key: "changes", label: `변경 ${changes.length}` },
          ]}
        />
      </div>
      <div className="flex min-h-0 flex-1">
        <main aria-label="비교 보기" className={cn("min-h-0 min-w-0 flex-1", mobileTab === "changes" && "max-lg:hidden")}>
          {mode === "pdf" ? (
            <PdfComparison
              projectId={projectId}
              documentId={documentId}
              data={data}
              activeChangeId={activeChange}
              onSelectChange={(id) => setActiveChange(id)}
              navRequest={navRequest}
              mobileSide={mobileTab === "base" ? "base" : "target"}
            />
          ) : (
            <div className="h-full overflow-y-auto px-4 py-4 lg:px-6">
              <TextComparison
                blocks={data.result.blocks}
                baseLabel={`이전 · v${data.base.versionNumber}`}
                targetLabel={`이후 · v${data.target.versionNumber}`}
                activeChangeId={activeChange}
                onSelectChange={(id) => setActiveChange(id)}
              />
              {data.result.coarseBlocks > 0 && (
                <p className="mt-3 text-xs text-ink-muted">변경 범위가 넓은 구간 {data.result.coarseBlocks}곳은 단어 단위로 나누지 않고 한 번에 표시했습니다.</p>
              )}
            </div>
          )}
        </main>
        {panelOpen && (
          <>
            <PaneResizer label="변경 패널 너비" edge="right" value={panelWidth} min={PANEL.min} max={PANEL.max} onChange={setPanelWidth} className="max-lg:hidden" />
            <aside
              aria-label="변경사항"
              style={{ "--pane-w": `${panelWidth}px` } as CSSProperties}
              className={cn("min-h-0 w-full shrink-0 border-line bg-surface lg:w-[var(--pane-w)] lg:border-l", mobileTab !== "changes" && "max-lg:hidden")}
            >
              {panel}
            </aside>
          </>
        )}
        {!panelOpen && mobileTab === "changes" && <aside aria-label="변경사항" className="min-h-0 w-full bg-surface lg:hidden">{panel}</aside>}
      </div>
    </>
  );
}

export function ChangeBadge({ type }: { type: Change["type"] }) {
  const tone = type === "ADDED" ? "success" : type === "DELETED" ? "danger" : "warning";
  return (
    <Badge tone={tone}>
      <span aria-hidden="true">{CHANGE_LABELS[type].symbol}</span> {CHANGE_LABELS[type].label}
    </Badge>
  );
}

function ChangeCounts({ counts }: { counts: { added: number; deleted: number; modified: number } }) {
  return (
    <div className="flex flex-wrap gap-1.5 xl:ml-auto" aria-label="변경 개수">
      <Badge tone="success">+ 추가 {counts.added}</Badge>
      <Badge tone="danger">− 삭제 {counts.deleted}</Badge>
      <Badge tone="warning">~ 수정 {counts.modified}</Badge>
    </div>
  );
}

function VersionSelect({ id, label, value, versions, onChange }: {
  id: string;
  label: string;
  value: number | null;
  versions: DocumentVersionSummary[];
  onChange: (value: number) => void;
}) {
  return (
    <span className="flex items-center gap-1.5">
      <label htmlFor={id} className="sr-only">
        {label}
      </label>
      <select
        id={id}
        value={value ?? ""}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-8 max-w-[11rem] rounded-[var(--radius-control)] border border-line-strong bg-surface px-2 text-sm"
      >
        {versions.map((version) => (
          <option key={version.versionNumber} value={version.versionNumber}>
            v{version.versionNumber}
            {version.latest ? " (최신)" : ""} · {version.originalFilename}
          </option>
        ))}
      </select>
    </span>
  );
}
