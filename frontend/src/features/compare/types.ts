/** SPEC-12 버전 비교 응답 타입. 좌표는 CropBox 왼쪽 아래 기준 PDF 사용자 공간(pt, 회전 전, SPEC-10). */

export type ChangeType = "ADDED" | "DELETED" | "MODIFIED";
export type LocationKind = "FULL" | "PARTIAL" | "NONE" | "EMPTY";

export type HighlightBox = { page: number; rect: [number, number, number, number] };

export type ChangeSide = { text: string; pages: number[]; boxes: HighlightBox[]; location: LocationKind };

export type Change = { id: number; type: ChangeType; base: ChangeSide; target: ChangeSide };

export type Segment = { op: "EQUAL" | "INSERT" | "DELETE" | "MODIFY"; text: string; changeId: number | null };

export type Block = {
  type: "EQUAL" | "CHANGED";
  paragraphCount: number;
  firstText: string | null;
  lastText: string | null;
  base: Segment[];
  target: Segment[];
};

export type VersionInfo = {
  versionNumber: number;
  originalFilename: string;
  pageCount: number;
  pages: { width: number; height: number; rotation: number }[];
};

export type CompareResponse = {
  base: VersionInfo;
  target: VersionInfo;
  result: {
    identical: boolean;
    counts: { added: number; deleted: number; modified: number };
    changes: Change[];
    blocks: Block[];
    coarseBlocks: number;
  };
};
