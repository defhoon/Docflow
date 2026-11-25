"use client";

import { type KeyboardEvent, type ReactNode, useRef } from "react";

import { cn } from "./cn";

export type TabItem<K extends string> = { key: K; label: ReactNode; count?: number; hidden?: boolean };

/**
 * WAI-ARIA 탭 목록 (#68). 화살표·Home·End로 이동하며 선택된 탭만 Tab 순서에 둔다(roving tabindex). 탭 패널은 호출자가
 * `tabPanelProps(idBase, key)`로 연결한다.
 */
export function Tabs<K extends string>({ items, value, onChange, label, idBase, className, size = "md" }: {
  items: TabItem<K>[];
  value: K;
  onChange: (key: K) => void;
  /** 탭 목록의 접근 가능한 이름 */
  label: string;
  idBase: string;
  className?: string;
  size?: "sm" | "md";
}) {
  const refs = useRef<Record<string, HTMLButtonElement | null>>({});
  const visible = items.filter((item) => !item.hidden);

  function onKeyDown(event: KeyboardEvent<HTMLButtonElement>, index: number) {
    let next = -1;
    if (event.key === "ArrowRight") next = (index + 1) % visible.length;
    else if (event.key === "ArrowLeft") next = (index - 1 + visible.length) % visible.length;
    else if (event.key === "Home") next = 0;
    else if (event.key === "End") next = visible.length - 1;
    if (next < 0) {
      return;
    }
    event.preventDefault();
    const key = visible[next].key;
    onChange(key);
    refs.current[key]?.focus();
  }

  return (
    <div role="tablist" aria-label={label} className={cn("flex items-end gap-1 overflow-x-auto", className)}>
      {visible.map((item, index) => {
        const selected = item.key === value;
        return (
          <button
            key={item.key}
            ref={(element) => {
              refs.current[item.key] = element;
            }}
            type="button"
            role="tab"
            id={`${idBase}-tab-${item.key}`}
            aria-selected={selected}
            aria-controls={`${idBase}-panel-${item.key}`}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(item.key)}
            onKeyDown={(event) => onKeyDown(event, index)}
            className={cn(
              "-mb-px flex shrink-0 items-center gap-1.5 border-b-2 font-medium whitespace-nowrap transition-colors",
              size === "sm" ? "px-2 py-2 text-[13px]" : "px-3 py-2.5 text-sm",
              selected ? "border-brand-600 text-ink" : "border-transparent text-ink-muted hover:text-ink",
            )}
          >
            {item.label}
            {item.count !== undefined && (
              <span className={cn("rounded-full px-1.5 text-xs tabular-nums", selected ? "bg-brand-50 text-brand-700" : "bg-subtle text-ink-muted")}>
                {item.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}

/** 탭 패널 속성. 선택되지 않은 패널은 호출자가 그리지 않거나 `hidden`으로 숨긴다. */
export function tabPanelProps(idBase: string, key: string) {
  return { role: "tabpanel" as const, id: `${idBase}-panel-${key}`, "aria-labelledby": `${idBase}-tab-${key}`, tabIndex: 0 };
}

/**
 * 단일 선택 버튼 묶음 (#68). 보기 방식·필터처럼 화면 일부를 바꾸는 선택에 쓴다. 각 버튼은 `aria-pressed`로 상태를 알린다.
 */
export function Segmented<K extends string>({ items, value, onChange, label, className }: {
  items: { key: K; label: ReactNode; disabled?: boolean; title?: string }[];
  value: K;
  onChange: (key: K) => void;
  label: string;
  className?: string;
}) {
  return (
    <div role="group" aria-label={label} className={cn("inline-flex shrink-0 rounded-[var(--radius-control)] bg-subtle p-0.5", className)}>
      {items.map((item) => {
        const selected = item.key === value;
        return (
          <button
            key={item.key}
            type="button"
            aria-pressed={selected}
            disabled={item.disabled}
            title={item.title}
            onClick={() => onChange(item.key)}
            className={cn(
              "flex items-center gap-1.5 rounded-[calc(var(--radius-control)-2px)] px-2.5 py-1 text-[13px] font-medium whitespace-nowrap transition-colors disabled:cursor-not-allowed disabled:text-ink-decor",
              selected ? "bg-surface text-ink shadow-sm" : "text-ink-muted hover:text-ink",
            )}
          >
            {item.label}
          </button>
        );
      })}
    </div>
  );
}
