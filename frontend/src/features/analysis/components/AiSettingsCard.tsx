"use client";

import { useCallback, useEffect, useRef, useState } from "react";

import { Button } from "@/components/ui/Button";
import { Card, CardBody, CardHeader } from "@/components/ui/Card";
import { Skeleton } from "@/components/ui/States";
import { Badge } from "@/components/ui/StatusBadge";
import { useToast } from "@/components/ui/Toast";
import { useAuth } from "@/features/auth/AuthProvider";
import { failureMessage, isUnauthenticated } from "@/features/projects/errors";
import { formatDateTime } from "@/features/projects/format";

import { fetchAiSettings, updateAiSettings } from "../api";
import type { AiSettings } from "../types";

/**
 * 프로젝트 AI 기능 설정 (SPEC-11 §3). 기본 꺼짐. Owner만 켜고 끌 수 있으며, 켤 때 외부 전송 안내에 동의해야 한다. 서비스에서
 * AI를 쓸 수 없으면(관리자 미설정) 카드를 표시하지 않는다.
 */
export function AiSettingsCard({ projectId }: { projectId: string }) {
  const { refresh } = useAuth();
  const toast = useToast();
  const [settings, setSettings] = useState<{ key: string; value: AiSettings | null } | null>(null);
  const [consent, setConsent] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const savingRef = useRef(false);

  useEffect(() => {
    let active = true;
    fetchAiSettings(projectId).then(
      (value) => active && setSettings({ key: projectId, value }),
      (failure: unknown) => {
        if (isUnauthenticated(failure)) {
          void refresh();
        }
        if (active) {
          setSettings({ key: projectId, value: null });
        }
      },
    );
    return () => {
      active = false;
    };
  }, [projectId, refresh]);

  const save = useCallback(
    async (enabled: boolean) => {
      if (savingRef.current) {
        return;
      }
      savingRef.current = true;
      setSaving(true);
      setError(null);
      try {
        const value = await updateAiSettings(projectId, enabled, enabled && consent);
        setSettings({ key: projectId, value });
        setConsent(false);
        toast(enabled ? "AI 기능을 켰습니다." : "AI 기능을 껐습니다.");
      } catch (failure) {
        if (isUnauthenticated(failure)) {
          void refresh();
        }
        setError(failureMessage(failure));
      } finally {
        savingRef.current = false;
        setSaving(false);
      }
    },
    [projectId, consent, refresh, toast],
  );

  const current = settings?.key === projectId ? settings.value : undefined;
  if (current === undefined) {
    return (
      <Card>
        <CardBody>
          <Skeleton className="h-4 w-1/2" />
        </CardBody>
      </Card>
    );
  }
  if (current === null || (!current.available && !current.enabled)) {
    return null;
  }
  return (
    <Card aria-labelledby="ai-settings-heading">
      <CardHeader
        title={
          <span className="flex items-center gap-2">
            AI 문서 분석 <Badge tone={current.enabled ? "success" : "neutral"}>{current.enabled ? "켜짐" : "꺼짐"}</Badge>
          </span>
        }
        titleId="ai-settings-heading"
        description="PDF 새 버전을 올리면 AI가 요약하고, 버전 비교 결과를 요약합니다."
      />
      <CardBody className="flex flex-col gap-3 text-sm">
        <p className="text-ink-muted">
          켜면 이 프로젝트에 올린 PDF의 텍스트가 외부 AI 서비스({current.provider}, 모델 {current.model})로 전송되어 처리됩니다. 민감한
          정보가 있는 문서는 올리기 전에 확인해 주세요. AI 결과는 참고용이며 틀릴 수 있습니다.
        </p>
        {current.enabled && current.enabledAt && (
          <p className="text-xs text-ink-muted">
            {current.enabledByName ?? "오너"}님이 <time dateTime={current.enabledAt}>{formatDateTime(current.enabledAt)}</time>에 켰습니다.
          </p>
        )}
        {current.canManage ? (
          current.enabled ? (
            <Button size="sm" onClick={() => void save(false)} disabled={saving} className="self-start">
              {saving ? "저장 중..." : "AI 기능 끄기"}
            </Button>
          ) : (
            <>
              <label className="flex items-start gap-2">
                <input
                  type="checkbox"
                  checked={consent}
                  onChange={(event) => setConsent(event.target.checked)}
                  className="mt-0.5 size-4 accent-brand-600"
                />
                <span>문서 내용이 외부 AI 서비스로 전송된다는 점을 확인했고, 구성원에게 알렸습니다.</span>
              </label>
              <Button size="sm" variant="primary" onClick={() => void save(true)} disabled={saving || !consent} className="self-start">
                {saving ? "저장 중..." : "AI 기능 켜기"}
              </Button>
            </>
          )
        ) : (
          <p className="text-xs text-ink-muted">설정은 프로젝트 오너만 바꿀 수 있습니다.</p>
        )}
        {error && (
          <p role="alert" className="text-sm text-danger-700">
            {error}
          </p>
        )}
      </CardBody>
    </Card>
  );
}
