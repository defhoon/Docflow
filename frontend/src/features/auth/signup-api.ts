import { apiRequest } from "@/lib/api-client";

import type { AuthUser } from "./types";

export type SignupInput = { inviteCode: string; email: string; password: string; displayName: string };

/** 초대코드 회원가입 (#26). 성공해도 로그인하지 않으며 로그인 화면으로 이동한다. */
export function signup(input: SignupInput): Promise<AuthUser> {
  return apiRequest<AuthUser>("/api/auth/signup", { method: "POST", body: input });
}
