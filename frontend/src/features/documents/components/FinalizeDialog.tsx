"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { cn } from "@/components/ui/cn";
import { Dialog } from "@/components/ui/Dialog";
import { AlertIcon } from "@/components/ui/icons";
import { Badge } from "@/components/ui/StatusBadge";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";
import { ApiError } from "@/lib/api-client";

import { finalizeDocument } from "../api-finalize";
import type { DocumentDetail } from "../types";
import type { DocumentVersionSummary } from "../version-files";

/**
 * 최종본 확정 모달 (SPEC-07, SPEC-16 §3.5). 버전을 라디오로 고르고(최신이 아니어도 가능), 최신이 아니면 경고하며, 되돌릴 수
 * 없음을 확인 체크로 받은 뒤에만 확정한다. 버튼 표시는 사용성 보조이며 권한·상태 판정은 Backend가 한다.
 */
export function FinalizeDialog({ open, onClose, projectId, documentId, versions, defaultVersion, onFinalized, onConflict }: {
  open: boolean;
  onClose: () => void;
  projectId: string;
  documentId: string;
  versions: DocumentVersionSummary[];
  defaultVersion: number;
  onFinalized: (document: DocumentDetail) => void;
  onConflict: () => void;
}) {
  const { refresh } = useAuth();
  const toast = useToast();
  const [choice, setChoice] = useState<number | null>(null);
  const [confirmed, setConfirmed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const selected = choice ?? defaultVersion;
  const latest = versions.find((version) => version.latest)?.versionNumber ?? null;
  const selectedSummary = versions.find((version) => version.versionNumber === selected);
  const notLatest = latest !== null && selected !== latest;

  function close() {
    if (submitting) {
      return;
    }
    setChoice(null);
    setConfirmed(false);
    setError(null);
    onClose();
  }

  async function confirm() {
    if (submitting || !confirmed || !selectedSummary) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      const document = await finalizeDocument(projectId, documentId, selected);
      toast(`v${selected}을 최종본으로 확정했습니다.`);
      setChoice(null);
      setConfirmed(false);
      onFinalized(document);
      onClose();
    } catch (failure) {
      if (isUnauthenticated(failure)) {
        void refresh();
        return;
      }
      const code = errorCode(failure);
      if (code === "DOCUMENT_ALREADY_FINALIZED") {
        setError("이미 최종 확정된 문서입니다. 화면을 새 상태로 바꿉니다.");
        onConflict();
      } else if (code === "ACCESS_DENIED") {
        setError("프로젝트 오너만 최종본을 확정할 수 있습니다.");
      } else if (failure instanceof ApiError && failure.status === 404) {
        setError("선택한 버전을 찾을 수 없습니다. 목록을 새로 고친 뒤 다시 선택해 주세요.");
      } else {
        setError(failureMessage(failure));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Dialog
      open={open}
      busy={submitting}
      onClose={close}
      title="최종본 확정"
      description="확정할 버전을 선택하세요. 최신 버전이 아니어도 확정할 수 있습니다."
      footer={
        <>
          <Button onClick={close} disabled={submitting} data-autofocus>
            취소
          </Button>
          <Button variant="danger" onClick={() => void confirm()} disabled={submitting || !confirmed || !selectedSummary}>
            {submitting ? "확정하는 중..." : `v${selected} 최종 확정`}
          </Button>
        </>
      }
    >
      <fieldset className="flex flex-col gap-3">
        <legend className="sr-only">최종본으로 확정할 버전</legend>
        <div className="flex max-h-64 flex-col gap-1.5 overflow-y-auto">
          {versions.map((version) => {
            const checked = version.versionNumber === selected;
            return (
              <label
                key={version.versionNumber}
                className={cn(
                  "flex cursor-pointer items-start gap-3 rounded-[var(--radius-control)] border px-3 py-2.5",
                  checked ? "border-brand-600/50 bg-brand-50/60" : "border-line hover:bg-subtle/60",
                )}
              >
                <input
                  type="radio"
                  name="finalize-version"
                  value={version.versionNumber}
                  checked={checked}
                  disabled={submitting}
                  onChange={() => {
                    setChoice(version.versionNumber);
                    setError(null);
                  }}
                  className="mt-1 size-4 accent-brand-600"
                />
                <span className="flex min-w-0 flex-1 flex-col gap-0.5">
                  <span className="flex flex-wrap items-center gap-1.5 text-sm">
                    <span className="font-semibold text-ink">v{version.versionNumber}</span>
                    {version.latest && <Badge>최신</Badge>}
                  </span>
                  <span className="truncate text-sm text-ink" title={version.originalFilename}>
                    {version.originalFilename}
                  </span>
                  <span className="text-xs text-ink-muted">
                    {version.uploadedBy.displayName} · <time dateTime={version.uploadedAt}>{formatDateTime(version.uploadedAt)}</time>
                  </span>
                </span>
              </label>
            );
          })}
        </div>
        {notLatest && (
          <p role="status" className="flex gap-2 rounded-[var(--radius-control)] border border-warning-700/25 bg-warning-50 px-3 py-2 text-sm text-warning-700">
            <AlertIcon className="mt-0.5 size-4 shrink-0" />
            <span>
              <strong className="font-semibold">최신 버전(v{latest})이 아닙니다.</strong> v{selected}을 최종본으로 확정하면 이후 버전은 최종본이 아닌 기록으로만
              남습니다.
            </span>
          </p>
        )}
        <ul className="flex list-disc flex-col gap-1 pl-5 text-sm text-ink">
          <li>확정 후에는 새 버전 업로드, 피드백 작성, 문서 설명 수정이 모두 막힙니다.</li>
          <li>확정을 취소하거나 협업을 다시 열 수 없습니다. 변경이 필요하면 새 문서를 등록해야 합니다.</li>
        </ul>
        <label className="flex items-start gap-2 text-sm font-medium text-ink">
          <input
            type="checkbox"
            checked={confirmed}
            disabled={submitting}
            onChange={(event) => setConfirmed(event.target.checked)}
            className="mt-0.5 size-4 accent-danger-600"
          />
          되돌릴 수 없다는 것을 확인했습니다.
        </label>
        {error && (
          <p role="alert" className="text-sm text-danger-700">
            {error}
          </p>
        )}
      </fieldset>
    </Dialog>
  );
}
