"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { type FormEvent, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Field";
import { PasswordInput } from "@/components/ui/PasswordInput";

import { ApiError } from "@/lib/api-client";

import { login } from "../api";
import { useAuth } from "../AuthProvider";
import { safeNextPath } from "../redirect";

type FieldErrors = Partial<Record<"email" | "password", string>>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

function validate(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {};
  if (!email.trim()) {
    errors.email = "이메일을 입력해 주세요.";
  } else if (!EMAIL_PATTERN.test(email.trim())) {
    errors.email = "이메일 형식이 올바르지 않습니다.";
  }
  if (!password.trim()) {
    errors.password = "비밀번호를 입력해 주세요.";
  }
  return errors;
}

function messageFor(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return "이메일 또는 비밀번호가 올바르지 않습니다.";
    }
    if (error.status === 429) {
      return "로그인 시도가 너무 많습니다. 잠시 후 다시 시도해 주세요.";
    }
    if (error.code === "CSRF_INVALID") {
      return "요청을 처리할 수 없습니다. 페이지를 새로고침해 주세요.";
    }
  }
  return "서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.";
}

export function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { status, signIn } = useAuth();
  const nextPath = safeNextPath(searchParams.get("next"));
  const expired = searchParams.get("reason") === "expired";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);

  useEffect(() => {
    if (status === "authenticated") {
      router.replace(nextPath);
    }
  }, [status, nextPath, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    // 상태 반영 전 연속 제출(두 번 클릭·Enter)을 막는다.
    if (submittingRef.current) {
      return;
    }
    setFormError(null);
    const errors = validate(email, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const user = await login(email.trim(), password);
      signIn(user);
      router.replace(nextPath);
    } catch (error) {
      if (error instanceof ApiError && error.status === 400 && error.fieldErrors.length > 0) {
        const serverErrors: FieldErrors = {};
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === "email" || fieldError.field === "password") {
            serverErrors[fieldError.field] = fieldError.message;
          }
        }
        setFieldErrors(serverErrors);
      } else {
        setFormError(messageFor(error));
      }
    } finally {
      submittingRef.current = false;
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4" aria-busy={submitting}>
      {expired && (
        <p role="status" className="rounded-[var(--radius-control)] border border-warning-700/20 bg-warning-50 px-3 py-2 text-sm text-warning-700">
          세션이 만료되었습니다. 다시 로그인해 주세요.
        </p>
      )}
      <Field label="이메일" htmlFor="login-email" error={fieldErrors.email} errorId="email-error">
        <Input
          id="login-email"
          type="email"
          name="email"
          autoComplete="username"
          inputMode="email"
          value={email}
          disabled={submitting}
          onChange={(event) => setEmail(event.target.value)}
          invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? "email-error" : undefined}
        />
      </Field>
      <Field label="비밀번호" htmlFor="login-password" error={fieldErrors.password} errorId="password-error">
        <PasswordInput
          id="login-password"
          name="password"
          autoComplete="current-password"
          value={password}
          disabled={submitting}
          onChange={(event) => setPassword(event.target.value)}
          invalid={Boolean(fieldErrors.password)}
          aria-describedby={fieldErrors.password ? "password-error" : undefined}
        />
      </Field>
      {formError && (
        <p role="alert" className="rounded-[var(--radius-control)] border border-danger-600/20 bg-danger-50 px-3 py-2 text-sm font-medium text-danger-700">
          {formError}
        </p>
      )}
      <Button type="submit" variant="primary" disabled={submitting} className="w-full">
        {submitting ? "로그인 중..." : "로그인"}
      </Button>
    </form>
  );
}
