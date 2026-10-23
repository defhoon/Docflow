import { describe, expect, it } from "vitest";

import {
  isAllowedDescription,
  isAllowedName,
  MESSAGES,
  trimEdges,
  trimEdgesToNull,
  validateProjectInput,
} from "./validation";

/** SPEC-01 §5.5.5 #1~#17. Backend `CreateProjectRequestValidationTest`와 같은 케이스 */
describe("validateProjectInput (§5.5.5)", () => {
  const nameError = (name: string | null | undefined) => validateProjectInput(name, null).errors.name;
  const descriptionError = (description: string | null | undefined) =>
    validateProjectInput("DocFlow", description).errors.description;

  it("#1 이름 누락·null·빈 문자열·스페이스만은 필수 오류", () => {
    for (const name of [undefined, null, "", "   "]) {
      expect(nameError(name)).toBe(MESSAGES.nameRequired);
    }
  });

  it("#2 특수 공백만 있는 이름은 정규화 후 빈 값이라 필수 오류", () => {
    expect(nameError(" 　\t")).toBe(MESSAGES.nameRequired);
    expect(nameError("    ")).toBe(MESSAGES.nameRequired);
  });

  it("#3, #4 앞뒤 공백을 제거한다", () => {
    expect(validateProjectInput("  DocFlow 기획  ", null)).toEqual({
      value: { name: "DocFlow 기획", description: null },
      errors: {},
    });
    expect(validateProjectInput(" DocFlow ", null).value.name).toBe("DocFlow");
  });

  it("#5 내부 전각 공백은 그대로 둔다", () => {
    expect(validateProjectInput("Doc　Flow", null)).toEqual({
      value: { name: "Doc　Flow", description: null },
      errors: {},
    });
  });

  it("#6 제어 문자(줄바꿈·탭·NUL)가 있는 이름은 거부한다", () => {
    for (const name of ["Doc\nFlow", "Doc\tFlow", "Doc\u0000Flow"]) {
      expect(nameError(name)).toBe(MESSAGES.nameCharacters);
    }
  });

  it("#7 폭 없는 문자·BOM은 거부한다 (JavaScript trim()과 달리 U+FEFF를 제거하지 않는다)", () => {
    expect(nameError("Doc​Flow")).toBe(MESSAGES.nameCharacters);
    expect(nameError("﻿DocFlow")).toBe(MESSAGES.nameCharacters);
    expect("﻿DocFlow".trim()).toBe("DocFlow");
  });

  it("#8 양방향 서식 제어 문자는 거부한다", () => {
    expect(nameError("Doc‮Flow")).toBe(MESSAGES.nameCharacters);
    expect(nameError("Doc⁦Flow")).toBe(MESSAGES.nameCharacters);
  });

  it("#9 보이는 문자가 없는 이름(ZWJ만)은 거부한다", () => {
    expect(nameError("‍")).toBe(MESSAGES.nameCharacters);
  });

  it("#10 ZWJ 결합 이모지는 허용한다", () => {
    expect(nameError("👨‍👩‍👧")).toBeUndefined();
  });

  it("#11 정규화 후 100 code unit은 허용, 101은 길이 오류", () => {
    expect(nameError("a".repeat(100))).toBeUndefined();
    expect(nameError("a".repeat(101))).toBe(MESSAGES.nameTooLong);
  });

  it("#12 앞뒤 공백 포함 103 code unit도 정규화 후 100이면 허용", () => {
    const result = validateProjectInput(" " + "b".repeat(100) + "  ", null);
    expect(result.errors).toEqual({});
    expect(result.value.name).toHaveLength(100);
  });

  it("#13 이모지는 UTF-16 code unit 2개로 센다", () => {
    expect("😀".repeat(50)).toHaveLength(100);
    expect(nameError("😀".repeat(50))).toBeUndefined();
    expect(nameError("😀".repeat(51))).toBe(MESSAGES.nameTooLong);
  });

  it("#14 설명 누락·null·빈 문자열·공백만은 null", () => {
    for (const description of [undefined, null, "", "  \n "]) {
      const result = validateProjectInput("DocFlow", description);
      expect(result.errors).toEqual({});
      expect(result.value.description).toBeNull();
    }
  });

  it("#15 설명 내부의 줄바꿈·탭은 그대로 둔다", () => {
    expect(validateProjectInput("DocFlow", "1행\n\t2행\r\n3행").value.description).toBe("1행\n\t2행\r\n3행");
    expect(descriptionError("1행\n\t2행\r\n3행")).toBeUndefined();
  });

  it("#16 설명의 그 밖의 제어 문자는 거부한다", () => {
    expect(descriptionError("설명\u0000")).toBe(MESSAGES.descriptionCharacters);
    expect(descriptionError("설명\u0007")).toBe(MESSAGES.descriptionCharacters);
  });

  it("#17 정규화 후 설명 1000 code unit은 허용, 1001은 길이 오류", () => {
    expect(descriptionError("d".repeat(1000))).toBeUndefined();
    expect(descriptionError("  " + "d".repeat(1000) + "\n")).toBeUndefined();
    expect(descriptionError("d".repeat(1001))).toBe(MESSAGES.descriptionTooLong);
  });
});

describe("Java ProjectText와의 문자 기준 일치", () => {
  it("Java isWhitespace ∪ isSpaceChar에 해당하는 문자만 가장자리에서 제거한다", () => {
    const edgeSpaces = [" ", "\t", "\n", "\u000B", "\f", "\r", "\u001C", "\u001F", " ", " ",
      " ", " ", " ", " ", " ", " ", " ", "　"];
    for (const space of edgeSpaces) {
      expect(trimEdges(`${space}x${space}`)).toBe("x");
    }
    // Java에서 공백이 아닌 문자: U+FEFF(Cf), U+200B(Cf), U+0085(Cc), U+180E(Cf)
    for (const notSpace of ["﻿", "​", "\u0085", "᠎"]) {
      expect(trimEdges(`${notSpace}x`)).toBe(`${notSpace}x`);
    }
  });

  it("null은 null로, 공백만은 빈 문자열 또는 null로 정규화한다", () => {
    expect(trimEdges(null)).toBeNull();
    expect(trimEdges("  ")).toBe("");
    expect(trimEdgesToNull("  ")).toBeNull();
    expect(trimEdgesToNull(undefined)).toBeNull();
  });

  it("이름 내부의 Zs 공백과 ZWNJ·ZWJ·LRM·RLM은 허용한다", () => {
    for (const name of ["Doc Flow", "Doc‌Flow", "Doc‍Flow", "Doc‎Flow", "Doc‏Flow"]) {
      expect(isAllowedName(name)).toBe(true);
    }
    for (const name of ["Doc Flow", "Doc⁠Flow", "Doc᠎Flow", "Doc\u007FFlow", "Doc\u0085Flow"]) {
      expect(isAllowedName(name)).toBe(false);
    }
  });

  it("설명은 제어 문자 외의 문자를 제한하지 않는다", () => {
    expect(isAllowedDescription("설명 ​‮")).toBe(true);
    expect(isAllowedDescription(null)).toBe(true);
    expect(isAllowedDescription("설명\u007F")).toBe(false);
  });
});
