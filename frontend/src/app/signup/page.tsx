import type { Metadata } from "next";

import { AuthLayout } from "@/components/app-shell/AuthLayout";
import { SignupForm } from "@/features/auth/components/SignupForm";

export const metadata: Metadata = {
  title: "회원가입 | DocFlow",
};

export default function SignupPage() {
  return (
    <AuthLayout title="회원가입" subtitle="관리자에게 받은 초대코드로 가입합니다.">
      <SignupForm />
    </AuthLayout>
  );
}
