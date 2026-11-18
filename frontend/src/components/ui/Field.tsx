import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

import { cn } from "./cn";

/** 입력 컨트롤 공통 스타일 (#50): 높이 40px, 반경 8px, 얇은 테두리, 명확한 포커스·비활성 상태. */
const CONTROL =
  "w-full rounded-[var(--radius-control)] border bg-surface px-3 text-sm text-ink placeholder:text-ink-decor transition-colors hover:border-ink-decor focus:border-brand-600 focus:outline-none focus-visible:outline-none focus:ring-3 focus:ring-brand-600/15 disabled:cursor-not-allowed disabled:bg-subtle disabled:text-ink-muted";

export function controlClass(invalid?: boolean, extra?: string): string {
  return cn(CONTROL, invalid ? "border-danger-600 hover:border-danger-600" : "border-line-strong", extra);
}

export function Input({ invalid, className, ...props }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={controlClass(invalid, cn("h-10", className))} {...props} />;
}

export function Textarea({ invalid, className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return (
    <textarea
      aria-invalid={invalid || undefined}
      className={controlClass(invalid, cn("min-h-24 resize-y py-2 leading-relaxed", className))}
      {...props}
    />
  );
}

export function Select({ invalid, className, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return <select aria-invalid={invalid || undefined} className={controlClass(invalid, cn("h-10", className))} {...props} />;
}

/**
 * 라벨·도움말·오류를 묶는다. 오류는 aria-describedby로 연결할 수 있도록 id를 받는다.
 * `aside`는 라벨 오른쪽(글자 수 등), `optional`은 라벨 뒤 '(선택)' 표시(접근성 이름에 포함)다.
 */
export function Field({ label, htmlFor, hint, hintId, error, errorId, children, required, optional, aside }: {
  label: string;
  htmlFor: string;
  hint?: ReactNode;
  hintId?: string;
  error?: string;
  errorId?: string;
  required?: boolean;
  optional?: boolean;
  aside?: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <div className="flex items-baseline justify-between gap-3">
        <label htmlFor={htmlFor} className="text-sm font-medium text-ink">
          {label}
          {optional && (
            <>
              {" "}
              <span className="font-normal text-ink-muted">(선택)</span>
            </>
          )}
          {required && <span className="ml-0.5 text-danger-600" aria-hidden="true">*</span>}
        </label>
        {aside}
      </div>
      {children}
      {hint && !error && (
        <p id={hintId} className="text-xs text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-xs font-medium text-danger-700">
          {error}
        </p>
      )}
    </div>
  );
}
