import type { ReactNode } from "react";

/**
 * 로그인·가입 화면 틀 (#50): 배경 위 가운데 정렬한 인증 카드. 서비스 이름과 짧은 설명을 카드 위에 두고, 카드 안에는 제목·안내와
 * 폼만 둔다. 모바일에서도 같은 구조로 세로 배치한다.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="flex min-h-dvh flex-col items-center justify-center bg-canvas px-4 py-12 sm:px-6">
      <div className="flex w-full max-w-md flex-col gap-6">
        <div className="flex flex-col items-center gap-2 text-center">
          <div className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink">
            <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
              D
            </span>
            DocFlow
          </div>
          <p className="text-sm text-ink-muted">문서의 버전과 피드백을 한곳에서 관리하고, 최종본을 분명하게 확정하세요.</p>
        </div>
        <section className="rounded-[var(--radius-card)] border border-line bg-surface px-6 py-7 shadow-[var(--shadow-card)] sm:px-8">
          <div className="mb-6 flex flex-col gap-1">
            <h1 className="text-xl font-semibold tracking-tight text-ink">{title}</h1>
            <p className="text-sm text-ink-muted">{subtitle}</p>
          </div>
          <div className="flex flex-col gap-5">{children}</div>
        </section>
        <p className="text-center text-xs text-ink-muted">DocFlow · 문서 협업 워크플로</p>
      </div>
    </div>
  );
}
