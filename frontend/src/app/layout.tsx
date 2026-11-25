import type { Metadata } from "next";

import { AuthProvider } from "@/features/auth/AuthProvider";

// Pretendard 동적 서브셋(자체 호스팅, OFL). 화면에 쓰인 글자 조각만 내려받는다 (ADR-0012).
import "./fonts/pretendard/pretendard.css";
import "./globals.css";

export const metadata: Metadata = {
  title: "DocFlow",
  description: "문서 버전·피드백·최종 확정 협업",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="antialiased">
      <body className="min-h-dvh">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
