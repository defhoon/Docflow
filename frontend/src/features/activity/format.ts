import type { Activity } from "./types";

/** 활동 한 줄 설명. 문서 제목은 링크로 따로 표시하므로 여기에는 넣지 않는다. */
export function describeActivity(activity: Activity): string {
  const version = activity.versionNumber ? `v${activity.versionNumber}` : "";
  switch (activity.action) {
    case "DOCUMENT_CREATED":
      return "문서를 등록했습니다";
    case "VERSION_CREATED":
      return `새 버전 ${version}을 올렸습니다`;
    case "FEEDBACK_CREATED":
      return `${version}에 피드백을 남겼습니다`;
    case "DOCUMENT_FINALIZED":
      return `${version}을 최종 버전으로 확정했습니다`;
  }
}

/** 상대 시간(방금, N분 전, N시간 전, N일 전). 7일이 넘으면 날짜. */
export function relativeTime(iso: string, now: Date = new Date()): string {
  const diffSeconds = Math.max(0, Math.round((now.getTime() - new Date(iso).getTime()) / 1000));
  if (diffSeconds < 60) {
    return "방금";
  }
  const minutes = Math.floor(diffSeconds / 60);
  if (minutes < 60) {
    return `${minutes}분 전`;
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return `${hours}시간 전`;
  }
  const days = Math.floor(hours / 24);
  if (days < 7) {
    return `${days}일 전`;
  }
  return new Date(iso).toLocaleDateString("ko-KR");
}
