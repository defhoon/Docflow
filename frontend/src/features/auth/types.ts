export type AuthUser = {
  id: number;
  email: string;
  displayName: string;
};

export type AuthStatus = "loading" | "authenticated" | "anonymous" | "error";
