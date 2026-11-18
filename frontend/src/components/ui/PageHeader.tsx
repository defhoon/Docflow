import Link from "next/link";
import type { ReactNode } from "react";

export type Crumb = { label: string; href?: string };

/**
 * 현재 위치. 긴 이름은 한 줄에서 말줄임하고(마지막 항목에 가장 넓은 폭), 전체 이름은 `title`로 확인할 수 있다. 페이지의 전체
 * 제목은 항상 h1에 그대로 표시한다(#50).
 */
export function Breadcrumb({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="현재 위치" className="min-w-0">
      <ol className="flex min-w-0 items-center gap-1 overflow-hidden text-xs text-ink-muted">
        {items.map((item, index) => {
          const last = index === items.length - 1;
          const width = last ? "max-w-[14rem] sm:max-w-[28rem]" : "max-w-[8rem] sm:max-w-[14rem]";
          return (
            <li key={`${item.label}-${index}`} className={`flex min-w-0 items-center gap-1 ${last ? "shrink" : "shrink-0"}`}>
              {index > 0 && <span aria-hidden="true" className="text-ink-decor">/</span>}
              {item.href ? (
                <Link href={item.href} title={item.label} className={`block truncate hover:text-ink ${width}`}>
                  {item.label}
                </Link>
              ) : (
                <span aria-current="page" title={item.label} className={`block truncate text-ink ${width}`}>
                  {item.label}
                </span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

export function PageHeader({ title, description, actions, breadcrumb, meta }: {
  title: ReactNode;
  description?: ReactNode;
  actions?: ReactNode;
  breadcrumb?: Crumb[];
  meta?: ReactNode;
}) {
  return (
    <header className="flex flex-col gap-3">
      {breadcrumb && <Breadcrumb items={breadcrumb} />}
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="text-xl font-semibold tracking-tight break-words text-ink sm:text-2xl">{title}</h1>
          {description && <p className="text-sm text-ink-muted">{description}</p>}
          {meta && <div className="mt-1 flex flex-wrap items-center gap-2 text-xs text-ink-muted">{meta}</div>}
        </div>
        {actions && <div className="flex flex-wrap items-center gap-2">{actions}</div>}
      </div>
    </header>
  );
}
