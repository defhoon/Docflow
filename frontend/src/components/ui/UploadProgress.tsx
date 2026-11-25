import type { UploadProgress } from "@/lib/api-client";

/**
 * 업로드 진행 표시 (SPEC-19). 전송률을 막대와 숫자로 보여 주고, 다 보낸 뒤에는 서버 확인 중임을 알린다. 진행률을 모르면
 * (브라우저가 알려 주지 않음) 문구만 보여 준다.
 */
export function UploadProgressBar({ progress, filename }: { progress: UploadProgress | null; filename?: string }) {
  const percent = progress && progress.total > 0 ? Math.min(100, Math.round((progress.loaded / progress.total) * 100)) : null;
  const label =
    percent === null
      ? "파일을 업로드하는 중입니다."
      : percent < 100
        ? `파일을 업로드하는 중입니다. ${percent}%`
        : "업로드를 마쳤습니다. 서버에서 파일을 확인하는 중입니다.";
  return (
    <div className="flex flex-col gap-1.5" role="status" aria-live="polite">
      <div className="flex items-center justify-between gap-3 text-sm">
        <span className="min-w-0 truncate text-ink-muted">{label}</span>
        {filename && <span className="max-w-[40%] shrink-0 truncate text-xs text-ink-muted">{filename}</span>}
      </div>
      <div
        className="h-1.5 overflow-hidden rounded-full bg-subtle"
        role="progressbar"
        aria-label="업로드 진행률"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={percent ?? undefined}
      >
        <div
          className={percent === null ? "h-full w-1/3 animate-pulse rounded-full bg-brand-500" : "h-full rounded-full bg-brand-600 transition-[width]"}
          style={percent === null ? undefined : { width: `${percent}%` }}
        />
      </div>
      <p className="text-xs text-ink-muted">화면을 닫지 마세요.</p>
    </div>
  );
}
