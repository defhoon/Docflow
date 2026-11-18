import { cn } from "./cn";

/**
 * 글자 수 안내(현재/최대). 최대를 넘으면 위험 색으로 바꾼다. 스크린리더에는 입력란과 연결된 설명으로만 읽히도록
 * `aria-live`를 쓰지 않는다(입력할 때마다 읽히지 않게).
 */
export function CharCount({ id, length, max, className }: { id?: string; length: number; max: number; className?: string }) {
  const over = length > max;
  return (
    <span id={id} className={cn("text-xs tabular-nums", over ? "font-medium text-danger-700" : "text-ink-faint", className)}>
      {length.toLocaleString("ko-KR")} / {max.toLocaleString("ko-KR")}자
    </span>
  );
}
