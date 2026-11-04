import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";

import type { DocumentVersionSummary } from "../version-files";
import { DocumentVersionHistory } from "./DocumentVersionHistory";

const refresh = vi.fn();
const fetchVersionsMock = vi.fn();
const downloadMock = vi.fn();
const saveBlobMock = vi.fn();
const onPreview = vi.fn();

vi.mock("@/features/auth/AuthProvider", () => ({
  useAuth: () => ({ refresh }),
}));

vi.mock("../version-files", () => ({
  fetchDocumentVersions: (...args: unknown[]) => fetchVersionsMock(...args),
  downloadVersionFile: (...args: unknown[]) => downloadMock(...args),
  saveBlob: (...args: unknown[]) => saveBlobMock(...args),
}));

const versions: DocumentVersionSummary[] = [
  {
    versionNumber: 2,
    originalFilename: "요구사항-v2.docx",
    sizeBytes: 2048,
    uploadedBy: { id: 2, displayName: "Member" },
    uploadedAt: "2026-11-04T09:00:00Z",
    latest: true,
  },
  {
    versionNumber: 1,
    originalFilename: "요구사항.pdf",
    sizeBytes: 1_572_864,
    uploadedBy: { id: 1, displayName: "Owner" },
    uploadedAt: "2026-11-03T09:00:00Z",
    latest: false,
  },
];

function renderHistory(reloadKey = 0) {
  return render(
    <DocumentVersionHistory projectId="7" documentId="3" reloadKey={reloadKey} selectedVersion={null} onPreview={onPreview} />,
  );
}

describe("DocumentVersionHistory (SPEC-05 §9.1, AC-VDL-18)", () => {
  beforeEach(() => {
    refresh.mockReset();
    fetchVersionsMock.mockReset();
    downloadMock.mockReset();
    saveBlobMock.mockReset();
    onPreview.mockReset();
  });

  it("최신 버전부터 번호·최신 표시·파일명·업로더·크기를 보여 준다", async () => {
    fetchVersionsMock.mockResolvedValue(versions);
    renderHistory();

    const items = await screen.findAllByRole("listitem");
    expect(fetchVersionsMock).toHaveBeenCalledWith("7", "3");
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent("v2");
    expect(within(items[0]).getByText("최신")).toBeInTheDocument();
    expect(items[0]).toHaveTextContent("요구사항-v2.docx");
    expect(items[0]).toHaveTextContent("Member");
    expect(items[0]).toHaveTextContent("2.0 KB");
    expect(within(items[1]).queryByText("최신")).not.toBeInTheDocument();
    expect(items[1]).toHaveTextContent("1.5 MB");
  });

  it("미리보기 버튼은 해당 버전을 넘긴다", async () => {
    fetchVersionsMock.mockResolvedValue(versions);
    renderHistory();

    fireEvent.click(await screen.findByRole("button", { name: "v1 미리보기" }));

    expect(onPreview).toHaveBeenCalledWith(versions[1]);
  });

  it("다운로드는 원본 파일명으로 저장하고 진행 중에는 버튼을 비활성화한다", async () => {
    fetchVersionsMock.mockResolvedValue(versions);
    let resolve: (blob: Blob) => void = () => undefined;
    downloadMock.mockReturnValue(new Promise<Blob>((r) => (resolve = r)));
    renderHistory();

    fireEvent.click(await screen.findByRole("button", { name: "v2 다운로드" }));
    expect(screen.getByRole("button", { name: "v2 다운로드" })).toBeDisabled();
    expect(screen.getByRole("button", { name: "v2 다운로드" })).toHaveTextContent("다운로드 중...");
    const blob = new Blob(["x"]);
    resolve(blob);

    await waitFor(() => expect(saveBlobMock).toHaveBeenCalledWith(blob, "요구사항-v2.docx"));
    expect(downloadMock).toHaveBeenCalledWith("7", "3", 2);
    expect(screen.getByRole("button", { name: "v2 다운로드" })).toBeEnabled();
  });

  it.each<[string, unknown, string]>([
    ["버전 없음", new ApiError("http", 404, "DOCUMENT_VERSION_NOT_FOUND", "x"), "버전을 찾을 수 없습니다."],
    [
      "파일 없음",
      new ApiError("http", 500, "DOCUMENT_FILE_UNAVAILABLE", "x"),
      "파일을 불러올 수 없습니다. 관리자에게 문의해 주세요.",
    ],
    [
      "네트워크",
      new ApiError("network", null, null, "x"),
      "파일을 불러오지 못했습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.",
    ],
  ])("다운로드 실패(%s)는 안내 문구를 표시한다", async (_label, error, message) => {
    fetchVersionsMock.mockResolvedValue(versions);
    downloadMock.mockRejectedValue(error);
    renderHistory();

    fireEvent.click(await screen.findByRole("button", { name: "v1 다운로드" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(message);
    expect(saveBlobMock).not.toHaveBeenCalled();
  });

  it("다운로드 401은 세션 만료 흐름에 맡긴다", async () => {
    fetchVersionsMock.mockResolvedValue(versions);
    downloadMock.mockRejectedValue(new ApiError("http", 401, "AUTH_REQUIRED", "x"));
    renderHistory();

    fireEvent.click(await screen.findByRole("button", { name: "v1 다운로드" }));

    await waitFor(() => expect(refresh).toHaveBeenCalledTimes(1));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("목록 조회 실패는 영역 안에만 표시하고 다시 불러올 수 있다", async () => {
    fetchVersionsMock.mockRejectedValueOnce(new ApiError("http", 500, "INTERNAL_ERROR", "x")).mockResolvedValueOnce(versions);
    renderHistory();

    expect(await screen.findByText(/버전 목록을 불러오지 못했습니다/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", { name: "목록 다시 불러오기" }));

    expect(await screen.findAllByRole("listitem")).toHaveLength(2);
  });

  it("reloadKey가 바뀌면 목록을 다시 조회한다 (새 버전 등록 후 갱신)", async () => {
    fetchVersionsMock.mockResolvedValueOnce(versions.slice(1)).mockResolvedValueOnce(versions);
    const view = renderHistory(0);
    expect(await screen.findAllByRole("listitem")).toHaveLength(1);

    view.rerender(
      <DocumentVersionHistory projectId="7" documentId="3" reloadKey={1} selectedVersion={null} onPreview={onPreview} />,
    );

    await waitFor(() => expect(screen.getAllByRole("listitem")).toHaveLength(2));
    expect(fetchVersionsMock).toHaveBeenCalledTimes(2);
  });
});
