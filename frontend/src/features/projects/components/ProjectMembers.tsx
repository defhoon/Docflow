"use client";

import { useCallback, useEffect, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";

import { fetchMembers, removeMember } from "../api";
import { errorCode, failureMessage, isUnauthenticated } from "../errors";
import type { Member, ProjectRole } from "../types";
import { AddMemberForm } from "./AddMemberForm";

const ROLE_LABELS: Record<ProjectRole, string> = { OWNER: "Owner", MEMBER: "Member" };

type MembersResult = { kind: "error"; message: string } | { kind: "success"; members: Member[] };

type ProjectMembersProps = {
  projectId: string;
  myRole: ProjectRole;
  /** 권한이 바뀌었거나(403) 프로젝트에 접근할 수 없게 되면(404) 상세를 다시 불러온다. */
  onProjectChanged: () => void;
};

/**
 * 프로젝트 상세의 구성원 섹션 (SPEC-02 §8.2). 표시 이름과 역할만 보여 준다(이메일·추가 시각 미표시).
 * 추가·제거 UI는 `myRole`이 OWNER일 때만 보이며, 최종 권한 판단은 서버가 한다.
 */
export function ProjectMembers({ projectId, myRole, onProjectChanged }: ProjectMembersProps) {
  const { refresh } = useAuth();
  const canManage = myRole === "OWNER";
  const [attempt, setAttempt] = useState(0);
  const [result, setResult] = useState<{ attempt: number; value: MembersResult } | null>(null);
  const [removingUserId, setRemovingUserId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const state = result?.attempt === attempt ? result.value : null;

  useEffect(() => {
    // 언마운트되거나 다시 불러오면 이전 요청의 응답은 반영하지 않는다.
    let active = true;
    fetchMembers(projectId).then(
      (members) => {
        if (active) {
          setResult({ attempt, value: { kind: "success", members } });
        }
      },
      (error: unknown) => {
        if (!active) {
          return;
        }
        if (isUnauthenticated(error)) {
          void refresh();
        } else if (errorCode(error) === "PROJECT_NOT_FOUND") {
          onProjectChanged();
        }
        setResult({ attempt, value: { kind: "error", message: failureMessage(error) } });
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, attempt, refresh, onProjectChanged]);

  const reload = useCallback(() => setAttempt((current) => current + 1), []);

  async function handleRemove(member: Member) {
    if (removingUserId !== null) {
      return;
    }
    if (!window.confirm(`${member.displayName}님을 프로젝트에서 제거할까요?`)) {
      return;
    }
    setNotice(null);
    setRemovingUserId(member.userId);
    try {
      await removeMember(projectId, member.userId);
      reload();
    } catch (error) {
      if (isUnauthenticated(error)) {
        void refresh();
      } else if (errorCode(error) === "PROJECT_MEMBER_NOT_FOUND") {
        setNotice("이미 제거된 구성원입니다.");
        reload();
      } else if (errorCode(error) === "ACCESS_DENIED") {
        setNotice("권한이 없습니다.");
        onProjectChanged();
      } else if (errorCode(error) === "PROJECT_NOT_FOUND") {
        onProjectChanged();
      } else {
        setNotice(failureMessage(error));
      }
    } finally {
      setRemovingUserId(null);
    }
  }

  return (
    <section aria-labelledby="project-members-heading" className="flex flex-col gap-3">
      <h3 id="project-members-heading" className="text-sm font-semibold text-zinc-500">
        구성원
      </h3>
      {canManage && (
        <AddMemberForm
          projectId={projectId}
          onAdded={() => {
            setNotice(null);
            reload();
          }}
          onProjectChanged={onProjectChanged}
        />
      )}
      {notice && (
        <p role="alert" className="text-sm font-medium text-red-700">
          {notice}
        </p>
      )}
      {state === null && (
        <p role="status" className="text-sm text-zinc-500">
          구성원을 불러오는 중...
        </p>
      )}
      {state?.kind === "error" && (
        <div role="alert" className="flex flex-col items-start gap-2 text-sm text-red-700">
          <p>구성원을 불러오지 못했습니다.</p>
          <p>{state.message}</p>
          <button type="button" onClick={reload} className="rounded border border-zinc-300 px-3 py-1 text-zinc-800">
            다시 시도
          </button>
        </div>
      )}
      {state?.kind === "success" && (
        <ul aria-label="구성원 목록" className="flex flex-col divide-y divide-zinc-200 rounded-lg border border-zinc-200">
          {state.members.map((member) => (
            <li key={member.userId} className="flex items-center justify-between gap-4 px-4 py-2 text-sm">
              <span className="break-all">
                {member.displayName}
                <span className="ml-2 text-zinc-500">{ROLE_LABELS[member.role]}</span>
              </span>
              {canManage && member.role === "MEMBER" && (
                <button
                  type="button"
                  onClick={() => void handleRemove(member)}
                  disabled={removingUserId !== null}
                  aria-label={`${member.displayName} 제거`}
                  className="rounded border border-zinc-300 px-3 py-1 text-zinc-800 disabled:opacity-50"
                >
                  제거
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
