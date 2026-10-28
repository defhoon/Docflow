import type { NextConfig } from "next";

// Backend(Spring Boot) 주소. 서버 측에서만 사용하며 브라우저에는 노출되지 않는다.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  experimental: {
    // rewrite 프록시가 요청 본문을 복제할 때의 상한 (기본 10MB, 초과분은 오류 없이 잘린다).
    // '25mb' = 26,214,400바이트로, Backend multipart 상한(파일 20MiB·요청 21MiB)을 넘는 요청도 잘리지 않고
    // Backend에 도착해 413으로 판단되게 한다 (SPEC-03 §5.6, 결정 D1). experimental 옵션이므로 Next.js 업그레이드 시 재확인한다.
    proxyClientMaxBodySize: "25mb",
  },
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async rewrites() {
    return [
      {
        // 개발용 연결 확인 화면에서만 사용한다. Actuator는 health만 프록시한다.
        source: "/backend/health",
        destination: `${backendUrl}/actuator/health`,
      },
      {
        // Backend API. 브라우저는 동일 출처(Next.js)만 호출하므로 세션·CSRF 쿠키가 이 출처에 저장된다.
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
