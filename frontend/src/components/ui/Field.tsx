import type { InputHTMLAttributes, ReactNode, SelectHTMLAttributes, TextareaHTMLAttributes } from "react";

import { cn } from "./cn";

const CONTROL =
  "w-full rounded-lg border bg-surface px-3 text-sm text-ink placeholder:text-ink-faint transition-colors focus:border-brand-500 focus:outline-none focus:ring-2 focus:ring-brand-500/20 disabled:bg-subtle disabled:text-ink-faint";

export function controlClass(invalid?: boolean, extra?: string): string {
  return cn(CONTROL, invalid ? "border-danger-600" : "border-line-strong", extra);
}

export function Input({ invalid, className, ...props }: InputHTMLAttributes<HTMLInputElement> & { invalid?: boolean }) {
  return <input aria-invalid={invalid || undefined} className={controlClass(invalid, cn("h-10", className))} {...props} />;
}

export function Textarea({ invalid, className, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { invalid?: boolean }) {
  return <textarea aria-invalid={invalid || undefined} className={controlClass(invalid, cn("min-h-24 py-2", className))} {...props} />;
}

export function Select({ invalid, className, ...props }: SelectHTMLAttributes<HTMLSelectElement> & { invalid?: boolean }) {
  return <select aria-invalid={invalid || undefined} className={controlClass(invalid, cn("h-10", className))} {...props} />;
}

/** 라벨·도움말·오류를 묶는다. 오류는 aria-describedby로 연결할 수 있도록 id를 받는다. */
export function Field({ label, htmlFor, hint, error, errorId, children, required }: {
  label: string;
  htmlFor: string;
  hint?: ReactNode;
  error?: string;
  errorId?: string;
  required?: boolean;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-ink">
        {label}
        {required && <span className="ml-0.5 text-danger-600" aria-hidden="true">*</span>}
      </label>
      {children}
      {hint && !error && <p className="text-xs text-ink-muted">{hint}</p>}
      {error && (
        <p id={errorId} className="text-xs text-danger-700">
          {error}
        </p>
      )}
    </div>
  );
}
