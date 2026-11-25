import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { Activity } from "../types";
import { ActivityFeed } from "./ActivityFeed";

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh: vi.fn() }),
}));

const LONG_TITLE = "요구사항 명세서 — 긴 제목도 활동 목록에서 한 줄로 줄여 반복되지 않는지 확인하는 문서";

function activity(id: number, action: Activity["action"], versionNumber: number | null): Activity {
  return {
    id,
    action,
    actor: { id: 1, displayName: "김오너" },
    project: { id: 7, name: "기획 프로젝트" },
    document: { id: 3, title: LONG_TITLE },
    versionNumber,
    createdAt: new Date().toISOString(),
  };
}

describe("ActivityFeed (#50)", () => {
  it("행동 문장과 대상 문서를 분리하고, 문서 제목은 한 줄 링크(title로 전체 제목)로 표시한다", async () => {
    const load = vi.fn().mockResolvedValue([activity(2, "VERSION_CREATED", 2), activity(1, "DOCUMENT_DESCRIPTION_UPDATED", null)]);
    render(<ActivityFeed load={load} loadKey="k" showProject />);

    expect(await screen.findByText(/새 버전 v2을 올렸습니다/)).toBeInTheDocument();
    expect(screen.getByText(/문서 설명을 수정했습니다/)).toBeInTheDocument();
    // 링크 이름은 문서 제목(+ 버전, SPEC-18)이다.
    const links = screen.getAllByTitle(LONG_TITLE);
    expect(links).toHaveLength(2);
    expect(links[0]).toHaveTextContent(`${LONG_TITLE}· v2`);
    // SPEC-18: 버전이 있으면 그 버전을 연 문서 화면으로 이동한다
    expect(links[0]).toHaveAttribute("href", "/projects/7/documents/3?v=2");
    expect(links[0]).toHaveAttribute("title", LONG_TITLE);
    // 행동 문장 안에는 문서 제목을 반복하지 않는다.
    expect(screen.getByText(/새 버전 v2을 올렸습니다/).textContent).not.toContain(LONG_TITLE);
    expect(screen.getAllByRole("link", { name: "기획 프로젝트" })[0]).toHaveAttribute("href", "/projects/7");
  });

  it("문서 화면(showDocument=false)에서는 문서 링크를 표시하지 않는다", async () => {
    const load = vi.fn().mockResolvedValue([activity(1, "FEEDBACK_CREATED", 1)]);
    render(<ActivityFeed load={load} loadKey="k2" showDocument={false} />);

    expect(await screen.findByText(/v1에 피드백을 남겼습니다/)).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: LONG_TITLE })).not.toBeInTheDocument();
  });
});
