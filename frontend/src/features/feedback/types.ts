/** Backend `FeedbackResponse` (SPEC-08 §6). */
export type Feedback = {
  id: number;
  versionNumber: number;
  author: { id: number; displayName: string };
  content: string;
  createdAt: string;
};
