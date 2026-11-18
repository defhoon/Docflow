"use client";

import { type InputHTMLAttributes, useState } from "react";

import { cn } from "./cn";
import { Input } from "./Field";
import { EyeIcon, EyeOffIcon } from "./icons";

/**
 * 비밀번호 입력 (#50). 보기/숨기기 버튼으로 입력한 값을 확인할 수 있다. 버튼은 폼을 제출하지 않으며, 상태를 aria-pressed로
 * 알린다. 값과 자동 완성 동작은 일반 비밀번호 입력과 같다.
 */
export function PasswordInput({ className, invalid, ...props }: Omit<InputHTMLAttributes<HTMLInputElement>, "type"> & { invalid?: boolean }) {
  const [visible, setVisible] = useState(false);
  return (
    <div className="relative">
      <Input {...props} type={visible ? "text" : "password"} invalid={invalid} className={cn("pr-10", className)} />
      <button
        type="button"
        onClick={() => setVisible((current) => !current)}
        aria-label={visible ? "비밀번호 숨기기" : "비밀번호 표시"}
        aria-pressed={visible}
        aria-controls={props.id}
        className="absolute inset-y-0 right-0 flex w-10 items-center justify-center rounded-r-[var(--radius-control)] text-ink-muted hover:text-ink"
      >
        {visible ? <EyeOffIcon className="size-4" /> : <EyeIcon className="size-4" />}
      </button>
    </div>
  );
}
