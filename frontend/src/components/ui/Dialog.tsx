"use client";

import { useEffect, useId, useRef, type ReactNode } from "react";

/**
 * 모달 대화상자. 열릴 때 첫 포커스 대상으로 이동하고, Esc로 닫으며, Tab 포커스를 대화상자 안에 가둔다.
 * 닫히면 열기 전 요소로 포커스를 돌려준다. {@code busy}이면 Esc·배경 클릭으로 닫지 않는다.
 */
export function Dialog({ open, title, description, children, footer, onClose, busy = false }: {
  open: boolean;
  title: string;
  description?: ReactNode;
  children?: ReactNode;
  footer: ReactNode;
  onClose: () => void;
  busy?: boolean;
}) {
  const titleId = useId();
  const descriptionId = useId();
  const panelRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return;
    }
    const previous = document.activeElement as HTMLElement | null;
    const panel = panelRef.current;
    const focusables = () =>
      Array.from(
        panel?.querySelectorAll<HTMLElement>("button, [href], input, textarea, select, [tabindex]:not([tabindex='-1'])") ?? [],
      ).filter((element) => !element.hasAttribute("disabled"));
    (panel?.querySelector<HTMLElement>("[data-autofocus]") ?? focusables()[0] ?? panel)?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape" && !busy) {
        event.preventDefault();
        onClose();
      } else if (event.key === "Tab") {
        const items = focusables();
        if (items.length === 0) {
          return;
        }
        const first = items[0];
        const last = items[items.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    };
    document.addEventListener("keydown", onKeyDown);
    return () => {
      document.removeEventListener("keydown", onKeyDown);
      previous?.focus?.();
    };
  }, [open, busy, onClose]);

  if (!open) {
    return null;
  }
  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center p-4 sm:items-center">
      <div aria-hidden="true" className="absolute inset-0 bg-ink/40" onClick={busy ? undefined : onClose} />
      <div
        ref={panelRef}
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        aria-describedby={description ? descriptionId : undefined}
        tabIndex={-1}
        className="relative flex max-h-[90vh] w-full max-w-lg flex-col overflow-y-auto rounded-xl bg-surface shadow-[var(--shadow-overlay)]"
      >
        <div className="flex flex-col gap-2 px-6 pt-6">
          <h2 id={titleId} className="text-lg font-semibold text-ink">
            {title}
          </h2>
          {description && (
            <div id={descriptionId} className="text-sm text-ink-muted">
              {description}
            </div>
          )}
        </div>
        {children && <div className="px-6 pt-4">{children}</div>}
        <div className="flex flex-col-reverse gap-2 px-6 py-5 sm:flex-row sm:justify-end">{footer}</div>
      </div>
    </div>
  );
}
