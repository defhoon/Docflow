import type { Activity } from "@/features/activity/types";
import type { DocumentAuthor, DocumentStatus, FileType } from "@/features/documents/types";

/** Backend `HomeDocumentResponse` (SPEC-18) */
export type HomeDocument = {
  id: number;
  title: string;
  status: DocumentStatus;
  project: { id: number; name: string };
  latestVersionNumber: number;
  finalVersionNumber: number | null;
  updatedAt: string;
  latestUploader: DocumentAuthor;
  fileType: FileType | null;
};

/** Backend `HomeResponse` (`GET /api/home`, SPEC-18). 현재 사용자가 접근할 수 있는 프로젝트만 기준으로 한다. */
export type HomeSummary = {
  projectCount: number;
  activeDocumentCount: number;
  finalizedDocumentCount: number;
  recentDocuments: HomeDocument[];
  recentActivities: Activity[];
};
