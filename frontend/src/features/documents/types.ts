/** 문서 상태 (`spec.md` §7.1). SPEC-03에서는 등록 시 항상 DRAFT이며 상태 변경 기능은 없다. */
export type DocumentStatus = "DRAFT" | "IN_REVIEW" | "CHANGES_REQUESTED" | "RE_REVIEW" | "APPROVED";

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
};

/** 등록 요청. 작성자·상태·버전 번호는 서버가 정하므로 보내지 않는다. */
export type CreateDocumentInput = {
  title: string;
  description: string | null;
  file: File;
};
