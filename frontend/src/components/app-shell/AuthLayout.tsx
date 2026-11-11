import type { ReactNode } from "react";

/** 로그인·가입 화면 틀: 데스크톱은 소개 패널 + 폼, 모바일은 폼만. */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh lg:grid-cols-2">
      <section className="hidden flex-col justify-between bg-ink p-12 text-white lg:flex">
        <div className="flex items-center gap-2 text-lg font-semibold">
          <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-brand-500 font-bold">
            D
          </span>
          DocFlow
        </div>
        <div className="flex max-w-md flex-col gap-4">
          <p className="text-3xl leading-snug font-semibold">문서의 모든 버전과 피드백을 한곳에서, 최종본은 분명하게.</p>
          <ul className="flex flex-col gap-2 text-sm text-white/70">
            <li>· 구성원 누구나 새 버전을 올리고 버전별로 의견을 남깁니다.</li>
            <li>· 프로젝트 오너가 공식 최종 버전을 확정하면 문서는 읽기 전용이 됩니다.</li>
            <li>· 누가 언제 무엇을 했는지 활동 이력으로 확인합니다.</li>
          </ul>
        </div>
        <p className="text-xs text-white/40">DocFlow · 문서 협업 워크플로</p>
      </section>
      <section className="flex items-center justify-center px-4 py-12 sm:px-6">
        <div className="flex w-full max-w-sm flex-col gap-6">
          <div className="flex flex-col gap-1">
            <div className="mb-4 flex items-center gap-2 font-semibold lg:hidden">
              <span aria-hidden="true" className="flex size-7 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
                D
              </span>
              DocFlow
            </div>
            <h1 className="text-2xl font-semibold tracking-tight">{title}</h1>
            <p className="text-sm text-ink-muted">{subtitle}</p>
          </div>
          {children}
        </div>
      </section>
    </div>
  );
}
