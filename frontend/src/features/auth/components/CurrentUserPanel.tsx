"use client";

import { useAuth } from "../AuthProvider";
import { LogoutButton } from "./LogoutButton";

export function CurrentUserPanel() {
  const { user } = useAuth();
  if (!user) {
    return null;
  }
  return (
    <div className="flex items-center justify-between gap-4">
      <div>
        <p className="font-medium">{user.displayName}</p>
        <p className="text-sm text-zinc-600">{user.email}</p>
      </div>
      <LogoutButton />
    </div>
  );
}
