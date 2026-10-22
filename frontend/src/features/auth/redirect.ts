/**
 * 로그인 후 이동할 경로. 같은 사이트의 내부 경로만 허용하여 open redirect를 막는다.
 */
export function safeNextPath(value: string | null | undefined): string {
  if (!value || !value.startsWith("/") || value.startsWith("//") || value.startsWith("/\\")) {
    return "/";
  }
  return value;
}
