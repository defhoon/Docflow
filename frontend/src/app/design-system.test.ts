import { existsSync, readdirSync, readFileSync } from "node:fs";
import { join } from "node:path";

import { describe, expect, it } from "vitest";

const FONT_DIR = join(__dirname, "fonts", "pretendard");

describe("Pretendard 자체 호스팅 (SPEC-15 AC-DS-01, ADR-0012)", () => {
  const css = readFileSync(join(FONT_DIR, "pretendard.css"), "utf8");

  it("모든 @font-face가 저장소 안의 WOFF2 조각만 참조하고 외부 출처를 쓰지 않는다", () => {
    const urls = [...css.matchAll(/url\(([^)]+)\)/g)].map((match) => match[1]);
    expect(urls.length).toBeGreaterThan(50);
    for (const url of urls) {
      expect(url).toMatch(/^\.\/woff2-dynamic-subset\/PretendardVariable\.subset\.\d+\.woff2$/);
      expect(existsSync(join(FONT_DIR, url))).toBe(true);
    }
    expect(css).not.toMatch(/url\(\s*['"]?https?:/);
  });

  it("글자 범위별로 나뉘고(unicode-range) 교체 표시(swap)를 쓴다", () => {
    const faces = css.match(/@font-face/g) ?? [];
    expect((css.match(/unicode-range/g) ?? []).length).toBe(faces.length);
    expect((css.match(/font-display: swap/g) ?? []).length).toBe(faces.length);
    expect(readdirSync(join(FONT_DIR, "woff2-dynamic-subset")).length).toBe(faces.length);
  });

  it("OFL 라이선스 전문을 함께 둔다", () => {
    expect(readFileSync(join(FONT_DIR, "OFL-LICENSE.txt"), "utf8")).toContain("SIL OPEN FONT LICENSE Version 1.1");
  });

  it("기본 글꼴 스택의 첫 항목이 Pretendard Variable이다", () => {
    const globals = readFileSync(join(__dirname, "globals.css"), "utf8");
    expect(globals).toMatch(/--font-sans:\s*"Pretendard Variable"/);
  });
});
