# Capturing Screen Recordings with Playwright

Record real app interactions for release videos. Playwright captures the full viewport as `.webm` (VP8) via `recordVideo` on the browser context.

## Setup

Playwright must be installed in the project being recorded (or run from a directory where it is). Scripts must run from a directory where Playwright resolves. App must be running at `{{APP_BASE_URL}}` (see `project.config.md`).

## Recording Rules

**Viewport:** 540×960, `deviceScaleFactor: 1`. Recording size matches viewport. Remotion scales 2× to fill 1080×1920 via `objectFit: "cover"`.

**DPR must be 1.** Playwright's `recordVideo` ignores `deviceScaleFactor` — at DPR 2, content lands in the top-left corner of the canvas instead of filling it. Always use DPR 1.

**One clip per scene.** Separate browser contexts per clip. Each has:
- **Fast setup** — login, navigate, prepare state (click checkboxes, open dialogs, etc.)
- **Slow showcase** — the part the viewer sees. 600-900ms between actions, 1200ms pauses to let things settle.

Log `Date.now()` at the setup→showcase boundary. Convert to Remotion frames: `Math.round(seconds × 30)`.

**Hide app chrome that doesn't belong in the video.** Inject CSS via `addStyleTag` after each navigation. Example for a shadcn sidebar (use whatever `hide_chrome_css` is defined in your `project.config.md`):

```js
const HIDE_CHROME_CSS = `
  [data-sidebar="sidebar"], [data-sidebar="rail"] { display: none !important; }
  [data-slot="sidebar-wrapper"] {
    --sidebar-width: 0px !important;
    --sidebar-width-icon: 0px !important;
  }
`;
// After navigating to the target page:
await page.addStyleTag({ content: HIDE_CHROME_CSS });
```

## Recording Pattern

```js
// _capture.cjs — run from a directory where playwright resolves: node _capture.cjs
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const CLIPS_DIR = path.join(__dirname, 'release-video-{feature}', 'public', 'clips');
const LOGIN_URL = '{{LOGIN_URL}}';   // from project.config.md
const READY_SELECTOR = '{{READY_SELECTOR}}';
const VP = { width: 540, height: 960 };

const HIDE_CHROME_CSS = `{{HIDE_CHROME_CSS}}`;

(async () => {
  fs.mkdirSync(CLIPS_DIR, { recursive: true });
  const browser = await chromium.launch({ headless: true });
  const startFromValues = {};

  async function makeContext() {
    return browser.newContext({
      viewport: VP,
      deviceScaleFactor: 1,
      recordVideo: { dir: CLIPS_DIR, size: VP },
    });
  }

  async function setup(page, targetUrl) {
    await page.goto(LOGIN_URL);
    await page.waitForURL('**/{{POST_LOGIN_PATH}}**', { timeout: 15000 });
    await page.goto(targetUrl);
    await page.waitForSelector(READY_SELECTOR, { timeout: 15000 });
    await page.addStyleTag({ content: HIDE_CHROME_CSS });
    await page.waitForTimeout(1200);
  }

  async function saveClip(ctx, name) {
    const pg = ctx.pages()[0];
    const videoPath = await pg.video().path();
    await ctx.close();
    const dest = path.join(CLIPS_DIR, name);
    if (fs.existsSync(dest)) fs.unlinkSync(dest);
    fs.renameSync(videoPath, dest);
    console.log(`  Saved: ${name}`);
  }

  // ── CLIP: example ──────────────────────────────────────────────
  {
    const ctx = await makeContext();
    const page = await ctx.newPage();
    const ctxStart = Date.now();
    await setup(page, '{{APP_BASE_URL}}/{{TARGET_PATH}}');

    // ▸ Showcase starts here
    const showcaseStart = (Date.now() - ctxStart) / 1000;
    startFromValues['clip-01'] = showcaseStart;
    console.log(`  Setup: ${showcaseStart.toFixed(1)}s`);

    // Slow, deliberate interactions — this is what the viewer sees
    await page.locator('table tbody tr').nth(0).locator('td').first().hover();
    await page.waitForTimeout(600);
    await page.locator('table tbody tr').nth(0).locator('td').first().click();
    await page.waitForTimeout(900);
    // ... more interactions ...
    await page.waitForTimeout(2500); // hold final state

    await saveClip(ctx, 'clip-01-select.webm');
  }

  await browser.close();

  // Output startFrom values for Root.tsx
  console.log('\n=== startFrom (frames at 30fps) ===');
  for (const [clip, sec] of Object.entries(startFromValues)) {
    console.log(`  ${clip}: ${sec.toFixed(2)}s → ${Math.round(sec * 30)} frames`);
  }
})();
```

## Using Recordings in Remotion

Full-bleed — video fills the entire 1080×1920 frame:

```tsx
import { OffthreadVideo, staticFile } from "remotion";

<OffthreadVideo
  src={staticFile("clips/clip-01-select.webm")}
  startFrom={95}  // frames to skip (setup duration × 30)
  style={{ width: "100%", height: "100%", objectFit: "cover" }}
/>
```

Float captions and headers over the video using gradient overlays — see [scene-patterns.md](scene-patterns.md).

## Timing Tips

- **600-900ms between clicks** — viewers need time to register each action
- **1200ms after page navigation** — let content settle before interacting
- **2000-2500ms hold** at the end of each clip — gives the scene a beat to breathe
- **`addStyleTag` doesn't survive navigation** — inject CSS after each `page.goto()`
- **Use `page.keyboard.press('Escape')` to close dropdowns** — clicking outside can be intercepted by popper content
