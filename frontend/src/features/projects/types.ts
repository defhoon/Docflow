/** Backend `ProjectSummaryResponse` (`GET /api/projects`) */
export type ProjectSummary = {
  id: number;
  name: string;
  createdAt: string;
};

/** Backend `ProjectResponse` (`POST /api/projects`, `GET /api/projects/{projectId}`) */
export type Project = {
  id: number;
  name: string;
  description: string | null;
  owner: {
    id: number;
    displayName: string;
  };
  createdAt: string;
  updatedAt: string;
};

/** 생성 요청 본문. Owner는 서버가 세션 사용자로 정하므로 보내지 않는다. */
export type CreateProjectInput = {
  name: string;
  description: string | null;
};
