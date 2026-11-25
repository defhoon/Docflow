// UI/UX 2.0 통합 검증 (Issue #73). 실제 Backend(local, AI 비활성, Office 미리보기 끔) + Frontend(standalone) + Chromium.
// AI 상태는 실제 API를 부르지 않고 브라우저에서 응답을 바꿔(Stub) 확인한다.
const { chromium } = require("playwright");
const fs = require("fs");
const path = require("path");

// 로컬 시드 계정 비밀번호는 환경 변수로 받는다(저장소에 값을 두지 않음).
const SEED_PASSWORD = process.env.DEV_SEED_PASSWORD;
const NEW_USER_PASSWORD = process.env.E2E_NEW_USER_PASSWORD;
if (!SEED_PASSWORD || !NEW_USER_PASSWORD) throw new Error("DEV_SEED_PASSWORD and E2E_NEW_USER_PASSWORD are required");
const APP = "http://127.0.0.1:3000";
const API = "http://localhost:8080";
const F = path.join(__dirname, "files");
const OUT = process.argv[2] || path.join(__dirname, "verify-shots");
fs.mkdirSync(OUT, { recursive: true });
const seed = JSON.parse(fs.readFileSync(path.join(__dirname, "seed.json"), "utf8"));
const results = [];
function record(id, name, pass, detail = "") {
  results.push({ id, name, pass: Boolean(pass), detail });
  console.log(`${pass ? "PASS" : "FAIL"} ${id} ${name}${detail ? " — " + detail : ""}`);
}

// ---- API client (cookie jar + CSRF) ----
function client() {
  const jar = {};
  const cookie = () => Object.entries(jar).map(([k, v]) => `${k}=${v}`).join("; ");
  async function req(method, p, body, isForm) {
    const headers = { Cookie: cookie(), Accept: "application/json" };
    if (jar["XSRF-TOKEN"]) headers["X-XSRF-TOKEN"] = jar["XSRF-TOKEN"];
    if (body && !isForm) headers["Content-Type"] = "application/json";
    const res = await fetch(API + p, { method, headers, body: isForm ? body : body ? JSON.stringify(body) : undefined });
    for (const c of res.headers.getSetCookie()) {
      const [kv] = c.split(";");
      const i = kv.indexOf("=");
      const v = kv.slice(i + 1);
      if (v) jar[kv.slice(0, i)] = v;
      else delete jar[kv.slice(0, i)];
    }
    const text = await res.text();
    return { status: res.status, body: text ? JSON.parse(text) : null };
  }
  return {
    req,
    async login(email, password = SEED_PASSWORD) {
      await req("GET", "/api/auth/csrf");
      const r = await req("POST", "/api/auth/login", { email, password });
      await req("GET", "/api/auth/csrf");
      return r;
    },
  };
}
function form(file, fields = {}) {
  const f = new FormData();
  for (const [k, v] of Object.entries(fields)) f.append(k, v);
  f.append("file", new Blob([fs.readFileSync(path.join(F, file))], { type: "application/pdf" }), file);
  return f;
}

async function login(page, email, password = SEED_PASSWORD) {
  await page.goto(APP + "/login");
  await page.fill("input[type=email]", email);
  await page.fill("input[type=password]", password);
  await page.press("input[type=password]", "Enter");
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 15000 });
}
const settle = (page, ms = 1200) => page.waitForTimeout(ms);
const docUrl = (d, v) => `${APP}/projects/${seed.project}/documents/${d}${v ? `?v=${v}` : ""}`;

