/** 문서 협업 상태 (`spec.md` §7.1, SPEC-07). FINALIZED는 영구 읽기 전용이다. */
export type DocumentStatus = "ACTIVE" | "FINALIZED";

export type DocumentAuthor = {
  id: number;
  displayName: string;
};

/** Backend `DocumentSummaryResponse` (`GET /api/projects/{projectId}/documents`) */
export type DocumentSummary = {
  id: number;
  title: string;
  /** 문서 설명 (SPEC-09). 없으면 null. 목록에서는 줄여서 표시한다. */
  description: string | null;
  status: DocumentStatus;
  createdBy: DocumentAuthor;
  createdAt: string;
  latestVersionNumber: number;
  /** 최종 확정 버전 번호 (SPEC-07). ACTIVE이면 null. */
  finalVersionNumber: number | null;
  /** 문서 변경 시각(새 버전·설명 변경·최종 확정, SPEC-18) */
  updatedAt: string;
  /** 최신 버전 업로더 (SPEC-18) */
  latestUploader: DocumentAuthor;
  /** 최신 버전 형식 (SPEC-18). 알 수 없으면 null. */
  fileType: FileType | null;
};

export type FileType = "PDF" | "DOCX" | "XLSX" | "PPTX";

/** Backend `DocumentResponse` (등록 `201`, 상세 `200`). 저장 경로·저장 키·해시는 포함되지 않는다. */
export type DocumentDetail = {
  id: number;
  title: string;
  description: string | null;
  status: DocumentStatus;
  createdBy: DocumentAuthor;
  createdAt: string;
  updatedAt: string;
  latestVersion: {
    versionNumber: number;
    originalFilename: string;
    sizeBytes: number;
    uploadedAt: string;
    /** 버전 정보 (SPEC-09): v1은 초기 버전 내용, v2 이상은 변경사항. 없으면 null. */
    versionNote: string | null;
  };
  /** 최종 확정 정보 (SPEC-07 FR-7). ACTIVE이면 null. */
  finalization: DocumentFinalization | null;
};

export type DocumentFinalization = {
  versionNumber: number;
  finalizedBy: DocumentAuthor;
  finalizedAt: string;
};

/** 등록 요청. 작성자·상태·버전 번호는 서버가 정하므로 보내지 않는다. */
export type CreateDocumentInput = {
  title: string;
  description: string | null;
  /** v1 초기 버전 내용 (SPEC-09, 선택) */
  versionNote: string | null;
  file: File;
};
