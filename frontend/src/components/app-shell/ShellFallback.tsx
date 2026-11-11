/** 동적 화면의 Suspense 대체 화면. 레이아웃이 크게 흔들리지 않도록 사이드바 자리를 둔다. */
export function ShellFallback() {
  return (
    <div className="flex min-h-dvh w-full">
      <div className="hidden w-60 shrink-0 border-r border-line bg-surface lg:block" />
      <div className="flex-1 px-4 py-6 sm:px-6 lg:px-10 lg:py-8">
        <p role="status" className="text-sm text-ink-muted">
          불러오는 중...
        </p>
      </div>
    </div>
  );
}