(async () => {
  const browser = await chromium.launch();
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, acceptDownloads: true });
  const page = await ctx.newPage();
  const consoleErrors = [];
  page.on("console", (m) => m.type() === "error" && !/401|403|404|409|500|503/.test(m.text()) && consoleErrors.push(m.text()));
  page.on("pageerror", (e) => consoleErrors.push(String(e)));
  const author = client();
  await author.login("author@docflow.local");

  try {
    // S1 로그인·비밀번호 표시
    await page.goto(APP + "/login");
    await page.fill("input[type=email]", "author@docflow.local");
    await page.fill("input[type=password]", "wrong-password");
    await page.getByRole("button", { name: "비밀번호 표시" }).click();
    const typeShown = await page.locator("#login-password").getAttribute("type");
    await page.press("#login-password", "Enter");
    // Next.js 경로 안내(next-route-announcer)도 role=alert이므로 내용 있는 안내만 본다.
    const loginError = await page.locator("p[role=alert]").first().textContent({ timeout: 8000 }).catch(() => "");
    record("S1-a", "잘못된 비밀번호는 오류 안내, 비밀번호 표시 전환", typeShown === "text" && /이메일|비밀번호/.test(loginError), `type=${typeShown}, alert="${loginError.trim()}"`);
    await login(page, "author@docflow.local");
    record("S1-b", "로그인 후 홈으로 이동", new URL(page.url()).pathname === "/");

    // S2 초대코드 가입 → S3 권한
    const invite = await author.req("POST", "/api/admin/invitations", { expiresInHours: 1, note: "통합 검증" });
    const code = invite.body?.code;
    const page2 = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
    await page2.goto(APP + "/signup");
    await page2.fill("#signup-inviteCode", code || "");
    await page2.fill("#signup-email", "newbie@docflow.local");
    await page2.fill("#signup-displayName", "새구성원");
    await page2.fill("#signup-password", NEW_USER_PASSWORD);
    await page2.fill("#signup-passwordConfirm", NEW_USER_PASSWORD);
    await page2.getByRole("button", { name: /가입/ }).click();
    await page2.waitForURL((u) => u.pathname.startsWith("/login"), { timeout: 10000 }).catch(() => undefined);
    const signedUp = new URL(page2.url()).pathname.startsWith("/login");
    await login(page2, "newbie@docflow.local", NEW_USER_PASSWORD);
    await settle(page2, 1500);
    const zeroProjects = await page2.getByRole("region", { name: "요약" }).textContent();
    record("S2", "초대코드로 가입하고 로그인한다", invite.status === 201 && signedUp, `invite=${invite.status}, home stats="${zeroProjects.replace(/\s+/g, " ").trim()}"`);
    await page2.goto(docUrl(seed.meeting));
    await settle(page2);
    const denied = await page2.locator("div[role=alert]").first().textContent().catch(() => "");
    const homeAfter = zeroProjects.includes("0");
    record("S3", "구성원이 아닌 사용자는 다른 프로젝트 문서에 접근할 수 없다(홈도 0)", /찾을 수 없습니다/.test(denied) && homeAfter, `alert="${denied.trim()}"`);
    await page2.context().close();

    // S4 파일 우선 문서 등록
    await page.goto(`${APP}/projects/${seed.project}/documents/new`);
    await page.setInputFiles("input[type=file]", path.join(F, "meeting-v1.pdf"));
    const autoTitle = await page.getByLabel("문서 제목").inputValue();
    await page.getByLabel("문서 제목").fill("통합 검증 문서");
    await page.setInputFiles("input[type=file]", path.join(F, "spec-v1.pdf"));
    const keptTitle = await page.getByLabel("문서 제목").inputValue();
    await page.getByRole("button", { name: "문서 등록" }).click();
    await page.waitForURL(/\/documents\/\d+/, { timeout: 15000 });
    const newDocId = Number(page.url().match(/documents\/(\d+)/)[1]);
    await settle(page, 2000);
    record("S4", "파일 우선 등록: 파일 이름 제목 채움, 직접 고친 제목 유지, 등록 후 상세 이동", autoTitle === "meeting-v1" && keptTitle === "통합 검증 문서" && (await page.getByRole("heading", { level: 1 }).textContent()) === "통합 검증 문서", `auto="${autoTitle}", kept="${keptTitle}", doc=${newDocId}`);

    // S5 새 버전 업로드(모달)
    await page.getByRole("button", { name: "새 버전" }).click();
    const dialog = page.getByRole("dialog", { name: "새 버전 등록" });
    await dialog.locator("input[type=file]").setInputFiles(path.join(F, "spec-v2.pdf"));
    await dialog.getByLabel(/직전 버전 대비 변경사항/).fill("보안 요구사항 추가");
    await dialog.getByRole("button", { name: "새 버전 등록" }).click();
    await page.waitForURL(/\?v=2/, { timeout: 15000 });
    await settle(page, 1500);
    const v2Current = await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2 최신/ }).getAttribute("aria-current");
    record("S5", "새 버전 등록 후 새 최신 버전(v2)이 선택된다", v2Current === "true" && (await dialog.count()) === 0);

    // S6 버전별 피드백
    await page.getByLabel("v2에 피드백 남기기").fill("v2 통합 검증 피드백");
    await page.getByRole("button", { name: "피드백 등록" }).click();
    await page.getByText("v2 통합 검증 피드백").first().waitFor({ timeout: 8000 });
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v1/ }).click();
    await settle(page);
    const v1HasV2Feedback = await page.getByText("v2 통합 검증 피드백").count();
    const v1Empty = await page.getByText("v1에 남긴 피드백이 없습니다.").count();
    record("S6", "피드백은 작성한 버전에만 보인다", v1HasV2Feedback === 0 && v1Empty === 1);

    // S7 빠른 버전 전환·응답 순서 역전
    await page.route(new RegExp(`/documents/${newDocId}/versions/1/feedbacks$`), async (route) => {
      await new Promise((r) => setTimeout(r, 1500));
      await route.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify([{ id: 999, versionNumber: 1, author: { id: 1, displayName: "지연" }, content: "늦게 도착한 v1 응답", createdAt: new Date().toISOString() }]) });
    });
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2/ }).click();
    await page.waitForTimeout(100);
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v1/ }).click();
    await page.waitForTimeout(50);
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2/ }).click();
    await page.waitForTimeout(2200);
    const lateShown = await page.getByText("늦게 도착한 v1 응답").count();
    const viewerOnV2 = await page.getByRole("region", { name: "v2 문서 보기" }).count();
    record("S7", "빠른 전환에서 늦게 온 이전 버전 응답이 현재 화면을 덮지 않는다", lateShown === 0 && viewerOnV2 === 1 && (await page.getByText("v2 통합 검증 피드백").count()) > 0);
    await page.unroute(new RegExp(`/documents/${newDocId}/versions/1/feedbacks$`));

    // S8 작성 중 버전 전환
    await page.getByLabel("v2에 피드백 남기기").fill("작성 중인 v2 초안");
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v1/ }).click();
    await settle(page, 600);
    const v1Value = await page.getByLabel("v1에 피드백 남기기").inputValue();
    const draftMark = await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2/ }).getByText("작성 중").count();
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2/ }).click();
    await settle(page, 600);
    const restored = await page.getByLabel("v2에 피드백 남기기").inputValue();
    record("S8", "작성 중 버전을 바꿔도 초안이 유지·복원되고 다른 버전 입력창은 비어 있다", v1Value === "" && draftMark === 1 && restored === "작성 중인 v2 초안");
    await page.getByLabel("v2에 피드백 남기기").fill("");

    // S9·S10·S11·S12 비교
    await page.goto(`${APP}/projects/${seed.project}/documents/${seed.meeting}/compare`);
    await page.locator("#pdf-target-page-1").waitFor({ timeout: 15000 });
    await settle(page, 1500);
    const pdfDefault = await page.getByRole("button", { name: "PDF" }).getAttribute("aria-pressed");
    const syncDefault = await page.getByRole("button", { name: "동기 스크롤" }).getAttribute("aria-pressed");
    const hlDefault = await page.getByLabel("변경 강조 표시").isChecked();
    const pageCounts = await page.locator("[data-side] p").allTextContents();
    record("S9", "PDF 비교가 기본이며 최신·직전 버전(v2→v3), 동기 스크롤·강조 기본 켜짐", pdfDefault === "true" && syncDefault === "true" && hlDefault && /compare$/.test(new URL(page.url()).pathname), `columns=${pageCounts.map((t) => t.replace(/\s+/g, " ")).join(" | ")}`);
    await page.screenshot({ path: `${OUT}/v-compare-pdf.png` });
    // 서로 다른 페이지 수: v3를 3쪽으로 스크롤하면 v2(1쪽)는 끝으로
    const sync = await page.evaluate(async () => {
      const [base, target] = document.querySelectorAll("[data-viewer-scroll]");
      const p3 = target.querySelector("#pdf-target-page-3");
      target.scrollTop = p3.offsetTop + 50;
      target.dispatchEvent(new Event("scroll"));
      await new Promise((r) => setTimeout(r, 400));
      const atEnd = Math.abs(base.scrollTop - (base.scrollHeight - base.clientHeight)) <= 2;
      target.scrollTop = 0;
      target.dispatchEvent(new Event("scroll"));
      await new Promise((r) => setTimeout(r, 400));
      return { atEnd, backToTop: base.scrollTop <= 2 };
    });
    record("S10", "페이지 수가 다른 문서(1쪽↔3쪽) 동기 스크롤: 없는 쪽은 끝, 돌아오면 다시 맞춤", sync.atEnd && sync.backToTop, JSON.stringify(sync));
    await page.getByRole("button", { name: "확대" }).click();
    await page.getByRole("button", { name: "확대" }).click();
    await page.getByRole("button", { name: /^회전/ }).click();
    await settle(page, 1200);
    const rel = async () => page.evaluate(() => {
      const pr = document.querySelector("#pdf-target-page-1").getBoundingClientRect();
      const hr = document.querySelector("#pdf-target-change-0").getBoundingClientRect();
      return { inside: hr.left >= pr.left && hr.right <= pr.right && hr.top >= pr.top && hr.bottom <= pr.bottom, x: +((hr.left - pr.left) / pr.width).toFixed(3), y: +((hr.top - pr.top) / pr.height).toFixed(3) };
    });
    const r1 = await rel();
    await page.getByRole("button", { name: "변경 패널 접기" }).click();
    await settle(page, 900);
    const r2 = await rel();
    await page.getByRole("button", { name: /변경 패널/ }).click();
    await settle(page, 900);
    record("S11", "확대·회전·패널 접기 후에도 강조가 같은 상대 위치(페이지 안)", r1.inside && r2.inside && Math.abs(r1.x - r2.x) < 0.01 && Math.abs(r1.y - r2.y) < 0.01, `${JSON.stringify(r1)} → ${JSON.stringify(r2)}`);
    await page.getByRole("button", { name: "폭 맞춤" }).click();
    for (let i = 0; i < 3; i++) await page.getByRole("button", { name: /^회전/ }).click();
    await page.getByRole("button", { name: /^추가 \d/ }).click();
    const addedCount = await page.getByRole("list", { name: "변경 목록" }).getByRole("button").count();
    await page.getByRole("button", { name: /^전체 \d/ }).click();
    await page.getByRole("button", { name: "다음 변경" }).click();
    await page.getByRole("button", { name: "다음 변경" }).click();
    const position = await page.getByText(/^\d+ \/ \d+$/).textContent();
    await page.getByRole("button", { name: "이전 변경" }).click();
    const position2 = await page.getByText(/^\d+ \/ \d+$/).textContent();
    record("S12", "변경 필터와 이전/다음 탐색", addedCount === 6 && position === "2 / 9" && position2 === "1 / 9", `added=${addedCount}, ${position} → ${position2}`);
    await page.getByRole("button", { name: "텍스트" }).click();
    await settle(page, 800);
    const textMode = (await page.getByText(/원본 PDF의 줄 번호가 아니라/).count()) === 1 && /mode=text/.test(page.url());
    record("S9-b", "텍스트 비교 전환과 번호 안내", textMode);
    await page.screenshot({ path: `${OUT}/v-compare-text.png` });

    // S13 AI 상태 (Stub, 실제 AI 호출 없음)
    const stubAi = async (summary, settings = { enabled: true, available: true, canManage: true }) => {
      await page.unrouteAll({ behavior: "ignoreErrors" });
      await page.route(/\/ai-settings$/, (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(settings) }));
      await page.route(/\/versions\/\d+\/summary$/, (r) => r.request().method() === "GET" ? r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify(summary) }) : r.abort());
    };
    const base = { errorCode: null, retryable: false, aiEnabled: true, supported: true, result: null, model: null, updatedAt: null, completedAt: null };
    await page.unrouteAll({ behavior: "ignoreErrors" });
    await page.goto(docUrl(seed.meeting));
    await settle(page, 1500);
    const aiOffTab = await page.getByRole("tab", { name: "AI 요약" }).count();
    await stubAi({ ...base, status: "NONE" });
    await page.goto(docUrl(seed.meeting)); await settle(page, 1500);
    await page.getByRole("tab", { name: "AI 요약" }).click();
    const noneOk = (await page.getByRole("button", { name: "AI 요약 만들기" }).count()) === 1 && (await page.getByText(/외부 AI 서비스로 전송됩니다/).count()) === 1;
    await stubAi({ ...base, status: "PROCESSING" });
    await page.goto(docUrl(seed.meeting)); await settle(page, 1500);
    await page.getByRole("tab", { name: "AI 요약" }).click();
    const loadingOk = (await page.getByText(/AI가 문서를 요약하고 있습니다/).count()) === 1;
    await stubAi({ ...base, status: "FAILED", errorCode: "AI_API_ERROR", retryable: true });
    await page.goto(docUrl(seed.meeting)); await settle(page, 1500);
    await page.getByRole("tab", { name: "AI 요약" }).click();
    const failedOk = (await page.getByText(/AI 요약을 만들지 못했습니다/).count()) === 1 && (await page.getByRole("button", { name: "다시 시도" }).count()) === 1;
    await stubAi({ ...base, status: "COMPLETED", model: "stub-model-from-server", completedAt: "2026-11-25T08:00:00Z", result: { summary: "스텁 요약 본문", keyPoints: [{ text: "부록 일정표 추가", pages: [2] }], keyFacts: [], meta: null } }, { enabled: false, available: false, canManage: true });
    await page.goto(docUrl(seed.meeting)); await settle(page, 1500);
    await page.getByRole("tab", { name: "AI 요약" }).click();
    const doneOk = (await page.getByText("스텁 요약 본문").count()) === 1 && (await page.getByText(/stub-model-from-server/).count()) === 1 && (await page.getByText("AI 생성 · 검토 필요").count()) >= 1;
    await page.getByRole("button", { name: "2쪽으로 이동" }).click();
    await settle(page, 1500);
    const pageInput = await page.getByLabel(/쪽 번호/).inputValue();
    await page.screenshot({ path: `${OUT}/v-doc-ai.png` });
    record("S13", "AI 비활성 시 탭 없음, 요청 전·진행·실패(재시도)·완료(서버 모델명·검토 필요)·쪽 이동", aiOffTab === 0 && noneOk && loadingOk && failedOk && doneOk && pageInput === "2", `off=${aiOffTab}, none=${noneOk}, processing=${loadingOk}, failed=${failedOk}, done=${doneOk}, page=${pageInput}`);
    await page.unrouteAll({ behavior: "ignoreErrors" });
    // 비교 화면 AI 변경 요약 탭
    await page.route(/\/ai-settings$/, (r) => r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ enabled: true, available: true, canManage: true }) }));
    await page.route(/\/compare\/summary/, (r) => r.request().method() === "GET" ? r.fulfill({ status: 200, contentType: "application/json", body: JSON.stringify({ ...base, status: "NONE" }) }) : r.abort());
    await page.goto(`${APP}/projects/${seed.project}/documents/${seed.meeting}/compare`); await settle(page, 2500);
    await page.getByRole("tab", { name: "AI 변경 요약" }).click();
    const cmpAi = (await page.getByRole("button", { name: "AI 변경 요약 만들기" }).count()) === 1;
    record("S13-b", "비교 화면 오른쪽 패널에 AI 변경 요약 탭(요청 전 상태)", cmpAi);
    await page.unrouteAll({ behavior: "ignoreErrors" });

    // S15 Office·PDF 로딩 실패
    await page.goto(docUrl(seed.guide)); await settle(page, 2500);
    const officeOk = (await page.getByText("이 환경에서는 Office 문서 미리보기를 지원하지 않습니다.").count()) === 1 && (await page.getByRole("button", { name: "확대" }).count()) === 0;
    await page.screenshot({ path: `${OUT}/v-office.png` });
    await page.route(/\/versions\/\d+\/preview$/, (r) => r.fulfill({ status: 500, contentType: "application/json", body: JSON.stringify({ code: "DOCUMENT_FILE_UNAVAILABLE", message: "x" }) }));
    await page.goto(docUrl(seed.meeting)); await settle(page, 2000);
    const pdfFail = (await page.getByText("문서를 표시하지 못했습니다.").count()) === 1 && (await page.getByRole("button", { name: "다시 시도" }).count()) === 1 && (await page.getByRole("button", { name: "확대" }).count()) === 0;
    await page.unrouteAll({ behavior: "ignoreErrors" });
    await page.getByRole("button", { name: "다시 시도" }).click();
    await page.getByRole("toolbar", { name: "문서 보기 도구" }).waitFor({ timeout: 10000 });
    record("S15", "Office 미지원 안내(PDF 도구 숨김), PDF 실패 시 오류·다시 시도·원본 받기, 재시도 성공", officeOk && pdfFail);

    // S14 최종본 확정(과거 버전·최신 버전)과 읽기 전용
    const reviewer = await (await browser.newContext({ viewport: { width: 1440, height: 900 } })).newPage();
    await login(reviewer, "reviewer@docflow.local");
    await reviewer.goto(docUrl(newDocId)); await settle(reviewer, 1500);
    const memberNoFinalize = (await reviewer.getByRole("button", { name: /최종본 확정/ }).count()) === 0;
    await page.goto(docUrl(newDocId)); await settle(page, 1500);
    await page.getByRole("button", { name: /최종본 확정/ }).click();
    const fdialog = page.getByRole("dialog", { name: "최종본 확정" });
    await fdialog.getByRole("radio", { name: /v1/ }).check();
    const warn = (await fdialog.getByText("최신 버전(v2)이 아닙니다.").count()) === 1;
    const disabledBefore = await fdialog.getByRole("button", { name: "v1 최종 확정" }).isDisabled();
    await fdialog.getByRole("checkbox").check();
    await fdialog.getByRole("button", { name: "v1 최종 확정" }).click();
    await page.getByText("v1이 최종본으로 확정되었습니다.").waitFor({ timeout: 10000 });
    await settle(page, 1200);
    const ro = {
      noUpload: (await page.getByRole("button", { name: "새 버전" }).count()) === 0,
      noComposer: (await page.getByLabel(/피드백 남기기/).count()) === 0,
      latestNote: (await page.getByText(/최신 버전\(v2\)은 최종본이 아닙니다/).count()) === 1,
      finalBtn: (await page.getByRole("button", { name: "최종본 v1 받기" }).count()) === 1,
    };
    await page.getByRole("tab", { name: "정보" }).click();
    ro.noDescEdit = (await page.getByRole("button", { name: /설명 (수정|추가)/ }).count()) === 0;
    await page.getByRole("list", { name: "버전 목록" }).getByRole("button", { name: /^v2/ }).click();
    await page.getByRole("toolbar", { name: "문서 보기 도구" }).waitFor({ timeout: 10000 });
    const [download] = await Promise.all([page.waitForEvent("download", { timeout: 10000 }), page.getByRole("button", { name: "v2 원본 받기" }).click()]);
    ro.pastDownload = download.suggestedFilename() === "spec-v2.pdf";
    ro.compareLink = (await page.getByRole("link", { name: "버전 비교" }).count()) === 1;
    const apiBlocked = await author.req("POST", `/api/projects/${seed.project}/documents/${newDocId}/versions/1/feedbacks`, { content: "확정 후" });
    ro.serverBlocks = apiBlocked.status === 409;
    await page.screenshot({ path: `${OUT}/v-doc-finalized.png` });
    record("S14-a", "과거 버전(v1) 확정: 경고·확인 체크·오너만 버튼, 확정 후 읽기 전용(서버도 409), 과거 버전 조회·다운로드·비교 유지", memberNoFinalize && warn && disabledBefore && Object.values(ro).every(Boolean), JSON.stringify({ memberNoFinalize, warn, disabledBefore, ...ro }));
    await reviewer.context().close();
    await page.goto(docUrl(seed.plan)); await settle(page, 1500);
    await page.getByRole("button", { name: /최종본 확정/ }).click();
    const latestDialog = page.getByRole("dialog", { name: "최종본 확정" });
    const noWarnLatest = (await latestDialog.getByText(/최신 버전\(v\d\)이 아닙니다/).count()) === 0;
    await latestDialog.getByRole("checkbox").check();
    await latestDialog.getByRole("button", { name: "v1 최종 확정" }).click();
    await page.getByText("v1이 최종본으로 확정되었습니다.").waitFor({ timeout: 10000 });
    record("S14-b", "최신 버전 확정(경고 없음)", noWarnLatest);

    // S16 동시 업로드·동시 확정 (API, 실제 서버 정합성)
    const reviewerApi = client();
    await reviewerApi.login("reviewer@docflow.local");
    const created = await author.req("POST", `/api/projects/${seed.project}/documents`, form("plan-v1.pdf", { title: "동시성 검증 문서" }), true);
    const cid = created.body.id;
    const uploads = await Promise.all([
      author.req("POST", `/api/projects/${seed.project}/documents/${cid}/versions`, form("meeting-v2.pdf"), true),
      reviewerApi.req("POST", `/api/projects/${seed.project}/documents/${cid}/versions`, form("meeting-v3.pdf"), true),
    ]);
    const versions = await author.req("GET", `/api/projects/${seed.project}/documents/${cid}/versions`);
    const numbers = versions.body.map((v) => v.versionNumber).sort();
    const finals = await Promise.all([
      author.req("POST", `/api/projects/${seed.project}/documents/${cid}/finalize`, { versionNumber: 2 }),
      author.req("POST", `/api/projects/${seed.project}/documents/${cid}/finalize`, { versionNumber: 3 }),
      reviewerApi.req("POST", `/api/projects/${seed.project}/documents/${cid}/finalize`, { versionNumber: 1 }),
    ]);
    const lateUpload = await reviewerApi.req("POST", `/api/projects/${seed.project}/documents/${cid}/versions`, form("meeting-v1.pdf"), true);
    const statuses = finals.map((f) => f.status);
    await page.goto(docUrl(cid)); await settle(page, 1500);
    const uiFinal = (await page.getByText(/최종본으로 확정되었습니다/).count()) === 1;
    record("S16", "동시 업로드는 번호가 겹치지 않고, 동시 확정은 하나만 성공(나머지 409, 구성원 403), 확정 후 업로드 409", uploads.every((u) => u.status === 201) && JSON.stringify(numbers) === "[1,2,3]" && statuses.filter((s) => s === 200).length === 1 && statuses.includes(409) && statuses[2] === 403 && lateUpload.status === 409 && uiFinal, `uploads=${uploads.map((u) => u.status)}, versions=${numbers}, finalize=${statuses}, late=${lateUpload.status}`);

    // S17 홈·프로젝트·전체 활동 이동
    await page.goto(APP + "/"); await settle(page, 1500);
    await page.getByRole("region", { name: "최근 활동" }).getByRole("link").filter({ hasText: /· v\d/ }).first().click();
    await page.waitForURL(/\?v=\d+/, { timeout: 10000 });
    const fromActivity = /\/documents\/\d+\?v=\d+/.test(page.url());
    await page.goto(APP + "/activity"); await settle(page, 1500);
    const activityItems = await page.locator("ol > li").count();
    await page.goto(`${APP}/projects/${seed.project}?tab=members`); await settle(page, 1200);
    const membersTab = await page.getByRole("tab", { name: "구성원" }).getAttribute("aria-selected");
    record("S17", "최근 활동에서 문서·버전으로 이동, 전체 활동, 프로젝트 탭 URL", fromActivity && activityItems > 5 && membersTab === "true", `activity=${activityItems}`);
  } catch (error) {
    record("ERROR", "스크립트 예외", false, String(error).slice(0, 400));
    await page.screenshot({ path: `${OUT}/v-error.png` }).catch(() => undefined);
  }
  record("CONSOLE", "예상하지 못한 콘솔 오류 없음", consoleErrors.length === 0, consoleErrors.slice(0, 3).join(" | "));
  fs.writeFileSync(path.join(OUT, "verify-results.json"), JSON.stringify(results, null, 2));
  await browser.close();
  const failed = results.filter((r) => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} passed`);
})();
