"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fetchDocuments } from "../api";
import { statusLabel } from "../format";
import type { DocumentSummary } from "../types";

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
    <section aria-labelledby="documents-heading" className="flex flex-col gap-3">
      <div className="flex items-center justify-between gap-4">
        <h3 id="documents-heading" className="text-lg font-semibold">
          문서
        </h3>
        <NewDocumentLink projectId={projectId} />
      </div>
      <DocumentListBody state={state} projectId={projectId} onRetry={retry} />
    </section>
  );
}

function DocumentListBody({
  state,
  projectId,
  onRetry,
}: {
  state: ListState;
  projectId: string;
  onRetry: () => void;
}) {
  switch (state.kind) {
    case "loading":
      return (
        <p role="status" className="text-sm text-zinc-500">
          문서를 불러오는 중...
        </p>
      );
    case "error":
      return (
        <div role="alert" className="flex flex-col items-start gap-2 text-red-700">
          <p>문서 목록을 불러오지 못했습니다.</p>
          <p className="text-sm">{state.message}</p>
          <button
            type="button"
            onClick={onRetry}
            className="rounded border border-zinc-300 px-3 py-1 text-sm text-zinc-800"
          >
            다시 시도
          </button>
        </div>
      );
    case "success":
      if (state.documents.length === 0) {
        return <p className="text-sm text-zinc-600">아직 등록된 문서가 없습니다.</p>;
      }
      return (
        <ul className="flex flex-col divide-y divide-zinc-200 rounded-lg border border-zinc-200">
          {state.documents.map((document) => (
            <li key={document.id}>
              <Link
                href={`/projects/${projectId}/documents/${document.id}`}
                className="flex flex-col gap-1 px-4 py-3 hover:bg-zinc-50"
              >
                <span className="flex items-center justify-between gap-4">
                  <span className="font-medium break-all">{document.title}</span>
                  <span className="shrink-0 rounded bg-zinc-100 px-2 py-0.5 text-xs text-zinc-700">
                    {statusLabel(document.status)}
                  </span>
                </span>
                <span className="text-sm text-zinc-500">
                  {document.createdBy.displayName} · v{document.latestVersionNumber} ·{" "}
                  <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
                </span>
              </Link>
            </li>
          ))}
        </ul>
      );
  }
}

function NewDocumentLink({ projectId }: { projectId: string }) {
  return (
    <Link
      href={`/projects/${projectId}/documents/new`}
      className="rounded bg-zinc-900 px-3 py-1.5 text-sm font-medium text-white"
    >
      문서 등록
    </Link>
  );
}
