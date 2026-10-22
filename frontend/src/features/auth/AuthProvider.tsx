"use client";

import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from "react";

import { fetchCurrentUser, logout } from "./api";
import type { AuthStatus, AuthUser } from "./types";

type AuthContextValue = {
  status: AuthStatus;
  user: AuthUser | null;
  /** 로그인 상태였다가 서버 세션이 사라진 경우 true (세션 만료 안내용) */
  sessionExpired: boolean;
  refresh: () => Promise<void>;
  signIn: (user: AuthUser) => void;
  signOut: () => Promise<void>;
};

const AuthContext = createContext<AuthContextValue | null>(null);

/**
 * 인증 상태는 서버 응답(`GET /api/auth/me`)으로만 판단한다. 브라우저 저장소에 사용자 정보나
 * 토큰을 저장하지 않는다.
 */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [status, setStatus] = useState<AuthStatus>("loading");
  const [user, setUser] = useState<AuthUser | null>(null);
  const [sessionExpired, setSessionExpired] = useState(false);
  const statusRef = useRef<AuthStatus>("loading");

  const apply = useCallback((next: AuthStatus, nextUser: AuthUser | null) => {
    statusRef.current = next;
    setStatus(next);
    setUser(nextUser);
  }, []);

  const handleCurrentUser = useCallback(
    (current: AuthUser | null) => {
      if (current) {
        setSessionExpired(false);
        apply("authenticated", current);
        return;
      }
      if (statusRef.current === "authenticated") {
        setSessionExpired(true);
      }
      apply("anonymous", null);
    },
    [apply],
  );

  const handleFailure = useCallback(() => apply("error", null), [apply]);

  const refresh = useCallback(
    () => fetchCurrentUser().then(handleCurrentUser, handleFailure),
    [handleCurrentUser, handleFailure],
  );

  const signIn = useCallback(
    (signedIn: AuthUser) => {
      setSessionExpired(false);
      apply("authenticated", signedIn);
    },
    [apply],
  );

  const signOut = useCallback(async () => {
    await logout();
    setSessionExpired(false);
    apply("anonymous", null);
  }, [apply]);

  useEffect(() => {
    let active = true;
    fetchCurrentUser().then(
      (current) => {
        if (active) {
          handleCurrentUser(current);
        }
      },
      () => {
        if (active) {
          handleFailure();
        }
      },
    );
    return () => {
      active = false;
    };
  }, [handleCurrentUser, handleFailure]);

  // 장시간 자리를 비운 뒤 돌아오면 세션 만료 여부를 다시 확인한다.
  useEffect(() => {
    const onFocus = () => {
      if (statusRef.current === "authenticated") {
        void refresh();
      }
    };
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [refresh]);

  const value = useMemo(
    () => ({ status, user, sessionExpired, refresh, signIn, signOut }),
    [status, user, sessionExpired, refresh, signIn, signOut],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth must be used within AuthProvider");
  }
  return context;
}
