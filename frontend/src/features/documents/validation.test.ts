import { describe, expect, it } from "vitest";

import { hasAllowedExtension, MAX_FILE_BYTES, MESSAGES, validateDocumentInput, validateFile } from "./validation";

function fileOfSize(name: string, size: number): File {
  const file = new File(["x"], name);
  Object.defineProperty(file, "size", { value: size });
  return file;
}

describe("문서 입력 검증 (SPEC-03 §5.3, §8.3)", () => {
  it("제목·설명을 정규화한다", () => {
    const result = validateDocumentInput("  요구사항  ", "   ", fileOfSize("a.pdf", 1));
    expect(result.errors).toEqual({});
    expect(result.value).toEqual({ title: "요구사항", description: null });
  });

  it("제목 필수·문자·200자 경계", () => {
    const file = fileOfSize("a.pdf", 1);
    expect(validateDocumentInput("   ", null, file).errors.title).toBe(MESSAGES.titleRequired);
    expect(validateDocumentInput("a‮b", null, file).errors.title).toBe(MESSAGES.titleCharacters);
    expect(validateDocumentInput("가".repeat(200), null, file).errors.title).toBeUndefined();
    expect(validateDocumentInput("가".repeat(201), null, file).errors.title).toBe(MESSAGES.titleTooLong);
  });

  it("설명 문자·1000자 경계", () => {
    const file = fileOfSize("a.pdf", 1);
    expect(validateDocumentInput("t", "a\u0007b", file).errors.description).toBe(MESSAGES.descriptionCharacters);
    expect(validateDocumentInput("t", "가".repeat(1000), file).errors.description).toBeUndefined();
    expect(validateDocumentInput("t", "가".repeat(1001), file).errors.description).toBe(MESSAGES.descriptionTooLong);
  });

  it("파일 필수", () => {
    expect(validateDocumentInput("t", null, null).errors.file).toBe(MESSAGES.fileRequired);
  });

  it("마지막 확장자만 보고 4종만 허용한다", () => {
    for (const name of ["a.pdf", "a.DOCX", "a.xlsx", "a.PpTx"]) {
      expect(hasAllowedExtension(name)).toBe(true);
    }
    for (const name of ["a.pdf.exe", "a.doc", "a.docm", "a", "a.", ".pdf", "a.txt"]) {
      expect(hasAllowedExtension(name)).toBe(false);
    }
    expect(validateFile(fileOfSize("a.txt", 1))).toBe(MESSAGES.fileType);
  });

  it("크기 1 ~ 20,971,520바이트만 허용한다", () => {
    expect(MAX_FILE_BYTES).toBe(20_971_520);
    expect(validateFile(fileOfSize("a.pdf", 0))).toBe(MESSAGES.fileEmpty);
    expect(validateFile(fileOfSize("a.pdf", 1))).toBeUndefined();
    expect(validateFile(fileOfSize("a.pdf", 20_971_520))).toBeUndefined();
    expect(validateFile(fileOfSize("a.pdf", 20_971_521))).toBe(MESSAGES.fileTooLarge);
  });

  it("파일 이름 255자 초과를 거부한다", () => {
    expect(validateFile(fileOfSize(`${"가".repeat(251)}.pdf`, 1))).toBeUndefined();
    expect(validateFile(fileOfSize(`${"가".repeat(252)}.pdf`, 1))).toBe(MESSAGES.fileNameTooLong);
  });
});
