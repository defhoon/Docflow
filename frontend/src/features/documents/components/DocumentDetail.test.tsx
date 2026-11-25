import { act, fireEvent, render as rtlRender, screen, waitFor, within } from "@testing-library/react";
import { useSyncExternalStore } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ToastProvider } from "@/components/ui/Toast";
import { ApiError } from "@/lib/api-client";

import type { DocumentDetail as DocumentDetailData } from "../types";
import type { DocumentVersionSummary } from "../version-files";
import { DocumentDetail } from "./DocumentDetail";

const refresh = vi.fn();
const fetchDocumentMock = vi.fn();
const fetchVersionsMock = vi.fn();
const fetchProjectMock = vi.fn();
const fetchAiSettingsMock = vi.fn();
const fetchSummaryMock = vi.fn();
const fetchFeedbacksMock = vi.fn();
const createFeedbackMock = vi.fn();
const createVersionMock = vi.fn();
const finalizeMock = vi.fn();
const downloadMock = vi.fn();
const saveBlobMock = vi.fn();
const replaceMock = vi.fn();
let params: { projectId: string; documentId: string };

// URL 검색어 저장소: router.replace가 바꾸면 useSearchParams를 쓰는 화면이 다시 그려진다.
let search = new URLSearchParams();
const listeners = new Set<() => void>();
function setSearch(value: string) {
  search = new URLSearchParams(value);
  listeners.forEach((listener) => listener());
}

vi.mock("next/navigation", () => ({
  useParams: () => params,
  usePathname: () => `/projects/${params.projectId}/documents/${params.documentId}`,
  useRouter: () => ({ replace: replaceMock }),
  useSearchParams: () =>
    useSyncExternalStore(
      (listener) => {
        listeners.add(listener);
        return () => listeners.delete(listener);
      },
      () => search,
    ),
}));
vi.mock("@/features/auth/AuthProvider", () => ({ useAuth: () => ({ refresh }) }));
vi.mock("../api", () => ({
  fetchDocument: (...args: unknown[]) => fetchDocumentMock(...args),
  createDocumentVersion: (...args: unknown[]) => createVersionMock(...args),
  updateDocumentDescription: vi.fn(),
}));
vi.mock("../api-finalize", () => ({ finalizeDocument: (...args: unknown[]) => finalizeMock(...args) }));
vi.mock("../version-files", () => ({
  fetchDocumentVersions: (...args: unknown[]) => fetchVersionsMock(...args),
  downloadVersionFile: (...args: unknown[]) => downloadMock(...args),
  saveBlob: (...args: unknown[]) => saveBlobMock(...args),
  fetchVersionPreview: vi.fn(() => new Promise(() => undefined)),
}));
vi.mock("@/features/projects/api", () => ({ fetchProject: (...args: unknown[]) => fetchProjectMock(...args) }));
vi.mock("@/features/analysis/api", () => ({
  fetchAiSettings: (...args: unknown[]) => fetchAiSettingsMock(...args),
  fetchVersionSummary: (...args: unknown[]) => fetchSummaryMock(...args),
  requestVersionSummary: vi.fn(),
}));
vi.mock("@/features/feedback/api", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/feedback/api")>()),
  fetchFeedbacks: (...args: unknown[]) => fetchFeedbacksMock(...args),
  createFeedback: (...args: unknown[]) => createFeedbackMock(...args),
}));
vi.mock("@/features/activity/api", () => ({ fetchDocumentActivities: vi.fn(() => Promise.resolve([])) }));
// 뷰어는 PdfDocumentViewer 테스트에서 따로 검증한다. 여기서는 어떤 버전을 보여 주는지만 확인한다.
vi.mock("./PdfDocumentViewer", () => ({
  PdfDocumentViewer: ({ version, pageRequest }: { version: DocumentVersionSummary; pageRequest: { page: number } | null }) => (
    <div data-testid="viewer">
      viewer v{version.versionNumber}
      {pageRequest && ` page ${pageRequest.page}`}
    </div>
  ),
}));

function version(versionNumber: number, latest = false, feedbackCount = 0): DocumentVersionSummary {
  return {
    versionNumber,
    originalFilename: `meeting-v${versionNumber}.pdf`,
    sizeBytes: 2048,
    uploadedBy: { id: 1, displayName: "Author" },
    uploadedAt: "2026-11-20T09:00:00Z",
    latest,
    versionNote: versionNumber === 1 ? "초안" : `v${versionNumber} 변경`,
    feedbackCount,
  };
}

