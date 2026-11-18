"use client";

import { type DragEvent, useState } from "react";

import { cn } from "./cn";
import { FileIcon } from "./icons";

function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

type Props = {
  id: string;
  /** 접근성 이름. 파일 선택 창과 끌어다 놓기 모두 이 라벨로 안내한다. */
  label: string;
  hint: string;
  accept: string;
  file: File | null;
  disabled?: boolean;
  error?: string;
  errorId?: string;
  describedBy?: string;
  onFile: (file: File | null) => void;
};

/**
 * 파일 선택 영역 (#50). 실제 선택은 기존과 같은 `<input type="file">`이 담당하고(키보드·스크린리더 그대로 사용), 영역에
 * 파일을 끌어다 놓으면 같은 `onFile` 경로로 전달한다. 검증·업로드 로직은 호출하는 폼이 그대로 가진다.
 * 입력을 비우려면 호출하는 쪽에서 `key`를 바꿔 다시 마운트한다.
 */
export function FileDropzone({ id, label, hint, accept, file, disabled, error, errorId, describedBy, onFile }: Props) {
  const [dragging, setDragging] = useState(false);

  function handleDrop(event: DragEvent<HTMLLabelElement>) {
    event.preventDefault();
    setDragging(false);
    if (disabled) {
      return;
    }
    const dropped = event.dataTransfer.files?.[0];
    if (dropped) {
      onFile(dropped);
    }
  }

  return (
    <div className="flex flex-col gap-1.5">
      <input
        id={id}
        type="file"
        name="file"
        accept={accept}
        disabled={disabled}
        onChange={(event) => onFile(event.target.files?.[0] ?? null)}
        aria-label={label}
        aria-invalid={Boolean(error) || undefined}
        aria-describedby={[error ? errorId : undefined, `${id}-hint`, describedBy].filter(Boolean).join(" ") || undefined}
        className="peer sr-only"
      />
      <label
        htmlFor={id}
        onDragOver={(event) => {
          event.preventDefault();
          if (!disabled) {
            setDragging(true);
          }
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
        className={cn(
          "flex cursor-pointer flex-col items-center justify-center gap-2 rounded-[var(--radius-card)] border border-dashed px-4 py-6 text-center transition-colors",
          "peer-focus-visible:outline-2 peer-focus-visible:outline-offset-2 peer-focus-visible:outline-brand-600",
          dragging ? "border-brand-600 bg-brand-50" : error ? "border-danger-600 bg-danger-50/40" : "border-line-strong bg-subtle/40 hover:border-ink-decor hover:bg-subtle",
          disabled && "cursor-not-allowed opacity-60",
        )}
      >
        <span className="text-sm font-medium text-ink">{label}</span>
        {file ? (
          <span className="flex max-w-full items-center gap-2 rounded-md border border-line bg-surface px-3 py-1.5 text-sm text-ink">
            <FileIcon className="size-4 shrink-0 text-ink-decor" />
            <span className="min-w-0 truncate" title={file.name}>
              {file.name}
            </span>
            <span className="shrink-0 text-xs text-ink-muted">{formatBytes(file.size)}</span>
          </span>
        ) : (
          <span className="text-sm text-ink-muted">
            <span className="font-medium text-brand-700">파일 선택</span> 또는 여기로 끌어다 놓기
          </span>
        )}
        <span id={`${id}-hint`} className="text-xs text-ink-muted">
          {hint}
        </span>
      </label>
      {error && (
        <p id={errorId} className="text-xs font-medium text-danger-700">
          {error}
        </p>
      )}
    </div>
  );
}
