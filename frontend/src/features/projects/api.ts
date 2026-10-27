import { apiRequest } from "@/lib/api-client";

import type { CreateProjectInput, Member, Project, ProjectSummary } from "./types";

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

/** 구성원 목록: Owner가 첫 항목, MEMBER는 추가 순 (SPEC-02 §6.3). */
export function fetchMembers(projectId: string): Promise<Member[]> {
  return apiRequest<Member[]>(`/api/projects/${encodeURIComponent(projectId)}/members`);
}

/** 이메일로 가입 사용자를 MEMBER로 추가한다. 역할·사용자 ID는 보내지 않는다. */
export function addMember(projectId: string, email: string): Promise<Member> {
  return apiRequest<Member>(`/api/projects/${encodeURIComponent(projectId)}/members`, {
    method: "POST",
    body: { email },
  });
}

/** 구성원 제거는 표시 이름이 아니라 `userId`로 대상을 정한다. */
export function removeMember(projectId: string, userId: number): Promise<void> {
  return apiRequest<void>(`/api/projects/${encodeURIComponent(projectId)}/members/${userId}`, {
    method: "DELETE",
  });
}
