import { describe, expect, it } from "vitest";

import type { Change } from "../types";
import { highlightsFor, toScreenRect, unlocatedCount } from "./geometry";

/**
 * PDF.js `PageViewport`와 같은 변환(페이지 회전 + 확대, y축 뒤집기)을 직접 계산한다. CropBox `view`가 원점이 아니어도 맞아야 한다.
 */
function viewport(view: number[], scale: number, rotation: number) {
  const [x1, y1, x2, y2] = view;
  const cx = (x1 + x2) / 2;
  const cy = (y1 + y2) / 2;
  let a: number, b: number, c: number, d: number;
  switch (rotation % 360) {
    case 90: [a, b, c, d] = [0, 1, 1, 0]; break;
    case 180: [a, b, c, d] = [-1, 0, 0, 1]; break;
    case 270: [a, b, c, d] = [0, -1, -1, 0]; break;
    default: [a, b, c, d] = [1, 0, 0, -1];
  }
  const width = (rotation % 180 === 0 ? x2 - x1 : y2 - y1) * scale;
  const height = (rotation % 180 === 0 ? y2 - y1 : x2 - x1) * scale;
  const offsetX = width / 2;
  const offsetY = height / 2;
  const t = [a * scale, b * scale, c * scale, d * scale, offsetX - a * scale * cx - c * scale * cy, offsetY - b * scale * cx - d * scale * cy];
  return { width, height, point: (x: number, y: number) => [t[0] * x + t[2] * y + t[4], t[1] * x + t[3] * y + t[5]] };
}

describe("toScreenRect (SPEC-13)", () => {
  const view = [50, 100, 645, 942]; // CropBox 원점이 (50, 100)
  const rect: [number, number, number, number] = [100, 700, 150, 712]; // CropBox 기준 좌표

  it("회전 없이 확대만 하면 위쪽 기준으로 뒤집고 배율을 곱한다", () => {
    const vp = viewport(view, 2, 0);
    expect(toScreenRect(rect, view, vp.point)).toEqual({ left: 200, top: (842 - 712) * 2, width: 100, height: 24 });
  });

  it("90·180·270도 회전 후에도 같은 글자 영역을 가리킨다", () => {
    const vp90 = viewport(view, 1, 90);
    expect(toScreenRect(rect, view, vp90.point)).toEqual({ left: 700, top: 100, width: 12, height: 50 });
    const vp180 = viewport(view, 1, 180);
    expect(toScreenRect(rect, view, vp180.point)).toEqual({ left: 595 - 150, top: 700, width: 50, height: 12 });
    const vp270 = viewport(view, 1, 270);
    const r270 = toScreenRect(rect, view, vp270.point);
    expect(r270).toEqual({ left: 842 - 712, top: 595 - 150, width: 12, height: 50 });
    // 회전된 화면 크기 안에 있다.
    expect(r270.left + r270.width).toBeLessThanOrEqual(vp270.width);
    expect(r270.top + r270.height).toBeLessThanOrEqual(vp270.height);
  });
});

const box = (page: number) => ({ page, rect: [1, 2, 3, 4] as [number, number, number, number] });
const located = (page: number) => ({ text: "x", pages: [page], boxes: [box(page)], location: "FULL" as const });
const none = { text: "x", pages: [1], boxes: [], location: "NONE" as const };
const empty = { text: "", pages: [], boxes: [], location: "EMPTY" as const };

describe("highlightsFor (SPEC-13)", () => {
  const changes: Change[] = [
    { id: 0, type: "ADDED", base: empty, target: located(1) },
    { id: 1, type: "DELETED", base: located(2), target: empty },
    { id: 2, type: "MODIFIED", base: located(1), target: located(3) },
    { id: 3, type: "ADDED", base: empty, target: none },
  ];

  it("이전 버전에는 삭제·수정 전, 이후 버전에는 추가·수정 후만 그린다", () => {
    expect(highlightsFor(changes, "base").map((h) => [h.changeId, h.page])).toEqual([[1, 2], [2, 1]]);
    expect(highlightsFor(changes, "target").map((h) => [h.changeId, h.page])).toEqual([[0, 1], [2, 3]]);
  });

  it("위치가 없는 변경은 그리지 않고 따로 센다", () => {
    expect(highlightsFor(changes, "target").some((h) => h.changeId === 3)).toBe(false);
    expect(unlocatedCount(changes)).toBe(1);
  });
});
