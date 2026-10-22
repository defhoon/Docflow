import type { NextConfig } from "next";

// Backend(Spring Boot) 주소. 서버 측에서만 사용하며 브라우저에는 노출되지 않는다.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
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