const VERSIONS = [version(3, true, 2), version(2, false, 1), version(1)];

function detail(overrides: Partial<DocumentDetailData> = {}): DocumentDetailData {
  return {
    id: 4,
    title: "11월 정기 회의록",
    description: "결정 사항",
    status: "ACTIVE",
    createdBy: { id: 1, displayName: "Author" },
    createdAt: "2026-11-18T09:00:00Z",
    updatedAt: "2026-11-20T09:00:00Z",
    finalization: null,
    latestVersion: { versionNumber: 3, originalFilename: "meeting-v3.pdf", sizeBytes: 2048, uploadedAt: "2026-11-20T09:00:00Z", versionNote: null },
    ...overrides,
  };
}

const FINALIZED = detail({
  status: "FINALIZED",
  finalization: { versionNumber: 2, finalizedBy: { id: 1, displayName: "Owner" }, finalizedAt: "2026-11-21T09:00:00Z" },
});

function summaryState(overrides = {}) {
  return { status: "NONE", errorCode: null, retryable: false, aiEnabled: false, supported: true, result: null, model: null, updatedAt: null, completedAt: null, ...overrides };
}

function feedback(id: number, versionNumber: number, content: string) {
  return { id, versionNumber, author: { id: 2, displayName: "Reviewer" }, content, createdAt: "2026-11-20T10:00:00Z" };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((r) => {
    resolve = r;
  });
  return { promise, resolve };
}

function render() {
  return rtlRender(
    <ToastProvider>
      <DocumentDetail />
    </ToastProvider>,
  );
}

/** router.replace가 받은 URL을 검색어 저장소에 반영한다. */
function followReplace() {
  replaceMock.mockImplementation((url: string) => act(() => setSearch(url.split("?")[1] ?? "")));
}

