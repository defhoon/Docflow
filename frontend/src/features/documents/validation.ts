/**
 * 문서 등록 입력 검증 (SPEC-03 §5.3, §5.5, §5.6, §8.3). 서버 검증이 최종 기준이며 여기서는 사용성을 위한 사전 검사만 한다.
 * 제목·설명 문자 규칙은 [기존] 프로젝트 검증 함수(Backend `ProjectText`와 같은 규칙)를 재사용한다.
 */
import { isAllowedDescription, isAllowedName, trimEdges, trimEdgesToNull } from "@/features/projects/validation";

export const TITLE_MAX_LENGTH = 200;
export const DESCRIPTION_MAX_LENGTH = 1000;
/** 20MiB = 20,971,520바이트 (SPEC-03 §5.6) */
export const MAX_FILE_BYTES = 20 * 1024 * 1024;
export const FILE_NAME_MAX_LENGTH = 255;
export const ALLOWED_EXTENSIONS = ["pdf", "docx", "xlsx", "pptx"] as const;
/** 파일 선택 창 필터. 사용성 보조이며 서버가 내용으로 형식을 다시 확인한다. */
export const FILE_ACCEPT = ALLOWED_EXTENSIONS.map((extension) => `.${extension}`).join(",");

export const MESSAGES = {
  titleRequired: "문서 제목을 입력해 주세요.",
  titleTooLong: "문서 제목은 200자 이하로 입력해 주세요.",
  titleCharacters: "문서 제목에 사용할 수 없는 문자가 포함되어 있습니다.",
  descriptionTooLong: "설명은 1000자 이하로 입력해 주세요.",
  descriptionCharacters: "설명에 사용할 수 없는 문자가 포함되어 있습니다.",
  fileRequired: "파일을 선택해 주세요.",
  fileType: "PDF, DOCX, XLSX, PPTX 파일만 업로드할 수 있습니다.",
  fileEmpty: "빈 파일은 업로드할 수 없습니다.",
  fileTooLarge: "파일은 20MB 이하만 업로드할 수 있습니다.",
  fileNameTooLong: "파일 이름은 255자 이하여야 합니다.",
} as const;

export type DocumentFieldErrors = Partial<Record<"title" | "description" | "file", string>>;

export type DocumentInputValidation = {
  /** 서버에 보낼 정규화된 제목·설명 */
  value: { title: string; description: string | null };
  errors: DocumentFieldErrors;
};

/** 마지막 확장자(소문자)가 허용 목록에 있는지. `report.pdf.exe`는 `exe`로 판단한다. */
export function hasAllowedExtension(fileName: string): boolean {
  const dot = fileName.lastIndexOf(".");
  if (dot <= 0 || dot === fileName.length - 1) {
    return false;
  }
  const extension = fileName.slice(dot + 1).toLowerCase();
  return (ALLOWED_EXTENSIONS as readonly string[]).includes(extension);
}

export function validateFile(file: File | null): string | undefined {
  if (!file) {
    return MESSAGES.fileRequired;
  }
  if (!hasAllowedExtension(file.name)) {
    return MESSAGES.fileType;
  }
  if (file.name.length > FILE_NAME_MAX_LENGTH) {
    return MESSAGES.fileNameTooLong;
  }
  if (file.size === 0) {
    return MESSAGES.fileEmpty;
  }
  if (file.size > MAX_FILE_BYTES) {
    return MESSAGES.fileTooLarge;
  }
  return undefined;
}

/** 정규화 후 필수 → 허용 문자 → 길이(UTF-16 code unit) 순으로 검사한다. */
export function validateDocumentInput(
  title: string | null | undefined,
  description: string | null | undefined,
  file: File | null,
): DocumentInputValidation {
  const normalizedTitle = trimEdges(title) ?? "";
  const normalizedDescription = trimEdgesToNull(description);
  const errors: DocumentFieldErrors = {};

  if (normalizedTitle.length === 0) {
    errors.title = MESSAGES.titleRequired;
  } else if (!isAllowedName(normalizedTitle)) {
    errors.title = MESSAGES.titleCharacters;
  } else if (normalizedTitle.length > TITLE_MAX_LENGTH) {
    errors.title = MESSAGES.titleTooLong;
  }

  if (!isAllowedDescription(normalizedDescription)) {
    errors.description = MESSAGES.descriptionCharacters;
  } else if (normalizedDescription !== null && normalizedDescription.length > DESCRIPTION_MAX_LENGTH) {
    errors.description = MESSAGES.descriptionTooLong;
  }

  const fileError = validateFile(file);
  if (fileError) {
    errors.file = fileError;
  }

  return { value: { title: normalizedTitle, description: normalizedDescription }, errors };
}
