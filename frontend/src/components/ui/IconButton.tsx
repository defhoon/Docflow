import Link from "next/link";
import type { ButtonHTMLAttributes, ReactNode } from "react";

import { cn } from "./cn";

type Side = "top" | "bottom" | "left" | "right";

const SIDE: Record<Side, string> = {
  top: "bottom-full left-1/2 mb-1.5 -translate-x-1/2",
  bottom: "top-full left-1/2 mt-1.5 -translate-x-1/2",
  left: "right-full top-1/2 mr-1.5 -translate-y-1/2",
  right: "left-full top-1/2 ml-1.5 -translate-y-1/2",
};

/**
 * 마우스를 올리거나 키보드로 포커스하면 보이는 툴팁 (#68). 이름은 대상 요소의 `aria-label`이 제공하므로 툴팁 자체는
 * 화면 읽기 프로그램에서 숨긴다(중복 낭독 방지). 감싸는 요소에 `group/tip`이 있어야 한다.
 */
export function TooltipText({ children, side = "bottom" }: { children: ReactNode; side?: Side }) {
  return (
    <span
      aria-hidden="true"
      className={cn(
        "pointer-events-none absolute z-50 hidden rounded-md bg-ink px-2 py-1 text-xs font-medium whitespace-nowrap text-white shadow-[var(--shadow-overlay)]",
        "group-hover/tip:block group-has-[:focus-visible]/tip:block",
        SIDE[side],
      )}
    >
      {children}
    </span>
  );
}

const BASE =
  "inline-flex size-8 shrink-0 items-center justify-center rounded-md text-ink-muted transition-colors hover:bg-subtle hover:text-ink disabled:cursor-not-allowed disabled:text-ink-decor disabled:hover:bg-transparent";

type IconButtonProps = Omit<ButtonHTMLAttributes<HTMLButtonElement>, "aria-label" | "children"> & {
  /** 접근 가능한 이름이자 툴팁 문구. 필수. */
  label: string;
  icon: ReactNode;
  tooltipSide?: Side;
  /** 눌린 상태를 가지는 전환 버튼이면 지정한다. */
  pressed?: boolean;
};

/** 아이콘만 있는 버튼 (#68). 이름(`aria-label`)과 툴팁을 항상 함께 둔다. */
export function IconButton({ label, icon, tooltipSide = "bottom", pressed, className, type = "button", ...props }: IconButtonProps) {
  return (
    <span className="group/tip relative inline-flex">
      <button
        type={type}
        aria-label={label}
        aria-pressed={pressed}
        className={cn(BASE, pressed && "bg-brand-50 text-brand-700 hover:bg-brand-50 hover:text-brand-700", className)}
        {...props}
      >
        {icon}
      </button>
      <TooltipText side={tooltipSide}>{label}</TooltipText>
    </span>
  );
}

/** 아이콘만 있는 링크. */
export function IconLink({ href, label, icon, tooltipSide = "bottom", className }: {
  href: string;
  label: string;
  icon: ReactNode;
  tooltipSide?: Side;
  className?: string;
}) {
  return (
    <span className="group/tip relative inline-flex">
      <Link href={href} aria-label={label} className={cn(BASE, className)}>
        {icon}
      </Link>
      <TooltipText side={tooltipSide}>{label}</TooltipText>
    </span>
  );
}
