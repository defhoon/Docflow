import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { addMember, createProject, fetchMembers, fetchProject, fetchProjects, removeMember } from "./api";

const fetchMock = vi.fn();

function jsonResponse(status: number, body: unknown) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

describe("projects api", () => {
  beforeEach(() => {
    fetchMock.mockReset();
    vi.stubGlobal("fetch", fetchMock);
    document.cookie = "XSRF-TOKEN=token-1";
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
  });

  it("createProject는 POST /api/projects에 이름·설명만 CSRF 헤더와 함께 보낸다", async () => {
    const created = { id: 7, name: "DocFlow" };
    fetchMock.mockResolvedValue(jsonResponse(201, created));

    await expect(createProject({ name: "DocFlow", description: null })).resolves.toEqual(created);

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/projects");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ name: "DocFlow", description: null });
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
    expect(init.headers["Content-Type"]).toBe("application/json");
  });

  it("fetchProjects는 GET /api/projects 응답 순서를 그대로 반환한다", async () => {
    const projects = [
      { id: 3, name: "c", createdAt: "2026-01-03T00:00:00Z" },
      { id: 1, name: "a", createdAt: "2026-01-01T00:00:00Z" },
    ];
    fetchMock.mockResolvedValue(jsonResponse(200, projects));

    await expect(fetchProjects()).resolves.toEqual(projects);
    expect(fetchMock.mock.calls[0][0]).toBe("/api/projects");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("fetchProject는 GET /api/projects/{projectId}를 호출하고 경로 값을 인코딩한다", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, { id: 5 }));

    await fetchProject("5");
    await fetchProject("../auth/me");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/projects/5");
    expect(fetchMock.mock.calls[1][0]).toBe("/api/projects/..%2Fauth%2Fme");
  });

  it("오류 응답은 상태·코드·필드 오류를 담은 ApiError가 된다", async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(400, {
        code: "VALIDATION_FAILED",
        message: "입력값이 올바르지 않습니다.",
        fieldErrors: [{ field: "name", message: "프로젝트 이름을 입력해 주세요." }],
      }),
    );

    await expect(createProject({ name: "x", description: null })).rejects.toMatchObject({
      status: 400,
      code: "VALIDATION_FAILED",
      fieldErrors: [{ field: "name", message: "프로젝트 이름을 입력해 주세요." }],
    });
  });

  it("fetchMembers는 GET /api/projects/{projectId}/members를 호출한다", async () => {
    fetchMock.mockImplementation(async () => jsonResponse(200, []));

    await fetchMembers("7");

    expect(fetchMock.mock.calls[0][0]).toBe("/api/projects/7/members");
    expect(fetchMock.mock.calls[0][1].method).toBe("GET");
  });

  it("addMember는 POST 본문에 email만 CSRF 헤더와 함께 보낸다", async () => {
    fetchMock.mockImplementation(async () =>
      jsonResponse(201, { userId: 2, displayName: "Reviewer", role: "MEMBER", addedAt: "2026-01-01T00:00:00Z" }),
    );

    await addMember("7", "reviewer@docflow.local");

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/projects/7/members");
    expect(init.method).toBe("POST");
    expect(JSON.parse(init.body)).toEqual({ email: "reviewer@docflow.local" });
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
  });

  it("removeMember는 DELETE /api/projects/{projectId}/members/{userId}를 호출한다", async () => {
    fetchMock.mockImplementation(async () => new Response(null, { status: 204 }));

    await expect(removeMember("7", 2)).resolves.toBeUndefined();

    const [path, init] = fetchMock.mock.calls[0];
    expect(path).toBe("/api/projects/7/members/2");
    expect(init.method).toBe("DELETE");
    expect(init.body).toBeUndefined();
    expect(init.headers["X-XSRF-TOKEN"]).toBe("token-1");
  });
});
