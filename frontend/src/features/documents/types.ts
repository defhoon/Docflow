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
  status: DocumentStatus;
  createdBy: DocumentAuthor;
  createdAt: string;
  latestVersionNumber: number;
  /** 최종 확정 버전 번호 (SPEC-07). ACTIVE이면 null. */
  finalVersionNumber: number | null;
};

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
  file: File;
};
