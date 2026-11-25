import { apiRequest } from "@/lib/api-client";

import type { HomeSummary } from "./types";

/** 홈 통합 조회 (SPEC-18, 읽기 전용). */
export function fetchHome(): Promise<HomeSummary> {
  return apiRequest<HomeSummary>("/api/home");
}
