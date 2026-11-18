"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { type FormEvent, useCallback, useLayoutEffect, useRef, useState } from "react";

import { CharCount } from "@/components/ui/CharCount";
import { useAuth } from "@/features/auth/AuthProvider";
import { isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { createDocument } from "../api";
import { uploadFailureMessage } from "../errors";
import {
  DESCRIPTION_MAX_LENGTH,
  type DocumentFieldErrors,
  FILE_ACCEPT,
  MESSAGES,
  validateDocumentInput,
  validateFile,
  VERSION_NOTE_MAX_LENGTH,
} from "../validation";

const FIELDS = ["title", "description", "versionNote", "file"] as const;

/**
 * 문서 등록 폼 (SPEC-03 §8.3, SPEC-09). 제목·설명(선택)·초기 버전 내용(선택)·파일을 `FormData` 요청 하나로 보낸다. 실패하면
 * 입력값을 그대로 유지한다.
 */
export function DocumentCreateForm() {
  const { projectId } = useParams<{ projectId: string }>();
  const router = useRouter();
  const { refresh } = useAuth();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [versionNote, setVersionNote] = useState("");
  const [file, setFile] = useState<File | null>(null);
  // 파일 입력은 코드로 값을 비울 수 없으므로 key를 바꿔 다시 마운트한다.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [fieldErrors, setFieldErrors] = useState<DocumentFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [projectNotFound, setProjectNotFound] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // 상태 반영 전 연속 제출도 막는다.
  const submittingRef = useRef(false);
  // cacheComponents 사용 시 Next.js는 다른 경로로 이동해도 이 화면을 언마운트하지 않고 <Activity>로 숨긴다.
  // 등록에 성공한 폼은 숨겨질 때 초기화해 다시 진입하면 빈 폼이 보이게 한다 ([기존] SPEC-01 연속 생성 버그 교훈).
  const visibleRef = useRef(false);
  const resetOnHideRef = useRef(false);

  const resetForm = useCallback(() => {
    submittingRef.current = false;
    setSubmitting(false);
    setTitle("");
    setDescription("");
    setVersionNote("");
    setFile(null);
    setFileInputKey((current) => current + 1);
    setFieldErrors({});
    setFormError(null);
    setProjectNotFound(false);
  }, []);

  useLayoutEffect(() => {
    visibleRef.current = true;
    return () => {
      visibleRef.current = false;
      if (resetOnHideRef.current) {
        resetOnHideRef.current = false;
        resetForm();
      }
    };
  }, [resetForm]);

  function handleFileChange(selected: File | null) {
    setFile(selected);
    // 선택 즉시 크기·형식을 알려 20MB 초과 파일을 보내지 않게 한다.
    setFieldErrors((current) => ({ ...current, file: selected ? validateFile(selected) : undefined }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) {
      return;
    }
    setFormError(null);
    const { value, errors } = validateDocumentInput(title, description, file, versionNote);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0 || !file) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const created = await createDocument(projectId, { ...value, file });
      if (visibleRef.current) {
        // 이동하는 동안 다시 제출되지 않도록 제출 상태를 유지하고, 화면이 숨겨질 때 초기화한다.
        resetOnHideRef.current = true;
      } else {
        resetForm();
      }
      router.push(`/projects/${projectId}/documents/${created.id}`);
      return;
    } catch (error) {
      handleFailure(error);
    }
    submittingRef.current = false;
    setSubmitting(false);
  }

  function handleFailure(error: unknown) {
    if (isUnauthenticated(error)) {
      // 세션 만료 흐름: RequireAuth가 로그인 화면(reason=expired)으로 보낸다.
      void refresh();
      return;
    }
    if (error instanceof ApiError && error.status === 404) {
      // 구성원이 아니거나 없는 프로젝트 (서버는 둘을 구별하지 않는다).
      setProjectNotFound(true);
      return;
    }
    if (error instanceof ApiError && error.status === 413) {
      setFieldErrors({ file: MESSAGES.fileTooLarge });
      return;
    }
    if (error instanceof ApiError && error.status === 400 && error.fieldErrors.length > 0) {
      const serverErrors: DocumentFieldErrors = {};
      const unmatched: string[] = [];
      for (const fieldError of error.fieldErrors) {
        const field = FIELDS.find((name) => name === fieldError.field);
        if (field) {
          serverErrors[field] ??= fieldError.message;
        } else {
          unmatched.push(fieldError.message);
        }
      }
      setFieldErrors(serverErrors);
      if (unmatched.length > 0) {
        setFormError(unmatched[0]);
      }
      return;
    }
    setFormError(uploadFailureMessage(error));
  }

  if (projectNotFound) {
    return (
      <div role="alert" className="flex flex-col items-start gap-3">
        <p className="font-medium">프로젝트를 찾을 수 없습니다.</p>
        <Link href="/projects" className="text-sm text-ink underline">
          프로젝트 목록으로
        </Link>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4" aria-busy={submitting}>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="document-title">문서 제목</label>
        <input
          id="document-title"
          type="text"
          name="title"
          value={title}
          disabled={submitting}
          onChange={(event) => setTitle(event.target.value)}
          aria-invalid={Boolean(fieldErrors.title)}
          aria-describedby={fieldErrors.title ? "document-title-error" : undefined}
          className="rounded-lg border border-line-strong bg-surface px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
        />
        {fieldErrors.title && (
          <span id="document-title-error" className="text-danger-700">
            {fieldErrors.title}
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <div className="flex items-baseline justify-between gap-3">
          <label htmlFor="document-description">문서 설명 (선택)</label>
          <CharCount id="document-description-count" length={description.length} max={DESCRIPTION_MAX_LENGTH} />
        </div>
        <textarea
          id="document-description"
          name="description"
          rows={4}
          value={description}
          disabled={submitting}
          placeholder="문서의 목적과 주요 내용을 적어 주세요. 구성원이 파일을 열기 전에 확인할 수 있습니다."
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={Boolean(fieldErrors.description)}
          aria-describedby={
            fieldErrors.description ? "document-description-error document-description-count" : "document-description-count"
          }
          className="rounded-lg border border-line-strong bg-surface px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
        />
        {fieldErrors.description && (
          <span id="document-description-error" className="text-danger-700">
            {fieldErrors.description}
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <div className="flex items-baseline justify-between gap-3">
          <label htmlFor="document-version-note">초기 버전 내용 (선택)</label>
          <CharCount id="document-version-note-count" length={versionNote.length} max={VERSION_NOTE_MAX_LENGTH} />
        </div>
        <textarea
          id="document-version-note"
          name="versionNote"
          rows={3}
          value={versionNote}
          disabled={submitting}
          placeholder="v1에 담긴 주요 내용을 적어 주세요."
          onChange={(event) => setVersionNote(event.target.value)}
          aria-invalid={Boolean(fieldErrors.versionNote)}
          aria-describedby={
            fieldErrors.versionNote
              ? "document-version-note-error document-version-note-count"
              : "document-version-note-count"
          }
          className="rounded-lg border border-line-strong bg-surface px-3 py-2 text-sm focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20"
        />
        {fieldErrors.versionNote && (
          <span id="document-version-note-error" className="text-danger-700">
            {fieldErrors.versionNote}
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="document-file">파일 (PDF, DOCX, XLSX, PPTX · 20MB 이하)</label>
        <input
          key={fileInputKey}
          id="document-file"
          type="file"
          name="file"
          accept={FILE_ACCEPT}
          disabled={submitting}
          onChange={(event) => handleFileChange(event.target.files?.[0] ?? null)}
          aria-invalid={Boolean(fieldErrors.file)}
          aria-describedby={fieldErrors.file ? "document-file-error" : undefined}
          className="text-sm"
        />
        {fieldErrors.file && (
          <span id="document-file-error" className="text-danger-700">
            {fieldErrors.file}
          </span>
        )}
      </div>
      {formError && (
        <p role="alert" className="text-sm font-medium text-danger-700">
          {formError}
        </p>
      )}
      {submitting && (
        <p role="status" className="text-sm text-ink-muted">
          파일을 업로드하는 중입니다. 화면을 닫지 마세요.
        </p>
      )}
      <button
        type="submit"
        disabled={submitting}
        className="inline-flex h-10 items-center justify-center rounded-lg bg-brand-600 px-4 text-sm font-medium text-white hover:bg-brand-700 disabled:opacity-50"
      >
        {submitting ? "등록 중..." : "문서 등록"}
      </button>
    </form>
  );
}
