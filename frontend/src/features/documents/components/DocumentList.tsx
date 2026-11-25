"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { ChevronRightIcon, FileIcon, SearchIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { Segmented } from "@/components/ui/Tabs";
import { relativeTime } from "@/features/activity/format";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fetchDocuments } from "../api";
import type { DocumentStatus, DocumentSummary } from "../types";
import { FileTypeIcon } from "./FileTypeIcon";

type ListResult = { kind: "error"; message: string } | { kind: "success"; documents: DocumentSummary[] };

type ListState = { kind: "loading" } | ListResult;

type Filter = "ALL" | DocumentStatus;
type Sort = "updated" | "created" | "title";

const SORTS: Record<Sort, { label: string; compare: (a: DocumentSummary, b: DocumentSummary) => number }> = {
  updated: { label: "최근 업데이트순", compare: (a, b) => b.updatedAt.localeCompare(a.updatedAt) || b.id - a.id },
  created: { label: "최근 등록순", compare: (a, b) => b.createdAt.localeCompare(a.createdAt) || b.id - a.id },
  title: { label: "이름순", compare: (a, b) => a.title.localeCompare(b.title, "ko") || a.id - b.id },
};

/**
 * 프로젝트의 문서 표 (SPEC-03 §8.1, SPEC-18). 제목 검색·상태 필터·정렬은 화면에서 처리한다(서버 목록 API 그대로 사용).
 * 접근 확인은 상위 프로젝트 상세 조회가 이미 수행했다.
 */
