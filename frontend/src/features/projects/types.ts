/** 프로젝트 안에서의 역할 (SPEC-02 §5.1). OWNER는 생성자, MEMBER는 Owner가 추가한 구성원이다. */
export type ProjectRole = "OWNER" | "MEMBER";

/** Backend `ProjectSummaryResponse` (`GET /api/projects`) */
export type ProjectSummary = {
  id: number;
  name: string;
  createdAt: string;
  myRole: ProjectRole;
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
  myRole: ProjectRole;
};

/** Backend `MemberResponse` (SPEC-02 §6.3). 이메일은 포함되지 않는다. `addedAt`은 화면에 표시하지 않는다. */
export type Member = {
  userId: number;
  displayName: string;
  role: ProjectRole;
  addedAt: string | null;
};

/** 생성 요청 본문. Owner는 서버가 세션 사용자로 정하므로 보내지 않는다. */
export type CreateProjectInput = {
  name: string;
  description: string | null;
};
