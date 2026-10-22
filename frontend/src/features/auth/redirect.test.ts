import { describe, expect, it } from "vitest";

import { safeNextPath } from "./redirect";

describe("safeNextPath", () => {
  it("내부 경로는 그대로 허용한다", () => {
    expect(safeNextPath("/projects/1?tab=review")).toBe("/projects/1?tab=review");
  });

  it.each([null, "", "https://evil.example", "//evil.example", "/\\evil.example", "javascript:alert(1)"])(
    "외부·잘못된 값(%s)은 /로 대체한다",
    (value) => {
      expect(safeNextPath(value)).toBe("/");
    },
  );
});
