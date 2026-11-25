/* DocFlow UI/UX 2.0 프로토타입 (Issue #67) — 실제 서비스와 분리된 정적 프로토타입.
   해시 라우팅(#/...)과 가상 데이터(data.js)만 사용한다. 운영 API·DB를 호출하지 않는다. */
(function () {
  "use strict";
  const D = window.DF;
  const root = document.getElementById("root");

  /* ---------------- 공통 ---------------- */
  const P = {
    home: '<path d="M3 10.5 12 3l9 7.5"/><path d="M5 9.5V21h14V9.5"/>',
    folder: '<path d="M3 6.5A1.5 1.5 0 0 1 4.5 5H9l2 2.5h8.5A1.5 1.5 0 0 1 21 9v9.5a1.5 1.5 0 0 1-1.5 1.5h-15A1.5 1.5 0 0 1 3 18.5z"/>',
    ticket: '<path d="M4 7h16v3a2 2 0 0 0 0 4v3H4v-3a2 2 0 0 0 0-4z"/><path d="M13 7v10" stroke-dasharray="2 2"/>',
    file: '<path d="M14 3H6.5A1.5 1.5 0 0 0 5 4.5v15A1.5 1.5 0 0 0 6.5 21h11a1.5 1.5 0 0 0 1.5-1.5V8z"/><path d="M14 3v5h5"/>',
    plus: '<path d="M12 5v14M5 12h14"/>',
    upload: '<path d="M12 16V4m0 0-4.5 4.5M12 4l4.5 4.5"/><path d="M4 16v3.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V16"/>',
    download: '<path d="M12 4v12m0 0 4.5-4.5M12 16l-4.5-4.5"/><path d="M4 16v3.5A1.5 1.5 0 0 0 5.5 21h13a1.5 1.5 0 0 0 1.5-1.5V16"/>',
    compare: '<rect x="3.5" y="4" width="7" height="16" rx="1.2"/><rect x="13.5" y="4" width="7" height="16" rx="1.2"/>',
    check: '<path d="m5 12.5 4.5 4.5L19 7.5"/>',
    checkCircle: '<circle cx="12" cy="12" r="9"/><path d="m8.5 12.5 2.5 2.5 4.5-5"/>',
    lock: '<rect x="5" y="10.5" width="14" height="10" rx="1.5"/><path d="M8 10.5V7.5a4 4 0 0 1 8 0v3"/>',
    chat: '<path d="M5 5h14a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H10l-4 3v-3H5a1 1 0 0 1-1-1V6a1 1 0 0 1 1-1z"/>',
    sparkle: '<path d="M12 3.5 13.9 9l5.6 1.9-5.6 1.9L12 18.5l-1.9-5.7L4.5 10.9 10.1 9z"/>',
    info: '<circle cx="12" cy="12" r="9"/><path d="M12 11v5M12 8h.01"/>',
    activity: '<path d="M3 12h4l3-8 4 16 3-8h4"/>',
    users: '<circle cx="9" cy="8" r="3.5"/><path d="M2.5 20a6.5 6.5 0 0 1 13 0"/><path d="M16 4.5a3.5 3.5 0 0 1 0 7M18 14.5a6.5 6.5 0 0 1 3.5 5.5"/>',
    settings: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
    search: '<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
    chevR: '<path d="m9 6 6 6-6 6"/>',
    chevL: '<path d="m15 6-6 6 6 6"/>',
    chevD: '<path d="m6 9 6 6 6-6"/>',
    up: '<path d="m6 15 6-6 6 6"/>',
    down: '<path d="m6 9 6 6 6-6"/>',
    arrowL: '<path d="M19 12H5m6-6-6 6 6 6"/>',
    arrowR: '<path d="M5 12h14m-6-6 6 6-6 6"/>',
    swap: '<path d="M7 7h12l-3-3M17 17H5l3 3"/>',
    rotate: '<path d="M20 12a8 8 0 1 1-2.3-5.6"/><path d="M20 4v5h-5"/>',
    fit: '<path d="M4 9V5h4M20 9V5h-4M4 15v4h4M20 15v4h-4"/>',
    minus: '<path d="M5 12h14"/>',
    x: '<path d="M6 6l12 12M18 6 6 18"/>',
    panelL: '<rect x="3.5" y="4" width="17" height="16" rx="2"/><path d="M9 4v16"/>',
    panelR: '<rect x="3.5" y="4" width="17" height="16" rx="2"/><path d="M15 4v16"/>',
    eye: '<path d="M2.5 12S6 5.5 12 5.5 21.5 12 21.5 12 18 18.5 12 18.5 2.5 12 2.5 12z"/><circle cx="12" cy="12" r="3"/>',
    link: '<path d="M10 14a4 4 0 0 0 5.7 0l3-3a4 4 0 0 0-5.7-5.7l-1 1"/><path d="M14 10a4 4 0 0 0-5.7 0l-3 3a4 4 0 0 0 5.7 5.7l1-1"/>',
    alert: '<path d="M12 4 2.5 20h19z"/><path d="M12 10v4M12 17h.01"/>',
    more: '<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
    menu: '<path d="M4 7h16M4 12h16M4 17h16"/>',
  };
  const ic = (n, c = "") => `<svg class="icon ${c}" viewBox="0 0 24 24" aria-hidden="true">${P[n] || ""}</svg>`;
  const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" })[c]);
  const user = (id) => D.users[id];
  const avatar = (id, sm) => `<span class="avatar ${sm ? "avatar-sm" : ""} ${user(id).c}" aria-hidden="true">${esc(user(id).name[0])}</span>`;
  const fmt = (iso) => {
    const d = new Date(iso);
    const h = d.getHours();
    return `${d.getMonth() + 1}월 ${d.getDate()}일 ${h < 12 ? "오전" : "오후"} ${h % 12 || 12}:${String(d.getMinutes()).padStart(2, "0")}`;
  };
  const day = (iso) => { const d = new Date(iso); return `${d.getMonth() + 1}월 ${d.getDate()}일`; };
  const project = (id) => D.projects.find((p) => p.id === +id);
  const doc = (id) => D.documents.find((d) => d.id === +id);
  const statusEl = (s) => s === "FINALIZED" ? '<span class="status status-final">최종 확정</span>' : '<span class="status status-active">협업 중</span>';
  const docIco = (t) => `<span class="doc-ico ${t}" aria-hidden="true">${t.toUpperCase()}</span>`;

  /* ---------------- 상태 ---------------- */
  const ui = { left: true, rtab: "feedback", mtab: "doc", zoom: null, rot: 0, hl: true, sync: true, panel: true, filter: "ALL", cur: 0, fold: true, projTab: "docs", query: "", status: "ALL", modal: null, file: null, titleTouched: false, title: "", cmpM: "target", proto: false, split: null };
  function route() {
    const [path, qs] = (location.hash.slice(1) || "/").split("?");
    const q = Object.fromEntries(new URLSearchParams(qs || ""));
    return { path, q, seg: path.split("/").filter(Boolean) };
  }
  const R = () => route();
  const opt = (k, d) => R().q[k] || d;
  const isOwner = () => opt("role", "owner") === "owner";
  const aiOn = () => opt("ai", "on") === "on";
  const fbSome = () => opt("fb", "some") === "some";
  const st = () => opt("st", "ready");
  function withQ(path, extra = {}) {
    const keep = {};
    ["role", "ai", "fb", "st", "shot"].forEach((k) => { if (R().q[k]) keep[k] = R().q[k]; });
    const q = new URLSearchParams({ ...keep, ...extra });
    const s = q.toString();
    return `#${path}${s ? "?" + s : ""}`;
  }

  /* ---------------- 셸 ---------------- */
  function shell(content, { mode = "full", current = "" } = {}) {
    const nav = (href, icon, label, key) => `<a class="nav-item" href="${withQ(href)}" ${current === key ? 'aria-current="page"' : ""} title="${label}">${ic(icon)}<span class="label-text">${label}</span></a>`;
    const side = mode === "focus" ? "" : `
      <nav class="sidebar" aria-label="주 메뉴">
        <div class="brand"><span class="logo">D</span><span>DocFlow</span></div>
        ${nav("/", "home", "홈", "home")}
        ${nav("/projects", "folder", "프로젝트", "projects")}
        ${D.me.admin ? nav("/invites", "ticket", "초대 관리", "invites") : ""}
        <div class="nav-section">내 프로젝트</div>
        ${D.projects.map((p) => `<a class="nav-item" href="${withQ("/projects/" + p.id)}" title="${esc(p.name)}" ${current === "p" + p.id ? 'aria-current="page"' : ""}><span class="proj-dot" aria-hidden="true"></span><span class="label-text truncate">${esc(p.name)}</span></a>`).join("")}
        <div class="sidebar-foot">
          <div class="user-chip">${avatar(1)}<div class="meta" style="min-width:0"><div style="font-weight:500;font-size:13px">${esc(D.me.name)}</div><div class="muted truncate" style="font-size:12px">${esc(D.me.email)}</div></div></div>
          <a class="nav-item" href="${withQ("/login")}" title="로그아웃">${ic("arrowL")}<span class="label-text">로그아웃</span></a>
        </div>
      </nav>`;
    const mtop = mode === "full" ? `<div class="mobile-top"><button class="btn btn-ghost btn-icon" aria-label="메뉴">${ic("menu")}</button><span class="logo">D</span><strong>DocFlow</strong></div>` : "";
    return `<div class="app ${mode === "rail" ? "is-rail" : mode === "focus" ? "is-focus" : ""}">${side}<main class="main" id="main">${mtop}${content}</main></div>`;
  }

  /* ---------------- 홈 ---------------- */
  function home() {
    const docs = [...D.documents].sort((a, b) => b.updated.localeCompare(a.updated));
    const loading = st() === "loading";
    const error = st() === "error";
    const rows = loading
      ? Array.from({ length: 5 }, () => `<div class="list-row" style="grid-template-columns:28px 1fr 90px 60px 110px;cursor:default"><span class="skel" style="width:28px;height:28px"></span><span class="skel" style="height:12px;width:60%"></span><span class="skel" style="height:10px"></span><span class="skel" style="height:10px"></span><span class="skel" style="height:10px"></span></div>`).join("")
      : docs.map((d) => `<a class="list-row" href="${withQ("/doc/" + d.id)}" style="grid-template-columns:28px minmax(0,1fr) 96px 56px 120px 16px">
          ${docIco(d.type)}
          <div style="min-width:0"><div class="title truncate">${esc(d.title)}</div><div class="desc truncate">${esc(project(d.project).name)}</div></div>
          <span class="hide-m">${statusEl(d.status)}</span><span class="muted hide-m">v${d.latest}</span><span class="muted hide-m" style="font-size:12.5px">${fmt(d.updated)}</span>${ic("chevR", "chev")}</a>`).join("");
    return shell(`
      <div class="page page-wide">
        <div class="page-head">
          <div><h1 class="h1">내 작업</h1><p class="subtitle">최근 문서를 이어서 검토하세요.</p></div>
          <button class="btn btn-primary" data-act="modal" data-m="newProject">${ic("plus")}새 프로젝트</button>
        </div>
        ${error ? `<div class="alert alert-danger" role="alert" style="margin-bottom:16px">${ic("alert")}<div class="grow">대시보드를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</div><button class="btn btn-sm">다시 시도</button></div>` : ""}
        <div class="stats" aria-label="요약">
          <div class="stat"><span class="k">${ic("folder", "icon-sm")}참여 프로젝트</span><span class="v">${loading ? '<span class="skel" style="display:block;height:26px;width:40px"></span>' : D.projects.length}</span><span class="d">오너 1 · 구성원 1</span></div>
          <div class="stat"><span class="k">${ic("file", "icon-sm")}협업 중인 문서</span><span class="v">${loading ? "" : D.documents.filter((d) => d.status === "ACTIVE").length}</span><span class="d">최근 7일 새 버전 3개</span></div>
          <div class="stat"><span class="k">${ic("checkCircle", "icon-sm")}최종 확정 문서</span><span class="v">${loading ? "" : D.documents.filter((d) => d.status === "FINALIZED").length}</span><span class="d">읽기 전용</span></div>
        </div>
        <div class="grid-home section">
          <div>
            <div class="section-head"><h2 class="h2">최근 업데이트된 문서</h2><span class="muted" style="font-size:12.5px">참여 중인 모든 프로젝트</span></div>
            <div class="list">
              <div class="list-head" style="grid-template-columns:28px minmax(0,1fr) 96px 56px 120px 16px"><span></span><span>문서</span><span>상태</span><span>최신</span><span>업데이트</span><span></span></div>
              ${rows}
            </div>
            <div class="section">
              <div class="section-head"><h2 class="h2">내 프로젝트</h2><a class="link" href="${withQ("/projects")}">전체 보기</a></div>
              <div style="display:grid;grid-template-columns:repeat(auto-fill,minmax(260px,1fr));gap:10px">
                ${D.projects.map((p) => `<a class="proj-card" href="${withQ("/projects/" + p.id)}"><span class="proj-mark">${esc(p.name[0])}</span><div class="grow"><div class="truncate" style="font-weight:600">${esc(p.name)}</div><div class="muted" style="font-size:12.5px">문서 ${D.documents.filter((d) => d.project === p.id).length} · 구성원 ${p.members.length} · ${p.role === "OWNER" ? "오너" : "구성원"}</div></div>${ic("chevR", "chev")}</a>`).join("")}
              </div>
            </div>
          </div>
          <aside>
            <div class="section-head"><h2 class="h2">최근 활동</h2></div>
            <div class="activity">${activityItems(D.activity.slice(0, 6))}</div>
          </aside>
        </div>
      </div>`, { current: "home" });
  }
  function activityItems(list) {
    const icon = { v: "upload", f: "chat", ok: "check" };
    return list.map((a) => `<a class="act" href="${withQ("/doc/" + a.doc)}"><span class="act-dot ${a.t}">${ic(icon[a.t], "icon-sm")}</span><div class="grow"><div class="what">${esc(a.what)}</div><div class="when">${esc(a.when)}</div></div></a>`).join("");
  }

  /* ---------------- 프로젝트 목록·상세 ---------------- */
  function projects() {
    return shell(`<div class="page page-mid">
      <div class="page-head"><div><h1 class="h1">프로젝트</h1><p class="subtitle">참여 중인 프로젝트 ${D.projects.length}개</p></div><button class="btn btn-primary" data-act="modal" data-m="newProject">${ic("plus")}새 프로젝트</button></div>
      <div class="list"><div class="list-head" style="grid-template-columns:32px minmax(0,1fr) 90px 80px 100px 16px"><span></span><span>프로젝트</span><span>내 역할</span><span>문서</span><span>생성</span><span></span></div>
      ${D.projects.map((p) => `<a class="list-row" href="${withQ("/projects/" + p.id)}" style="grid-template-columns:32px minmax(0,1fr) 90px 80px 100px 16px"><span class="proj-mark" style="width:28px;height:28px">${esc(p.name[0])}</span><div style="min-width:0"><div class="title truncate">${esc(p.name)}</div><div class="desc truncate">${esc(p.desc)}</div></div><span class="hide-m"><span class="badge ${p.role === "OWNER" ? "badge-brand" : ""}">${p.role === "OWNER" ? "오너" : "구성원"}</span></span><span class="muted hide-m">${D.documents.filter((d) => d.project === p.id).length}</span><span class="muted hide-m">${day(p.created)}</span>${ic("chevR", "chev")}</a>`).join("")}</div></div>`, { current: "projects" });
  }

  function projectDetail(id) {
    const p = project(id);
    const tab = opt("tab", "docs");
    const docs = D.documents.filter((d) => d.project === p.id);
    const owner = isOwner() && p.role === "OWNER";
    const tabs = [["docs", "문서", docs.length], ["members", "구성원", p.members.length], ["activity", "활동 이력"], ["settings", "설정"]];
    let body = "";
    if (tab === "docs") {
      const filtered = docs.filter((d) => (ui.status === "ALL" || d.status === ui.status) && d.title.includes(ui.query));
      const cols = "28px minmax(0,1fr) 96px 64px 72px 150px 16px";
      body = `<div class="toolbar">
          <label class="input-icon" style="width:260px">${ic("search")}<span class="sr-only">문서 검색</span><input class="input" data-act="search" placeholder="문서 제목 검색" value="${esc(ui.query)}"></label>
          <div class="seg" role="group" aria-label="상태 필터">${[["ALL", "전체", docs.length], ["ACTIVE", "협업 중", docs.filter((d) => d.status === "ACTIVE").length], ["FINALIZED", "최종 확정", docs.filter((d) => d.status === "FINALIZED").length]].map(([k, l, n]) => `<button data-act="status" data-v="${k}" aria-pressed="${ui.status === k}">${l} <span class="muted">${n}</span></button>`).join("")}</div>
          <select class="select" style="width:auto;margin-left:auto" aria-label="정렬"><option>최근 업데이트순</option><option>제목순</option><option>등록순</option></select>
        </div>
        <div class="list">
          <div class="list-head" style="grid-template-columns:${cols}"><span></span><span>문서</span><span>상태</span><span>최신</span><span>최종본</span><span>업데이트</span><span></span></div>
          ${filtered.length ? filtered.map((d) => `<a class="list-row" href="${withQ("/doc/" + d.id)}" style="grid-template-columns:${cols}">${docIco(d.type)}<div style="min-width:0"><div class="title truncate">${esc(d.title)}</div><div class="desc truncate">${esc(d.desc || "설명 없음")}</div></div><span class="hide-m">${statusEl(d.status)}</span><span class="muted hide-m">v${d.latest}</span><span class="hide-m">${d.final ? `<span class="badge badge-success">v${d.final}</span>` : '<span class="muted">—</span>'}</span><span class="muted hide-m" style="font-size:12.5px">${avatar(d.author, true)} ${fmt(d.updated)}</span>${ic("chevR", "chev")}</a>`).join("")
            : `<div class="empty"><span class="icon-wrap">${ic("search")}</span><strong>조건에 맞는 문서가 없습니다</strong><span>검색어나 상태 필터를 바꿔 보세요.</span><button class="btn btn-sm" data-act="clearFilter">필터 초기화</button></div>`}
        </div>`;
    } else if (tab === "members") {
      body = `${owner ? `<div class="panel" style="margin-bottom:16px"><form class="row" onsubmit="return false" style="flex-wrap:wrap"><div class="field grow"><label class="label" for="add-email">구성원 추가</label><input id="add-email" class="input" type="email" placeholder="가입한 사용자의 이메일"></div><button class="btn btn-primary" style="align-self:flex-end">추가</button></form><p class="hint" style="margin:8px 0 0">DocFlow에 가입한 사용자만 추가할 수 있습니다.</p></div>` : ""}
        <div class="list">${p.members.map((m) => `<div class="list-row" style="grid-template-columns:28px minmax(0,1fr) 90px 80px;cursor:default">${avatar(m)}<div><div class="title">${esc(user(m).name)}</div><div class="desc">${m === 1 ? "owner@example.com" : m === 2 ? "minji@example.com" : "seojun@example.com"}</div></div><span><span class="badge ${m === p.owner ? "badge-brand" : ""}">${m === p.owner ? "오너" : "구성원"}</span></span><span style="text-align:right">${owner && m !== p.owner ? '<button class="btn btn-sm btn-ghost">제거</button>' : ""}</span></div>`).join("")}</div>`;
    } else if (tab === "activity") {
      body = `<div class="panel"><div class="activity">${activityItems(D.activity)}</div></div>`;
    } else {
      const canManage = owner;
      body = `<div style="display:flex;flex-direction:column;gap:24px;max-width:640px">
        <section aria-labelledby="ai-h"><div class="row" style="margin-bottom:8px"><h2 class="h2" id="ai-h">AI 문서 분석</h2><span class="badge ${p.ai ? "badge-success" : ""}">${p.ai ? "켜짐" : "꺼짐"}</span></div>
          <div class="panel" style="display:flex;flex-direction:column;gap:12px">
            <p style="margin:0;color:var(--ink-2)">켜면 이 프로젝트에 올린 PDF의 텍스트가 외부 AI 서비스(Anthropic Claude API)로 전송되어 버전 요약과 변경 요약을 만듭니다. AI 결과는 참고용이며 틀릴 수 있습니다.</p>
            ${p.ai ? `<p class="hint" style="margin:0">정의훈님이 11월 20일 오후 2:10에 켰습니다 · 모델 claude-sonnet-5-5</p>` : ""}
            ${canManage ? (p.ai ? `<div><button class="btn">AI 기능 끄기</button></div>` : `<label class="check"><input type="checkbox">문서 내용이 외부 AI 서비스로 전송된다는 점을 확인했고, 구성원에게 알렸습니다.</label><div><button class="btn btn-primary" disabled>AI 기능 켜기</button></div>`) : `<p class="hint" style="margin:0">설정은 프로젝트 오너만 바꿀 수 있습니다.</p>`}
          </div>
          <p class="hint">서비스에서 AI를 사용할 수 없으면(관리자 미설정) 이 섹션은 표시하지 않습니다.</p></section>
        <section aria-labelledby="info-h"><h2 class="h2" id="info-h" style="margin-bottom:8px">프로젝트 정보</h2>
          <dl class="kv panel"><dt>이름</dt><dd>${esc(p.name)}</dd><dt>설명</dt><dd>${esc(p.desc)}</dd><dt>오너</dt><dd>${esc(user(p.owner).name)}</dd><dt>생성일</dt><dd>${day(p.created)}</dd></dl></section>
      </div>`;
    }
    return shell(`<div class="page page-wide">
        <div class="crumbs"><a href="${withQ("/projects")}">프로젝트</a><span class="sep">/</span><span class="truncate">${esc(p.name)}</span></div>
        <div class="page-head">
          <div class="row" style="align-items:flex-start;gap:14px"><span class="proj-mark" style="width:40px;height:40px;font-size:17px">${esc(p.name[0])}</span><div><h1 class="h1">${esc(p.name)}</h1><p class="subtitle">${esc(p.desc)}</p>
            <div class="row" style="margin-top:8px;font-size:12.5px"><span class="badge ${p.role === "OWNER" ? "badge-brand" : ""}">${p.role === "OWNER" ? "내가 오너" : "구성원"}</span><span class="muted">오너 ${esc(user(p.owner).name)}</span><span class="row" style="gap:0">${p.members.map((m) => avatar(m, true)).join("")}</span></div></div></div>
          <a class="btn btn-primary" href="${withQ("/projects/" + p.id + "/new")}">${ic("plus")}문서 등록</a>
        </div>
        <div class="tabs" role="tablist" style="margin-bottom:16px">${tabs.map(([k, l, n]) => `<a class="tab" role="tab" aria-selected="${tab === k}" href="${withQ("/projects/" + p.id, { tab: k })}">${l}${n != null ? `<span class="count">${n}</span>` : ""}</a>`).join("")}</div>
        ${body}
      </div>`, { current: "p" + p.id });
  }

  /* ---------------- 문서 등록 ---------------- */
  function docCreate(pid) {
    const p = project(pid);
    const f = ui.file;
    const uploading = ui.uploading;
    return shell(`<div class="page page-narrow">
      <div class="crumbs"><a href="${withQ("/projects")}">프로젝트</a><span class="sep">/</span><a href="${withQ("/projects/" + p.id)}" class="truncate">${esc(p.name)}</a><span class="sep">/</span><span>새 문서</span></div>
      <h1 class="h1" style="margin-bottom:4px">새 문서 등록</h1><p class="subtitle" style="margin-bottom:20px">파일을 올리면 v1으로 등록됩니다. 이후 새 버전을 올리며 피드백을 주고받을 수 있습니다.</p>
      <form class="panel" style="display:flex;flex-direction:column;gap:18px;padding:20px" onsubmit="return false" aria-busy="${!!uploading}">
        ${fileField("doc-file", f)}
        <div class="field"><label class="label" for="doc-title">문서 제목</label><input id="doc-title" class="input" data-act="title" maxlength="100" value="${esc(ui.title)}" placeholder="예: 11월 정기 회의록" ${uploading ? "disabled" : ""}>${f && !ui.titleTouched ? '<span class="hint">파일 이름으로 채웠습니다. 바꿀 수 있어요.</span>' : '<span class="hint">최대 100자</span>'}</div>
        <details ${ui.optOpen ? "open" : ""} data-act="opt" style="border-top:1px solid var(--line);padding-top:14px">
          <summary style="cursor:pointer;font-weight:500;font-size:13px;color:var(--ink-2)">선택 입력 <span class="muted" style="font-weight:400">· 문서 설명, 초기 버전 내용</span></summary>
          <div style="display:flex;flex-direction:column;gap:14px;margin-top:12px">
            <div class="field"><label class="label" for="doc-desc">문서 설명 <span class="opt">선택</span></label><textarea id="doc-desc" class="textarea" placeholder="문서의 목적과 주요 내용을 적어 두면 구성원이 파일을 열기 전에 확인할 수 있습니다."></textarea><span class="hint" style="text-align:right">0 / 2,000</span></div>
            <div class="field"><label class="label" for="doc-note">초기 버전 내용 <span class="opt">선택</span></label><textarea id="doc-note" class="textarea" style="min-height:64px" placeholder="예: 1장 개요, 2장 범위 초안"></textarea><span class="hint" style="text-align:right">0 / 1,000</span></div>
          </div>
        </details>
        ${uploading ? `<div class="field"><div class="row" style="font-size:12.5px"><span>업로드 중…</span><span class="muted" style="margin-left:auto">62%</span></div><div class="progress"><i></i></div></div>` : ""}
        ${st() === "error" ? `<div class="alert alert-danger" role="alert">${ic("alert")}<span>파일을 등록하지 못했습니다. 입력한 내용은 그대로 남아 있습니다. 다시 시도해 주세요.</span></div>` : ""}
        <div class="row" style="justify-content:flex-end;border-top:1px solid var(--line);padding-top:14px">
          <a class="btn" href="${withQ("/projects/" + p.id)}">취소</a>
          <button class="btn btn-primary" data-act="submitDoc" ${!f || !ui.title.trim() || uploading ? "disabled" : ""}>${uploading ? "등록 중…" : "문서 등록"}</button>
        </div>
      </form></div>`, { current: "p" + p.id });
  }
  function fileField(id, f) {
    return `<div class="field"><span class="label">파일</span>
      <input type="file" id="${id}" class="sr-only" data-act="file" accept=".pdf,.docx,.xlsx,.pptx">
      ${f ? `<div class="file-row">${docIco(f.name.split(".").pop().toLowerCase())}<div class="grow"><div class="truncate" style="font-weight:500">${esc(f.name)}</div><div class="muted" style="font-size:12px">${f.size}</div></div><label for="${id}" class="btn btn-sm">교체</label><button class="btn btn-sm btn-ghost" data-act="removeFile" aria-label="파일 제거">${ic("x", "icon-sm")}</button></div>`
        : `<label for="${id}" class="dropzone" data-drop><span class="dz-ico">${ic("upload")}</span><span><strong style="color:var(--brand-700)">파일 선택</strong> 또는 여기로 끌어다 놓기</span><span class="hint">PDF, DOCX, XLSX, PPTX · 최대 20MB</span></label>`}</div>`;
  }

  /* ---------------- 문서 뷰어(공통 PDF 뷰어 개념) ---------------- */
  function paperHtml(key, pageIdx, pageCount, { hlSide } = {}) {
    const blocks = (D.pages[key] || [[]])[pageIdx] || [];
    const typeOf = (id) => (D.changes.find((c) => c.id === id) || {}).type;
    const body = blocks.map((b) => {
      const cut = b.indexOf("|");
      const tag = b.slice(0, cut), raw = b.slice(cut + 1);
      const html = esc(raw).replace(/\[\[(c\d)\|(.+?)\]\]/g, (m, id, t) => {
        if (!hlSide) return t;
        const ty = typeOf(id);
        const show = (hlSide === "base" && ty !== "ADDED") || (hlSide === "target" && ty !== "DELETED");
        if (!show) return t;
        const cls = ty === "ADDED" ? "hl-add" : ty === "DELETED" ? "hl-del" : "hl-mod";
        const cur = D.changes[ui.cur] && D.changes[ui.cur].id === id;
        return `<span class="hl ${cls} ${cur ? "is-current" : ""}" data-chg="${id}" role="button" tabindex="0" aria-label="${ty === "ADDED" ? "추가" : ty === "DELETED" ? "삭제" : "수정"}된 내용">${t}</span>`;
      });
      return tag === "h2" ? `<h2>${html}</h2>` : tag === "h3" ? `<h3>${html}</h3>` : `<p>${html}</p>`;
    }).join("");
    return `<div class="page-wrap" data-page="${pageIdx + 1}"><div class="paper">${body}<div class="pno">${pageIdx + 1} / ${pageCount}</div></div></div>`;
  }
  function viewerBar({ file, pages, extra = "" }) {
    return `<div class="vbar" role="toolbar" aria-label="문서 보기 도구">
      <span class="truncate hide-m" style="font-size:12.5px;font-weight:500;max-width:220px" title="${esc(file)}">${esc(file)}</span><span class="sep hide-m"></span>
      <span class="pager"><label class="sr-only" for="pg">페이지</label><input id="pg" value="1" inputmode="numeric"> / ${pages}</span><span class="sep"></span>
      <button class="btn btn-ghost btn-sm btn-icon" data-act="zoom" data-d="-1" aria-label="축소">${ic("minus")}</button><span class="zoom" aria-live="polite" data-zoom-label>100%</span>
      <button class="btn btn-ghost btn-sm btn-icon" data-act="zoom" data-d="1" aria-label="확대">${ic("plus")}</button>
      <button class="btn btn-ghost btn-sm ${ui.zoom === null ? "is-active" : ""}" data-act="fit">${ic("fit", "icon-sm")}<span class="hide-m">폭 맞춤</span></button>
      <button class="btn btn-ghost btn-sm btn-icon" data-act="rotate" aria-label="회전 (현재 ${ui.rot}도)">${ic("rotate")}</button>
      ${extra}
    </div>`;
  }

  /* ---------------- 문서 상세 워크스페이스 ---------------- */
  function docDetail(id) {
    const d = doc(id);
    const p = project(d.project);
    const versions = D.versions[d.id] || [{ n: d.latest, file: d.title + "." + d.type, by: d.author, at: d.updated, size: "120 KB", note: "" }];
    const finalized = d.status === "FINALIZED";
    const sel = +(opt("v", finalized ? d.final : d.latest));
    const v = versions.find((x) => x.n === sel) || versions[0];
    const key = `${d.id}:${v.n}`;
    const owner = isOwner() && p.role === "OWNER";
    const showAi = aiOn() && p.ai && d.type === "pdf";
    const fb = fbSome() ? D.feedback[key] || [] : [];
    const rtab = !showAi && ui.rtab === "ai" ? "feedback" : ui.rtab;
    const loading = st() === "loading", error = st() === "error";
    const verItem = (x) => `<a class="ver" href="${withQ("/doc/" + d.id, { v: x.n })}" ${x.n === v.n ? 'aria-current="true"' : ""}>
        <div class="row"><span class="vno">v${x.n}</span>${x.n === d.latest ? '<span class="badge">최신</span>' : ""}${x.n === d.final ? `<span class="badge badge-success">${ic("check", "icon-sm")}최종본</span>` : ""}<span class="grow"></span>${(D.feedback[d.id + ":" + x.n] || []).length && fbSome() ? `<span class="muted row" style="gap:3px;font-size:12px">${ic("chat", "icon-sm")}${D.feedback[d.id + ":" + x.n].length}</span>` : ""}</div>
        <div class="file truncate">${esc(x.file)}</div>
        <div class="meta">${esc(user(x.by).name)} · ${fmt(x.at)}</div>
        ${x.note ? `<div class="note">${esc(x.note)}</div>` : ""}</a>`;
    const left = ui.left ? `<section class="pane pane-left" aria-label="버전 히스토리">
        <div class="pane-head"><strong>버전</strong><span class="muted">${versions.length}</span><span class="grow"></span><button class="btn btn-ghost btn-sm btn-icon collapse-btn" data-act="left" aria-label="버전 패널 접기">${ic("panelL")}</button></div>
        <div class="pane-scroll"><div class="versions">${versions.map(verItem).join("")}</div></div></section>`
      : `<section class="pane pane-left" aria-label="버전 히스토리(접힘)"><div class="collapsed-rail"><button class="btn btn-ghost btn-sm btn-icon" data-act="left" aria-label="버전 패널 펼치기">${ic("panelL")}</button>${versions.map((x) => `<a class="vchip" href="${withQ("/doc/" + d.id, { v: x.n })}" ${x.n === v.n ? 'aria-current="true"' : ""} title="v${x.n}">v${x.n}</a>`).join("")}</div></section>`;
    let canvas;
    if (loading) canvas = `<div class="canvas"><div class="page-wrap" style="width:520px;height:672px"><div class="skel" style="position:absolute;inset:0"></div></div></div>`;
    else if (error) canvas = `<div class="canvas" style="justify-content:center"><div class="empty" style="background:var(--bg);border-radius:12px;padding:32px 40px;border:1px solid var(--line)"><span class="icon-wrap" style="color:var(--danger-700);background:var(--danger-50)">${ic("alert")}</span><strong>미리보기를 불러오지 못했습니다</strong><span>네트워크를 확인하고 다시 시도하거나 원본을 내려받아 확인해 주세요.</span><div class="row"><button class="btn btn-sm">다시 시도</button><button class="btn btn-sm">${ic("download", "icon-sm")}원본 받기</button></div></div></div>`;
    else if (d.type !== "pdf") canvas = `<div class="canvas" style="justify-content:center"><div class="empty" style="background:var(--bg);border-radius:12px;padding:32px 40px;border:1px solid var(--line);max-width:420px"><span class="icon-wrap">${docIco(d.type)}</span><strong>Office 문서는 미리보기를 지원하지 않습니다</strong><span>현재 운영 환경에서는 Office 변환을 사용하지 않습니다. 원본 파일을 내려받아 확인해 주세요.</span><button class="btn btn-primary btn-sm">${ic("download", "icon-sm")}원본 받기 (${esc(v.size)})</button></div></div>`;
    else { const pages = D.pages[key] || [[]]; canvas = `<div class="canvas" data-canvas>${pages.map((_, i) => paperHtml(key, i, pages.length)).join("")}</div>`; }
    const pagesN = (D.pages[key] || [[]]).length;
    const viewer = `<section class="viewer" aria-label="문서 미리보기">${viewerBar({ file: v.file, pages: d.type === "pdf" ? pagesN : 1, extra: `<span class="grow"></span><button class="btn btn-ghost btn-sm">${ic("download", "icon-sm")}<span class="hide-m">원본 받기</span></button>` })}${canvas}</section>`;
    const tabs = [["feedback", "피드백", fb.length], ...(showAi ? [["ai", "AI 요약"]] : []), ["info", "정보"], ["activity", "활동"]];
    let rbody = "";
    if (rtab === "feedback") {
      const list = fb.length ? `<div class="feed">${fb.map((f) => `<article class="fb">${avatar(f.by, true)}<div class="body"><div class="who">${esc(user(f.by).name)}<time>${fmt(f.at)}</time></div><div class="text">${esc(f.text)}</div></div></article>`).join("")}</div>`
        : `<div class="empty">${'<span class="icon-wrap">' + ic("chat") + "</span>"}<strong>v${v.n}에 남긴 피드백이 없습니다</strong><span>${finalized ? "최종 확정된 문서입니다." : "문서를 보면서 첫 의견을 남겨 보세요."}</span></div>`;
      const composer = finalized ? `<div class="composer"><div class="row muted" style="font-size:12.5px">${ic("lock", "icon-sm")}최종 확정된 문서는 피드백을 조회만 할 수 있습니다.</div></div>`
        : `<form class="composer" onsubmit="return false"><label class="sr-only" for="fb-text">v${v.n}에 피드백 작성</label><textarea id="fb-text" class="textarea" placeholder="v${v.n}에 피드백 남기기…"></textarea><div class="composer-foot"><span class="hint">v${v.n}에 남겨집니다 · <span class="kbd">Ctrl</span>+<span class="kbd">Enter</span></span><button class="btn btn-primary btn-sm">등록</button></div></form>`;
      rbody = `<div class="pane-scroll">${list}</div>${composer}`;
    } else if (rtab === "ai") {
      const a = D.ai[key];
      rbody = `<div class="pane-scroll"><div class="ai-box">
        <div class="row"><span class="ai-tag">${ic("sparkle", "icon-sm")}AI 요약</span><span class="badge badge-brand">AI 생성 · 검토 필요</span></div>
        ${a ? `<p style="margin:0;font-size:13.5px;line-height:1.65">${esc(a.summary)}</p>
          <div><div class="h3" style="margin-bottom:4px">주요 내용</div><ul style="margin:0;padding-left:18px;font-size:13px;display:flex;flex-direction:column;gap:4px">${a.points.map(([t, pg]) => `<li>${esc(t)} <span class="muted" style="font-size:12px">p.${pg.join(",")}</span></li>`).join("")}</ul></div>
          <div><div class="h3" style="margin-bottom:6px">주요 일정·수치</div><dl class="kv" style="grid-template-columns:auto 1fr">${a.facts.map(([ty, l, val, q, pg]) => `<dt><span class="badge">${ty === "DATE" ? "일정" : "수치"}</span> ${esc(l)}</dt><dd><strong>${esc(val)}</strong><div class="muted" style="font-size:12px">“${esc(q)}” · p.${pg}</div></dd>`).join("")}</dl></div>
          <p class="hint" style="margin:0;border-top:1px solid var(--line);padding-top:10px">AI가 자동으로 만든 요약입니다. 중요한 내용은 원본에서 확인해 주세요. · 11월 25일 오후 3:16 · claude-sonnet-5-5</p>`
          : `<div class="empty" style="padding:24px 8px"><strong>v${v.n}의 AI 요약이 아직 없습니다</strong><span>요청하면 문서 내용이 외부 AI 서비스로 전송됩니다.</span><button class="btn btn-primary btn-sm">AI 요약 만들기</button></div>`}
      </div></div>`;
    } else if (rtab === "info") {
      rbody = `<div class="pane-scroll"><div class="info">
        <div class="info-block"><div class="row"><span class="h3">문서 설명</span><span class="grow"></span>${finalized ? "" : '<button class="btn btn-ghost btn-sm">수정</button>'}</div><p style="margin:0;font-size:13.5px;white-space:pre-wrap">${esc(d.desc || "설명이 없습니다.")}</p></div>
        <div class="info-block"><span class="h3">${v.n === 1 ? "초기 버전 내용" : "변경사항"} · v${v.n}</span><p style="margin:0;font-size:13.5px">${esc(v.note || "작성하지 않았습니다.")}</p></div>
        <div class="info-block"><span class="h3">선택한 버전 파일</span><dl class="kv"><dt>파일</dt><dd class="truncate">${esc(v.file)}</dd><dt>크기</dt><dd>${esc(v.size)}</dd><dt>올린 사람</dt><dd>${esc(user(v.by).name)}</dd><dt>업로드</dt><dd>${fmt(v.at)}</dd></dl></div>
        <div class="info-block"><span class="h3">문서</span><dl class="kv"><dt>상태</dt><dd>${statusEl(d.status)}</dd><dt>작성자</dt><dd>${esc(user(d.author).name)}</dd><dt>최신 버전</dt><dd>v${d.latest}</dd><dt>최종본</dt><dd>${d.final ? "v" + d.final : "—"}</dd></dl></div>
      </div></div>`;
    } else {
      rbody = `<div class="pane-scroll" style="padding:4px 16px"><div class="activity">${activityItems(D.activity.filter((a) => a.doc === d.id).concat(finalized ? [] : []))}</div></div>`;
    }
    const right = `<section class="pane pane-right" aria-label="협업">
      <div class="rtabs" role="tablist">${tabs.map(([k, l, n]) => `<button class="tab" role="tab" aria-selected="${rtab === k}" data-act="rtab" data-v="${k}">${l}${n != null ? `<span class="count">${n}</span>` : ""}</button>`).join("")}</div>${rbody}</section>`;
    const head = `<header class="ws-head">
      <a class="btn btn-ghost btn-icon" href="${withQ("/projects/" + p.id)}" aria-label="프로젝트로 돌아가기">${ic("arrowL")}</a>
      <div class="ws-title"><div class="crumbs"><a href="${withQ("/projects/" + p.id)}" class="truncate">${esc(p.name)}</a><span class="sep">/</span><span>문서</span></div>
        <h1><span class="truncate">${esc(d.title)}</span>${statusEl(d.status)}<span class="badge badge-outline">v${v.n} 보는 중</span></h1></div>
      <div class="ws-actions">
        <a class="btn hide-m" href="${withQ("/compare/" + d.id, { mode: "pdf", base: Math.max(1, v.n - 1), target: v.n })}" ${versions.length < 2 || d.type !== "pdf" ? 'aria-disabled="true" style="pointer-events:none;opacity:.5"' : ""}>${ic("compare")}버전 비교</a>
        ${!finalized && owner ? `<button class="btn hide-m" data-act="modal" data-m="finalize">${ic("checkCircle")}최종본 확정</button>` : ""}
        ${finalized ? `<button class="btn btn-primary">${ic("download")}최종본 v${d.final} 받기</button>` : `<button class="btn btn-primary" data-act="modal" data-m="newVersion">${ic("upload")}새 버전</button>`}
      </div></header>`;
    const bar = finalized ? `<div class="final-bar" role="status">${ic("checkCircle")}<span><strong>v${d.final}이 최종본으로 확정되었습니다.</strong> ${esc(user(d.finalizedBy).name)} · ${fmt(d.finalizedAt)} · 읽기 전용이며 다시 열 수 없습니다.${d.latest !== d.final ? ` 최신 버전(v${d.latest})은 최종본이 아닙니다.` : ""}</span></div>` : "";
    const mtabs = `<div class="m-tabs" role="tablist">${[["doc", "문서"], ["versions", "버전 " + versions.length], ["feedback", "협업"]].map(([k, l]) => `<button class="tab" role="tab" aria-selected="${ui.mtab === k}" data-act="mtab" data-v="${k}">${l}</button>`).join("")}</div>`;
    return shell(`<div class="ws ${finalized ? "has-bar" : ""}">${head}${bar}${mtabs}<div class="ws-body ${ui.left ? "" : "left-collapsed"}" data-m="${ui.mtab}">${left}${viewer}${right}</div></div>${modalHtml(d, versions, v)}`, { mode: "rail", current: "p" + p.id });
  }

  /* ---------------- 모달 ---------------- */
  function modalHtml(d, versions, v) {
    if (ui.modal === "newVersion") {
      return `<div class="overlay" data-act="closeOverlay"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="mv-h">
        <div class="modal-head"><div class="grow"><h2 id="mv-h">새 버전 올리기</h2><p class="muted" style="margin:2px 0 0;font-size:13px">v${d.latest + 1}로 등록됩니다. 이전 버전과 피드백은 그대로 남습니다.</p></div><button class="btn btn-ghost btn-sm btn-icon" data-act="closeModal" aria-label="닫기">${ic("x")}</button></div>
        <div class="modal-body">${fileField("nv-file", ui.file)}
          <div class="field"><label class="label" for="nv-note">변경사항 <span class="opt">선택</span></label><textarea id="nv-note" class="textarea" placeholder="v${d.latest} 대비 무엇이 바뀌었는지 적어 주세요."></textarea><span class="hint" style="text-align:right">0 / 1,000</span></div></div>
        <div class="modal-foot"><button class="btn" data-act="closeModal">취소</button><button class="btn btn-primary" ${ui.file ? "" : "disabled"}>v${d.latest + 1} 올리기</button></div></div></div>`;
    }
    if (ui.modal === "finalize") {
      const target = ui.finalV || d.latest;
      return `<div class="overlay" data-act="closeOverlay"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="mf-h">
        <div class="modal-head"><div class="grow"><h2 id="mf-h">최종본 확정</h2><p class="muted" style="margin:2px 0 0;font-size:13px">확정할 버전을 고르세요. 최신이 아닌 버전도 확정할 수 있습니다.</p></div><button class="btn btn-ghost btn-sm btn-icon" data-act="closeModal" aria-label="닫기">${ic("x")}</button></div>
        <div class="modal-body">
          <div class="radio-list" role="radiogroup" aria-label="확정할 버전">${versions.map((x) => `<label class="radio"><input type="radio" name="fv" value="${x.n}" data-act="finalV" ${x.n === target ? "checked" : ""}><div class="grow"><div class="row"><strong>v${x.n}</strong>${x.n === d.latest ? '<span class="badge">최신</span>' : ""}</div><div class="muted truncate" style="font-size:12.5px">${esc(x.file)} · ${esc(user(x.by).name)} · ${fmt(x.at)}</div></div></label>`).join("")}</div>
          ${target !== d.latest ? `<div class="alert alert-warning">${ic("alert")}<span>v${target}은 최신 버전(v${d.latest})이 아닙니다. v${target}을 공식 최종본으로 확정합니다.</span></div>` : ""}
          <div class="alert alert-danger" style="flex-direction:column;gap:4px"><strong class="row">${ic("lock", "icon-sm")}되돌릴 수 없는 작업입니다</strong><span>확정하면 문서가 영구 읽기 전용이 되어 새 버전 업로드, 피드백 작성, 설명 수정이 모두 막힙니다. 확정을 취소하거나 다시 열 수 없습니다.</span></div>
          <label class="check"><input type="checkbox" data-act="confirmFinal" ${ui.confirmFinal ? "checked" : ""}>위 내용을 이해했습니다.</label>
        </div>
        <div class="modal-foot"><button class="btn" data-act="closeModal">취소</button><button class="btn btn-danger" ${ui.confirmFinal ? "" : "disabled"}>v${target}을 최종본으로 확정</button></div></div></div>`;
    }
    if (ui.modal === "newProject") {
      return `<div class="overlay" data-act="closeOverlay"><div class="modal" role="dialog" aria-modal="true" aria-labelledby="np-h" style="max-width:480px">
        <div class="modal-head"><h2 id="np-h" class="grow">새 프로젝트</h2><button class="btn btn-ghost btn-sm btn-icon" data-act="closeModal" aria-label="닫기">${ic("x")}</button></div>
        <div class="modal-body"><div class="field"><label class="label" for="np-name">프로젝트 이름</label><input id="np-name" class="input" maxlength="100" placeholder="예: 2026 하반기 서비스 기획"></div>
        <div class="field"><label class="label" for="np-desc">설명 <span class="opt">선택</span></label><textarea id="np-desc" class="textarea" style="min-height:64px"></textarea></div><p class="hint" style="margin:0">만든 사람이 프로젝트 오너가 됩니다.</p></div>
        <div class="modal-foot"><button class="btn" data-act="closeModal">취소</button><button class="btn btn-primary">만들기</button></div></div></div>`;
    }
    return "";
  }

  /* ---------------- 버전 비교 워크스페이스 ---------------- */
  function compare(id) {
    const d = doc(id);
    const mode = opt("mode", "pdf");
    const base = +opt("base", 2), target = +opt("target", 3);
    const versions = D.versions[d.id];
    const ch = D.changes.filter((c) => ui.filter === "ALL" || c.type === ui.filter);
    const counts = { ADDED: 0, DELETED: 0, MODIFIED: 0 };
    D.changes.forEach((c) => counts[c.type]++);
    const cur = D.changes[ui.cur];
    const tyName = { ADDED: "추가", DELETED: "삭제", MODIFIED: "수정" };
    const tyCls = { ADDED: "add", DELETED: "del", MODIFIED: "mod" };
    const sel = (name, val) => `<label class="sr-only" for="${name}">${name === "base" ? "이전 버전" : "이후 버전"}</label><select class="select" id="${name}" data-act="vsel" data-k="${name}">${versions.map((x) => `<option value="${x.n}" ${x.n === val ? "selected" : ""}>v${x.n} · ${esc(x.file)}</option>`).join("")}</select>`;
    const head = `<header class="cmp-head">
      <a class="btn btn-ghost btn-sm" href="${withQ("/doc/" + d.id, { v: target })}">${ic("arrowL", "icon-sm")}문서로</a>
      <div style="min-width:0"><div class="title truncate">버전 비교 · ${esc(d.title)}</div></div>
      <div class="vsel">${sel("base", base)}<button class="btn btn-ghost btn-sm btn-icon" data-act="swap" aria-label="이전·이후 바꾸기">${ic("swap")}</button>${sel("target", target)}</div>
      <div class="seg" role="tablist" aria-label="비교 방식"><button role="tab" aria-selected="${mode === "text"}" data-act="mode" data-v="text">텍스트</button><button role="tab" aria-selected="${mode === "pdf"}" data-act="mode" data-v="pdf">PDF</button></div>
      <span class="grow"></span>
      <div class="counts" aria-label="변경 개수"><span class="badge badge-success">+ 추가 ${counts.ADDED}</span><span class="badge badge-danger">− 삭제 ${counts.DELETED}</span><span class="badge badge-warning">~ 수정 ${counts.MODIFIED}</span></div>
      <div class="chg-nav"><button class="btn btn-ghost btn-sm btn-icon" data-act="prev" aria-label="이전 변경 (K)">${ic("up")}</button><span aria-live="polite">${ui.cur + 1} / ${D.changes.length}</span><button class="btn btn-ghost btn-sm btn-icon" data-act="next" aria-label="다음 변경 (J)">${ic("down")}</button></div>
      <button class="btn btn-ghost btn-sm btn-icon ${ui.panel ? "is-active" : ""}" data-act="panel" aria-label="변경사항 패널 ${ui.panel ? "접기" : "펼치기"}">${ic("panelR")}</button>
    </header>`;
    const bar = mode === "pdf" ? `<div class="cmp-bar" role="toolbar" aria-label="PDF 비교 도구">
        <button class="btn btn-ghost btn-sm btn-icon" data-act="zoom" data-d="-1" aria-label="축소">${ic("minus")}</button><span class="zoom" style="width:46px;text-align:center;font-size:12.5px" data-zoom-label>100%</span><button class="btn btn-ghost btn-sm btn-icon" data-act="zoom" data-d="1" aria-label="확대">${ic("plus")}</button>
        <button class="btn btn-ghost btn-sm ${ui.zoom === null ? "is-active" : ""}" data-act="fit">${ic("fit", "icon-sm")}폭 맞춤</button>
        <button class="btn btn-ghost btn-sm" data-act="rotate">${ic("rotate", "icon-sm")}회전</button><span class="sep"></span>
        <button class="btn btn-ghost btn-sm ${ui.sync ? "is-active" : ""}" data-act="sync" aria-pressed="${ui.sync}">${ic("link", "icon-sm")}동기 스크롤</button>
        <button class="btn btn-ghost btn-sm ${ui.hl ? "is-active" : ""}" data-act="hl" aria-pressed="${ui.hl}">${ic("eye", "icon-sm")}강조 표시</button>
        <div class="legend" aria-label="범례"><span><i class="a"></i>추가 · 실선</span><span><i class="d"></i>삭제 · 파선</span><span><i class="m"></i>수정 · 점선</span></div>
      </div>`
      : `<div class="cmp-bar"><button class="btn btn-ghost btn-sm ${ui.fold ? "is-active" : ""}" data-act="fold" aria-pressed="${ui.fold}">변경 없는 문단 접기</button><span class="muted" style="font-size:12.5px;margin-left:8px">공백·줄바꿈·쪽 번호 차이는 무시합니다 · 변경 위치는 비교 알고리즘이 정합니다</span></div>`;
    const panel = `<aside class="chg-panel" aria-label="변경사항">
        <div class="pane-head"><strong>변경사항</strong><span class="muted">${D.changes.length}</span></div>
        <div class="chg-filter" role="group" aria-label="유형 필터">${[["ALL", "전체", D.changes.length], ["ADDED", "추가", counts.ADDED], ["DELETED", "삭제", counts.DELETED], ["MODIFIED", "수정", counts.MODIFIED]].map(([k, l, n]) => `<button class="chip" data-act="filter" data-v="${k}" aria-pressed="${ui.filter === k}">${l} ${n}</button>`).join("")}</div>
        <div class="pane-scroll"><div class="chg-list">${ch.map((c) => { const i = D.changes.indexOf(c); return `<button class="chg t-${tyCls[c.type]}" data-act="goto" data-i="${i}" ${i === ui.cur ? 'aria-current="true"' : ""}>
          <span class="ty"><span class="t-${tyCls[c.type]}-text">${c.type === "ADDED" ? "+" : c.type === "DELETED" ? "−" : "~"} ${tyName[c.type]}</span><span class="muted">· p.${c.page} · ${esc(c.label)}</span></span>
          <span class="ba">${c.type === "MODIFIED" ? `<del>${esc(c.before)}</del> → <ins>${esc(c.after)}</ins>` : c.type === "ADDED" ? `<ins>${esc(c.after)}</ins>` : `<del>${esc(c.before)}</del>`}</span></button>`; }).join("")}</div>
          <p class="hint" style="padding:4px 12px 12px;margin:0">모든 변경의 위치를 PDF에서 확인했습니다. 위치를 찾지 못한 변경은 PDF에 표시하지 않고 여기에 따로 안내합니다.</p></div>
      </aside>`;
    let bodyHtml;
    if (mode === "pdf") {
      const pane = (side, n) => { const k = `${d.id}:${n}`; const pages = D.pages[k] || [[]]; const vv = versions.find((x) => x.n === n); return `<section class="cmp-pane ${side}" aria-label="${side === "base" ? "이전" : "이후"} 버전 v${n}"><div class="cmp-pane-head"><strong>${side === "base" ? "이전" : "이후"} · v${n}</strong><span class="muted truncate">${esc(vv.file)} · ${pages.length}쪽</span><span class="grow"></span><span class="muted" style="font-size:12px">${side === "base" ? "삭제·수정 전" : "추가·수정 후"}</span></div><div class="canvas ${ui.hl ? "" : "no-hl"}" data-canvas data-side="${side}">${pages.map((_, i) => paperHtml(k, i, pages.length, { hlSide: side })).join("")}</div></section>`; };
      bodyHtml = `${pane("base", base)}<div class="splitter" role="separator" aria-orientation="vertical" aria-label="패널 너비 조절" tabindex="0" data-split></div>${pane("target", target)}${panel}`;
    } else {
      bodyHtml = `<div class="tcmp" data-tcmp>${textRows()}</div>${panel}`;
    }
    const mt = `<div class="cmp-m-tabs" role="tablist">${[["base", "이전 v" + base], ["target", "이후 v" + target], ["changes", "변경 " + D.changes.length]].map(([k, l]) => `<button class="tab" role="tab" aria-selected="${ui.cmpM === k}" data-act="cmpM" data-v="${k}">${l}</button>`).join("")}</div>`;
    return shell(`<div class="cmp">${head}${bar}${mt}<div class="cmp-body ${ui.panel ? "" : "panel-collapsed"}" data-m="${ui.cmpM}" ${ui.split ? `style="--split:${ui.split}fr"` : ""}>${bodyHtml}</div></div>`, { mode: "focus" });
  }
  function textRows() {
    const rows = [
      { eq: ["프로젝트 회의록"], ln: 1 },
      { k: "mod", id: "c0", l: "회의 날짜: <mark>2026-11-11</mark>", r: "회의 날짜: <mark>2026-11-25</mark>", ln: [2, 2] },
      { eq: ["1. 프로젝트 목표", "문서 협업 시스템 개발", "2. 개발 일정", "11월 20일까지 기능 검증 완료", "3. 결정 사항", "문서 버전 관리 기능을 우선 개발한다."], ln: 3 },
      { k: "del", id: "c3", l: "<del>참고: 회의록은 매주 갱신한다.</del>", r: null, ln: [9, null] },
      { k: "add", id: "c1", l: null, r: "<ins>UI/UX를 개선한다.</ins>", ln: [null, 9] },
      { eq: ["4. 최종 발표"], ln: 10 },
      { k: "mod", id: "c2", l: "11월 <mark>25일</mark> 발표 자료 완성", r: "11월 <mark>30일</mark> 발표 자료 완성", ln: [11, 11] },
      { eq: ["5. 참석자", "정의훈, 김민지, 박서준"], ln: 12 },
      { k: "add", id: "c4", l: null, r: "<ins>부록. 다음 회의 안건</ins>\n<ins>1) 버전 비교 화면 검토</ins>\n<ins>2) 피드백 알림 정책 논의</ins>\n<ins>3) 발표 리허설 일정 확정</ins>", ln: [null, 14] },
    ];
    const curId = D.changes[ui.cur] && D.changes[ui.cur].id;
    const sign = { add: "+", del: "−", mod: "~" };
    let html = `<div class="tcmp-head"><div>이전 · v2 · 회의록 v2.pdf</div><div>이후 · v3 · 회의록 v3.pdf</div></div>`;
    rows.forEach((r, idx) => {
      if (r.eq) {
        const lines = r.eq;
        const folded = ui.fold && lines.length > 2 && !(ui.openFold || []).includes(idx);
        const show = folded ? [lines[0], null, lines[lines.length - 1]] : lines;
        let n = r.ln;
        show.forEach((t, i) => {
          if (t === null) { html += `<button class="fold" data-act="unfold" data-i="${idx}">${ic("chevD", "icon-sm")}변경 없는 문단 ${lines.length - 2}개 펼치기</button>`; n += lines.length - 2; return; }
          html += `<div class="trow"><div class="tcell"><span class="gut">${n}</span><div class="tline">${esc(t)}</div></div><div class="tcell"><span class="gut">${n}</span><div class="tline">${esc(t)}</div></div></div>`;
          n++;
        });
        return;
      }
      const cell = (side, content, ln) => content == null ? `<div class="tcell empty-side"><span class="gut"></span><div class="tline"></div></div>`
        : `<div class="tcell ${r.k}"><span class="gut">${sign[r.k]} ${ln ?? ""}</span><div class="tline"><span class="sr-only">${r.k === "add" ? "추가:" : r.k === "del" ? "삭제:" : side === "l" ? "수정 전:" : "수정 후:"}</span>${content}</div></div>`;
      html += `<div class="trow ${r.id === curId ? "is-current" : ""}" data-chg="${r.id}">${cell("l", r.l, r.ln[0])}${cell("r", r.r, r.ln[1])}</div>`;
    });
    return html;
  }

  /* ---------------- 인증·기타 ---------------- */
  function auth(signup) {
    const err = st() === "error";
    const side = `<div class="auth-side"><div class="brand" style="padding:0"><span class="logo">D</span><span>DocFlow</span></div>
      <div style="display:flex;flex-direction:column;gap:20px"><h2 style="font-size:26px;line-height:1.35;margin:0;letter-spacing:-0.02em">버전마다 쌓이는 피드백을<br>한 화면에서 검토하고 확정하세요.</h2>
        <div class="auth-preview" aria-hidden="true"><div class="row" style="font-size:12.5px"><strong>11월 정기 회의록</strong><span class="status status-active">협업 중</span><span class="grow"></span><span class="badge">v3</span></div>
          <div class="row" style="font-size:12px;gap:6px"><span class="badge badge-success">+ 추가 2</span><span class="badge badge-danger">− 삭제 1</span><span class="badge badge-warning">~ 수정 2</span></div>
          <div style="font-size:12.5px;color:var(--ink-2)">회의 날짜: <span class="hl hl-mod">2026-11-25</span> · <span class="hl hl-add">UI/UX를 개선한다.</span></div></div></div>
      <p class="muted" style="margin:0;font-size:12.5px">초대받은 구성원만 사용할 수 있는 비공개 서비스입니다.</p></div>`;
    const form = signup ? `<form class="auth-form" onsubmit="return false"><div><h1 class="h1">초대코드로 가입</h1><p class="subtitle">관리자에게 받은 초대코드가 필요합니다.</p></div>
        <div class="field"><label class="label" for="su-code">초대코드</label><input id="su-code" class="input mono" autocomplete="off" placeholder="XXXX-XXXX-XXXX"></div>
        <div class="field"><label class="label" for="su-email">이메일</label><input id="su-email" class="input" type="email" autocomplete="email"></div>
        <div class="field"><label class="label" for="su-name">이름</label><input id="su-name" class="input" autocomplete="name"></div>
        <div class="field"><label class="label" for="su-pw">비밀번호</label><input id="su-pw" class="input" type="password" autocomplete="new-password"><span class="hint">8~72자, 영문·숫자·기호 중 2가지 이상</span></div>
        <div class="field"><label class="label" for="su-pw2">비밀번호 확인</label><input id="su-pw2" class="input" type="password" autocomplete="new-password"></div>
        ${err ? `<div class="alert alert-danger" role="alert">${ic("alert")}<span>초대코드가 올바르지 않거나 만료되었습니다.</span></div>` : ""}
        <button class="btn btn-primary btn-lg">가입하기</button><p class="muted" style="text-align:center;margin:0;font-size:13px">이미 계정이 있나요? <a class="link" href="${withQ("/login")}">로그인</a></p></form>`
      : `<form class="auth-form" onsubmit="return false"><div><h1 class="h1">로그인</h1><p class="subtitle">DocFlow 계정으로 로그인하세요.</p></div>
        <div class="field"><label class="label" for="li-email">이메일</label><input id="li-email" class="input" type="email" autocomplete="email" value="owner@example.com"></div>
        <div class="field"><label class="label" for="li-pw">비밀번호</label><input id="li-pw" class="input" type="password" autocomplete="current-password" value="password"></div>
        ${err ? `<div class="alert alert-danger" role="alert">${ic("alert")}<span>이메일 또는 비밀번호가 올바르지 않습니다.</span></div>` : ""}
        <a class="btn btn-primary btn-lg" href="${withQ("/")}">로그인</a><p class="muted" style="text-align:center;margin:0;font-size:13px">초대받으셨나요? <a class="link" href="${withQ("/signup")}">초대코드로 가입</a></p></form>`;
    return `<div class="auth">${side}<div class="auth-main">${form}</div></div>`;
  }
  function invites() {
    const rows = [["ABCD-…-7Q2K", "QA 테스트 참여자", "사용 가능", "11월 26일까지", ""], ["H3KD-…-91PX", "김민지", "사용됨", "11월 20일", "김민지"], ["9QW2-…-LL0A", "외부 검토자", "만료", "11월 18일", ""]];
    return shell(`<div class="page page-mid"><div class="page-head"><div><h1 class="h1">초대 관리</h1><p class="subtitle">초대코드를 발급하고 사용 현황을 확인합니다. 관리자만 볼 수 있습니다.</p></div><button class="btn btn-primary">${ic("plus")}초대코드 발급</button></div>
      <div class="list"><div class="list-head" style="grid-template-columns:minmax(0,1fr) 140px 90px 120px 80px"><span>코드 · 메모</span><span>사용자</span><span>상태</span><span>만료·사용</span><span></span></div>
      ${rows.map(([c, m, s, e, u]) => `<div class="list-row" style="grid-template-columns:minmax(0,1fr) 140px 90px 120px 80px;cursor:default"><div><div class="mono">${c}</div><div class="desc">${m}</div></div><span class="muted hide-m">${u || "—"}</span><span><span class="badge ${s === "사용 가능" ? "badge-brand" : s === "사용됨" ? "badge-success" : ""}">${s}</span></span><span class="muted hide-m">${e}</span><span style="text-align:right">${s === "사용 가능" ? '<button class="btn btn-sm btn-ghost">폐기</button>' : ""}</span></div>`).join("")}</div></div>`, { current: "invites" });
  }

  /* ---------------- 프로토타입 컨트롤 ---------------- */
  function protoPanel() {
    const setter = (k, opts) => `<div><div style="font-weight:600;margin-bottom:4px">${k === "role" ? "역할" : k === "ai" ? "AI 기능" : k === "fb" ? "피드백" : "화면 상태"}</div><div class="seg">${opts.map(([v, l]) => `<button data-act="proto" data-k="${k}" data-v="${v}" aria-pressed="${opt(k, opts[0][0]) === v}">${l}</button>`).join("")}</div></div>`;
    const links = [["/login", "로그인"], ["/signup", "회원가입"], ["/", "홈"], ["/projects/1", "프로젝트 상세"], ["/projects/1/new", "문서 등록"], ["/doc/2", "문서 상세"], ["/doc/3", "최종본 확정"], ["/doc/4", "Office 문서"], ["/compare/2?mode=pdf", "PDF 비교"], ["/compare/2?mode=text", "텍스트 비교"], ["/invites", "초대 관리"], ["/projects", "프로젝트 목록"]];
    return `<div class="proto">${ui.proto ? `<div class="proto-panel" role="dialog" aria-label="프로토타입 설정"><strong>프로토타입 설정</strong>${setter("role", [["owner", "오너"], ["member", "구성원"]])}${setter("ai", [["on", "켜짐"], ["off", "꺼짐"]])}${setter("fb", [["some", "있음"], ["none", "없음"]])}${setter("st", [["ready", "정상"], ["loading", "로딩"], ["error", "오류"]])}
      <div><div style="font-weight:600;margin-bottom:4px">화면</div><div class="proto-links">${links.map(([h, l]) => { const [pa, qs] = h.split("?"); return `<a href="${withQ(pa, Object.fromEntries(new URLSearchParams(qs || "")))}">${l}</a>`; }).join("")}</div></div></div>` : ""}<button class="proto-btn" data-act="protoToggle" aria-expanded="${ui.proto}">프로토타입 ⚙</button></div>`;
  }

  /* ---------------- 렌더·이벤트 ---------------- */
  let lastPath = "";
  function render() {
    const { path, seg } = R();
    if (path !== lastPath) { // 화면이 바뀌면 화면별 상태 초기화
      const keepDoc = path.startsWith("/doc/") && lastPath.startsWith("/doc/") && path === lastPath;
      if (!keepDoc) { ui.modal = null; ui.file = null; ui.title = ""; ui.titleTouched = false; ui.uploading = false; ui.confirmFinal = false; ui.finalV = null; }
      if (!path.startsWith("/compare")) ui.cur = 0;
      lastPath = path;
    }
    document.body.classList.toggle("shot", opt("shot", "") === "1");
    let html;
    if (seg[0] === "login") html = auth(false);
    else if (seg[0] === "signup") html = auth(true);
    else if (seg[0] === "projects" && seg[2] === "new") html = docCreate(seg[1]);
    else if (seg[0] === "projects" && seg[1]) html = projectDetail(seg[1]);
    else if (seg[0] === "projects") html = projects();
    else if (seg[0] === "doc") html = docDetail(seg[1]);
    else if (seg[0] === "compare") html = compare(seg[1]);
    else if (seg[0] === "invites") html = invites();
    else html = home();
    root.innerHTML = html + protoPanel();
    applyZoom();
    bindAfter();
  }

  /* 폭 맞춤·확대·회전: 페이지 크기를 회전에 맞게 바꾸고 변수(--z, --rot)로 종이를 그린다 */
  const ZOOMS = [0.5, 0.75, 1, 1.25, 1.5, 2];
  function applyZoom() {
    const canvases = document.querySelectorAll("[data-canvas]");
    if (!canvases.length) return;
    const odd = ui.rot % 180 !== 0;
    let z = ui.zoom;
    if (z === null) {
      const w = Math.min(...[...canvases].map((c) => c.clientWidth)) - 48;
      // 폭 맞춤: 문서 상세는 읽기 편한 1.25배, 비교 화면은 1.5배를 넘지 않게 한다
      const cap = document.querySelector(".cmp") ? 1.5 : 1.25;
      z = Math.max(0.4, Math.min(cap, w / (odd ? 792 : 612)));
    }
    canvases.forEach((c) => c.querySelectorAll(".page-wrap").forEach((pw) => {
      pw.style.width = `${(odd ? 792 : 612) * z}px`;
      pw.style.height = `${(odd ? 612 : 792) * z}px`;
      pw.querySelector(".paper").style.setProperty("--z", z);
      pw.querySelector(".paper").style.setProperty("--rot", `${ui.rot}deg`);
    }));
    document.querySelectorAll("[data-zoom-label]").forEach((el) => { el.textContent = `${Math.round(z * 100)}%`; });
    ui.lastZ = z;
  }

  function scrollToChange(smooth = true) {
    const c = D.changes[ui.cur];
    if (!c) return;
    document.querySelectorAll(`[data-chg="${c.id}"]`).forEach((el) => {
      const pane = el.closest("[data-canvas], [data-tcmp]");
      if (!pane) return;
      const r = el.getBoundingClientRect(), pr = pane.getBoundingClientRect();
      pane.scrollTo({ top: pane.scrollTop + r.top - pr.top - pr.height / 3, behavior: smooth ? "smooth" : "auto" });
    });
  }

  /* 동기 스크롤: 같은 쪽 번호 + 쪽 안의 위치 비율로 맞춘다(쪽 수가 달라도 마지막 쪽에서 멈춤) */
  let syncing = false;
  function bindAfter() {
    const panes = document.querySelectorAll('.cmp-pane [data-canvas]');
    panes.forEach((pane) => pane.addEventListener("scroll", () => {
      if (!ui.sync || syncing) return;
      const other = [...panes].find((p) => p !== pane);
      if (!other) return;
      const pages = [...pane.querySelectorAll(".page-wrap")];
      const top = pane.scrollTop;
      let idx = 0;
      pages.forEach((pg, i) => { if (pg.offsetTop - pane.offsetTop <= top + 1) idx = i; });
      const pg = pages[idx];
      const frac = pg ? (top - (pg.offsetTop - pane.offsetTop)) / pg.offsetHeight : 0;
      const op = other.querySelectorAll(".page-wrap");
      const target = op[Math.min(idx, op.length - 1)];
      if (!target) return;
      syncing = true;
      other.scrollTop = target.offsetTop - other.offsetTop + Math.min(frac, idx >= op.length ? 1 : frac) * target.offsetHeight;
      requestAnimationFrame(() => { syncing = false; });
    }, { passive: true }));
    const split = document.querySelector("[data-split]");
    if (split) split.addEventListener("pointerdown", (e) => {
      const body = split.parentElement;
      const rect = body.getBoundingClientRect();
      const panelW = ui.panel ? 300 : 0;
      split.classList.add("dragging");
      split.setPointerCapture(e.pointerId);
      const move = (ev) => {
        const avail = rect.width - panelW - 6;
        const ratio = Math.min(0.75, Math.max(0.25, (ev.clientX - rect.left) / avail));
        ui.split = (ratio / (1 - ratio)).toFixed(3);
        body.style.setProperty("--split", `${ui.split}fr`);
      };
      const up = () => { split.classList.remove("dragging"); split.removeEventListener("pointermove", move); applyZoom(); };
      split.addEventListener("pointermove", move);
      split.addEventListener("pointerup", up, { once: true });
    });
    const dz = document.querySelector("[data-drop]");
    if (dz) {
      dz.addEventListener("dragover", (e) => { e.preventDefault(); dz.classList.add("drag"); });
      dz.addEventListener("dragleave", () => dz.classList.remove("drag"));
      dz.addEventListener("drop", (e) => { e.preventDefault(); const f = e.dataTransfer.files[0]; if (f) setFile(f); });
    }
    const dialog = document.querySelector('[role="dialog"][aria-modal="true"]');
    if (dialog && !dialog.contains(document.activeElement)) (dialog.querySelector("input, textarea, button:not([data-act=closeModal])") || dialog).focus();
  }
  function setFile(f) {
    const kb = f.size / 1024;
    ui.file = { name: f.name, size: kb > 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb.toFixed(1)} KB` };
    if (!ui.titleTouched) ui.title = f.name.replace(/\.[^.]+$/, "");
    render();
  }
  function setQuery(extra) {
    const { path, q } = R();
    location.hash = `${path}?${new URLSearchParams({ ...q, ...extra })}`;
  }

  document.addEventListener("click", (e) => {
    const t = e.target.closest("[data-act]");
    const hl = e.target.closest(".hl[data-chg]");
    if (hl && !t) { ui.cur = D.changes.findIndex((c) => c.id === hl.dataset.chg); render(); scrollToChange(); return; }
    if (!t) return;
    const a = t.dataset.act, v = t.dataset.v;
    switch (a) {
      case "rtab": ui.rtab = v; render(); break;
      case "mtab": ui.mtab = v; render(); break;
      case "cmpM": ui.cmpM = v; render(); break;
      case "left": ui.left = !ui.left; render(); break;
      case "zoom": { const i = ZOOMS.findIndex((z) => z >= (ui.lastZ || 1) - 0.001); ui.zoom = ZOOMS[Math.max(0, Math.min(ZOOMS.length - 1, (i < 0 ? ZOOMS.length - 1 : i) + (+t.dataset.d)))]; render(); scrollToChange(false); break; }
      case "fit": ui.zoom = null; render(); break;
      case "rotate": ui.rot = (ui.rot + 90) % 360; render(); scrollToChange(false); break;
      case "sync": ui.sync = !ui.sync; render(); break;
      case "hl": ui.hl = !ui.hl; render(); break;
      case "panel": ui.panel = !ui.panel; render(); break;
      case "fold": ui.fold = !ui.fold; render(); break;
      case "unfold": ui.openFold = [...(ui.openFold || []), +t.dataset.i]; render(); break;
      case "filter": ui.filter = v; render(); break;
      case "goto": ui.cur = +t.dataset.i; render(); scrollToChange(); break;
      case "next": ui.cur = Math.min(D.changes.length - 1, ui.cur + 1); render(); scrollToChange(); break;
      case "prev": ui.cur = Math.max(0, ui.cur - 1); render(); scrollToChange(); break;
      case "mode": setQuery({ mode: v }); break;
      case "swap": { const q = R().q; setQuery({ base: q.target || 3, target: q.base || 2 }); break; }
      case "status": ui.status = v; render(); break;
      case "clearFilter": ui.status = "ALL"; ui.query = ""; render(); break;
      case "modal": ui.modal = t.dataset.m; ui.file = null; ui.confirmFinal = false; ui.finalV = null; ui.opener = t; render(); break;
      case "closeModal": closeModal(); break;
      case "closeOverlay": if (e.target === t) closeModal(); break;
      case "removeFile": ui.file = null; if (!ui.titleTouched) ui.title = ""; render(); break;
      case "submitDoc": ui.uploading = true; render(); setTimeout(() => { location.hash = withQ("/doc/2"); }, 1200); break;
      case "protoToggle": ui.proto = !ui.proto; render(); break;
      case "proto": setQuery({ [t.dataset.k]: v }); break;
      case "confirmFinal": break;
      default: break;
    }
  });
  document.addEventListener("change", (e) => {
    const t = e.target.closest("[data-act]");
    if (!t) return;
    if (t.dataset.act === "file" && t.files[0]) setFile(t.files[0]);
    if (t.dataset.act === "vsel") setQuery({ [t.dataset.k]: t.value });
    if (t.dataset.act === "finalV") { ui.finalV = +t.value; render(); }
    if (t.dataset.act === "confirmFinal") { ui.confirmFinal = t.checked; render(); }
  });
  document.addEventListener("input", (e) => {
    const t = e.target;
    if (t.dataset.act === "title") { ui.title = t.value; ui.titleTouched = true; const btn = document.querySelector('[data-act="submitDoc"]'); if (btn) btn.disabled = !ui.file || !ui.title.trim(); }
    if (t.dataset.act === "search") { ui.query = t.value; const pos = t.selectionStart; render(); const s = document.querySelector('[data-act="search"]'); if (s) { s.focus(); s.setSelectionRange(pos, pos); } }
  });
  document.addEventListener("toggle", (e) => { if (e.target.dataset && e.target.dataset.act === "opt") ui.optOpen = e.target.open; }, true);
  function closeModal() { ui.modal = null; const o = ui.opener; render(); if (o && o.dataset) { const again = document.querySelector(`[data-act="modal"][data-m="${o.dataset.m}"]`); if (again) again.focus(); } }
  document.addEventListener("keydown", (e) => {
    const dialog = document.querySelector('[role="dialog"][aria-modal="true"]');
    if (dialog) {
      if (e.key === "Escape") { e.preventDefault(); closeModal(); return; }
      if (e.key === "Tab") { // 포커스 가두기
        const f = [...dialog.querySelectorAll('a[href], button:not([disabled]), input:not([disabled]), textarea, select, [tabindex="0"]')].filter((x) => x.offsetParent !== null);
        if (!f.length) return;
        if (e.shiftKey && document.activeElement === f[0]) { e.preventDefault(); f[f.length - 1].focus(); }
        else if (!e.shiftKey && document.activeElement === f[f.length - 1]) { e.preventDefault(); f[0].focus(); }
      }
      return;
    }
    if (R().seg[0] === "compare" && !/INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName)) {
      if (e.key === "j" || e.key === "ArrowDown" && e.altKey) { ui.cur = Math.min(D.changes.length - 1, ui.cur + 1); render(); scrollToChange(); }
      if (e.key === "k" || e.key === "ArrowUp" && e.altKey) { ui.cur = Math.max(0, ui.cur - 1); render(); scrollToChange(); }
    }
    if (e.key === "Enter" && e.target.classList && e.target.classList.contains("hl")) e.target.click();
  });
  window.addEventListener("resize", () => { if (ui.zoom === null) applyZoom(); });
  window.addEventListener("hashchange", render);
  render();
})();
