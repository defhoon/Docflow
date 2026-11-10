export type AuthUser = {
  id: number;
  email: string;
  displayName: string;
  /** 운영 관리자(초대코드 발급) 여부 (#26). 화면 표시용이며 권한은 Backend가 확인한다. */
  admin?: boolean;
};

export type AuthStatus = "loading" | "authenticated" | "anonymous" | "error";
