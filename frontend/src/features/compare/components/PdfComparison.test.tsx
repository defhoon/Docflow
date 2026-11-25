import { act, fireEvent, render, screen, within } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CompareResponse } from "../types";
import { PdfComparison } from "./PdfComparison";

const openPdf = vi.fn();
vi.mock("../pdf/pdfjs", () => ({ openPdf: (...args: unknown[]) => openPdf(...args) }));

/** PDF.js 페이지 흉내: 회전 없음, 배율만 적용, y축 뒤집기. */
function fakePdf() {
  const page = {
    pageNumber: 1,
    rotate: 0,
    view: [0, 0, 600, 800],
    getViewport: ({ scale }: { scale: number }) => ({
      width: 600 * scale,
      height: 800 * scale,
      convertToViewportPoint: (x: number, y: number) => [x * scale, (800 - y) * scale],
    }),
    render: () => ({ promise: Promise.resolve(), cancel: vi.fn() }),
  };
  return { pdf: { numPages: 1, getPage: () => Promise.resolve(page) }, close: vi.fn() };
}

const side = (text: string, rect: [number, number, number, number]) => ({ text, pages: [1], boxes: [{ page: 1, rect }], location: "FULL" as const });
const empty = { text: "", pages: [], boxes: [], location: "EMPTY" as const };
const info = (versionNumber: number) => ({ versionNumber, originalFilename: `v${versionNumber}.pdf`, pageCount: 1, pages: [{ width: 600, height: 800, rotation: 0 }] });

const data: CompareResponse = {
  base: info(1),
  target: info(2),
  result: {
    identical: false,
    counts: { added: 2, deleted: 1, modified: 1 },
    changes: [
      { id: 0, type: "MODIFIED", base: side("100", [10, 700, 60, 712]), target: side("200", [10, 700, 60, 712]) },
      { id: 1, type: "ADDED", base: empty, target: side("새 문단", [10, 600, 200, 612]) },
      { id: 2, type: "DELETED", base: side("옛 문단", [10, 500, 200, 512]), target: empty },
      { id: 3, type: "ADDED", base: empty, target: { text: "회전 글자", pages: [1], boxes: [], location: "NONE" } },
    ],
    blocks: [],
    coarseBlocks: 0,
  },
};

describe("PdfComparison (SPEC-13)", () => {
  beforeEach(() => {
    openPdf.mockReset();
    openPdf.mockImplementation(() => Promise.resolve(fakePdf()));
    Element.prototype.scrollIntoView = vi.fn();
  });

  it("이전 버전에는 삭제·수정 전, 이후 버전에는 추가·수정 후 위치를 기존 다운로드 API로 받은 PDF 위에 표시한다", async () => {
    render(<PdfComparison projectId="7" documentId="3" data={data} activeChangeId={null} onSelectChange={vi.fn()} />);
    const base = within(await screen.findByRole("region", { name: "이전 · v1" }));
    const target = within(screen.getByRole("region", { name: "이후 · v2" }));
    await screen.findAllByRole("button", { name: /쪽\)/ });
    expect(base.getAllByRole("button", { name: /\(1쪽\)/ }).map((b) => b.getAttribute("aria-label"))).toEqual(["수정 (1쪽)", "삭제 (1쪽)"]);
    expect(target.getAllByRole("button", { name: /\(1쪽\)/ }).map((b) => b.getAttribute("aria-label"))).toEqual(["수정 (1쪽)", "추가 (1쪽)"]);
    expect(openPdf).toHaveBeenCalledWith("/api/projects/7/documents/3/versions/1/download", expect.anything());
    expect(openPdf).toHaveBeenCalledWith("/api/projects/7/documents/3/versions/2/download", expect.anything());
    expect(screen.getByText(/위치를 찾지 못한 변경 1개는 PDF에 표시하지 않았습니다/)).toBeInTheDocument();
  });

  it("좌표를 화면 위치로 바꾸고, 강조 표시를 끄고 켤 수 있다", async () => {
    render(<PdfComparison projectId="7" documentId="3" data={data} activeChangeId={null} onSelectChange={vi.fn()} />);
    const target = within(await screen.findByRole("region", { name: "이후 · v2" }));
    const added = await target.findByRole("button", { name: "추가 (1쪽)" });
    const scale = parseFloat(added.style.left) + 1 > 0 ? (parseFloat(added.style.width) - 2) / 190 : 0;
    expect(parseFloat(added.style.left)).toBeCloseTo(10 * scale - 1);
    expect(parseFloat(added.style.top)).toBeCloseTo((800 - 612) * scale - 1);
    fireEvent.click(screen.getByLabelText("변경 강조 표시"));
    expect(screen.queryAllByRole("button", { name: /쪽\)/ })).toHaveLength(0);
  });

  it("확대·축소·회전 도구가 있고, 강조를 누르면 해당 변경을 고른다", async () => {
    const onSelect = vi.fn();
    render(<PdfComparison projectId="7" documentId="3" data={data} activeChangeId={null} onSelectChange={onSelect} />);
    const toolbar = screen.getByRole("toolbar", { name: "PDF 보기 도구" });
    await act(async () => {});
    fireEvent.click(within(toolbar).getByRole("button", { name: "확대" }));
    expect(within(toolbar).getByText(/%$/)).toBeInTheDocument();
    expect(within(toolbar).getByRole("button", { name: "폭 맞춤" })).toBeEnabled();
    fireEvent.click(within(toolbar).getByRole("button", { name: /회전/ }));
    expect(within(toolbar).getByText("(현재 90도)")).toBeInTheDocument();
    fireEvent.click((await screen.findAllByRole("button", { name: "삭제 (1쪽)" }))[0]);
    expect(onSelect).toHaveBeenCalledWith(2);
  });

  it("PDF를 불러오지 못하면 원본 다운로드를 안내한다", async () => {
    openPdf.mockRejectedValue(new Error("x"));
    render(<PdfComparison projectId="7" documentId="3" data={data} activeChangeId={null} onSelectChange={vi.fn()} />);
    expect(await screen.findByText("PDF를 불러오지 못했습니다.")).toBeInTheDocument();
  });
});
