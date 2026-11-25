import type { ReactNode } from "react";

import { ChatIcon, CheckCircleIcon, CompareIcon } from "../ui/icons";

const FEATURES = [
  { icon: <CompareIcon className="size-5" />, title: "버전별로 쌓이는 문서", text: "새 파일을 올릴 때마다 다음 버전으로 남고, 두 버전의 차이를 나란히 비교합니다." },
  { icon: <ChatIcon className="size-5" />, title: "버전마다 남기는 피드백", text: "문서를 보면서 같은 화면에서 그 버전에 대한 의견을 주고받습니다." },
  { icon: <CheckCircleIcon className="size-5" />, title: "분명한 최종본", text: "프로젝트 오너가 확정한 버전은 읽기 전용 최종본이 됩니다." },
];

/**
 * 로그인·가입 화면 틀 (SPEC-19). 데스크톱은 서비스 소개 | 폼 2단, 모바일은 로고·폼만 세로로 둔다. 소개 영역은 실제 기능만
 * 설명하며 예시 데이터를 만들어 보여 주지 않는다.
 */
export function AuthLayout({ title, subtitle, children }: { title: string; subtitle: string; children: ReactNode }) {
  return (
    <div className="grid min-h-dvh grid-cols-1 bg-surface lg:grid-cols-2">
      <aside className="hidden flex-col justify-between border-r border-line bg-canvas px-12 py-10 lg:flex">
        <Brand />
        <div className="flex max-w-md flex-col gap-8">
          <p className="text-3xl leading-snug font-semibold tracking-tight text-ink">
            버전마다 쌓이는 피드백을
            <br />한 화면에서 검토하고 확정하세요.
          </p>
          <ul className="flex flex-col gap-5">
            {FEATURES.map((feature) => (
              <li key={feature.title} className="flex gap-3">
                <span aria-hidden="true" className="flex size-9 shrink-0 items-center justify-center rounded-lg bg-brand-50 text-brand-700">
                  {feature.icon}
                </span>
                <span className="flex flex-col gap-0.5">
                  <span className="text-sm font-semibold text-ink">{feature.title}</span>
                  <span className="text-sm text-ink-muted">{feature.text}</span>
                </span>
              </li>
            ))}
          </ul>
        </div>
        <p className="text-xs text-ink-muted">초대받은 구성원만 사용할 수 있는 비공개 서비스입니다.</p>
      </aside>
      <main className="flex flex-col items-center justify-center px-4 py-12 sm:px-6">
        <div className="flex w-full max-w-sm flex-col gap-8">
          <div className="lg:hidden">
            <Brand />
          </div>
          <section className="flex flex-col gap-6">
            <div className="flex flex-col gap-1">
              <h1 className="text-2xl font-semibold tracking-tight text-ink">{title}</h1>
              <p className="text-sm text-ink-muted">{subtitle}</p>
            </div>
            <div className="flex flex-col gap-5">{children}</div>
          </section>
          <p className="text-center text-xs text-ink-muted lg:hidden">DocFlow · 문서 협업 워크플로</p>
        </div>
      </main>
    </div>
  );
}

function Brand() {
  return (
    <div className="flex items-center gap-2 text-lg font-semibold tracking-tight text-ink">
      <span aria-hidden="true" className="flex size-8 items-center justify-center rounded-lg bg-brand-600 text-sm font-bold text-white">
        D
      </span>
      DocFlow
    </div>
  );
}
