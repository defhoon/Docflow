"use client";

import { type FormEvent, useRef, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/lib/api-client";

import { addMember } from "../api";
import { errorCode, failureMessage, isUnauthenticated } from "../errors";
import { validateMemberEmail } from "../validation";

type AddMemberFormProps = {
  projectId: string;
  /** 추가에 성공하면 구성원 목록을 다시 불러온다. */
  onAdded: () => void;
  /** 권한이 바뀌었거나(403) 프로젝트에 접근할 수 없게 되면(404) 상세를 다시 불러온다. */
  onProjectChanged: () => void;
};

/**
 * Owner 전용 구성원 추가 폼 (SPEC-02 §8.2). 입력한 이메일은 화면의 다른 곳이나 오류 문구에 표시하지 않는다.
 * <p>
 * 성공하면 이동하지 않고 같은 화면에 머무르므로 그 자리에서 폼을 초기화한다. 제출 상태는 성공·실패와 관계없이 요청이
 * 끝나면 해제하므로, cacheComponents의 <Activity>로 화면이 숨겨졌다 다시 보여도 "추가 중..."이 남지 않는다.
 */
export function AddMemberForm({ projectId, onAdded, onProjectChanged }: AddMemberFormProps) {
  const { refresh } = useAuth();
  const [email, setEmail] = useState("");
  const [fieldError, setFieldError] = useState<string | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // 상태 반영 전 연속 제출도 막는다.
  const submittingRef = useRef(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) {
      return;
    }
    setFormError(null);
    const { value, error } = validateMemberEmail(email);
    setFieldError(error ?? null);
    if (error) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      await addMember(projectId, value);
      setEmail("");
      onAdded();
    } catch (failure) {
      handleFailure(failure);
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  function handleFailure(failure: unknown) {
    if (isUnauthenticated(failure)) {
      void refresh();
      return;
    }
    const code = errorCode(failure);
    if (code === "USER_NOT_FOUND") {
      setFieldError("해당 이메일로 가입한 사용자를 찾을 수 없습니다.");
    } else if (code === "PROJECT_MEMBER_ALREADY_EXISTS") {
      setFieldError("이미 프로젝트 구성원입니다.");
    } else if (failure instanceof ApiError && failure.status === 400 && failure.fieldErrors.length > 0) {
      setFieldError(failure.fieldErrors.find((item) => item.field === "email")?.message ?? failure.fieldErrors[0].message);
    } else if (code === "ACCESS_DENIED") {
      setFormError("권한이 없습니다.");
      onProjectChanged();
    } else if (code === "PROJECT_NOT_FOUND") {
      onProjectChanged();
    } else {
      setFormError(failureMessage(failure));
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-2">
      <label htmlFor="member-email" className="text-sm">
        구성원 이메일
      </label>
      <div className="flex gap-2">
        <input
          id="member-email"
          type="email"
          name="email"
          autoComplete="off"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={Boolean(fieldError)}
          aria-describedby={fieldError ? "member-email-error" : undefined}
          className="min-w-0 flex-1 rounded border border-zinc-300 px-3 py-2 text-sm"
        />
        <button
          type="submit"
          disabled={submitting}
          className="rounded bg-zinc-900 px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          {submitting ? "추가 중..." : "추가"}
        </button>
      </div>
      {fieldError && (
        <span id="member-email-error" className="text-sm text-red-700">
          {fieldError}
        </span>
      )}
      {formError && (
        <p role="alert" className="text-sm font-medium text-red-700">
          {formError}
        </p>
      )}
    </form>
  );
}
