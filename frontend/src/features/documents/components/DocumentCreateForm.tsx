"use client";

import Link from "next/link";
import { useParams, useRouter } from "next/navigation";
import { type FormEvent, useCallback, useLayoutEffect, useRef, useState } from "react";

import { Button, ButtonLink } from "@/components/ui/Button";
import { CharCount } from "@/components/ui/CharCount";
import { Field, Input, Textarea } from "@/components/ui/Field";
import { FileDropzone } from "@/components/ui/FileDropzone";
import { UploadProgressBar } from "@/components/ui/UploadProgress";
import { useAuth } from "@/features/auth/AuthProvider";
import { isUnauthenticated } from "@/features/projects/errors";
import { ApiError, type UploadProgress } from "@/lib/api-client";

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

/** 제목 최대 글자 수(코드 포인트, SPEC-03). 파일 이름으로 채울 때 넘지 않게 자른다. */
const TITLE_MAX = 200;

/** 파일 이름에서 확장자를 뺀 제목 후보 (SPEC-19). */
export function titleFromFilename(name: string): string {
  const dot = name.lastIndexOf(".");
  const base = (dot > 0 ? name.slice(0, dot) : name).replace(/[_]+/g, " ").trim();
  return Array.from(base).slice(0, TITLE_MAX).join("");
}

/**
 * 문서 등록 폼 (SPEC-03 §8.3, SPEC-09, SPEC-19). 파일을 먼저 고르면 파일 이름으로 제목을 채운다(직접 고친 제목은 파일을 바꿔도
 * 유지). 설명·초기 버전 내용은 선택 입력으로 접어 둔다. 제목·설명·초기 버전 내용·파일을 `FormData` 요청 하나로 보내며 업로드
 * 진행률을 보여 준다. 실패하면 입력값을 그대로 유지하고 다시 시도할 수 있다.
 */
export function DocumentCreateForm() {
  const { projectId } = useParams<{ projectId: string }>();
  const router = useRouter();
  const { refresh } = useAuth();
  const [title, setTitle] = useState("");
  // 제목 출처: 파일 이름으로 채움(auto) / 사용자가 직접 입력(user) / 비어 있음(empty)
  const [titleSource, setTitleSource] = useState<"auto" | "user" | "empty">("empty");
  const [optionalOpen, setOptionalOpen] = useState(false);
  const [progress, setProgress] = useState<UploadProgress | null>(null);
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
    setTitleSource("empty");
    setOptionalOpen(false);
    setProgress(null);
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
    // 직접 고친 제목은 그대로 둔다. 파일 이름으로 채운 제목은 파일을 따라 바꾸고, 파일을 빼면 비운다.
    if (titleSource !== "user") {
      const next = selected ? titleFromFilename(selected.name) : "";
      setTitle(next);
      setTitleSource(next ? "auto" : "empty");
      if (next) {
        setFieldErrors((current) => ({ ...current, title: undefined }));
      }
    }
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
    if (errors.description || errors.versionNote) {
      setOptionalOpen(true);
    }
    if (Object.keys(errors).length > 0 || !file) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    setProgress(null);
    try {
      const created = await createDocument(projectId, { ...value, file }, setProgress);
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
      if (serverErrors.description || serverErrors.versionNote) {
        setOptionalOpen(true);
      }
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
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-6" aria-busy={submitting}>
      <FileDropzone
        key={fileInputKey}
        id="document-file"
        label="파일"
        hint="PDF, DOCX, XLSX, PPTX · 20MB 이하 · 이 파일이 v1이 됩니다"
        accept={FILE_ACCEPT}
        file={file}
        disabled={submitting}
        error={fieldErrors.file}
        errorId="document-file-error"
        onFile={handleFileChange}
      />
      <Field
        label="문서 제목"
        htmlFor="document-title"
        error={fieldErrors.title}
        errorId="document-title-error"
        hint={titleSource === "auto" ? "파일 이름으로 채웠습니다. 바꿀 수 있어요." : undefined}
        hintId="document-title-hint"
      >
        <Input
          id="document-title"
          type="text"
          name="title"
          value={title}
          disabled={submitting}
          onChange={(event) => {
            setTitle(event.target.value);
            setTitleSource(event.target.value.trim() ? "user" : "empty");
          }}
          invalid={Boolean(fieldErrors.title)}
          aria-describedby={fieldErrors.title ? "document-title-error" : titleSource === "auto" ? "document-title-hint" : undefined}
          placeholder="파일을 고르면 파일 이름으로 채워집니다"
        />
      </Field>
      <details
        open={optionalOpen}
        onToggle={(event) => setOptionalOpen(event.currentTarget.open)}
        className="group border-t border-line pt-4"
      >
        <summary className="cursor-pointer list-inside text-sm font-medium text-ink select-none">
          선택 입력 <span className="font-normal text-ink-muted">· 문서 설명, 초기 버전 내용</span>
          {!optionalOpen && (description.trim() || versionNote.trim()) && <span className="ml-2 text-xs text-brand-700">입력됨</span>}
        </summary>
        <div className="mt-4 flex flex-col gap-6">
      <Field
        label="문서 설명"
        optional
        htmlFor="document-description"
        error={fieldErrors.description}
        errorId="document-description-error"
        hint="문서의 목적과 주요 내용을 적어 두면 구성원이 파일을 열기 전에 확인할 수 있습니다."
        hintId="document-description-hint"
        aside={<CharCount id="document-description-count" length={description.length} max={DESCRIPTION_MAX_LENGTH} />}
      >
        <Textarea
          id="document-description"
          name="description"
          rows={4}
          value={description}
          disabled={submitting}
          onChange={(event) => setDescription(event.target.value)}
          invalid={Boolean(fieldErrors.description)}
          aria-describedby={
            fieldErrors.description
              ? "document-description-error document-description-count"
              : "document-description-hint document-description-count"
          }
        />
      </Field>
      <Field
        label="초기 버전 내용"
        optional
        htmlFor="document-version-note"
        error={fieldErrors.versionNote}
        errorId="document-version-note-error"
        hint="v1에 담긴 주요 내용을 적어 주세요. 버전 히스토리에 '초기 버전 내용'으로 표시됩니다."
        hintId="document-version-note-hint"
        aside={<CharCount id="document-version-note-count" length={versionNote.length} max={VERSION_NOTE_MAX_LENGTH} />}
      >
        <Textarea
          id="document-version-note"
          name="versionNote"
          rows={3}
          value={versionNote}
          disabled={submitting}
          onChange={(event) => setVersionNote(event.target.value)}
          invalid={Boolean(fieldErrors.versionNote)}
          aria-describedby={
            fieldErrors.versionNote
              ? "document-version-note-error document-version-note-count"
              : "document-version-note-hint document-version-note-count"
          }
        />
      </Field>
        </div>
      </details>
      {formError && (
        <div role="alert" className="flex flex-wrap items-center justify-between gap-2 rounded-[var(--radius-control)] border border-danger-600/20 bg-danger-50 px-3 py-2 text-sm font-medium text-danger-700">
          <span>{formError}</span>
          <Button type="submit" size="sm" disabled={submitting}>
            다시 시도
          </Button>
        </div>
      )}
      {submitting && <UploadProgressBar progress={progress} filename={file?.name} />}
      <div className="flex flex-col-reverse gap-2 border-t border-line pt-5 sm:flex-row sm:justify-end">
        <ButtonLink href={`/projects/${projectId}`}>취소</ButtonLink>
        <Button type="submit" variant="primary" disabled={submitting}>
          {submitting ? "등록 중..." : "문서 등록"}
        </Button>
      </div>
    </form>
  );
}
