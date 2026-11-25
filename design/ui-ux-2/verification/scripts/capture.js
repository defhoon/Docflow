// Capture comparable screenshots + layout metrics from the running app.
// Usage: node capture.js <outDir> <label>
const { chromium } = require("playwright");
const fs = require("fs");
const [out, label] = process.argv.slice(2);
const seed = JSON.parse(fs.readFileSync(__dirname + "/seed.json", "utf8"));
// 로컬 시드 계정 비밀번호는 환경 변수로 받는다(저장소에 값을 두지 않음).
const SEED_PASSWORD = process.env.DEV_SEED_PASSWORD;
if (!SEED_PASSWORD) throw new Error("DEV_SEED_PASSWORD is required");
const APP = "http://127.0.0.1:3000";
fs.mkdirSync(out, { recursive: true });

async function login(page, email = "author@docflow.local") {
  await page.goto(APP + "/login");
  await page.fill("input[type=email], input[name=email]", email);
  await page.fill("input[type=password]", SEED_PASSWORD);
  await page.press("input[type=password]", "Enter");
  await page.waitForURL((u) => !u.pathname.startsWith("/login"), { timeout: 15000 });
}

async function metrics(page) {
  return page.evaluate(() => {
    const vw = innerWidth, vh = innerHeight;
    const area = (sel) => {
      let best = 0;
      for (const el of document.querySelectorAll(sel)) {
        const r = el.getBoundingClientRect();
        const w = Math.max(0, Math.min(r.right, vw) - Math.max(r.left, 0));
        const h = Math.max(0, Math.min(r.bottom, vh) - Math.max(r.top, 0));
        best = Math.max(best, w * h);
      }
      return Math.round((best / (vw * vh)) * 1000) / 10;
    };
    const scrollers = [document.scrollingElement, ...document.querySelectorAll("main")];
    const docH = Math.max(...scrollers.map((e) => e.scrollHeight));
    return {
      viewport: `${vw}x${vh}`,
      pageHeight: docH,
      pageScreens: Math.round((docH / vh) * 10) / 10,
      horizontalOverflow: document.documentElement.scrollWidth > vw + 1,
      pdfAreaPct: area("iframe, [data-pdf-viewport], [data-viewer-scroll]"),
      feedbackVisible: (() => {
        const el = document.querySelector("[aria-label$='피드백'], #feedback-heading, [data-feedback-panel]");
        if (!el) return false;
        const r = el.getBoundingClientRect();
        return r.top < vh && r.bottom > 0;
      })(),
    };
  });
}

(async () => {
  const browser = await chromium.launch();
  const result = {};
  const shot = async (page, name) => {
    await page.waitForTimeout(900);
    await page.screenshot({ path: `${out}/${name}.png` });
    result[name] = await metrics(page);
  };
  const ctx = await browser.newContext({ viewport: { width: 1440, height: 900 }, deviceScaleFactor: 2 });
  const page = await ctx.newPage();
  const errors = [];
  page.on("console", (m) => m.type() === "error" && errors.push(m.text()));
  await shot(page, "01-login").catch(() => {});
  await page.goto(APP + "/login"); await shot(page, "01-login");
  await login(page);
  await page.goto(APP + "/"); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await shot(page, "02-home");
  await page.goto(`${APP}/projects/${seed.project}`); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await shot(page, "03-project");
  const doc = `${APP}/projects/${seed.project}/documents/${seed.meeting}`;
  await page.goto(doc); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await shot(page, "04-doc-initial");
  // Before UI needs an explicit click to open the preview. Count it.
  let clicks = 0;
  const open = page.getByRole("button", { name: /미리보기 열기/ });
  if (await open.count()) { await open.first().click(); clicks++; await page.waitForTimeout(2500); }
  await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await page.waitForTimeout(1500);
  await shot(page, "05-doc-preview");
  result["05-doc-preview"].clicksToPreview = clicks;
  await page.goto(`${APP}/projects/${seed.project}/documents/${seed.spec}`); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await shot(page, "06-doc-finalized");
  const cmp = `${APP}/projects/${seed.project}/documents/${seed.meeting}/compare?base=2&target=3`;
  await page.goto(cmp); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await page.waitForTimeout(2000); await shot(page, "07-compare-default");
  const pdfTab = page.getByRole("tab", { name: /PDF/ });
  let cmpClicks = 0;
  if (await pdfTab.count() && (await pdfTab.first().getAttribute("aria-selected")) !== "true") { await pdfTab.first().click(); cmpClicks++; await page.waitForTimeout(3000); }
  await shot(page, "08-compare-pdf");
  result["08-compare-pdf"].clicksToPdf = cmpClicks;
  const textTab = page.getByRole("tab", { name: /텍스트/ });
  if (await textTab.count()) { await textTab.first().click(); await page.waitForTimeout(1200); }
  await shot(page, "09-compare-text");
  await page.goto(`${APP}/projects/${seed.project}/documents/new`); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await shot(page, "10-doc-create");
  for (const vp of [{ width: 1920, height: 1080 }, { width: 1280, height: 720 }]) {
    await page.setViewportSize(vp);
    await page.goto(doc); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500));
    const o = page.getByRole("button", { name: /미리보기 열기/ });
    if (await o.count()) { await o.first().click(); await page.waitForTimeout(2500); }
    await shot(page, `11-doc-${vp.width}`);
    await page.goto(cmp); await page.waitForLoadState("load").then(() => page.waitForTimeout(1500)); await page.waitForTimeout(2500); await shot(page, `12-compare-${vp.width}`);
  }
  await ctx.close();
  const m = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true });
  const mp = await m.newPage();
  await login(mp);
  await mp.goto(APP + "/"); await mp.waitForLoadState("load").then(() => mp.waitForTimeout(1500)); await shot(mp, "m1-home");
  await mp.goto(doc); await mp.waitForLoadState("load").then(() => mp.waitForTimeout(1500)); await shot(mp, "m2-doc");
  await mp.goto(cmp); await mp.waitForLoadState("load").then(() => mp.waitForTimeout(1500)); await mp.waitForTimeout(2500); await shot(mp, "m3-compare");
  await browser.close();
  result.consoleErrors = errors;
  fs.writeFileSync(`${out}/metrics-${label}.json`, JSON.stringify(result, null, 2));
  console.log(JSON.stringify(result, null, 1));
})();
