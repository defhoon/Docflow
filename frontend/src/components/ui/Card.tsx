import type { ReactNode } from "react";

import { cn } from "./cn";

export function Card({ className, children, as: Tag = "section", ...rest }: {
  className?: string;
  children: ReactNode;
  as?: "section" | "div" | "article" | "aside";
  "aria-labelledby"?: string;
}) {
  return (
    <Tag className={cn("rounded-[var(--radius-card)] border border-line bg-surface shadow-[var(--shadow-card)]", className)} {...rest}>
      {children}
    </Tag>
  );
}

export function CardHeader({ title, titleId, description, actions }: {
  title: ReactNode;
  titleId?: string;
  description?: ReactNode;
  actions?: ReactNode;
}) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-line px-5 py-4">
      <div className="min-w-0">
        <h2 id={titleId} className="text-sm font-semibold text-ink">
          {title}
        </h2>
        {description && <p className="mt-0.5 text-xs text-ink-muted">{description}</p>}
      </div>
      {actions && <div className="flex shrink-0 items-center gap-2">{actions}</div>}
    </div>
  );
}

export function CardBody({ className, children }: { className?: string; children: ReactNode }) {
  return <div className={cn("px-5 py-4", className)}>{children}</div>;
}
