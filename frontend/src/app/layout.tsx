import type { Metadata } from "next";

import { AuthProvider } from "@/features/auth/AuthProvider";

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
