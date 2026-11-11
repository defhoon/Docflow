"use client";

import Link from "next/link";
import { useParams } from "next/navigation";

export function ProjectBackLink() {
  const { projectId } = useParams<{ projectId: string }>();
  return (
    <Link href={`/projects/${encodeURIComponent(projectId)}`} className="w-fit text-sm text-ink-muted hover:text-ink">
      <span aria-hidden="true">← </span>프로젝트로 돌아가기
    </Link>
  );
}
