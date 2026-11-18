/** 버전 정보 표시 문구 (SPEC-09 FR-7): v1은 '초기 버전 내용', v2 이상은 직전 버전 대비 '변경사항'. */
export function versionNoteLabel(versionNumber: number): string {
  return versionNumber <= 1 ? "초기 버전 내용" : "변경사항";
}

/** 버전 정보가 없을 때의 빈 상태 문구. SPEC-09 이전에 올린 버전도 여기에 해당한다. */
export function versionNoteEmptyText(versionNumber: number): string {
  return versionNumber <= 1 ? "작성된 초기 버전 내용이 없습니다." : "작성된 변경사항이 없습니다.";
}
