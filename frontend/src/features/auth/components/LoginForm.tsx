"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";

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

  useEffect(() => {
    if (status === "authenticated") {
      router.replace(nextPath);
    }
  }, [status, nextPath, router]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const errors = validate(email, password);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

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
      setSubmitting(false);
    }
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      {expired && (
        <p role="status" className="rounded bg-amber-50 px-3 py-2 text-sm text-amber-800">
          세션이 만료되었습니다. 다시 로그인해 주세요.
        </p>
      )}
      <label className="flex flex-col gap-1 text-sm">
        이메일
        <input
          type="email"
          name="email"
          autoComplete="username"
          value={email}
          onChange={(event) => setEmail(event.target.value)}
          aria-invalid={Boolean(fieldErrors.email)}
          aria-describedby={fieldErrors.email ? "email-error" : undefined}
          className="rounded border border-zinc-300 px-3 py-2"
        />
        {fieldErrors.email && (
          <span id="email-error" className="text-red-700">
            {fieldErrors.email}
          </span>
        )}
      </label>
      <label className="flex flex-col gap-1 text-sm">
        비밀번호
        <input
          type="password"
          name="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          aria-invalid={Boolean(fieldErrors.password)}
          aria-describedby={fieldErrors.password ? "password-error" : undefined}
          className="rounded border border-zinc-300 px-3 py-2"
        />
        {fieldErrors.password && (
          <span id="password-error" className="text-red-700">
            {fieldErrors.password}
          </span>
        )}
      </label>
      {formError && (
        <p role="alert" className="text-sm font-medium text-red-700">
          {formError}
        </p>
      )}
      <button
        type="submit"
        disabled={submitting}
        className="rounded bg-zinc-900 px-4 py-2 font-medium text-white disabled:opacity-50"
      >
        {submitting ? "로그인 중..." : "로그인"}
      </button>
    </form>
  );
}
