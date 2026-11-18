"use client";

import { type FormEvent, useRef, useState } from "react";

import { CharCount } from "@/components/ui/CharCount";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { updateDocumentDescription } from "../api";
import type { DocumentDetail } from "../types";
import { DESCRIPTION_MAX_LENGTH, validateDescription } from "../validation";
import { buttonClass } from "@/components/ui/Button";
import { controlClass } from "@/components/ui/Field";

type Props = {
  projectId: string;
  documentId: string;
  description: string | null;
  /** ACTIVE이면 수정 가능(권한은 Backend가 최종 판정). FINALIZED이면 읽기 전용. */
  editable: boolean;
  onUpdated: (document: DocumentDetail) => void;
  /** `409 DOCUMENT_ALREADY_FINALIZED`: 화면 표시 후 확정된 경우 상세를 다시 조회한다. */
  onFinalizedConflict: () => void;
};

export const DESCRIPTION_MESSAGES = {
  finalized: "이미 최종 확정된 문서라 설명을 수정할 수 없습니다.",
  saved: "문서 설명을 저장했습니다.",
} as const;

/**
 * 문서 설명 보기·수정 (SPEC-09 §8). 설명은 텍스트로만 표시하고 줄바꿈을 유지한다(HTML 해석 없음). 저장 중 중복 제출을 막고,
 * 실패하면 입력값을 유지한다.
 */
export function DocumentDescription({ projectId, documentId, description, editable, onUpdated, onFinalizedConflict }: Props) {
  const { refresh } = useAuth();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState("");
  const [error, setError] = useState<string | null>(null);
  const toast = useToast();
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const showEditor = editing && editable;

  function startEditing() {
    setDraft(description ?? "");
    setError(null);
    setEditing(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (savingRef.current) {
      return;
    }
    const { value, error: validationError } = validateDescription(draft);
    if (validationError) {
      setError(validationError);
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError(null);
    try {
      const updated = await updateDocumentDescription(projectId, documentId, value);
      onUpdated(updated);
      setEditing(false);
      // 성공은 바뀐 설명(화면 상태)과 토스트로 알린다 (#50).
      toast(DESCRIPTION_MESSAGES.saved);
    } catch (failure) {
      if (isUnauthenticated(failure)) {
        void refresh();
      } else if (failure instanceof ApiError && failure.status === 409 && errorCode(failure) === "DOCUMENT_ALREADY_FINALIZED") {
        setError(DESCRIPTION_MESSAGES.finalized);
        onFinalizedConflict();
      } else if (failure instanceof ApiError && failure.status === 400 && failure.fieldErrors.length > 0) {
        setError(failure.fieldErrors[0].message);
      } else {
        setError(failureMessage(failure));
      }
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }

  if (showEditor) {
    return (
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-2" aria-busy={saving}>
        <div className="flex items-baseline justify-between gap-3">
          <label htmlFor="document-description-edit" className="text-sm font-medium text-ink">
            문서 설명
          </label>
          <CharCount id="document-description-edit-count" length={draft.length} max={DESCRIPTION_MAX_LENGTH} />
        </div>
        <textarea
          id="document-description-edit"
          rows={5}
          value={draft}
          disabled={saving}
          autoFocus
          onChange={(event) => setDraft(event.target.value)}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? "document-description-edit-error document-description-edit-count" : "document-description-edit-count"}
          className={controlClass(false, "w-full py-2")}
        />
        {error && (
          <p id="document-description-edit-error" role="alert" className="text-sm text-danger-700">
            {error}
          </p>
        )}
        <div className="flex gap-2">
          <button
            type="submit"
            disabled={saving}
            className={buttonClass("primary", "sm")}
          >
            {saving ? "저장 중..." : "저장"}
          </button>
          <button
            type="button"
            disabled={saving}
            onClick={() => {
              setEditing(false);
              setError(null);
            }}
            className={buttonClass("secondary", "sm")}
          >
            취소
          </button>
        </div>
      </form>
    );
  }

  return (
    <div className="flex flex-col gap-2">
      {description ? (
        <p className="whitespace-pre-wrap break-words text-sm leading-relaxed text-ink">{description}</p>
      ) : (
        <p className="text-sm text-ink-muted">
          {editable ? "아직 문서 설명이 없습니다. 문서의 목적과 주요 내용을 적어 두면 구성원이 파일을 열기 전에 확인할 수 있습니다." : "문서 설명이 없습니다."}
        </p>
      )}
      {error && !showEditor && (
        <p role="alert" className="text-sm text-danger-700">
          {error}
        </p>
      )}
      {editable && (
        <button
          type="button"
          onClick={startEditing}
          className={buttonClass("secondary", "sm", "self-start")}
        >
          {description ? "설명 수정" : "설명 추가"}
        </button>
      )}
    </div>
  );
}
