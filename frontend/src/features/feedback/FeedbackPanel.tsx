"use client";

import { useEffect, useState, type FormEvent } from "react";

import { Button } from "@/components/ui/Button";
import { Textarea } from "@/components/ui/Field";
import { ChatIcon } from "@/components/ui/icons";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import { relativeTime } from "@/features/activity/format";
import { ApiError } from "@/lib/api-client";

import { createFeedback, FEEDBACK_MAX_LENGTH, feedbackLength, fetchFeedbacks } from "./api";
import type { Feedback } from "./types";

type ListState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "success"; items: Feedback[] };

/**
 * 선택한 버전의 피드백 목록과 작성 폼 (SPEC-08). 내용은 텍스트로만 렌더링한다(HTML 해석 없음).
 * {@code canWrite}가 false(FINALIZED)이면 작성 폼을 표시하지 않는다. 최종 판정은 Backend가 한다.
 */
export function FeedbackPanel({ projectId, documentId, versionNumber, canWrite, onFinalizedConflict, onCreated }: {
  projectId: string;
  documentId: string;
  versionNumber: number;
  canWrite: boolean;
  onFinalizedConflict: () => void;
  onCreated?: () => void;
}) {
  const { refresh } = useAuth();
  const toast = useToast();
  const [attempt, setAttempt] = useState(0);
  const key = `${projectId}/${documentId}/v${versionNumber}#${attempt}`;
  const [result, setResult] = useState<{ key: string; value: ListState } | null>(null);
  const state: ListState = result?.key === key ? result.value : { kind: "loading" };
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const length = feedbackLength(content);
  const tooLong = length > FEEDBACK_MAX_LENGTH;

  useEffect(() => {
    let active = true;
    fetchFeedbacks(projectId, documentId, versionNumber).then(
      (items) => active && setResult({ key, value: { kind: "success", items } }),
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({ key, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, documentId, versionNumber, key, refresh]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) {
      return;
    }
    if (length === 0) {
      setFormError("피드백 내용을 입력해 주세요.");
      return;
    }
    if (tooLong) {
      setFormError(`피드백은 ${FEEDBACK_MAX_LENGTH.toLocaleString("ko-KR")}자 이하로 입력해 주세요.`);
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const created = await createFeedback(projectId, documentId, versionNumber, content);
      setContent("");
      setResult((current) =>
        current?.key === key && current.value.kind === "success"
          ? { key, value: { kind: "success", items: [...current.value.items, created] } }
          : current,
      );
      toast(`v${versionNumber}에 피드백을 남겼습니다.`);
      onCreated?.();
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      } else if (error instanceof ApiError && error.status === 409 && errorCode(error) === "DOCUMENT_ALREADY_FINALIZED") {
        setFormError("문서가 최종 확정되어 더 이상 피드백을 남길 수 없습니다.");
        onFinalizedConflict();
      } else if (error instanceof ApiError && error.status === 400) {
        setFormError("피드백 내용을 확인해 주세요. (1~2,000자)");
      } else {
        setFormError(failureMessage(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      {state.kind === "loading" && <LoadingBlock label="피드백을 불러오는 중..." />}
      {state.kind === "error" && (
        <ErrorState role="status" title="피드백을 불러오지 못했습니다." message={state.message} onRetry={() => setAttempt((n) => n + 1)} />
      )}
      {state.kind === "success" && state.items.length === 0 && (
        <EmptyState
          icon={<ChatIcon className="size-6" />}
          title={`v${versionNumber}에 남긴 피드백이 없습니다.`}
          description={canWrite ? "이 버전에 대한 의견을 첫 번째로 남겨 보세요." : undefined}
        />
      )}
      {state.kind === "success" && state.items.length > 0 && (
        <ol aria-label={`v${versionNumber} 피드백`} className="flex flex-col gap-3">
          {state.items.map((feedback) => (
            <li key={feedback.id} className="flex gap-3">
              <span aria-hidden="true" className="flex size-8 shrink-0 items-center justify-center rounded-full bg-brand-50 text-xs font-semibold text-brand-700">
                {feedback.author.displayName.slice(0, 1)}
              </span>
              <div className="min-w-0 flex-1 rounded-lg border border-line bg-surface px-3 py-2">
                <p className="flex flex-wrap items-baseline gap-x-2 text-xs text-ink-muted">
                  <span className="text-sm font-medium text-ink">{feedback.author.displayName}</span>
                  <time dateTime={feedback.createdAt} title={formatDateTime(feedback.createdAt)}>
                    {relativeTime(feedback.createdAt)}
                  </time>
                </p>
                <p className="mt-1 text-sm whitespace-pre-wrap break-words text-ink">{feedback.content}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
      {canWrite && (
        <form onSubmit={(event) => void handleSubmit(event)} noValidate className="flex flex-col gap-2" aria-busy={submitting}>
          <label htmlFor="feedback-content" className="text-sm font-medium text-ink">
            v{versionNumber}에 피드백 남기기
          </label>
          <Textarea
            id="feedback-content"
            value={content}
            onChange={(event) => {
              setContent(event.target.value);
              setFormError(null);
            }}
            placeholder="수정이 필요한 부분이나 의견을 적어 주세요."
            invalid={Boolean(formError) || tooLong}
            aria-describedby="feedback-content-count"
            rows={3}
          />
          <div className="flex items-center justify-between gap-3">
            <span id="feedback-content-count" className={tooLong ? "text-xs text-danger-700" : "text-xs text-ink-faint"}>
              {length.toLocaleString("ko-KR")} / {FEEDBACK_MAX_LENGTH.toLocaleString("ko-KR")}
            </span>
            <Button type="submit" variant="primary" size="sm" disabled={submitting}>
              {submitting ? "등록 중..." : "피드백 등록"}
            </Button>
          </div>
          {formError && (
            <p role="alert" className="text-sm text-danger-700">
              {formError}
            </p>
          )}
        </form>
      )}
    </div>
  );
}
