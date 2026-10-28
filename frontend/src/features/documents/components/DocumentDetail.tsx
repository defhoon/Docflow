"use client";

import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import { ApiError } from "@/lib/api-client";

import { fetchDocument } from "../api";
import { formatFileSize, statusLabel } from "../format";
import type { DocumentDetail as DocumentDetailData } from "../types";

type DetailResult =
  | { kind: "project-not-found" }
  | { kind: "not-found" }
  | { kind: "error"; message: string }
  | { kind: "success"; document: DocumentDetailData };

type DetailState = { kind: "loading" } | DetailResult;

const ID_PATTERN = /^[0-9]+$/;

/** 문서 상세: 메타데이터만 표시한다. 다운로드·미리보기·버전 목록은 SPEC-05 범위다. */
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

  switch (state.kind) {
    case "loading":
      return (
        <p role="status" className="text-zinc-500">
          불러오는 중...
        </p>
      );
    case "project-not-found":
      return (
        <div role="alert" className="flex flex-col items-start gap-3">
          <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
          <Link href="/projects" className="text-sm text-zinc-700 underline">
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
      return (
        <div role="alert" className="flex flex-col items-start gap-2 text-red-700">
          <p>문서를 불러오지 못했습니다.</p>
          <p className="text-sm">{state.message}</p>
          <button
            type="button"
            onClick={retry}
            className="rounded border border-zinc-300 px-3 py-1 text-sm text-zinc-800"
          >
            다시 시도
          </button>
        </div>
      );
    case "success": {
      const { document } = state;
      const version = document.latestVersion;
      return (
        <article className="flex flex-col gap-4">
          <div className="flex items-start justify-between gap-4">
            <h2 className="text-2xl font-semibold break-all">{document.title}</h2>
            <span className="shrink-0 rounded bg-zinc-100 px-2 py-0.5 text-sm text-zinc-700">
              {statusLabel(document.status)}
            </span>
          </div>
          <dl className="grid grid-cols-[max-content_1fr] gap-x-4 gap-y-2 text-sm">
            <dt className="text-zinc-500">작성자</dt>
            <dd>{document.createdBy.displayName}</dd>
            <dt className="text-zinc-500">등록일</dt>
            <dd>
              <time dateTime={document.createdAt}>{formatDateTime(document.createdAt)}</time>
            </dd>
            <dt className="text-zinc-500">최신 버전</dt>
            <dd>v{version.versionNumber}</dd>
            <dt className="text-zinc-500">파일</dt>
            <dd className="break-all">{version.originalFilename}</dd>
            <dt className="text-zinc-500">크기</dt>
            <dd title={`${version.sizeBytes.toLocaleString("ko-KR")} 바이트`}>{formatFileSize(version.sizeBytes)}</dd>
            <dt className="text-zinc-500">업로드</dt>
            <dd>
              <time dateTime={version.uploadedAt}>{formatDateTime(version.uploadedAt)}</time>
            </dd>
          </dl>
          <section>
            <h3 className="mb-1 text-sm font-semibold text-zinc-500">설명</h3>
            {document.description ? (
              <p className="whitespace-pre-wrap break-all">{document.description}</p>
            ) : (
              <p className="text-zinc-500">설명이 없습니다.</p>
            )}
          </section>
          <ProjectLink projectId={projectId} />
        </article>
      );
    }
  }
}

function ProjectLink({ projectId }: { projectId: string }) {
  return (
    <Link href={`/projects/${projectId}`} className="text-sm text-zinc-700 underline">
      프로젝트로 돌아가기
    </Link>
  );
}
