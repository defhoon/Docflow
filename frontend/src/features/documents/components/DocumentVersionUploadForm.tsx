"use client";

import { type FormEvent, useCallback, useEffect, useLayoutEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { CharCount } from "@/components/ui/CharCount";
import { Field, Textarea } from "@/components/ui/Field";
import { FileDropzone } from "@/components/ui/FileDropzone";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { createDocumentVersion } from "../api";
import { VERSION_MESSAGES, versionUploadFailureMessage } from "../errors";
import type { DocumentDetail, DocumentStatus } from "../types";
import {
  canUploadNewVersion,
  FILE_ACCEPT,
  MESSAGES,
  validateFile,
  validateVersionNote,
  VERSION_NOTE_MAX_LENGTH,
} from "../validation";

type Props = {
  projectId: string;
  documentId: string;
  status: DocumentStatus;
  /** 등록 성공 시 응답(갱신된 문서 상세)으로 화면을 갱신한다. */
  onUploaded: (document: DocumentDetail) => void;
  /** `409 DOCUMENT_STATUS_CONFLICT` 시 문서 상세를 다시 조회한다 (결정 I6). */
  onConflict: () => void;
  /** 모달 안에서 쓸 때 제목을 숨긴다(모달 제목이 대신, SPEC-16). */
  bare?: boolean;
  /** 업로드 중 여부 알림(모달이 닫히지 않게, SPEC-16). */
  onSubmittingChange?: (submitting: boolean) => void;
  /** 취소 버튼(모달). 업로드 중에는 비활성화한다. */
  onCancel?: () => void;
};

/**
 * 문서 상세의 새 버전 등록 영역 (SPEC-04 §9, 결정 OQ-6·OQ-7·OQ-8). 파일과 직전 버전 대비 변경사항(선택, SPEC-09)을
 * 보낸다. 실패하면 파일과 변경사항을 그대로 유지하고, 성공하면 비운다. 상태에 따른 비활성화는 사용성 보조이며
 * Backend가 최종 판정한다. 네트워크 오류는 결과가 불명확하므로 자동으로 다시 보내지 않는다.
 */
export function DocumentVersionUploadForm({ projectId, documentId, status, onUploaded, onConflict, bare = false, onSubmittingChange, onCancel }: Props) {
  const { refresh } = useAuth();
  const [file, setFile] = useState<File | null>(null);
  // 파일 입력은 코드로 값을 비울 수 없으므로 key를 바꿔 다시 마운트한다.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [fileError, setFileError] = useState<string | undefined>(undefined);
  const [versionNote, setVersionNote] = useState("");
  const [noteError, setNoteError] = useState<string | undefined>(undefined);
  const [formError, setFormError] = useState<string | null>(null);
  // 성공은 화면 상태(새 버전이 목록·미리보기에 반영됨)와 짧은 토스트로 알린다. 화면 안 성공 문구는 두지 않는다 (#50).
  const toast = useToast();
  const [submitting, setSubmitting] = useState(false);
  // 상태 반영 전 연속 제출도 막는다 (결정 V7).
  const submittingRef = useRef(false);
  // cacheComponents 사용 시 Next.js는 다른 경로로 이동해도 이 화면을 언마운트하지 않고 <Activity>로 숨긴다.
  // 숨겨지면 입력·결과를 초기화하고, 요청 중이면 요청이 끝난 뒤 초기화한다 ([기존] SPEC-03 교훈).
  const visibleRef = useRef(false);
  const resetWhenSettledRef = useRef(false);
  const allowed = canUploadNewVersion(status);
  useEffect(() => {
    onSubmittingChange?.(submitting);
    // 알림용 콜백이라 바뀌어도 다시 알릴 필요가 없다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitting]);
  const disabled = submitting || !allowed;

  const resetForm = useCallback(() => {
    setFile(null);
    setFileInputKey((current) => current + 1);
    setFileError(undefined);
    setVersionNote("");
    setNoteError(undefined);
    setFormError(null);
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
    const error = validateFile(file);
    setFileError(error);
    const note = validateVersionNote(versionNote, "change");
    setNoteError(note.error);
    if (error || note.error || !file) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const updated = await createDocumentVersion(projectId, documentId, file, note.value);
      onUploaded(updated);
      settle(() => {
        resetForm();
        toast(`새 버전을 등록했습니다. 최신 버전: v${updated.latestVersion.versionNumber}`);
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
      const noteMessage = error.fieldErrors.find((fieldError) => fieldError.field === "versionNote")?.message;
      if (message || noteMessage) {
        setFileError(message);
        setNoteError(noteMessage);
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
    <section aria-labelledby={bare ? undefined : "document-version-heading"} aria-label={bare ? "새 버전 등록" : undefined} className="flex flex-col gap-4">
      <div className={bare ? "hidden" : undefined}>
        <h3 id="document-version-heading" className="text-sm font-semibold text-ink">
          새 버전 등록
        </h3>
        <p className="mt-0.5 text-xs text-ink-muted">새 파일은 다음 버전 번호로 추가되며 이전 버전과 피드백은 그대로 남습니다.</p>
      </div>
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4" aria-busy={submitting}>
        {!allowed && (
          <p id="document-version-guide" className="text-sm text-ink-muted">
            {VERSION_MESSAGES.statusGuide}
          </p>
        )}
        <FileDropzone
          key={fileInputKey}
          id="document-version-file"
          label="새 버전 파일"
          hint="PDF, DOCX, XLSX, PPTX · 20MB 이하"
          accept={FILE_ACCEPT}
          file={file}
          disabled={disabled}
          error={fileError}
          errorId="document-version-file-error"
          describedBy={allowed ? undefined : "document-version-guide"}
          onFile={handleFileChange}
        />
        <Field
          label="직전 버전 대비 변경사항"
          optional
          htmlFor="document-version-note"
          error={noteError}
          errorId="document-version-note-error"
          aside={<CharCount id="document-version-note-count" length={versionNote.length} max={VERSION_NOTE_MAX_LENGTH} />}
        >
          <Textarea
            id="document-version-note"
            name="versionNote"
            rows={3}
            value={versionNote}
            disabled={disabled}
            placeholder="예: 3장 일정 수정, 표 2 추가"
            onChange={(event) => setVersionNote(event.target.value)}
            invalid={Boolean(noteError)}
            aria-describedby={noteError ? "document-version-note-error document-version-note-count" : "document-version-note-count"}
          />
        </Field>
        {formError && (
          <p role="alert" className="rounded-[var(--radius-control)] border border-danger-600/20 bg-danger-50 px-3 py-2 text-sm font-medium text-danger-700">
            {formError}
          </p>
        )}
        {submitting && (
          <p role="status" className="text-sm text-ink-muted">
            파일을 업로드하는 중입니다. 화면을 닫지 마세요.
          </p>
        )}
        <div className={onCancel ? "flex flex-col-reverse gap-2 sm:flex-row sm:justify-end" : "flex"}>
          {onCancel && (
            <Button onClick={onCancel} disabled={submitting}>
              취소
            </Button>
          )}
          <Button type="submit" variant="primary" disabled={disabled} className={onCancel ? undefined : "self-start"}>
            {submitting ? "등록 중..." : "새 버전 등록"}
          </Button>
        </div>
      </form>
    </section>
  );
}
