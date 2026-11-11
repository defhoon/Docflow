/** 조건부 클래스 이름 결합. 거짓 값은 무시한다. */
export function cn(...parts: Array<string | false | null | undefined>): string {
  return parts.filter(Boolean).join(" ");
}
