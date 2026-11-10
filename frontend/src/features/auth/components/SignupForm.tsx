"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Field";
import { ApiError } from "@/lib/api-client";

import { signup } from "../signup-api";

type FieldName = "inviteCode" | "email" | "password" | "passwordConfirm" | "displayName";
type FieldErrors = Partial<Record<FieldName, string>>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;
const PASSWORD_MIN = 10;
const PASSWORD_MAX = 64;
const PASSWORD_MAX_BYTES = 72;

/** 입력 검증은 사용성 보조이며 최종 판정은 Backend가 한다 (#26). */
function validate(values: Record<FieldName, string>): FieldErrors {
  const errors: FieldErrors = {};
  if (!values.inviteCode.trim()) errors.inviteCode = "초대코드를 입력해 주세요.";
  if (!values.email.trim()) errors.email = "이메일을 입력해 주세요.";
  else if (!EMAIL_PATTERN.test(values.email.trim())) errors.email = "이메일 형식이 올바르지 않습니다.";
  if (!values.displayName.trim()) errors.displayName = "이름을 입력해 주세요.";
  else if (values.displayName.trim().length > 50) errors.displayName = "이름은 50자 이하로 입력해 주세요.";
  if (values.password.length < PASSWORD_MIN || values.password.length > PASSWORD_MAX) {
    errors.password = `비밀번호는 ${PASSWORD_MIN}~${PASSWORD_MAX}자로 입력해 주세요.`;
  } else if (new TextEncoder().encode(values.password).length > PASSWORD_MAX_BYTES) {
    errors.password = "비밀번호가 너무 깁니다. 한글 등은 더 짧게 입력해 주세요.";
  }
  if (values.passwordConfirm !== values.password) errors.passwordConfirm = "비밀번호가 일치하지 않습니다.";
  return errors;
}

export function SignupForm() {
  const router = useRouter();
  const [values, setValues] = useState<Record<FieldName, string>>({
    inviteCode: "",
    email: "",
    password: "",
    passwordConfirm: "",
    displayName: "",
  });
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const update = (name: FieldName) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setValues((current) => ({ ...current, [name]: event.target.value }));

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setFormError(null);
    const next = validate(values);
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    setSubmitting(true);
    try {
      await signup({
        inviteCode: values.inviteCode.trim(),
        email: values.email.trim(),
        password: values.password,
        displayName: values.displayName.trim(),
      });
      router.replace("/login?signup=done");
    } catch (error) {
      if (error instanceof ApiError && error.code === "INVITATION_INVALID") {
        setErrors({ inviteCode: "초대코드가 올바르지 않거나 만료되었습니다. 관리자에게 새 코드를 요청해 주세요." });
      } else if (error instanceof ApiError && error.code === "EMAIL_ALREADY_REGISTERED") {
        setErrors({ email: "이미 가입된 이메일입니다. 로그인해 주세요." });
      } else if (error instanceof ApiError && error.status === 429) {
        setFormError("시도 횟수를 초과했습니다. 잠시 후 다시 시도해 주세요.");
      } else if (error instanceof ApiError && error.status === 400) {
        const serverErrors: FieldErrors = {};
        for (const fieldError of error.fieldErrors) {
          if (["inviteCode", "email", "password", "displayName"].includes(fieldError.field)) {
            serverErrors[fieldError.field as FieldName] = fieldError.message;
          }
        }
        setErrors(serverErrors);
        if (Object.keys(serverErrors).length === 0) {
          setFormError("입력값을 확인해 주세요.");
        }
      } else {
        setFormError("서버에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  const field = (name: FieldName, label: string, input: React.ReactNode, hint?: string) => (
    <Field label={label} htmlFor={`signup-${name}`} error={errors[name]} errorId={`signup-${name}-error`} hint={hint} required>
      {input}
    </Field>
  );
  const inputProps = (name: FieldName) => ({
    id: `signup-${name}`,
    value: values[name],
    onChange: update(name),
    invalid: Boolean(errors[name]),
    "aria-describedby": errors[name] ? `signup-${name}-error` : undefined,
  });

  return (
    <form onSubmit={(event) => void handleSubmit(event)} noValidate className="flex flex-col gap-4">
      {field("inviteCode", "초대코드", <Input {...inputProps("inviteCode")} autoComplete="off" spellCheck={false} placeholder="XXXX-XXXX-XXXX-…" />, "관리자에게 받은 일회용 코드입니다.")}
      {field("email", "이메일", <Input {...inputProps("email")} type="email" autoComplete="email" />)}
      {field("displayName", "이름", <Input {...inputProps("displayName")} autoComplete="name" />, "구성원에게 표시되는 이름입니다.")}
      {field("password", "비밀번호", <Input {...inputProps("password")} type="password" autoComplete="new-password" />, `${PASSWORD_MIN}~${PASSWORD_MAX}자`)}
      {field("passwordConfirm", "비밀번호 확인", <Input {...inputProps("passwordConfirm")} type="password" autoComplete="new-password" />)}
      {formError && (
        <p role="alert" className="text-sm text-danger-700">
          {formError}
        </p>
      )}
      <Button type="submit" variant="primary" disabled={submitting}>
        {submitting ? "가입하는 중..." : "가입하기"}
      </Button>
      <p className="text-center text-sm text-ink-muted">
        이미 계정이 있나요?{" "}
        <Link href="/login" className="font-medium text-brand-700 hover:underline">
          로그인
        </Link>
      </p>
    </form>
  );
}
