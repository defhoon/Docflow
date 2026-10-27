/**
 * 프로젝트 입력 정규화·검증 (SPEC-01 §5.5). Backend `ProjectText`와 같은 규칙이며, 서버 검증이 최종 기준이다.
 *
 * JavaScript `trim()`·`\s`는 U+FEFF를 공백으로 보는 등 Java와 기준이 다르므로 사용하지 않고,
 * Java `Character.isWhitespace || Character.isSpaceChar`에 해당하는 문자 범주를 명시한다.
 */

export const NAME_MAX_LENGTH = 100;
export const DESCRIPTION_MAX_LENGTH = 1000;

export const MESSAGES = {
  nameRequired: "프로젝트 이름을 입력해 주세요.",
  nameTooLong: "프로젝트 이름은 100자 이하로 입력해 주세요.",
  nameCharacters: "프로젝트 이름에 사용할 수 없는 문자가 포함되어 있습니다.",
  descriptionTooLong: "설명은 1000자 이하로 입력해 주세요.",
  descriptionCharacters: "설명에 사용할 수 없는 문자가 포함되어 있습니다.",
} as const;

// isSpaceChar: Zs·Zl·Zp / isWhitespace에만 해당: U+0009–U+000D, U+001C–U+001F
const EDGE_SPACE = /[\p{Zs}\p{Zl}\p{Zp}\u0009-\u000D\u001C-\u001F]/u;
const NAME_FORBIDDEN = /[\p{Cc}\p{Zl}\p{Zp}​⁠﻿᠎‪-‮⁦-⁩]/u;
const INVISIBLE = /[\p{Zs}\p{Zl}\p{Zp}\p{Cc}\p{Cf}]/u;
const DESCRIPTION_FORBIDDEN = /[\p{Cc}]/u;
const DESCRIPTION_ALLOWED_CONTROLS = new Set(["\t", "\n", "\r"]);

/** 앞뒤의 공백 문자를 제거한다. 내부 문자는 변경하지 않는다. (Java `trimEdges`) */
export function trimEdges(value: string): string;
export function trimEdges(value: string | null | undefined): string | null;
export function trimEdges(value: string | null | undefined): string | null {
  if (value == null) {
    return null;
  }
  let start = 0;
  let end = value.length;
  // 공백 문자는 모두 BMP 문자이므로 Java와 같이 UTF-16 단위로 검사한다.
  while (start < end && EDGE_SPACE.test(value[start])) {
    start++;
  }
  while (end > start && EDGE_SPACE.test(value[end - 1])) {
    end--;
  }
  return value.slice(start, end);
}

/** 정규화 결과가 비어 있으면 null. (Java `trimEdgesToNull`) */
export function trimEdgesToNull(value: string | null | undefined): string | null {
  const trimmed = trimEdges(value);
  return trimmed ? trimmed : null;
}

/** 이름 허용 문자 규칙. 빈 값은 필수 검증이 담당한다. (Java `isAllowedName`) */
export function isAllowedName(value: string): boolean {
  if (!value) {
    return true;
  }
  let hasVisible = false;
  for (const char of value) {
    if (NAME_FORBIDDEN.test(char)) {
      return false;
    }
    if (!INVISIBLE.test(char)) {
      hasVisible = true;
    }
  }
  return hasVisible;
}

/** 설명 허용 문자 규칙. 제어 문자 중 `\t`, `\n`, `\r`만 허용한다. (Java `isAllowedDescription`) */
export function isAllowedDescription(value: string | null): boolean {
  if (value == null) {
    return true;
  }
  for (const char of value) {
    if (DESCRIPTION_FORBIDDEN.test(char) && !DESCRIPTION_ALLOWED_CONTROLS.has(char)) {
      return false;
    }
  }
  return true;
}

export type ProjectFieldErrors = Partial<Record<"name" | "description", string>>;

export type ProjectInputValidation = {
  /** 서버에 보낼 정규화된 값 */
  value: { name: string; description: string | null };
  errors: ProjectFieldErrors;
};

/** 정규화 후 필수 → 허용 문자 → 길이(UTF-16 code unit, `string.length`) 순으로 검사한다. */
export function validateProjectInput(
  name: string | null | undefined,
  description: string | null | undefined,
): ProjectInputValidation {
  const normalizedName = trimEdges(name) ?? "";
  const normalizedDescription = trimEdgesToNull(description);
  const errors: ProjectFieldErrors = {};

  if (normalizedName.length === 0) {
    errors.name = MESSAGES.nameRequired;
  } else if (!isAllowedName(normalizedName)) {
    errors.name = MESSAGES.nameCharacters;
  } else if (normalizedName.length > NAME_MAX_LENGTH) {
    errors.name = MESSAGES.nameTooLong;
  }

  if (!isAllowedDescription(normalizedDescription)) {
    errors.description = MESSAGES.descriptionCharacters;
  } else if (normalizedDescription !== null && normalizedDescription.length > DESCRIPTION_MAX_LENGTH) {
    errors.description = MESSAGES.descriptionTooLong;
  }

  return { value: { name: normalizedName, description: normalizedDescription }, errors };
}

export const MEMBER_EMAIL_MAX_LENGTH = 255;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

/**
 * 구성원 추가 이메일 검증 (SPEC-02 §5.4). 서버(`@NotBlank`, `@Email`, `@Size(max = 255)`)가 최종 기준이며,
 * 여기서는 앞뒤 공백 제거 후 기본 형식만 확인한다.
 */
export function validateMemberEmail(email: string): { value: string; error?: string } {
  const value = email.trim();
  if (!value) {
    return { value, error: "이메일을 입력해 주세요." };
  }
  if (value.length > MEMBER_EMAIL_MAX_LENGTH) {
    return { value, error: "이메일은 255자 이하로 입력해 주세요." };
  }
  if (!EMAIL_PATTERN.test(value)) {
    return { value, error: "이메일 형식이 올바르지 않습니다." };
  }
  return { value };
}