describe("DocumentDetail 워크스페이스 (SPEC-16)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    params = { projectId: "1", documentId: "4" };
    setSearch("");
    followReplace();
    fetchDocumentMock.mockResolvedValue(detail());
    fetchVersionsMock.mockResolvedValue(VERSIONS);
    fetchProjectMock.mockResolvedValue({ id: 1, name: "2026 상반기 서비스 기획", myRole: "OWNER" });
    fetchAiSettingsMock.mockResolvedValue({ enabled: false, available: false, canManage: true });
    fetchSummaryMock.mockResolvedValue(summaryState());
    fetchFeedbacksMock.mockImplementation((_p: string, _d: string, v: number) => Promise.resolve([feedback(v * 10, v, `v${v} 의견`)]));
  });

  it("AC-WS-01: 협업 중 문서는 최신 버전을 기본 선택하고 피드백 탭이 기본이다", async () => {
    render();
    expect(await screen.findByRole("heading", { level: 1, name: "11월 정기 회의록" })).toBeInTheDocument();
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v3");
    expect(screen.getByText("v3 보는 중")).toBeInTheDocument();
    expect(screen.getByRole("tab", { name: /피드백/ })).toHaveAttribute("aria-selected", "true");
    expect(await screen.findByText("v3 의견")).toBeInTheDocument();
    expect(screen.getByLabelText("v3에 피드백 남기기")).toBeInTheDocument();
    expect(within(screen.getByRole("list", { name: "버전 목록" })).getByRole("button", { name: /v3 최신/ })).toHaveAttribute("aria-current", "true");
    expect(screen.getByRole("link", { name: "버전 비교" })).toHaveAttribute("href", "/projects/1/documents/4/compare?base=2&target=3");
  });

  it("AC-WS-01·09: 확정 문서는 확정 버전을 기본 선택하고 읽기 전용이며 최종본·선택 버전 받기를 구분한다", async () => {
    fetchDocumentMock.mockResolvedValue(FINALIZED);
    downloadMock.mockResolvedValue(new Blob(["pdf"]));
    render();
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v2");
    expect(screen.getByText("v2이 최종본으로 확정되었습니다.")).toBeInTheDocument();
    expect(screen.getByText(/최신 버전\(v3\)은 최종본이 아닙니다/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /새 버전/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /최종본 확정/ })).not.toBeInTheDocument();
    expect(screen.queryByLabelText(/피드백 남기기/)).not.toBeInTheDocument();
    expect(screen.getByText("최종 확정된 문서는 피드백을 조회만 할 수 있습니다.")).toBeInTheDocument();
    // 정보 탭: 설명 수정 없음
    fireEvent.click(screen.getByRole("tab", { name: "정보" }));
    expect(screen.queryByRole("button", { name: /설명 (수정|추가)/ })).not.toBeInTheDocument();
    // 최종본 받기는 선택 버전과 상관없이 확정 버전(v2)을 받는다
    fireEvent.click(within(screen.getByRole("list", { name: "버전 목록" })).getByRole("button", { name: /v3 최신/ }));
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v3");
    fireEvent.click(screen.getByRole("button", { name: "최종본 v2 받기" }));
    await waitFor(() => expect(downloadMock).toHaveBeenCalledWith("1", "4", 2));
    expect(saveBlobMock).toHaveBeenCalledWith(expect.any(Blob), "meeting-v2.pdf");
  });

  it("AC-WS-02: ?v=N이면 그 버전을 선택하고, 버전을 고르면 URL을 갱신한다", async () => {
    setSearch("v=1");
    render();
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v1");
    fireEvent.click(within(screen.getByRole("list", { name: "버전 목록" })).getByRole("button", { name: /^v2/ }));
    expect(replaceMock).toHaveBeenCalledWith("/projects/1/documents/4?v=2", { scroll: false });
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v2");
    expect(screen.getByRole("link", { name: "버전 비교" })).toHaveAttribute("href", "/projects/1/documents/4/compare?base=1&target=2");
  });

  it("AC-WS-02: 없는 버전 번호는 기본 버전으로 대신한다", async () => {
    setSearch("v=99");
    render();
    await waitFor(() => expect(screen.getByTestId("viewer")).toHaveTextContent("viewer v3"));
  });

  it("AC-WS-03: 빠르게 버전을 바꿔 이전 버전 응답이 늦게 와도 현재 버전 피드백만 표시한다", async () => {
    const slowV3 = deferred<unknown[]>();
    fetchFeedbacksMock.mockImplementation((_p: string, _d: string, v: number) =>
      v === 3 ? slowV3.promise : Promise.resolve([feedback(20, 2, "v2 의견")]),
    );
    render();
    await screen.findByTestId("viewer");
    fireEvent.click(within(screen.getByRole("list", { name: "버전 목록" })).getByRole("button", { name: /^v2/ }));
    expect(await screen.findByText("v2 의견")).toBeInTheDocument();
    await act(async () => slowV3.resolve([feedback(30, 3, "늦게 온 v3 의견")]));
    expect(screen.queryByText("늦게 온 v3 의견")).not.toBeInTheDocument();
    expect(screen.getByText("v2 의견")).toBeInTheDocument();
    expect(screen.getByTestId("viewer")).toHaveTextContent("viewer v2");
  });

  it("AC-WS-04: 작성 중 초안은 버전별로 유지·복원되고 제출은 작성한 버전으로만 간다", async () => {
    const pending = deferred<unknown>();
    createFeedbackMock.mockReturnValue(pending.promise);
    render();
    const list = within(await screen.findByRole("list", { name: "버전 목록" }));
    fireEvent.change(await screen.findByLabelText("v3에 피드백 남기기"), { target: { value: "v3에 남길 의견" } });
    fireEvent.click(list.getByRole("button", { name: /^v2/ }));

    // v2로 바뀌면 입력창은 v2용(비어 있음)이고 v3에는 '작성 중' 표시
    const v2Input = await screen.findByLabelText("v2에 피드백 남기기");
    expect(v2Input).toHaveValue("");
    expect(within(list.getByRole("button", { name: /v3 최신/ })).getByText("작성 중")).toBeInTheDocument();

    // v2에 작성·제출한 뒤 응답 전에 v3로 이동
    fireEvent.change(v2Input, { target: { value: "v2 의견 제출" } });
    fireEvent.click(screen.getByRole("button", { name: "피드백 등록" }));
    expect(createFeedbackMock).toHaveBeenCalledWith("1", "4", 2, "v2 의견 제출");
    fireEvent.click(list.getByRole("button", { name: /v3 최신/ }));
    expect(await screen.findByLabelText("v3에 피드백 남기기")).toHaveValue("v3에 남길 의견");

    await act(async () => pending.resolve(feedback(99, 2, "v2 의견 제출")));
    expect(createFeedbackMock).toHaveBeenCalledTimes(1);
    expect(screen.getByLabelText("v3에 피드백 남기기")).toHaveValue("v3에 남길 의견");
    // v2 초안은 등록 성공으로 비워진다
    fireEvent.click(list.getByRole("button", { name: /^v2/ }));
    expect(await screen.findByLabelText("v2에 피드백 남기기")).toHaveValue("");
  });

  it("AC-WS-05: 좌우 패널을 접고 펼치며, 경계는 키보드로 너비를 조절한다", async () => {
    render();
    await screen.findByTestId("viewer");
    const separator = screen.getByRole("separator", { name: "버전 패널 너비" });
    expect(separator).toHaveAttribute("aria-valuenow", "264");
    fireEvent.keyDown(separator, { key: "ArrowRight" });
    expect(separator).toHaveAttribute("aria-valuenow", "280");
    fireEvent.keyDown(separator, { key: "End" });
    expect(separator).toHaveAttribute("aria-valuenow", "400");
    fireEvent.click(screen.getByRole("button", { name: "버전 패널 접기" }));
    expect(screen.queryByRole("list", { name: "버전 목록" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "버전 패널 펼치기" }));
    expect(screen.getByRole("list", { name: "버전 목록" })).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "협업 패널 접기" }));
    expect(screen.queryByRole("tablist", { name: "협업" })).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "협업 패널 펼치기" }));
    expect(screen.getByRole("tablist", { name: "협업" })).toBeInTheDocument();
  });

  it("AC-WS-07: 새 버전 모달에서 업로드 중에는 닫히지 않고, 성공하면 새 최신 버전을 선택한다", async () => {
    const upload = deferred<DocumentDetailData>();
    createVersionMock.mockReturnValue(upload.promise);
    render();
    fireEvent.click(await screen.findByRole("button", { name: "새 버전" }));
    const dialog = screen.getByRole("dialog", { name: "새 버전 등록" });
    expect(within(dialog).getByText(/v4로 추가됩니다/)).toBeInTheDocument();
    const file = new File(["%PDF-1.7"], "meeting-v4.pdf", { type: "application/pdf" });
    fireEvent.change(within(dialog).getByLabelText(/새 버전 파일/), { target: { files: [file] } });
    fireEvent.click(within(dialog).getByRole("button", { name: "새 버전 등록" }));
    expect(createVersionMock).toHaveBeenCalledTimes(1);
    // 업로드 중: 중복 제출·닫기 불가
    expect(within(dialog).getByRole("button", { name: "등록 중..." })).toBeDisabled();
    expect(within(dialog).getByRole("button", { name: "취소" })).toBeDisabled();
    fireEvent.keyDown(document, { key: "Escape" });
    expect(screen.getByRole("dialog", { name: "새 버전 등록" })).toBeInTheDocument();

    fetchVersionsMock.mockResolvedValue([version(4, true), ...VERSIONS.map((v) => ({ ...v, latest: false }))]);
    await act(async () =>
      upload.resolve(detail({ latestVersion: { versionNumber: 4, originalFilename: "meeting-v4.pdf", sizeBytes: 9, uploadedAt: "2026-11-25T09:00:00Z", versionNote: null } })),
    );
    expect(screen.queryByRole("dialog", { name: "새 버전 등록" })).not.toBeInTheDocument();
    expect(replaceMock).toHaveBeenLastCalledWith("/projects/1/documents/4?v=4", { scroll: false });
    expect(await screen.findByTestId("viewer")).toHaveTextContent("viewer v4");
  });

  it("최종본 확정 버튼은 오너에게만 보인다", async () => {
    fetchProjectMock.mockResolvedValue({ id: 1, name: "P", myRole: "MEMBER" });
    render();
    await screen.findByTestId("viewer");
    await waitFor(() => expect(fetchProjectMock).toHaveBeenCalled());
    expect(screen.queryByRole("button", { name: /최종본 확정/ })).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "새 버전" })).toBeInTheDocument();
  });

  it("AC-WS-10: AI가 꺼져 있으면 AI 요약 탭이 없고, 켜져 있으면 요약 상태를 표시한다", async () => {
    const { unmount } = render();
    await screen.findByTestId("viewer");
    await waitFor(() => expect(fetchAiSettingsMock).toHaveBeenCalled());
    expect(screen.queryByRole("tab", { name: "AI 요약" })).not.toBeInTheDocument();
    unmount();

    fetchAiSettingsMock.mockResolvedValue({ enabled: true, available: true, canManage: true });
    fetchSummaryMock.mockResolvedValue(summaryState({ aiEnabled: true }));
    render();
    fireEvent.click(await screen.findByRole("tab", { name: "AI 요약" }));
    expect(await screen.findByText(/요청하면 문서 내용이 외부 AI 서비스로 전송됩니다/)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "AI 요약 만들기" })).toBeInTheDocument();
  });

  it("AC-WS-10: 서버 AI를 쓸 수 없으면(프로젝트는 켜짐) 탭이 없고, 이미 완료된 요약이 있으면 보여 준다", async () => {
    fetchAiSettingsMock.mockResolvedValue({ enabled: true, available: false, canManage: true });
    const { unmount } = render();
    await screen.findByTestId("viewer");
    await waitFor(() => expect(fetchSummaryMock).toHaveBeenCalled());
    expect(screen.queryByRole("tab", { name: "AI 요약" })).not.toBeInTheDocument();
    unmount();

    fetchSummaryMock.mockResolvedValue(
      summaryState({
        status: "COMPLETED",
        model: "server-model-id",
        completedAt: "2026-11-24T09:00:00Z",
        result: { summary: "회의 요약", keyPoints: [{ text: "배포일 변경", pages: [2] }], keyFacts: [], meta: null },
      }),
    );
    render();
    fireEvent.click(await screen.findByRole("tab", { name: "AI 요약" }));
    expect(await screen.findByText("회의 요약")).toBeInTheDocument();
    expect(screen.getByText(/server-model-id/)).toBeInTheDocument();
    // 쪽 번호를 누르면 뷰어에 이동 요청이 간다(AC-WS-11)
    fireEvent.click(screen.getByRole("button", { name: "2쪽으로 이동" }));
    expect(screen.getByTestId("viewer")).toHaveTextContent("page 2");
  });

  it("정보 탭: 선택 버전의 파일·버전 정보(초기 버전 내용/변경사항)와 문서 정보를 보여 준다", async () => {
    setSearch("v=1");
    render();
    await screen.findByTestId("viewer");
    fireEvent.click(screen.getByRole("tab", { name: "정보" }));
    const panel = screen.getByRole("tabpanel");
    expect(within(panel).getByText("선택한 버전 · v1")).toBeInTheDocument();
    expect(within(panel).getByText("초기 버전 내용")).toBeInTheDocument();
    expect(within(panel).getByText("초안")).toBeInTheDocument();
    expect(within(panel).getByText(/^2(\.0)? KB$/)).toBeInTheDocument();
    expect(within(panel).getByText("결정 사항")).toBeInTheDocument();
  });

  it("피드백 수는 탭과 버전 목록에 표시된다", async () => {
    render();
    await screen.findByTestId("viewer");
    expect(screen.getByRole("tab", { name: /피드백/ })).toHaveTextContent("2");
    expect(within(screen.getByRole("list", { name: "버전 목록" })).getAllByTitle(/피드백 \d개/)).toHaveLength(2);
  });

  it("다른 프로젝트 문서·없는 문서는 문서 없음, 구성원이 아닌 프로젝트는 프로젝트 없음", async () => {
    fetchDocumentMock.mockRejectedValueOnce(new ApiError("http", 404, "DOCUMENT_NOT_FOUND", "x"));
    const { unmount } = render();
    expect(await screen.findByRole("alert")).toHaveTextContent("문서를 찾을 수 없습니다.");
    unmount();
    fetchDocumentMock.mockRejectedValueOnce(new ApiError("http", 404, "PROJECT_NOT_FOUND", "x"));
    render();
    expect(await screen.findByRole("alert")).toHaveTextContent("프로젝트를 찾을 수 없습니다.");
  });

  it("숫자가 아닌 ID는 요청 없이 문서 없음", () => {
    params = { projectId: "abc", documentId: "4" };
    render();
    expect(screen.getByRole("alert")).toHaveTextContent("문서를 찾을 수 없습니다.");
    expect(fetchDocumentMock).not.toHaveBeenCalled();
  });

  it("그 밖의 오류는 다시 시도할 수 있고, 401이면 세션 만료 흐름을 시작한다", async () => {
    fetchDocumentMock.mockRejectedValueOnce(new ApiError("network", null, null, "서버에 연결할 수 없습니다."));
    render();
    expect(await screen.findByText("문서를 불러오지 못했습니다.")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "다시 시도" }));
    expect(await screen.findByRole("heading", { level: 1, name: "11월 정기 회의록" })).toBeInTheDocument();

    fetchDocumentMock.mockRejectedValueOnce(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    params = { projectId: "1", documentId: "5" };
    render();
    await waitFor(() => expect(refresh).toHaveBeenCalled());
  });
});
