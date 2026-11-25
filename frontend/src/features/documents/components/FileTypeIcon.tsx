import { cn } from "@/components/ui/cn";

import type { FileType } from "../types";

const STYLE: Record<FileType, string> = {
  PDF: "bg-danger-50 text-danger-700",
  DOCX: "bg-info-50 text-info-700",
  XLSX: "bg-success-50 text-success-700",
  PPTX: "bg-warning-50 text-warning-700",
};

/** 파일 형식 표시 (SPEC-18). 색 외에 형식 이름을 글자로 함께 보여 준다. */
export function FileTypeIcon({ type, className }: { type: FileType | null; className?: string }) {
  return (
    <span
      className={cn(
        "flex h-8 w-9 shrink-0 items-center justify-center rounded-md text-[10px] font-bold tracking-tight",
        type ? STYLE[type] : "bg-subtle text-ink-muted",
        className,
      )}
    >
      <span className="sr-only">파일 형식 </span>
      {type ?? "FILE"}
    </span>
  );
}
