import { describe, expect, it } from "vitest";

import { anchorOf, scrollTopFor, type PageBox } from "./sync";

/** 같은 높이 페이지들(간격 16px). */
function pages(count: number, height = 1000, gap = 16, start = 24): PageBox[] {
  return Array.from({ length: count }, (_, i) => ({ top: start + i * (height + gap), height }));
}

describe("동기 스크롤 계산 (SPEC-17 AC-CW-04)", () => {
  it("페이지 안의 비율을 상대 문서의 같은 페이지 같은 비율로 옮긴다", () => {
    const a = pages(3);
    const b = pages(3, 1400);
    const anchor = anchorOf(a[1].top + 250, a);
    expect(anchor).toEqual({ index: 1, fraction: 0.25 });
    expect(scrollTopFor(anchor, b)).toBe(b[1].top + 350);
  });

  it("페이지 수가 다르면 없는 페이지 대신 상대 문서의 끝으로 보내고, 되돌아오면 다시 맞춘다", () => {
    const longer = pages(3);
    const shorter = pages(1);
    const third = anchorOf(longer[2].top + 100, longer);
    expect(third.index).toBe(2);
    expect(scrollTopFor(third, shorter)).toBe(shorter[0].top + shorter[0].height);
    const first = anchorOf(longer[0].top + 500, longer);
    expect(scrollTopFor(first, shorter)).toBe(shorter[0].top + 500);
  });

  it("첫 페이지 위 여백·페이지 사이 간격·빈 문서도 안전하게 처리한다", () => {
    const p = pages(2);
    // 첫 페이지 위 여백: 맨 위로 돌아오면 상대 문서도 맨 위(0)
    expect(anchorOf(0, p)).toEqual({ index: -1, fraction: 0 });
    expect(scrollTopFor(anchorOf(0, p), pages(3, 1400))).toBe(0);
    expect(scrollTopFor(anchorOf(12, p), pages(1, 800, 16, 48))).toBe(24);
    // 1쪽과 2쪽 사이 간격은 1쪽 끝(비율 1)으로 본다
    expect(anchorOf(p[0].top + p[0].height + 8, p)).toEqual({ index: 0, fraction: 1 });
    expect(anchorOf(100, [])).toEqual({ index: 0, fraction: 0 });
    expect(scrollTopFor({ index: 1, fraction: 0.5 }, [])).toBe(0);
  });
});
