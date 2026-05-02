# Project Config — Release Video (Hawa)

Per-project values consumed by the release-video skill.

## Video format

**Override the skill's default vertical (9:16) format.** Hawa is desktop-only — no mobile layout — so videos are landscape (16:9) and capture the dashboard at native desktop width.

- **output dimensions:** `1920 × 1080` (landscape 16:9)
- **capture viewport:** `1920 × 1080`, DPR 1 — recording matches output exactly, no scaling
- **objectFit:** `"contain"` (not `"cover"`) — Remotion shouldn't crop the recording
- **caption layout:** position captions at the bottom-center over the video (landscape gives more horizontal room than vertical) instead of full-width bars

Update `src/constants.ts` accordingly:
```ts
export const WIDTH = 1920;
export const HEIGHT = 1080;
```

And the Playwright capture script:
```js
const context = await browser.newContext({
  viewport: { width: 1920, height: 1080 },
  deviceScaleFactor: 1,
  recordVideo: { dir: clipsDir, size: { width: 1920, height: 1080 } },
});
```

## Output location

**Scaffold every video project under `release-videos/{feature-slug}/` at the repo root** — never directly at the repo root. That entire directory is gitignored, so the Remotion source, `node_modules`, recordings (`public/clips/`), voiceovers (`public/voiceover-*.mp3`), and final renders (`out/*.mp4`) all stay out of git automatically.

```
hawa/
└── release-videos/                    # gitignored
    ├── analysis-flow/                  # scaffolded by skill
    │   ├── src/, public/, out/, node_modules/, ...
    └── dashboard-redesign/
        └── ...
```

When running the skill, override the default `npx create-video` location:
```bash
mkdir -p release-videos
cd release-videos
npx create-video@latest analysis-flow --blank
```

## Pacing & cursor (lessons from prior runs)

> **Read before scaffolding any new video.** Previous renders felt rushed and had no visible mouse cursor. The fixes below are mandatory for Hawa videos.

### Pacing

- **Padding per scene:** add **45 frames** (1.5s at 30fps) of padding *after* each voiceover ends, not the skill's default 10–15. Capstone audience needs time to read captions and absorb UI changes.
- **Step headers:** hold each step header on screen for **at least 60 frames (2s)** before transitioning out.
- **Recording playback rate:** if a Playwright clip's "showcase" portion is shorter than the voiceover for that scene, slow the clip with Remotion's `playbackRate={0.75}` (or split + freeze on the final frame). Do **not** speed-cut to fit voiceover.
- **Tagline scene:** bump from 120 frames to **180 frames** (6s) so the tagline + logo register before the video ends.
- **Voiceover input:** when generating with ElevenLabs, write script lines as full sentences with natural punctuation (commas, periods). Short fragments produce rushed deliveries.

### Mouse cursor

Playwright's `recordVideo` does **not** capture the OS mouse cursor. Inject a synthetic cursor into the page so it shows up in the recording.

Add this snippet at the start of every capture script, right after `page.goto(...)` and `page.waitForSelector(readySelector)`:

```js
await page.addStyleTag({
  content: `
    #__pw_cursor {
      position: fixed;
      top: 0; left: 0;
      width: 24px; height: 24px;
      background: url("data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><path d='M3 2 L3 18 L7 14 L10 21 L13 20 L10 13 L17 13 Z' fill='black' stroke='white' stroke-width='1.5'/></svg>") no-repeat;
      pointer-events: none;
      z-index: 2147483647;
      transform: translate(-2px, -2px);
      transition: transform 80ms linear;
    }
  `,
});
await page.evaluate(() => {
  const cursor = document.createElement('div');
  cursor.id = '__pw_cursor';
  document.body.appendChild(cursor);
  document.addEventListener('mousemove', (e) => {
    cursor.style.transform = `translate(${e.clientX - 2}px, ${e.clientY - 2}px)`;
  }, true);
  document.addEventListener('mousedown', () => {
    cursor.style.filter = 'brightness(0.6)';
  }, true);
  document.addEventListener('mouseup', () => {
    cursor.style.filter = 'none';
  }, true);
});
```

Then, instead of jumping with `page.click(selector)`, animate the cursor first so viewers can follow:

```js
async function move(page, selector, steps = 25) {
  const box = await page.locator(selector).boundingBox();
  await page.mouse.move(box.x + box.width / 2, box.y + box.height / 2, { steps });
}
async function clickSlow(page, selector) {
  await move(page, selector);
  await page.waitForTimeout(300);  // hover beat
  await page.click(selector);
  await page.waitForTimeout(500);  // post-click beat
}
```

