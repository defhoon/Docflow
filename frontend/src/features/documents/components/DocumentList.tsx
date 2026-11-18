"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { ButtonLink } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { cn } from "@/components/ui/cn";
import { FileIcon, PlusIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fetchDocuments } from "../api";
import type { DocumentStatus, DocumentSummary } from "../types";

type ListResult = { kind: "error"; message: string } | { kind: "success"; documents: DocumentSummary[] };

type ListState = { kind: "loading" } | ListResult;

/** 프로젝트 상세의 문서 섹션 (SPEC-03 §8.1, §8.2). 접근 확인은 상위 프로젝트 상세 조회가 이미 수행했다. */
export function DocumentList({ projectId }: { projectId: string }) {
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  // 결과를 요청 키와 함께 저장하여 다른 프로젝트·이전 시도의 응답을 표시하지 않는다.
  const requestKey = `${projectId}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: ListResult } | null>(null);
  const state: ListState = result?.key === requestKey ? result.value : { kind: "loading" };

  useEffect(() => {
    let active = true;
    fetchDocuments(projectId).then(
      (documents) => {
        if (active) {
          setResult({ key: requestKey, value: { kind: "success", documents } });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({ key: requestKey, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, requestKey, refresh]);

  const retry = useCallback(() => setAttempt((current) => current + 1), []);

  return (
    <Card aria-labelledby="documents-heading">
      <CardHeader title="문서" titleId="documents-heading" description="모든 구성원이 문서를 등록하고 새 버전을 올릴 수 있습니다." actions={<NewDocumentLink projectId={projectId} />} />
      <CardBody>
        <DocumentListBody state={state} projectId={projectId} onRetry={retry} />
      </CardBody>
    </Card>
  );
}

type Filter = "ALL" | DocumentStatus;

const FILTERS: Array<{ value: Filter; label: string }> = [
  { value: "ALL", label: "전체" },
  { value: "ACTIVE", label: "협업 중" },
  { value: "FINALIZED", label: "최종 확정" },
];

function DocumentListBody({
  state,
  projectId,
  onRetry,
}: {
  state: ListState;
  projectId: string;
  onRetry: () => void;
}) {
  const [filter, setFilter] = useState<Filter>("ALL");
  switch (state.kind) {
    case "loading":
      return <LoadingBlock label="문서를 불러오는 중..." />;
    case "error":
      return <ErrorState title="문서 목록을 불러오지 못했습니다." message={state.message} onRetry={onRetry} />;
    case "success": {
      if (state.documents.length === 0) {
        return (
          <EmptyState
            icon={<FileIcon className="size-6" />}
            title="아직 등록된 문서가 없습니다."
            description="PDF, DOCX, XLSX, PPTX 파일을 등록하고 구성원과 버전별 피드백을 주고받으세요."
            action={<NewDocumentLink projectId={projectId} />}
          />
        );
      }
      const visible = state.documents.filter((document) => filter === "ALL" || document.status === filter);
      return (
        <div className="flex flex-col gap-3">
          <div role="group" aria-label="상태로 거르기" className="flex w-fit gap-1 rounded-lg bg-subtle p-1">
            {FILTERS.map((item) => {
              const count = state.documents.filter((document) => item.value === "ALL" || document.status === item.value).length;
              return (
                <button
                  key={item.value}
                  type="button"
                  aria-pressed={filter === item.value}
                  onClick={() => setFilter(item.value)}
                  className={cn(
                    "rounded-md px-3 py-1 text-xs font-medium transition-colors",
                    filter === item.value ? "bg-surface text-ink shadow-sm" : "text-ink-muted hover:text-ink",
                  )}
                >
                  {item.label} <span className="text-ink-faint">{count}</span>
                </button>
              );
            })}
          </div>
          {visible.length === 0 ? (
            <p className="py-6 text-center text-sm text-ink-muted">이 상태의 문서가 없습니다.</p>
          ) : (
            <>
              {/* 데스크톱: 표 형태 (#50). 제목 링크 하나로 행 전체 정보를 읽을 수 있게 한다. */}
              <div className="hidden overflow-hidden rounded-[var(--radius-control)] border border-line md:block">
                <table className="w-full table-fixed text-sm">
                  <caption className="sr-only">문서 목록</caption>
                  <thead className="bg-subtle/60 text-left text-xs font-medium text-ink-muted">
                    <tr>
                      <th scope="col" className="px-4 py-2.5 font-medium">문서</th>
                      <th scope="col" className="w-28 px-3 py-2.5 font-medium">상태</th>
                      <th scope="col" className="w-28 px-3 py-2.5 font-medium">버전</th>
                      <th scope="col" className="w-44 px-3 py-2.5 font-medium">등록</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {visible.map((document) => (
                      <tr key={document.id} className="align-top transition-colors hover:bg-subtle/50">
                        <td className="px-4 py-3">
                          <Link
                            href={`/projects/${projectId}/documents/${document.id}`}
                            className="flex min-w-0 items-start gap-2.5 rounded-sm"
                          >
                            <FileIcon className="mt-0.5 size-4 shrink-0 text-ink-decor" />
                            <span className="flex min-w-0 flex-col gap-0.5">
                              <span className="line-clamp-2 font-medium break-words text-ink hover:text-brand-700">{document.title}</span>
                              {document.description && (
                                // SPEC-09: 설명을 한 줄로 줄여 표시한다(전체는 상세 화면).
                                <span className="truncate text-xs text-ink-muted" title={document.description}>
                                  {document.description}
                                </span>
                              )}
                            </span>
                          </Link>
                        </td>
                        <td className="px-3 py-3">
                          <StatusBadge status={document.status} />
                        </td>
                        <td className="px-3 py-3 text-xs text-ink-muted">
                          <span className="block">최신 v{document.latestVersionNumber}</span>
                          {document.finalVersionNumber !== null && (
                            <span className="block font-medium text-success-700">최종 v{document.finalVersionNumber}</span>
                          )}
                        </td>
                        <td className="px-3 py-3 text-xs text-ink-muted">
                          <span className="block truncate">{document.createdBy.displayName}</span>
                          <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
              {/* 모바일: 카드 목록 */}
              <ul className="flex flex-col gap-2 md:hidden">
                {visible.map((document) => (
                  <li key={document.id}>
                    <Link
                      href={`/projects/${projectId}/documents/${document.id}`}
                      className="flex flex-col gap-2 rounded-[var(--radius-control)] border border-line px-4 py-3 transition-colors hover:bg-subtle/60"
                    >
                      <span className="flex min-w-0 items-start gap-2.5">
                        <FileIcon className="mt-0.5 size-4 shrink-0 text-ink-decor" />
                        <span className="flex min-w-0 flex-col gap-0.5">
                          <span className="font-medium break-words text-ink">{document.title}</span>
                          {document.description && (
                            <span className="line-clamp-2 text-xs text-ink-muted">{document.description}</span>
                          )}
                        </span>
                      </span>
                      <span className="flex flex-wrap items-center gap-x-3 gap-y-1 text-xs text-ink-muted">
                        <StatusBadge status={document.status} />
                        <span>
                          최신 v{document.latestVersionNumber}
                          {document.finalVersionNumber !== null && <> · 최종 v{document.finalVersionNumber}</>}
                        </span>
                        <span>
                          {document.createdBy.displayName} ·{" "}
                          <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
                        </span>
                      </span>
                    </Link>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
      );
    }
  }
}

function NewDocumentLink({ projectId }: { projectId: string }) {
  return (
    <ButtonLink href={`/projects/${projectId}/documents/new`} variant="primary" size="sm">
      <PlusIcon /> 문서 등록
    </ButtonLink>
  );
}
