// Seed local e2e data through the real API (no DB writes). Usage: node seed.mjs
import fs from "node:fs";
const B = process.env.API || "http://localhost:8080";
const F = new URL("./files/", import.meta.url).pathname;
function client() {
  const jar = {};
  const cookie = () => Object.entries(jar).map(([k, v]) => `${k}=${v}`).join("; ");
  async function req(method, path, body, isForm) {
    const headers = { Cookie: cookie(), Accept: "application/json" };
    if (jar["XSRF-TOKEN"]) headers["X-XSRF-TOKEN"] = jar["XSRF-TOKEN"];
    if (body && !isForm) headers["Content-Type"] = "application/json";
    const res = await fetch(B + path, { method, headers, body: isForm ? body : body ? JSON.stringify(body) : undefined });
    for (const c of res.headers.getSetCookie()) { const [kv] = c.split(";"); const i = kv.indexOf("="); const v = kv.slice(i + 1); if (v) jar[kv.slice(0, i)] = v; else delete jar[kv.slice(0, i)]; }
    const text = await res.text();
    if (!res.ok) throw new Error(`${method} ${path} ${res.status} ${text}`);
    return text ? JSON.parse(text) : null;
  }
  return {
    req,
    async login(email) { await req("GET", "/api/auth/csrf"); const u = await req("POST", "/api/auth/login", { email, password: "devpass1234" }); await req("GET", "/api/auth/csrf"); return u; },
  };
}
function form(file, fields = {}) {
  const f = new FormData();
  for (const [k, v] of Object.entries(fields)) f.append(k, v);
  const type = file.endsWith(".pdf") ? "application/pdf" : "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
  f.append("file", new Blob([fs.readFileSync(F + file)], { type }), file);
  return f;
}
const a = client(), r = client();
await a.login("author@docflow.local");
await r.login("reviewer@docflow.local");
const p = await a.req("POST", "/api/projects", { name: "2026 상반기 서비스 기획", description: "기획서·회의록·요구사항 문서를 버전별로 검토하는 프로젝트입니다." });
await a.req("POST", `/api/projects/${p.id}/members`, { email: "reviewer@docflow.local" });
const p2 = await a.req("POST", "/api/projects", { name: "Railway 배포 테스트", description: null });
const D = `/api/projects/${p.id}/documents`;
const guide = await r.req("POST", D, form("design-guide.docx", { title: "디자인 가이드" }), true);
const plan = await a.req("POST", D, form("plan-v1.pdf", { title: "서비스 기획서", description: "MVP 범위와 핵심 기능, 일정 계획" }), true);
const spec = await a.req("POST", D, form("spec-v1.pdf", { title: "요구사항 명세서", description: "기능·비기능 요구사항 정의", versionNote: "초안" }), true);
await a.req("POST", `${D}/${spec.id}/versions`, form("spec-v2.pdf", { versionNote: "보안 요구사항 반영" }), true);
await r.req("POST", `${D}/${spec.id}/versions/2/feedbacks`, { content: "보안 요구사항 문구 확인했습니다. 확정해도 좋겠습니다." });
await r.req("POST", `${D}/${spec.id}/versions`, form("spec-v3.pdf", { versionNote: "비기능 요구사항 추가(검토 중 초안)" }), true);
await a.req("POST", `${D}/${spec.id}/finalize`, { versionNumber: 2 });
const m = await a.req("POST", D, form("meeting-v1.pdf", { title: "11월 정기 회의록", description: "11월 정기 회의 결정 사항과 일정을 기록한 문서입니다.", versionNote: "회의 직후 초안" }), true);
await r.req("POST", `${D}/${m.id}/versions/1/feedbacks`, { content: "참석자 명단에 박서준 님 직함도 넣어 주세요." });
await r.req("POST", `${D}/${m.id}/versions`, form("meeting-v2.pdf", { versionNote: "회의 날짜 정정" }), true);
await a.req("POST", `${D}/${m.id}/versions/2/feedbacks`, { content: "날짜 정정 확인했습니다." });
await a.req("POST", `${D}/${m.id}/versions`, form("meeting-v3.pdf", { versionNote: "배포일 변경(25일→30일), UI/UX 안건 추가, 부록 일정표 추가" }), true);
await r.req("POST", `${D}/${m.id}/versions/3/feedbacks`, { content: "배포일이 30일로 바뀐 이유를 결정 사항에 한 줄 덧붙이면 좋겠습니다." });
await a.req("POST", `${D}/${m.id}/versions/3/feedbacks`, { content: "부록 일정표의 회고 날짜는 팀 일정과 맞춰 다시 확인하겠습니다." });
console.log(JSON.stringify({ project: p.id, project2: p2.id, meeting: m.id, spec: spec.id, plan: plan.id, guide: guide.id }));
