import { apiRequest } from "@/lib/api-client";

export type InvitationStatus = "ACTIVE" | "USED" | "EXPIRED" | "REVOKED";

/** Backend `InvitationResponse` (#26). 코드 원문·해시는 포함되지 않는다. */
export type Invitation = {
  id: number;
  note: string | null;
  status: InvitationStatus;
  grantsAdmin: boolean;
  createdAt: string;
  expiresAt: string;
  usedAt: string | null;
  usedByDisplayName: string | null;
};

export type IssuedInvitation = { id: number; code: string; expiresAt: string };

export function fetchInvitations(): Promise<Invitation[]> {
  return apiRequest<Invitation[]>("/api/admin/invitations");
}

export function issueInvitation(expiresInHours: number, note: string | null): Promise<IssuedInvitation> {
  return apiRequest<IssuedInvitation>("/api/admin/invitations", { method: "POST", body: { expiresInHours, note } });
}

export function revokeInvitation(id: number): Promise<void> {
  return apiRequest<void>(`/api/admin/invitations/${id}/revoke`, { method: "POST" });
}
