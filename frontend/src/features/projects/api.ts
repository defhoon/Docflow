import { apiRequest } from "@/lib/api-client";

import type { CreateProjectInput, Project, ProjectSummary } from "./types";

export function createProject(input: CreateProjectInput): Promise<Project> {
  return apiRequest<Project>("/api/projects", {
    method: "POST",
    body: { name: input.name, description: input.description },
  });
}

/** 서버 정렬(`created_at DESC, id DESC`)을 그대로 사용한다. */
export function fetchProjects(): Promise<ProjectSummary[]> {
  return apiRequest<ProjectSummary[]>("/api/projects");
}

export function fetchProject(projectId: string): Promise<Project> {
  return apiRequest<Project>(`/api/projects/${encodeURIComponent(projectId)}`);
}
