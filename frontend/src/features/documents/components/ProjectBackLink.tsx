"use client";

import Link from "next/link";
import { useParams } from "next/navigation";

export function ProjectBackLink() {
  const { projectId } = useParams<{ projectId: string }>();
  return (
    <Link href={`/projects/${encodeURIComponent(projectId)}`} className="text-sm text-zinc-600 underline">
      프로젝트로 돌아가기
    </Link>
  );
}
