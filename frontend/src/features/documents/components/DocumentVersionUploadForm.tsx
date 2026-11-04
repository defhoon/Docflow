"use client";

import { type FormEvent, useCallback, useLayoutEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { createDocumentVersion } from "../api";
import { VERSION_MESSAGES, versionUploadFailureMessage } from "../errors";
import type { DocumentDetail, DocumentStatus } from "../types";
import { canUploadNewVersion, FILE_ACCEPT, MESSAGES, validateFile } from "../validation";

type Props = {
  projectId: string;
  documentId: string;
  status: DocumentStatus;
  /** 등록 성공 시 응답(갱신된 문서 상세)으로 화면을 갱신한다. */
  onUploaded: (document: DocumentDetail) => void;
  /** `409 DOCUMENT_STATUS_CONFLICT` 시 문서 상세를 다시 조회한다 (결정 I6). */
  onConflict: () => void;
};

/**
 * 문서 상세의 새 버전 등록 영역 (SPEC-04 §9, 결정 OQ-6·OQ-7·OQ-8). 파일만 보낸다. 상태에 따른 비활성화는 사용성 보조이며
 * Backend가 최종 판정한다. 네트워크 오류는 결과가 불명확하므로 자동으로 다시 보내지 않는다.
 */
export function DocumentVersionUploadForm({ projectId, documentId, status, onUploaded, onConflict }: Props) {
  const { refresh } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  // 파일 입력은 코드로 값을 비울 수 없으므로 key를 바꿔 다시 마운트한다.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [fileError, setFileError] = useState<string | undefined>(undefined);
  const [formError, setFormError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // 상태 반영 전 연속 제출도 막는다 (결정 V7).
  const submittingRef = useRef(false);
  // cacheComponents 사용 시 Next.js는 다른 경로로 이동해도 이 화면을 언마운트하지 않고 <Activity>로 숨긴다.
  // 숨겨지면 입력·결과를 초기화하고, 요청 중이면 요청이 끝난 뒤 초기화한다 ([기존] SPEC-03 교훈).
  const visibleRef = useRef(false);
  const resetWhenSettledRef = useRef(false);
  const allowed = canUploadNewVersion(status);
  const disabled = submitting || !allowed;

  const resetForm = useCallback(() => {
    setFile(null);
    setFileInputKey((current) => current + 1);
    setFileError(undefined);
    setFormError(null);
    setSuccess(null);
  }, []);

  useLayoutEffect(() => {
    visibleRef.current = true;
    return () => {
      visibleRef.current = false;
      if (submittingRef.current) {
        resetWhenSettledRef.current = true;
      } else {
        resetForm();
      }
    };
  }, [resetForm]);

  function handleFileChange(selected: File | null) {
    setFile(selected);
    setSuccess(null);
    setFormError(null);
    // 선택 즉시 크기·형식을 알려 20MB 초과 파일을 보내지 않게 한다.
    setFileError(selected ? validateFile(selected) : undefined);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current || !allowed) {
      return;
    }
    setFormError(null);
    setSuccess(null);
    const error = validateFile(file);
    setFileError(error);
    if (error || !file) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const updated = await createDocumentVersion(projectId, documentId, file);
      onUploaded(updated);
      settle(() => {
        resetForm();
        setSuccess(`새 버전을 등록했습니다. 최신 버전: v${updated.latestVersion.versionNumber}`);
      });
    } catch (failure) {
      settle(() => handleFailure(failure));
    }
  }

  /** 요청이 끝난 뒤 결과를 반영한다. 요청 중 화면이 숨겨졌다면 결과 대신 초기화한다. */
  function settle(apply: () => void) {
    submittingRef.current = false;
    setSubmitting(false);
    if (resetWhenSettledRef.current || !visibleRef.current) {
      resetWhenSettledRef.current = false;
      resetForm();
      return;
    }
    apply();
  }

  function handleFailure(error: unknown) {
    if (isUnauthenticated(error)) {
      // 세션 만료 흐름: RequireAuth가 로그인 화면(reason=expired)으로 보낸다.
      void refresh();
      return;
    }
    if (error instanceof ApiError && error.status === 413) {
      setFileError(MESSAGES.fileTooLarge);
      return;
    }
    if (error instanceof ApiError && error.status === 400) {
      const message = error.fieldErrors.find((fieldError) => fieldError.field === "file")?.message;
      if (message) {
        setFileError(message);
        return;
      }
    }
    if (error instanceof ApiError && error.status === 409 && errorCode(error) === "DOCUMENT_STATUS_CONFLICT") {
      // 화면 표시 후 상태가 바뀐 경우: 문구를 유지한 채 상세를 다시 조회해 등록 영역을 비활성화한다.
      setFormError(VERSION_MESSAGES.statusConflict);
      onConflict();
      return;
    }
    setFormError(versionUploadFailureMessage(error));
  }

  return (
    <section aria-labelledby="document-version-heading" className="flex flex-col gap-3 border-t border-zinc-200 pt-4">
      <h3 id="document-version-heading" className="text-sm font-semibold text-zinc-500">
        새 버전 등록
      </h3>
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-3" aria-busy={submitting}>
        {!allowed && (
          <p id="document-version-guide" className="text-sm text-zinc-600">
            {VERSION_MESSAGES.statusGuide}
          </p>
        )}
        <div className="flex flex-col gap-1 text-sm">
          <label htmlFor="document-version-file">새 버전 파일 (PDF, DOCX, XLSX, PPTX · 20MB 이하)</label>
          <input
            key={fileInputKey}
            id="document-version-file"
            type="file"
            name="file"
            accept={FILE_ACCEPT}
            disabled={disabled}
            onChange={(event) => handleFileChange(event.target.files?.[0] ?? null)}
            aria-invalid={Boolean(fileError)}
            aria-describedby={fileError ? "document-version-file-error" : allowed ? undefined : "document-version-guide"}
            className="text-sm"
          />
          {fileError && (
            <span id="document-version-file-error" className="text-red-700">
              {fileError}
            </span>
          )}
        </div>
        {formError && (
          <p role="alert" className="text-sm font-medium text-red-700">
            {formError}
          </p>
        )}
        {submitting && (
          <p role="status" className="text-sm text-zinc-500">
            파일을 업로드하는 중입니다. 화면을 닫지 마세요.
          </p>
        )}
        {success && (
          <p role="status" className="text-sm text-green-700">
            {success}
          </p>
        )}
        <button
          type="submit"
          disabled={disabled}
          className="self-start rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {submitting ? "등록 중..." : "새 버전 등록"}
        </button>
      </form>
    </section>
  );
}