export function DocumentList({ projectId }: { projectId: string }) {
  const { refresh } = useAuth();
  const [attempt, setAttempt] = useState(0);
  const requestKey = `${projectId}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: ListResult } | null>(null);
  const state: ListState = result?.key === requestKey ? result.value : { kind: "loading" };

  useEffect(() => {
    let active = true;
    fetchDocuments(projectId).then(
      (documents) => active && setResult({ key: requestKey, value: { kind: "success", documents } }),
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
  return <DocumentListBody state={state} projectId={projectId} onRetry={retry} />;
}

function DocumentListBody({ state, projectId, onRetry }: { state: ListState; projectId: string; onRetry: () => void }) {
  const [filter, setFilter] = useState<Filter>("ALL");
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<Sort>("updated");
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
            description="'문서 등록'으로 PDF, DOCX, XLSX, PPTX 파일을 등록하고 구성원과 버전별 피드백을 주고받으세요."
          />
        );
      }
      const count = (value: Filter) => state.documents.filter((document) => value === "ALL" || document.status === value).length;
      const needle = query.trim().toLocaleLowerCase("ko");
      const visible = state.documents
        .filter((document) => filter === "ALL" || document.status === filter)
        .filter((document) => !needle || document.title.toLocaleLowerCase("ko").includes(needle))
        .sort(SORTS[sort].compare);
      const href = (document: DocumentSummary) => `/projects/${projectId}/documents/${document.id}`;
      return (
        <div className="flex flex-col gap-3">
          <div className="flex flex-wrap items-center gap-2">
            <label className="relative flex min-w-0 flex-1 items-center sm:max-w-xs">
              <span className="sr-only">문서 제목 검색</span>
              <SearchIcon className="pointer-events-none absolute left-3 size-4 text-ink-decor" />
              <input
                type="search"
                value={query}
                onChange={(event) => setQuery(event.target.value)}
                placeholder="문서 제목 검색"
                className="h-9 w-full rounded-[var(--radius-control)] border border-line-strong bg-surface pr-3 pl-9 text-sm"
              />
            </label>
            <Segmented<Filter>
              label="상태로 거르기"
              value={filter}
              onChange={setFilter}
              items={[
                { key: "ALL", label: <>전체 <span className="text-ink-muted">{count("ALL")}</span></> },
                { key: "ACTIVE", label: <>협업 중 <span className="text-ink-muted">{count("ACTIVE")}</span></> },
                { key: "FINALIZED", label: <>최종 확정 <span className="text-ink-muted">{count("FINALIZED")}</span></> },
              ]}
            />
            <label className="ml-auto flex items-center gap-2">
              <span className="sr-only">정렬</span>
              <select
                value={sort}
                onChange={(event) => setSort(event.target.value as Sort)}
                className="h-9 rounded-[var(--radius-control)] border border-line-strong bg-surface px-2 text-sm"
              >
                {(Object.keys(SORTS) as Sort[]).map((key) => (
                  <option key={key} value={key}>
                    {SORTS[key].label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          {visible.length === 0 ? (
            <p role="status" className="rounded-[var(--radius-card)] border border-dashed border-line-strong py-8 text-center text-sm text-ink-muted">
              {needle ? `"${query.trim()}"와 일치하는 문서가 없습니다.` : "이 상태의 문서가 없습니다."}
            </p>
          ) : (
            <>
              {/* 데스크톱: 표 */}
              <div className="hidden overflow-hidden rounded-[var(--radius-card)] border border-line bg-surface md:block">
                <table className="w-full table-fixed text-sm">
                  <caption className="sr-only">문서 목록</caption>
                  <thead className="border-b border-line bg-subtle/60 text-left text-xs text-ink-muted">
                    <tr>
                      <th scope="col" className="px-4 py-2.5 font-medium">문서</th>
                      <th scope="col" className="w-28 px-3 py-2.5 font-medium">상태</th>
                      <th scope="col" className="w-16 px-3 py-2.5 font-medium">최신</th>
                      <th scope="col" className="w-20 px-3 py-2.5 font-medium">최종본</th>
                      <th scope="col" className="w-48 px-3 py-2.5 font-medium">업데이트</th>
                      <th scope="col" className="w-10 px-2 py-2.5"><span className="sr-only">열기</span></th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-line">
                    {visible.map((document) => (
                      <tr key={document.id} className="group relative transition-colors hover:bg-subtle/50">
                        <td className="px-4 py-3">
                          <div className="flex min-w-0 items-center gap-3">
                            <FileTypeIcon type={document.fileType} />
                            <span className="flex min-w-0 flex-col gap-0.5">
                              {/* 행 전체를 누를 수 있게 제목 링크를 행 위로 넓힌다(접근 가능한 이름은 제목). */}
                              <Link
                                href={href(document)}
                                className="line-clamp-2 font-medium break-words text-ink after:absolute after:inset-0 group-hover:text-brand-700"
                                title={document.title}
                              >
                                {document.title}
                              </Link>
                              <span className="truncate text-xs text-ink-muted" title={document.description ?? undefined}>
                                {document.description ?? "설명 없음"}
                              </span>
                            </span>
                          </div>
                        </td>
                        <td className="px-3 py-3">
                          <StatusBadge status={document.status} />
                        </td>
                        <td className="px-3 py-3 text-ink tabular-nums">v{document.latestVersionNumber}</td>
                        <td className="px-3 py-3">
                          {document.finalVersionNumber !== null ? (
                            <span className="rounded-full bg-success-50 px-2 py-0.5 text-xs font-medium text-success-700 ring-1 ring-success-600/20 ring-inset">
                              v{document.finalVersionNumber}
                            </span>
                          ) : (
                            <span className="text-ink-decor" aria-label="없음">—</span>
                          )}
                        </td>
                        <td className="px-3 py-3 text-xs text-ink-muted">
                          <span className="flex items-center gap-2">
                            <Avatar name={document.latestUploader.displayName} />
                            <span className="flex min-w-0 flex-col">
                              <span className="truncate text-ink">{document.latestUploader.displayName}</span>
                              <time dateTime={document.updatedAt} title={formatDateTime(document.updatedAt)}>
                                {relativeTime(document.updatedAt)}
                              </time>
                            </span>
                          </span>
                        </td>
                        <td className="px-2 py-3 text-ink-decor">
                          <ChevronRightIcon />
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
                      href={href(document)}
                      className="flex gap-3 rounded-[var(--radius-card)] border border-line bg-surface px-3 py-3 transition-colors hover:bg-subtle/60"
                    >
                      <FileTypeIcon type={document.fileType} />
                      <span className="flex min-w-0 flex-1 flex-col gap-1">
                        <span className="line-clamp-2 font-medium break-words text-ink">{document.title}</span>
                        <span className="flex flex-wrap items-center gap-x-2 gap-y-1 text-xs text-ink-muted">
                          <StatusBadge status={document.status} />
                          <span>v{document.latestVersionNumber}</span>
                          {document.finalVersionNumber !== null && <span className="text-success-700">최종 v{document.finalVersionNumber}</span>}
                          <span>
                            {document.latestUploader.displayName} · <time dateTime={document.updatedAt}>{relativeTime(document.updatedAt)}</time>
                          </span>
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

function Avatar({ name }: { name: string }) {
  return (
    <span aria-hidden="true" className="flex size-6 shrink-0 items-center justify-center rounded-full bg-brand-50 text-[11px] font-semibold text-brand-700">
      {name.slice(0, 1)}
    </span>
  );
}
