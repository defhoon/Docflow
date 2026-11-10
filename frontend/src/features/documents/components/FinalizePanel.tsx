"use client";

import { useState } from "react";

import { Button } from "@/components/ui/Button";
import { Dialog } from "@/components/ui/Dialog";
import { Select } from "@/components/ui/Field";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { ApiError } from "@/lib/api-client";

import { finalizeDocument } from "../api-finalize";
import type { DocumentDetail } from "../types";
import type { DocumentVersionSummary } from "../version-files";

/**
 * 오너의 최종 버전 확정 (SPEC-07). 버전을 고르고 확인 대화상자에서 되돌릴 수 없음을 안내한 뒤 확정한다.
 * 이 영역은 오너·ACTIVE일 때만 표시하지만 권한·상태 판정은 Backend가 한다.
 */
export function FinalizePanel({ projectId, documentId, versions, defaultVersion, onFinalized, onConflict }: {
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
  const selected = choice ?? defaultVersion;
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const latest = versions.find((version) => version.latest)?.versionNumber ?? null;
  const selectedSummary = versions.find((version) => version.versionNumber === selected);

  async function confirm() {
    setSubmitting(true);
    setError(null);
    try {
      const document = await finalizeDocument(projectId, documentId, selected);
      setOpen(false);
      toast(`v${selected}을 최종 버전으로 확정했습니다.`);
      onFinalized(document);
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
        setError("프로젝트 오너만 최종 버전을 확정할 수 있습니다.");
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
    <section aria-labelledby="finalize-heading" className="flex flex-col gap-3">
      <div>
        <h3 id="finalize-heading" className="text-sm font-semibold text-ink">
          최종 버전 확정
        </h3>
        <p className="mt-0.5 text-xs text-ink-muted">프로젝트 오너만 할 수 있습니다. 최신 버전이 아니어도 선택할 수 있습니다.</p>
      </div>
      <div className="flex flex-col gap-2 sm:flex-row">
        <label htmlFor="finalize-version" className="sr-only">
          최종 버전으로 확정할 버전
        </label>
        <Select
          id="finalize-version"
          value={selected}
          onChange={(event) => setChoice(Number(event.target.value))}
          className="sm:w-56"
          disabled={versions.length === 0}
        >
          {versions.map((version) => (
            <option key={version.versionNumber} value={version.versionNumber}>
              v{version.versionNumber}
              {version.latest ? " (최신)" : ""} · {version.originalFilename}
            </option>
          ))}
        </Select>
        <Button variant="primary" onClick={() => setOpen(true)} disabled={versions.length === 0 || !selectedSummary}>
          v{selected} 최종 확정…
        </Button>
      </div>
      {error && !open && (
        <p role="alert" className="text-sm text-danger-700">
          {error}
        </p>
      )}
      <Dialog
        open={open}
        busy={submitting}
        onClose={() => setOpen(false)}
        title={`v${selected}을 최종 버전으로 확정할까요?`}
        description={selectedSummary ? <span className="break-all">{selectedSummary.originalFilename}</span> : undefined}
        footer={
          <>
            <Button onClick={() => setOpen(false)} disabled={submitting} data-autofocus>
              취소
            </Button>
            <Button variant="danger" onClick={() => void confirm()} disabled={submitting}>
              {submitting ? "확정하는 중..." : `v${selected} 최종 확정`}
            </Button>
          </>
        }
      >
        <ul className="flex list-disc flex-col gap-1.5 pl-5 text-sm text-ink">
          <li>선택한 v{selected}이 이 문서의 공식 최종본이 됩니다.</li>
          {latest !== null && latest !== selected && (
            <li>
              <strong className="font-semibold">최신 버전(v{latest})이 아닙니다.</strong> v{selected}을 최종본으로 확정합니다.
            </li>
          )}
          <li>확정 후에는 새 버전 업로드와 피드백 작성이 모두 막힙니다.</li>
          <li>확정을 취소하거나 협업을 다시 열 수 없습니다. 변경이 필요하면 새 문서를 등록해야 합니다.</li>
        </ul>
        {error && (
          <p role="alert" className="mt-3 text-sm text-danger-700">
            {error}
          </p>
        )}
      </Dialog>
    </section>
  );
}
