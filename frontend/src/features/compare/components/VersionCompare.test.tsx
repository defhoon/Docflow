import { fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { CompareResponse } from "../types";
import { VersionCompare } from "./VersionCompare";

const replace = vi.fn();
let search = new URLSearchParams("base=1&target=2");
const fetchComparison = vi.fn();

vi.mock("next/navigation", () => ({
  useParams: () => ({ projectId: "7", documentId: "3" }),
  useSearchParams: () => search,
  useRouter: () => ({ replace }),
}));
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh: vi.fn() }) }));
vi.mock("@/features/documents/api", () => ({
  fetchDocument: () => Promise.resolve({ id: 3, title: "사업 계획서", status: "ACTIVE" }),
}));
vi.mock("@/features/documents/version-files", () => ({
  fetchDocumentVersions: () =>
    Promise.resolve([
      { versionNumber: 2, originalFilename: "v2.pdf", latest: true },
      { versionNumber: 1, originalFilename: "v1.pdf", latest: false },
    ]),
}));
vi.mock("../api", () => ({
  fetchComparison: (...args: unknown[]) => fetchComparison(...args),
  compareHref: (p: string, d: string, b: number, t: number) => `/projects/${p}/documents/${d}/compare?base=${b}&target=${t}`,
}));

const info = (versionNumber: number) => ({ versionNumber, originalFilename: `v${versionNumber}.pdf`, pageCount: 1, pages: [{ width: 595, height: 842, rotation: 0 }] });
const side = (text: string) => ({ text, pages: [1], boxes: [{ page: 1, rect: [10, 10, 50, 22] as [number, number, number, number] }], location: "FULL" as const });
const empty = { text: "", pages: [], boxes: [], location: "EMPTY" as const };

const response: CompareResponse = {
  base: info(1),
  target: info(2),
  result: {
    identical: false,
    counts: { added: 1, deleted: 0, modified: 1 },
    changes: [
      { id: 0, type: "MODIFIED", base: side("1,200만원"), target: side("1,500만원") },
      { id: 1, type: "ADDED", base: empty, target: side("보안 점검") },
    ],
    blocks: [
      { type: "EQUAL", paragraphCount: 1, firstText: "사업 계획서", lastText: "사업 계획서", base: [], target: [] },
      {
        type: "CHANGED",
        paragraphCount: 1,
        firstText: null,
        lastText: null,
        base: [
          { op: "EQUAL", text: "예산은", changeId: null },
          { op: "MODIFY", text: " 1,200만원", changeId: 0 },
        ],
        target: [
          { op: "EQUAL", text: "예산은", changeId: null },
          { op: "MODIFY", text: " 1,500만원", changeId: 0 },
          { op: "INSERT", text: " 보안 점검", changeId: 1 },
        ],
      },
    ],
    coarseBlocks: 0,
  },
};

describe("VersionCompare (SPEC-12)", () => {
  beforeEach(() => {
    search = new URLSearchParams("base=1&target=2");
    fetchComparison.mockReset();
    replace.mockReset();
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("두 버전을 비교해 개수·나란히 보기·변경 목록을 표시하고, 색 외의 표시도 둔다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    expect(await screen.findByText("+ 추가 1")).toBeInTheDocument();
    expect(screen.getByText("~ 수정 1")).toBeInTheDocument();
    expect(fetchComparison).toHaveBeenCalledWith("7", "3", 1, 2);
    expect(screen.getByText("변경 없는 문단 1개")).toBeInTheDocument();
    expect(screen.getByText("수정 전:")).toBeInTheDocument();
    expect(screen.getByText("수정 후:")).toBeInTheDocument();
    expect(screen.getAllByText("추가:").length).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { name: "변경 2개" })).toBeInTheDocument();
  });

  it("변경 목록에서 고르면 해당 구간으로 이동하고 다음 변경으로 넘어갈 수 있다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    const list = within(await screen.findByRole("complementary", { name: "변경 2개" }));
    const item = list.getByRole("button", { name: /1,500만원/ });
    fireEvent.click(item);
    expect(Element.prototype.scrollIntoView).toHaveBeenCalled();
    expect(item).toHaveAttribute("aria-current", "true");
    fireEvent.click(screen.getByRole("button", { name: "다음 변경" }));
    expect(list.getByRole("button", { name: /보안 점검/ })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("button", { name: "다음 변경" })).toBeDisabled();
  });

  it("변경이 없으면 같다고 안내한다", async () => {
    fetchComparison.mockResolvedValue({ ...response, result: { ...response.result, identical: true, changes: [], blocks: [], counts: { added: 0, deleted: 0, modified: 0 } } });
    render(<VersionCompare />);
    expect(await screen.findByText(/본문 텍스트가 같습니다/)).toBeInTheDocument();
  });

  it("텍스트를 읽을 수 없는 버전은 어느 쪽인지와 사유를 안내한다", async () => {
    fetchComparison.mockRejectedValue(new ApiError("http", 422, "COMPARE_TEXT_UNAVAILABLE", "x", [{ field: "target", message: "NO_TEXT" }]));
    render(<VersionCompare />);
    expect(await screen.findByText(/이후 버전에서 비교할 텍스트를 읽을 수 없습니다. 텍스트가 없는 PDF/)).toBeInTheDocument();
  });

  it("버전을 바꾸면 주소를 바꾼다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    await screen.findByText("+ 추가 1");
    fireEvent.click(screen.getByRole("button", { name: "서로 바꾸기" }));
    expect(replace).toHaveBeenCalledWith("/projects/7/documents/3/compare?base=2&target=1");
  });

  it("같은 버전을 고르면 요청하지 않는다", async () => {
    search = new URLSearchParams("base=2&target=2");
    render(<VersionCompare />);
    expect(await screen.findByRole("alert")).toHaveTextContent("서로 다른 두 버전을 선택해 주세요.");
    expect(fetchComparison).not.toHaveBeenCalled();
  });
});
