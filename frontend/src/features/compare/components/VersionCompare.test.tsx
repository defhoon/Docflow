import { act, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
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
const refresh = vi.fn();
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("@/features/documents/api", () => ({
  fetchDocument: () => Promise.resolve({ id: 3, title: "사업 계획서", status: "ACTIVE" }),
}));
vi.mock("@/features/documents/version-files", () => ({
  fetchDocumentVersions: () =>
    Promise.resolve([
      { versionNumber: 3, originalFilename: "v3.pdf", latest: true },
      { versionNumber: 2, originalFilename: "v2.pdf", latest: false },
      { versionNumber: 1, originalFilename: "v1.pdf", latest: false },
    ]),
}));
const fetchAiSettings = vi.fn();
const fetchChangeSummary = vi.fn();
vi.mock("@/features/analysis/api", () => ({
  fetchAiSettings: (...args: unknown[]) => fetchAiSettings(...args),
  fetchChangeSummary: (...args: unknown[]) => fetchChangeSummary(...args),
  requestChangeSummary: vi.fn(),
}));
// PDF 그리기는 PdfComparison 테스트에서 검증한다. 여기서는 전달되는 값만 본다.
const pdfProps = vi.fn();
vi.mock("./PdfComparison", () => ({
  PdfComparison: (props: { navRequest: { changeId: number } | null; mobileSide: string }) => {
    pdfProps(props);
    return <div data-testid="pdf">pdf nav {props.navRequest?.changeId ?? "-"}</div>;
  },
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
    counts: { added: 1, deleted: 1, modified: 1 },
    changes: [
      { id: 0, type: "MODIFIED", base: side("1,200만원"), target: side("1,500만원") },
      { id: 1, type: "ADDED", base: empty, target: side("보안 점검") },
      { id: 2, type: "DELETED", base: { text: "표 안의 문구", pages: [1], boxes: [], location: "NONE" as const }, target: empty },
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

function aiState(overrides = {}) {
  return { status: "NONE", errorCode: null, retryable: false, aiEnabled: true, supported: true, result: null, model: null, updatedAt: null, completedAt: null, ...overrides };
}

describe("VersionCompare 집중 모드 (SPEC-12, SPEC-17)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    search = new URLSearchParams("base=1&target=2");
    fetchAiSettings.mockResolvedValue({ enabled: false, available: false });
    fetchChangeSummary.mockResolvedValue(aiState({ aiEnabled: false }));
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("AC-CW-01: 선택 문맥이 없으면 최신 버전과 직전 버전을 PDF 모드로 비교한다", async () => {
    search = new URLSearchParams("");
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    expect(await screen.findByTestId("pdf")).toBeInTheDocument();
    expect(fetchComparison).toHaveBeenCalledWith("7", "3", 2, 3);
    expect(screen.getByRole("button", { name: "PDF" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("link", { name: "문서로 돌아가기" })).toHaveAttribute("href", "/projects/7/documents/3?v=3");
  });

  it("AC-CW-02: 사용자가 고른 버전·방향(역방향 포함)과 보기 방식을 그대로 쓴다", async () => {
    search = new URLSearchParams("base=3&target=1&mode=text");
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    expect(await screen.findByText("변경 없는 문단 1개")).toBeInTheDocument();
    expect(fetchComparison).toHaveBeenCalledWith("7", "3", 3, 1);
    expect(screen.getByRole("button", { name: "텍스트" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.queryByTestId("pdf")).not.toBeInTheDocument();
  });

  it("개수·텍스트 나란히 보기에 색 외의 표시를 두고, 번호가 원본 줄 번호가 아님을 밝힌다", async () => {
    search = new URLSearchParams("base=1&target=2&mode=text");
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    expect(await screen.findByText("+ 추가 1")).toBeInTheDocument();
    expect(screen.getByText("~ 수정 1")).toBeInTheDocument();
    expect(screen.getByText("수정 전:")).toBeInTheDocument();
    expect(screen.getByText("수정 후:")).toBeInTheDocument();
    expect(screen.getAllByText("추가:").length).toBeGreaterThan(0);
    expect(screen.getByText(/원본 PDF의 줄 번호가 아니라/)).toBeInTheDocument();
    expect(screen.getByTitle("원본 줄 번호가 아니라 변경 구간 순서입니다")).toHaveTextContent("구간 1");
  });

  it("AC-CW-03: 변경 목록에서 고르면 위치 이동을 요청하고, 이전/다음으로 넘어가며, 종류로 거른다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    const list = within(await screen.findByRole("list", { name: "변경 목록" }));
    fireEvent.click(list.getByRole("button", { name: /1,500만원/ }));
    expect(screen.getByTestId("pdf")).toHaveTextContent("pdf nav 0");
    expect(list.getByRole("button", { name: /1,500만원/ })).toHaveAttribute("aria-current", "true");
    expect(screen.getByText("1 / 3")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다음 변경" }));
    expect(screen.getByTestId("pdf")).toHaveTextContent("pdf nav 1");
    fireEvent.click(screen.getByRole("button", { name: "추가 1" }));
    expect(list.getAllByRole("button")).toHaveLength(1);
    expect(list.getByRole("button", { name: /보안 점검/ })).toBeInTheDocument();
  });

  it("AC-CW-05: PDF에서 위치를 찾지 못한 변경은 임의 위치에 표시하지 않고 안내하며 텍스트 비교로 보낼 수 있다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    const list = within(await screen.findByRole("list", { name: "변경 목록" }));
    expect(list.getByRole("button", { name: /표 안의 문구/ })).toHaveTextContent("PDF 위치 없음");
    fireEvent.click(list.getByRole("button", { name: /표 안의 문구/ }));
    expect(screen.getByText("변경 #3은 PDF에서 위치를 찾지 못해 표시하지 않았습니다.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "텍스트 비교에서 보기" }));
    expect(replace).toHaveBeenCalledWith("/projects/7/documents/3/compare?base=1&target=2&mode=text");
  });

  it("AC-CW-06: AI가 꺼져 있으면 AI 변경 요약 탭이 없고, 켜져 있으면 요약의 변경 번호로 이동한다", async () => {
    fetchComparison.mockResolvedValue(response);
    const { unmount } = render(<VersionCompare />);
    await screen.findByRole("list", { name: "변경 목록" });
    await waitFor(() => expect(fetchAiSettings).toHaveBeenCalled());
    expect(screen.queryByRole("tab", { name: "AI 변경 요약" })).not.toBeInTheDocument();
    unmount();

    fetchAiSettings.mockResolvedValue({ enabled: true, available: true });
    fetchChangeSummary.mockResolvedValue(
      aiState({
        status: "COMPLETED",
        model: "server-model",
        result: {
          overview: "예산이 늘었습니다.",
          added: [{ text: "보안 점검 추가", changeIds: [1] }],
          deleted: [],
          modified: [],
          keyChanges: [],
          significance: { level: "LOW", reason: "" },
          meta: null,
        },
      }),
    );
    render(<VersionCompare />);
    fireEvent.click(await screen.findByRole("tab", { name: "AI 변경 요약" }));
    expect(await screen.findByText("예산이 늘었습니다.")).toBeInTheDocument();
    expect(screen.getByText(/server-model/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "변경 2로 이동" }));
    expect(screen.getByTestId("pdf")).toHaveTextContent("pdf nav 1");
  });

  it("AI 변경 요약 요청 전에는 외부 전송을 안내한다", async () => {
    fetchComparison.mockResolvedValue(response);
    fetchAiSettings.mockResolvedValue({ enabled: true, available: true });
    fetchChangeSummary.mockResolvedValue(aiState());
    render(<VersionCompare />);
    fireEvent.click(await screen.findByRole("tab", { name: "AI 변경 요약" }));
    expect(await screen.findByText(/변경된 텍스트가 외부 AI 서비스로 전송됩니다/)).toBeInTheDocument();
  });

  it("변경 패널을 접고 펼친다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    await screen.findByRole("list", { name: "변경 목록" });
    fireEvent.click(screen.getByRole("button", { name: "변경 패널 접기" }));
    expect(screen.queryByRole("list", { name: "변경 목록" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: /변경 패널/ }));
    expect(screen.getByRole("list", { name: "변경 목록" })).toBeInTheDocument();
  });

  it("변경이 없으면 같다고 안내한다", async () => {
    fetchComparison.mockResolvedValue({ ...response, result: { ...response.result, identical: true, changes: [], blocks: [], counts: { added: 0, deleted: 0, modified: 0 } } });
    render(<VersionCompare />);
    expect(await screen.findByText(/본문 텍스트가 같습니다/)).toBeInTheDocument();
  });

  it("텍스트를 읽을 수 없는 버전·Office 문서는 사유를 안내한다", async () => {
    fetchComparison.mockRejectedValueOnce(new ApiError("http", 422, "COMPARE_TEXT_UNAVAILABLE", "x", [{ field: "target", message: "NO_TEXT" }]));
    const { unmount } = render(<VersionCompare />);
    expect(await screen.findByText(/이후 버전에서 비교할 텍스트를 읽을 수 없습니다. 텍스트가 없는 PDF/)).toBeInTheDocument();
    unmount();
    fetchComparison.mockRejectedValueOnce(new ApiError("http", 422, "COMPARE_UNSUPPORTED_TYPE", "x"));
    render(<VersionCompare />);
    expect(await screen.findByText(/버전 비교는 PDF 문서만 지원합니다/)).toBeInTheDocument();
  });

  it("버전을 바꾸거나 서로 바꾸면 주소를 바꾼다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    await screen.findByRole("list", { name: "변경 목록" });
    fireEvent.click(screen.getByRole("button", { name: "서로 바꾸기" }));
    expect(replace).toHaveBeenCalledWith("/projects/7/documents/3/compare?base=2&target=1");
    fireEvent.change(screen.getByLabelText("이후 버전"), { target: { value: "3" } });
    expect(replace).toHaveBeenLastCalledWith("/projects/7/documents/3/compare?base=1&target=3");
  });

  it("같은 버전을 고르면 요청하지 않는다", async () => {
    search = new URLSearchParams("base=2&target=2");
    render(<VersionCompare />);
    expect(await screen.findByRole("alert")).toHaveTextContent("서로 다른 두 버전을 선택해 주세요.");
    expect(fetchComparison).not.toHaveBeenCalled();
  });

  it("좁은 화면에서 변경을 고르면 위치가 있는 문서 쪽으로 전환한다", async () => {
    fetchComparison.mockResolvedValue(response);
    render(<VersionCompare />);
    await screen.findByRole("list", { name: "변경 목록" });
    fireEvent.click(screen.getByRole("button", { name: "변경 3" }));
    await act(async () => {
      fireEvent.click(within(screen.getByRole("list", { name: "변경 목록" })).getByRole("button", { name: /표 안의 문구/ }));
    });
    expect(pdfProps).toHaveBeenLastCalledWith(expect.objectContaining({ mobileSide: "base" }));
  });
});
