"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useCallback, useLayoutEffect, useRef, useState } from "react";

import { useAuth } from "@/features/auth/AuthProvider";
import { ApiError } from "@/lib/api-client";

import { createProject } from "../api";
import { failureMessage, isUnauthenticated } from "../errors";
import { type ProjectFieldErrors, validateProjectInput } from "../validation";

export function ProjectCreateForm() {
  const router = useRouter();
  const { refresh } = useAuth();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [fieldErrors, setFieldErrors] = useState<ProjectFieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  // 상태 반영 전 연속 제출도 막는다.
  const submittingRef = useRef(false);
  // cacheComponents 사용 시 Next.js는 다른 경로로 이동해도 이 화면을 언마운트하지 않고 <Activity>로 숨긴다.
  // 다시 진입하면 같은 인스턴스의 상태가 그대로 보이므로, 생성에 성공한 폼은 숨겨질 때 초기화한다.
  const visibleRef = useRef(false);
  const resetOnHideRef = useRef(false);

  const resetForm = useCallback(() => {
    submittingRef.current = false;
    setSubmitting(false);
    setName("");
    setDescription("");
    setFieldErrors({});
    setFormError(null);
  }, []);

  // 화면이 숨겨지기 전에 동기적으로 초기화하여 다시 진입할 때 이전 상태가 잠깐 보이지 않게 한다.
  useLayoutEffect(() => {
    visibleRef.current = true;
    return () => {
      visibleRef.current = false;
      if (resetOnHideRef.current) {
        resetOnHideRef.current = false;
        resetForm();
      }
    };
  }, [resetForm]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submittingRef.current) {
      return;
    }
    setFormError(null);
    const { value, errors } = validateProjectInput(name, description);
    setFieldErrors(errors);
    if (Object.keys(errors).length > 0) {
      return;
    }

    submittingRef.current = true;
    setSubmitting(true);
    try {
      const created = await createProject(value);
      if (visibleRef.current) {
        // 이동하는 동안 다시 제출되지 않도록 제출 상태를 유지하고, 화면이 숨겨질 때 초기화한다.
        resetOnHideRef.current = true;
      } else {
        // 요청 중에 이미 다른 화면으로 이동했으면 바로 초기화한다.
        resetForm();
      }
      router.push(`/projects/${created.id}`);
      return;
    } catch (error) {
      // 숨겨진 상태(Activity)에서도 실패 결과를 반영해야 다시 진입했을 때 제출할 수 있다.
      // 언마운트된 뒤의 상태 갱신은 React가 무시한다.
      if (isUnauthenticated(error)) {
        void refresh();
      }
      if (error instanceof ApiError && error.status === 400 && error.fieldErrors.length > 0) {
        const serverErrors: ProjectFieldErrors = {};
        for (const fieldError of error.fieldErrors) {
          if ((fieldError.field === "name" || fieldError.field === "description") && !serverErrors[fieldError.field]) {
            serverErrors[fieldError.field] = fieldError.message;
          }
        }
        setFieldErrors(serverErrors);
      } else {
        setFormError(failureMessage(error));
      }
    }
    submittingRef.current = false;
    setSubmitting(false);
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="project-name">프로젝트 이름</label>
        <input
          id="project-name"
          type="text"
          name="name"
          value={name}
          onChange={(event) => setName(event.target.value)}
          aria-invalid={Boolean(fieldErrors.name)}
          aria-describedby={fieldErrors.name ? "project-name-error" : undefined}
          className="rounded border border-zinc-300 px-3 py-2"
        />
        {fieldErrors.name && (
          <span id="project-name-error" className="text-red-700">
            {fieldErrors.name}
          </span>
        )}
      </div>
      <div className="flex flex-col gap-1 text-sm">
        <label htmlFor="project-description">설명 (선택)</label>
        <textarea
          id="project-description"
          name="description"
          rows={5}
          value={description}
          onChange={(event) => setDescription(event.target.value)}
          aria-invalid={Boolean(fieldErrors.description)}
          aria-describedby={fieldErrors.description ? "project-description-error" : undefined}
          className="rounded border border-zinc-300 px-3 py-2"
        />
        {fieldErrors.description && (
          <span id="project-description-error" className="text-red-700">
            {fieldErrors.description}
          </span>
        )}
      </div>
      {formError && (
        <p role="alert" className="text-sm font-medium text-red-700">
          {formError}
        </p>
      )}
      <button
        type="submit"
        disabled={submitting}
        className="rounded bg-zinc-900 px-4 py-2 font-medium text-white disabled:opacity-50"
      >
        {submitting ? "생성 중..." : "프로젝트 생성"}
      </button>
    </form>
  );
}
