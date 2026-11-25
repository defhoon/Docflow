"use client";

import { type KeyboardEvent, type PointerEvent, useRef } from "react";

import { cn } from "./cn";

/**
 * 패널 경계 너비 조절 손잡이 (SPEC-16·17). 끌거나, 포커스한 뒤 ←/→(Shift: 크게)로 조절한다. 값은 저장하지 않는다.
 * `edge`는 손잡이가 조절하는 패널의 위치다(왼쪽 패널이면 오른쪽으로 끌수록 넓어지고, 오른쪽 패널이면 반대).
 */
export function PaneResizer({ label, value, min, max, onChange, edge, className }: {
  label: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  edge: "left" | "right";
  className?: string;
}) {
  const start = useRef<{ x: number; value: number } | null>(null);
  const clamp = (next: number) => Math.round(Math.min(max, Math.max(min, next)));
  const direction = edge === "left" ? 1 : -1;

  function onPointerDown(event: PointerEvent<HTMLDivElement>) {
    event.preventDefault();
    event.currentTarget.setPointerCapture(event.pointerId);
    start.current = { x: event.clientX, value };
  }
  function onPointerMove(event: PointerEvent<HTMLDivElement>) {
    if (start.current) {
      onChange(clamp(start.current.value + (event.clientX - start.current.x) * direction));
    }
  }
  function onPointerUp(event: PointerEvent<HTMLDivElement>) {
    start.current = null;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) {
      event.currentTarget.releasePointerCapture(event.pointerId);
    }
  }
  function onKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    const step = event.shiftKey ? 48 : 16;
    if (event.key === "ArrowLeft" || event.key === "ArrowRight") {
      event.preventDefault();
      const sign = event.key === "ArrowRight" ? 1 : -1;
      onChange(clamp(value + sign * step * direction));
    } else if (event.key === "Home") {
      event.preventDefault();
      onChange(min);
    } else if (event.key === "End") {
      event.preventDefault();
      onChange(max);
    }
  }

  return (
    <div
      role="separator"
      aria-orientation="vertical"
      aria-label={label}
      aria-valuenow={value}
      aria-valuemin={min}
      aria-valuemax={max}
      tabIndex={0}
      onPointerDown={onPointerDown}
      onPointerMove={onPointerMove}
      onPointerUp={onPointerUp}
      onPointerCancel={onPointerUp}
      onKeyDown={onKeyDown}
      className={cn(
        "group relative z-10 w-1.5 shrink-0 cursor-col-resize touch-none bg-transparent outline-none",
        "focus-visible:bg-brand-500/40",
        className,
      )}
    >
      <span aria-hidden="true" className="absolute inset-y-0 left-1/2 w-px -translate-x-1/2 bg-line transition-colors group-hover:bg-brand-500" />
    </div>
  );
}
