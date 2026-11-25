"use client";

import Link from "next/link";
import { useParams, useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { CheckCircleIcon } from "@/components/ui/icons";
import { PageHeader } from "@/components/ui/PageHeader";
import { ErrorState, LoadingBlock } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { useAuth } from "@/features/auth/AuthProvider";
import { fetchDocument } from "@/features/documents/api";
import type { DocumentDetail } from "@/features/documents/types";
import { fetchDocumentVersions, type DocumentVersionSummary } from "@/features/documents/version-files";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";

import { compareHref, fetchComparison } from "../api";
import { CHANGE_LABELS, compareFailureMessage } from "../messages";
import type { Change, CompareResponse } from "../types";
import { PdfComparison } from "./PdfComparison";
import { TextComparison } from "./TextComparison";

type Loaded<T> = { key: string; value: { ok: true; data: T } | { ok: false; message: string } } | null;

function parseVersion(value: string | null): number | null {
  return value && /^[0-9]+$/.test(value) ? Number(value) : null;
}

/**
 * 버전 비교 화면 (SPEC-12 §7). 두 버전을 고르면 결정론적 비교 결과를 나란히 보여 주고, 변경 목록에서 구간으로 이동한다.
 */
export function VersionCompare() {
  const { projectId, documentId } = useParams<{ projectId: string; documentId: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const { refresh } = useAuth();
  const [doc, setDoc] = useState<Loaded<{ document: DocumentDetail; versions: DocumentVersionSummary[] }>>(null);
  const [comparison, setComparison] = useState<Loaded<CompareResponse>>(null);
  const [activeChange, setActiveChange] = useState<number | null>(null);
  const [view, setView] = useState<"text" | "pdf">("text");
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
  const latest = versions[0]?.versionNumber ?? null;
  const target = parseVersion(searchParams.get("target")) ?? latest;
  const base = parseVersion(searchParams.get("base")) ?? (target !== null && target > 1 ? target - 1 : null);
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

  const selectVersions = useCallback(
    (nextBase: number, nextTarget: number) => {
      setActiveChange(null);
      router.replace(compareHref(projectId, documentId, nextBase, nextTarget));
    },
    [router, projectId, documentId],
  );

  const goToChange = useCallback((change: Change) => {
    setActiveChange(change.id);
    if (view === "pdf") {
      // PDF 보기에서는 PdfComparison이 양쪽 PDF의 해당 위치로 이동한다.
      return;
    }
    const side = change.type === "DELETED" ? "base" : "target";
    const element = window.document.getElementById(`change-${change.id}-${side}`);
    element?.scrollIntoView({ behavior: "smooth", block: "center" });
    element?.focus({ preventScroll: true });
  }, [view]);

  if (doc?.key === docKey && !doc.value.ok) {
    return <ErrorState title="문서를 불러오지 못했습니다." message={doc.value.message} />;
  }
  if (!loadedDoc) {
    return <LoadingBlock label="불러오는 중..." lines={5} />;
  }
  const { document } = loadedDoc;
  const current = comparison?.key === compareKey ? comparison.value : null;
  const data = current?.ok ? current.data : null;
  const changes = data?.result.changes ?? [];
  const activeIndex = changes.findIndex((change) => change.id === activeChange);

  return (
    <>
      <PageHeader
        breadcrumb={[
          { label: "프로젝트", href: "/projects" },
          { label: "문서", href: `/projects/${projectId}/documents/${documentId}` },
          { label: "버전 비교" },
        ]}
        title={`버전 비교 · ${document.title}`}
        description="공백·줄바꿈·쪽 번호 차이는 무시하고 본문 텍스트의 추가·삭제·수정을 찾습니다. 변경 위치는 AI가 아니라 비교 알고리즘이 정합니다."
      />
      <Card aria-labelledby="compare-select-heading">
        <CardHeader title="비교할 버전" titleId="compare-select-heading" />
        <CardBody className="flex flex-col gap-3 sm:flex-row sm:items-end">
          {versions.length < 2 ? (
            <p className="text-sm text-ink-muted">비교하려면 버전이 2개 이상 있어야 합니다.</p>
          ) : (
            <>
              <VersionSelect id="compare-base" label="이전 버전" value={base} versions={versions} onChange={(value) => target !== null && selectVersions(value, target)} />
              <VersionSelect id="compare-target" label="이후 버전" value={target} versions={versions} onChange={(value) => base !== null && selectVersions(base, value)} />
              {base !== null && target !== null && (
                <Button size="sm" onClick={() => selectVersions(target, base)} className="self-start sm:self-auto">
                  서로 바꾸기
                </Button>
              )}
            </>
          )}
        </CardBody>
      </Card>
      {versions.length >= 2 && base === target && <p role="alert" className="text-sm text-danger-700">서로 다른 두 버전을 선택해 주세요.</p>}
      {canCompare && !current && <LoadingBlock label="비교하는 중..." lines={4} />}
      {current && !current.ok && <ErrorState title="비교하지 못했습니다." message={current.message} />}
      {data && (
        <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-[minmax(0,1fr)_18rem]">
          <Card aria-labelledby="compare-result-heading" className="min-w-0">
            <CardHeader
              title={`v${data.base.versionNumber} → v${data.target.versionNumber}`}
              titleId="compare-result-heading"
              description={`${data.base.originalFilename} → ${data.target.originalFilename}`}
              actions={<ChangeCounts counts={data.result.counts} />}
            />
            <CardBody>
              {!data.result.identical && (
                <div role="tablist" aria-label="보기 방식" className="mb-4 inline-flex rounded-[var(--radius-control)] border border-line bg-subtle p-0.5">
                  {(["text", "pdf"] as const).map((mode) => (
                    <button
                      key={mode}
                      type="button"
                      role="tab"
                      aria-selected={view === mode}
                      onClick={() => setView(mode)}
                      className={`rounded-[calc(var(--radius-control)-2px)] px-3 py-1 text-sm ${view === mode ? "bg-surface font-medium text-ink shadow-sm" : "text-ink-muted hover:text-ink"}`}
                    >
                      {mode === "text" ? "텍스트 비교" : "PDF에서 보기"}
                    </button>
                  ))}
                </div>
              )}
              {data.result.identical ? (
                <p role="status" className="flex items-center gap-2 text-sm text-success-700">
                  <CheckCircleIcon className="size-5" /> 두 버전의 본문 텍스트가 같습니다. (공백·줄바꿈·쪽 번호 차이는 무시합니다)
                </p>
              ) : view === "pdf" ? (
                <PdfComparison projectId={projectId} documentId={documentId} data={data} activeChangeId={activeChange} onSelectChange={setActiveChange} />
              ) : (
                <TextComparison
                  blocks={data.result.blocks}
                  baseLabel={`이전 · v${data.base.versionNumber}`}
                  targetLabel={`이후 · v${data.target.versionNumber}`}
                  activeChangeId={activeChange}
                  onSelectChange={(id) => setActiveChange(id)}
                />
              )}
              {data.result.coarseBlocks > 0 && (
                <p className="mt-3 text-xs text-ink-muted">변경 범위가 넓은 구간 {data.result.coarseBlocks}곳은 단어 단위로 나누지 않고 한 번에 표시했습니다.</p>
              )}
            </CardBody>
          </Card>
          {changes.length > 0 && (
            <Card as="aside" aria-labelledby="change-list-heading" className="lg:sticky lg:top-20">
              <CardHeader
                title={`변경 ${changes.length}개`}
                titleId="change-list-heading"
                actions={
                  <div className="flex gap-1">
                    <Button size="sm" aria-label="이전 변경" disabled={activeIndex <= 0} onClick={() => goToChange(changes[activeIndex - 1])}>
                      ↑
                    </Button>
                    <Button size="sm" aria-label="다음 변경" disabled={activeIndex >= changes.length - 1} onClick={() => goToChange(changes[activeIndex + 1])}>
                      ↓
                    </Button>
                  </div>
                }
              />
              <CardBody className="max-h-[60vh] overflow-y-auto px-2 py-2">
                <ol className="flex flex-col gap-1">
                  {changes.map((change) => (
                    <li key={change.id}>
                      <button
                        type="button"
                        onClick={() => goToChange(change)}
                        aria-current={change.id === activeChange ? "true" : undefined}
                        className={`flex w-full items-start gap-2 rounded-md px-2 py-1.5 text-left text-sm hover:bg-subtle ${change.id === activeChange ? "bg-brand-50" : ""}`}
                      >
                        <ChangeBadge type={change.type} />
                        <span className="min-w-0 flex-1">
                          <span className="line-clamp-2 break-words">{change.type === "DELETED" ? change.base.text : change.target.text}</span>
                          <span className="text-xs text-ink-muted">p.{(change.type === "DELETED" ? change.base.pages : change.target.pages).join(", ")}</span>
                        </span>
                      </button>
                    </li>
                  ))}
                </ol>
              </CardBody>
            </Card>
          )}
        </div>
      )}
      <Link href={`/projects/${projectId}/documents/${documentId}`} className="text-sm text-brand-700 underline">
        문서로 돌아가기
      </Link>
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
    <div className="flex flex-wrap gap-1.5" aria-label="변경 개수">
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
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-xs font-medium text-ink-muted">
        {label}
      </label>
      <select
        id={id}
        value={value ?? ""}
        onChange={(event) => onChange(Number(event.target.value))}
        className="h-9 rounded-[var(--radius-control)] border border-line-strong bg-surface px-2 text-sm"
      >
        {versions.map((version) => (
          <option key={version.versionNumber} value={version.versionNumber}>
            v{version.versionNumber} · {version.originalFilename}
          </option>
        ))}
      </select>
    </div>
  );
}