Use `clickSlow` (or equivalent) for every meaningful click in the capture script. Raw `page.click` produces invisible interactions.

## Project

- **product_name:** Hawa
- **tagline_line1:** Hawa
- **tagline_line2:** Sentiment intelligence for marketing teams.
- **audience:** Mixed — IS498 capstone advisors and Hawa's intended marketing-team users. Frame features around brand-health workflows but explain enough context that capstone reviewers (who don't use the product daily) can follow.
- **tone:** Polished, demo-like. Slightly more produced than a standup walkthrough — suits capstone presentations. Still informational, not marketing.
- **language:** English
- **distribution:** Capstone presentation + Slack/Discord/WhatsApp. Assume mute viewing on mobile — captions always on.

## Brand colors

Hawa's palette (see `apps/frontend/DESIGN_SYSTEM.md`). Drop into `src/constants.ts → COLORS`. Videos use the same look as the app — light background with dark text and pink accents.

> **Adapting scenes:** Many of the skill's example components assume dark backgrounds with white overlay text. When scaffolding, flip overlay gradients (use light-on-dark fades for caption legibility over recordings) and switch text-on-background to dark text on `#F5F5F5`.

- **primary:**   `#F5F5F5`  # page background (near-white)
- **secondary:** `#9E9E9E`  # muted text, descriptions
- **support:**   `#757575`  # secondary text
- **text:**      `#1A1A1A`  # primary text on light bg
- **accent:**    `#E91E63`  # Hawa pink — emphasis, sparingly

Optional extras pulled from the design system if a video needs them:
- `#16A34A` success / very-positive sentiment
- `#DC2626` error / very-negative sentiment
- `#4A1D6A` brand-purple (logo Arabic text — use only if doing an Arabic video)

## Brand font

Frontend uses Google Fonts (`Poppins` for headings, `Open Sans` for body). Remotion needs local `.ttf` files — download once from Google Fonts and place in `assets/public/`.

- **font_family (headings):** `Poppins`
- **font_family (body):** `Open Sans`
- **font_files in `assets/public/`** (already downloaded):
  - `Poppins-Medium.ttf` (500)
  - `Poppins-SemiBold.ttf` (600)
  - `Poppins-Bold.ttf` (700)
  - `OpenSans-Variable.ttf` — variable font, supply `weight` when registering with `@remotion/fonts` (use 400/500/600/700 as needed)
- **logo:** `hawa-logo.png` — already copied into `assets/public/`. Cropped variant available at `apps/frontend/src/assets/hawa-logo-cropped.png` if tighter framing is needed.

## App (for Playwright capture)

- **base_url:** `http://localhost:5173`
- **login flow:** standard form at `/login`. Playwright steps:
  1. `goto('http://localhost:5173/login')`
  2. `fill('#email', process.env.HAWA_DEV_EMAIL)`
  3. `fill('#password', process.env.HAWA_DEV_PASSWORD)`
  4. `click('.login-submit')`
  5. Wait for navigation to `/dashboard` (or `/admin` for admin users)
- **dev credentials (env vars only — never commit):**
  - `HAWA_DEV_EMAIL` — set in `.env`
  - `HAWA_DEV_PASSWORD` — set in `.env`
- **ready_selector:** `.dashboard-stats` — the stat-card row only mounts after `getDashboard()` resolves, so it's a reliable "data loaded" signal. For other pages: `.brand-list` (brands), `.report-list` (reports), `.posts-list` (posts list).
- **hide_chrome_css:** Hawa uses a custom `MarketingLayout` / `AdminLayout` with a sidebar + top bar. Hide them during capture so the content fills the viewport:
  ```css
  .marketing-sidebar, .admin-sidebar,
  .marketing-topbar, .admin-topbar { display: none !important; }
  .marketing-layout-content, .admin-layout-content {
    margin-left: 0 !important;
    padding-top: 0 !important;
  }
  ```
  Verify these selectors against `apps/frontend/src/components/MarketingLayout/MarketingLayout.css` before first capture — adjust if class names differ.

## ElevenLabs (voiceover)

- **api_key:** stored in `<repo-root>/.env.local` as `ELEVENLABS_API_KEY` (gitignored). When scaffolding a Remotion project, copy this key into the project's own `.env` so `generate-voiceover.mjs` can read it.
- **voice_id:** `auq43ws1oslv0tO4BDa7`
- **voice_name:** Adam (British)
- **model_id:** `eleven_v3` (default)
