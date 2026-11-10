"use client";

import { type FormEvent, useCallback, useEffect, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Field, Input, Select } from "@/components/ui/Field";
import { PageHeader } from "@/components/ui/PageHeader";
import { EmptyState, ErrorState, LoadingBlock } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { errorCode, failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fetchInvitations, type Invitation, type InvitationStatus, type IssuedInvitation, issueInvitation, revokeInvitation } from "./api";

const STATUS: Record<InvitationStatus, { label: string; tone: "info" | "success" | "neutral" | "danger" }> = {
  ACTIVE: { label: "사용 가능", tone: "info" },
  USED: { label: "사용됨", tone: "success" },
  EXPIRED: { label: "만료", tone: "neutral" },
  REVOKED: { label: "폐기", tone: "danger" },
};

type ListState = { kind: "loading" } | { kind: "error"; message: string } | { kind: "forbidden" } | { kind: "success"; items: Invitation[] };

/** 관리자 초대코드 발급·조회·폐기 (#26). 관리자 여부는 Backend가 매 요청 확인한다(403). */
export function InvitationAdmin() {
  const { refresh } = useAuth();
  const toast = useToast();
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; value: ListState } | null>(null);
  const state: ListState = result?.attempt === attempt ? result.value : (result?.value ?? { kind: "loading" });
  const [hours, setHours] = useState("72");
  const [note, setNote] = useState("");
  const [issuing, setIssuing] = useState(false);
  const [issued, setIssued] = useState<IssuedInvitation | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const reload = useCallback(() => setAttempt((n) => n + 1), []);

  useEffect(() => {
    let active = true;
    fetchInvitations().then(
      (items) => active && setResult({ attempt, value: { kind: "success", items } }),
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        }
        setResult({
          attempt,
          value: errorCode(error) === "ACCESS_DENIED" ? { kind: "forbidden" } : { kind: "error", message: failureMessage(error) },
        });
      },
    );
    return () => {
      active = false;
    };
  }, [attempt, refresh]);

  async function handleIssue(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (note.trim().length > 100) {
      setFormError("메모는 100자 이하로 입력해 주세요.");
      return;
    }
    setIssuing(true);
    setFormError(null);
    try {
      const created = await issueInvitation(Number(hours), note.trim() || null);
      setIssued(created);
      setNote("");
      reload();
    } catch (error) {
      setFormError(errorCode(error) === "ACCESS_DENIED" ? "관리자만 초대코드를 발급할 수 있습니다." : failureMessage(error));
    } finally {
      setIssuing(false);
    }
  }

  async function handleRevoke(invitation: Invitation) {
    if (!window.confirm("이 초대코드를 폐기할까요? 폐기한 코드로는 가입할 수 없습니다.")) {
      return;
    }
    try {
      await revokeInvitation(invitation.id);
      toast("초대코드를 폐기했습니다.");
      reload();
    } catch (error) {
      toast(failureMessage(error), "error");
    }
  }

  async function copy(code: string) {
    try {
      await navigator.clipboard.writeText(code);
      toast("초대코드를 복사했습니다.");
    } catch {
      toast("복사하지 못했습니다. 코드를 직접 선택해 복사해 주세요.", "error");
    }
  }

  if (state.kind === "forbidden") {
    return (
      <>
        <PageHeader title="초대 관리" />
        <ErrorState title="관리자만 볼 수 있는 화면입니다." />
      </>
    );
  }

  return (
    <>
      <PageHeader title="초대 관리" description="초대코드는 한 번만 사용할 수 있으며 발급 직후에만 원문을 볼 수 있습니다." />
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-3">
        <Card aria-labelledby="issue-heading">
          <CardHeader title="초대코드 발급" titleId="issue-heading" />
          <CardBody>
            <form onSubmit={(event) => void handleIssue(event)} className="flex flex-col gap-4" noValidate>
              <Field label="유효 기간" htmlFor="invite-hours">
                <Select id="invite-hours" value={hours} onChange={(event) => setHours(event.target.value)}>
                  <option value="24">24시간</option>
                  <option value="72">3일</option>
                  <option value="168">7일</option>
                  <option value="336">14일</option>
                </Select>
              </Field>
              <Field label="메모 (선택)" htmlFor="invite-note" hint="누구에게 보낸 코드인지 적어 두세요. 100자 이하">
                <Input id="invite-note" value={note} onChange={(event) => setNote(event.target.value)} maxLength={100} />
              </Field>
              {formError && (
                <p role="alert" className="text-sm text-danger-700">
                  {formError}
                </p>
              )}
              <Button type="submit" variant="primary" disabled={issuing}>
                {issuing ? "발급 중..." : "초대코드 발급"}
              </Button>
            </form>
            {issued && (
              <div role="status" className="mt-4 flex flex-col gap-2 rounded-lg border border-brand-500/30 bg-brand-50 p-3">
                <p className="text-xs text-ink-muted">지금만 볼 수 있습니다. 안전한 경로로 전달해 주세요.</p>
                <code className="font-mono text-base font-semibold tracking-wide break-all text-ink">{issued.code}</code>
                <p className="text-xs text-ink-muted">만료 {formatDateTime(issued.expiresAt)}</p>
                <Button size="sm" onClick={() => void copy(issued.code)}>
                  복사
                </Button>
              </div>
            )}
          </CardBody>
        </Card>
        <Card className="xl:col-span-2" aria-labelledby="invitations-heading">
          <CardHeader title="최근 초대코드" titleId="invitations-heading" description="최근 100건" />
          <CardBody>
            {state.kind === "loading" && <LoadingBlock label="초대코드를 불러오는 중..." />}
            {state.kind === "error" && <ErrorState title="초대코드를 불러오지 못했습니다." message={state.message} onRetry={reload} />}
            {state.kind === "success" && state.items.length === 0 && <EmptyState title="발급한 초대코드가 없습니다." />}
            {state.kind === "success" && state.items.length > 0 && (
              <ul className="flex flex-col divide-y divide-line">
                {state.items.map((invitation) => (
                  <li key={invitation.id} className="flex flex-col gap-2 py-3 sm:flex-row sm:items-center sm:justify-between">
                    <div className="min-w-0 text-sm">
                      <p className="flex flex-wrap items-center gap-2">
                        <Badge tone={STATUS[invitation.status].tone}>{STATUS[invitation.status].label}</Badge>
                        {invitation.grantsAdmin && <Badge tone="brand">관리자</Badge>}
                        <span className="break-all text-ink">{invitation.note ?? "메모 없음"}</span>
                      </p>
                      <p className="mt-1 text-xs text-ink-muted">
                        발급 {formatDateTime(invitation.createdAt)} · 만료 {formatDateTime(invitation.expiresAt)}
                        {invitation.usedByDisplayName && <> · {invitation.usedByDisplayName} 가입</>}
                      </p>
                    </div>
                    {invitation.status === "ACTIVE" && (
                      <Button size="sm" variant="ghost" onClick={() => void handleRevoke(invitation)} aria-label={`초대코드 ${invitation.id} 폐기`}>
                        폐기
                      </Button>
                    )}
                  </li>
                ))}
              </ul>
            )}
          </CardBody>
        </Card>
      </div>
    </>
  );
}
