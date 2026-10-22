import type { Metadata } from "next";

import { AuthProvider } from "@/features/auth/AuthProvider";

import "./globals.css";

export const metadata: Metadata = {
  title: "DocFlow",
  description: "Document Review Workflow Management System",
};

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html lang="ko" className="h-full antialiased">
      <body className="flex min-h-full flex-col">
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
