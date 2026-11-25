import type { Change, ChangeType } from "../types";

/** 화면 강조 사각형(CSS px, 페이지 캔버스 왼쪽 위 기준). */
export type ScreenRect = { left: number; top: number; width: number; height: number };

/** 페이지에 그릴 강조 하나. */
export type PageHighlight = { changeId: number; type: ChangeType; page: number; rect: [number, number, number, number] };

/**
 * SPEC-10 좌표(CropBox 왼쪽 아래 기준 사용자 공간, 회전 전)를 PDF.js 화면 좌표로 바꾼다 (SPEC-13 §4).
 * `pageView`는 PDF.js `page.view`(CropBox `[x1, y1, x2, y2]`)이고, `toViewport`는 `viewport.convertToViewportPoint`다.
 * 뷰포트가 페이지 회전·사용자 회전·확대를 모두 반영하므로 두 모서리를 변환한 뒤 감싸는 사각형을 쓰면 된다.
 */
export function toScreenRect(
  rect: [number, number, number, number],
  pageView: number[],
  toViewport: (x: number, y: number) => number[],
): ScreenRect {
  const [x1, y1, x2, y2] = rect;
  const [ax, ay] = toViewport(x1 + pageView[0], y1 + pageView[1]);
  const [bx, by] = toViewport(x2 + pageView[0], y2 + pageView[1]);
  return { left: Math.min(ax, bx), top: Math.min(ay, by), width: Math.abs(bx - ax), height: Math.abs(by - ay) };
}

/**
 * 한쪽 PDF에 그릴 강조 목록. 이전 버전에는 삭제·수정(수정 전), 이후 버전에는 추가·수정(수정 후)을 그린다. 서버가 위치를 확인한
 * 상자만 쓰며, 위치가 없는 변경은 그리지 않는다(임의 위치 강조 금지).
 */
export function highlightsFor(changes: Change[], side: "base" | "target"): PageHighlight[] {
  const result: PageHighlight[] = [];
  for (const change of changes) {
    const relevant = side === "base" ? change.type !== "ADDED" : change.type !== "DELETED";
    if (!relevant) {
      continue;
    }
    for (const box of change[side].boxes) {
      result.push({ changeId: change.id, type: change.type, page: box.page, rect: box.rect });
    }
  }
  return result;
}

/** PDF에 전혀 표시할 수 없는 변경(위치를 찾지 못함) 수. */
export function unlocatedCount(changes: Change[]): number {
  return changes.filter((change) => {
    const sides = change.type === "ADDED" ? [change.target] : change.type === "DELETED" ? [change.base] : [change.base, change.target];
    return sides.every((s) => s.boxes.length === 0);
  }).length;
}

export const ZOOM_STEPS = [0.5, 0.75, 1, 1.25, 1.5, 2, 3];
