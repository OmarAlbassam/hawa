---
name: release-video
description: Create short Remotion videos to share new features and updates with an internal team. Use when (1) announcing a new feature or UI change, (2) creating a release update video, (3) user says "make a video", "evangelize", "release video", "announce feature", (4) sharing product updates via video. Builds a Remotion project using project brand colors, real app screen recordings via Playwright, ElevenLabs voiceover, and always-on captions.
---

# Release Video

You are a motion designer who works in React. Remotion is your editing timeline, React is your canvas. You design short films — not "assemble components." Every video you make should feel crafted, not generated.

**What you're making:** Short vertical videos sharing new features with an internal team.

> **Before you begin:** read [`project.config.md`](project.config.md) for product name, audience, tone, language, brand colors, font, logo, app URL, and voice ID. If any `{{TOKEN}}` is still present, ask the user to fill it in.

**Audience:** see `project.config.md` → `audience`. Internal team, not customers.
**Tone:** see `project.config.md` → `tone`. Direct, informational, conversational. No marketing.
**Language:** see `project.config.md` → `language`.
**Format:** **See `project.config.md → Video format`** — Hawa overrides the skill default. Hawa videos are **landscape 1920×1080 (16:9)** captured at native desktop width, because Hawa has no mobile layout.
**Distribution:** assume mute viewing — captions always visible.

## Workflow

### 1. Understand the Feature

Before writing a single line, understand what shipped and why it matters to the team.

Ask:
- **What changed?** — Clear description
- **Key talking points?** — 2-4 things the team needs to know
- **Which interactions to capture?** — Specific flows, clicks, states

Then **design the video for this specific feature.** A bulk update flow calls for a different rhythm than a new dashboard. A filter improvement needs different energy than a channel integration. Let the content decide the shape.

### 2. Capture Screen Recordings

Use Playwright to record real interactions from the app. See [references/captures.md](references/captures.md) for the full guide.

**The rules:**
- **Recordings only. Never screenshots.** The motion is the content — hover effects, selections, scrolling, dialogs opening. These provide natural visual focus without artificial overlays.
- **One clip per scene.** Separate browser contexts. Each clip has fast setup (login → navigate → prepare state) then slow showcase (the part viewers see).
- **540×960 viewport, DPR 1.** Recording size matches viewport. Remotion scales 2× to fill 1080×1920.
- **Hide chrome that doesn't belong** (sidebars, dev banners, etc.) via CSS injection so content fills the full viewport width.
- **Log timestamps** at the setup→showcase boundary. Convert to frames (`seconds × 30`) for Remotion's `startFrom`.

Quick pattern: write a `_capture.cjs` script to project root, run with `node _capture.cjs`, clips save to the Remotion project's `public/clips/`.

### 3. Write the Script

**Structure: Hook → Show → Wrap**

Every video needs a hook that sets context, recording scenes that show the feature, and a closing beat. But the specific structure depends on the feature — don't force a template.

Write in benefit language addressed to the team:
- "You can now..." not "New feature!"
- "Instead of doing X manually..." not "Tired of...?"
- No brand taglines, no marketing hooks

Each recording scene gets a **caption** (short, floats on screen over the video) and a **voiceover line** (spoken, conversational). Present script to user for approval before generating voiceover.

Focus on Experiences, Not Features:

A feature is "we sync inventory across channels." An experience is "you never have to worry about overselling because we handle it — and if something goes wrong, we catch it and fix it before your customer notices."

### 4. Generate Voiceover

ElevenLabs API. Key in `.env` as `ELEVENLABS_API_KEY`. See [references/elevenlabs.md](references/elevenlabs.md).

- Generate per-scene clips (one `.mp3` per scene)
- Voice: see `project.config.md` → `elevenlabs.voice_id`
- Model: `eleven_v3`, stability: 0.5 (Natural)
- Use `getAudioDurationInSeconds` from `@remotion/media-utils` for frame calculation

### 5. Build the Remotion Project

```bash
npx create-video@latest --blank
npm i @remotion/fonts @remotion/media-utils
```

**Project structure:**
```
release-video-{feature}/
├── public/
│   ├── {{FONT_FILES}}        # from project.config.md
│   ├── {{LOGO_FILE}}         # from project.config.md
│   ├── voiceover-*.mp3
│   └── clips/                # Screen recordings (.webm)
├── src/
│   ├── index.ts
│   ├── Root.tsx              # Composition + calculateMetadata
│   ├── Video.tsx             # Series orchestrator
│   ├── constants.ts          # COLORS, FONT_FAMILY, FPS, WIDTH, HEIGHT
│   └── components/
│       ├── TaglineScene.tsx  # Standard ending (copy from assets/)
│       └── ...               # Design everything else per-video
└── package.json
```

Copy fonts and logo from your project's brand assets into `public/`. Copy `assets/scripts/generate-voiceover.mjs` into `scripts/`.

### 6. Implement

**Full-bleed video.** `OffthreadVideo` fills the entire 1080×1920 frame with `objectFit: "cover"`. Float step headers and captions over the video using dark gradient overlays. Natural UI interactions provide visual focus — no highlight boxes, no artificial overlays.

**Scene duration = voiceover duration.** Use `calculateMetadata` in Root.tsx to async-load audio durations and set each scene's `durationInFrames`. Add padding frames (10-15) so scenes don't feel cut short.

**Always end with TaglineScene** (120 frames, no voiceover). Pass `productName`, `subtitle`, and brand colors as props. Requires the project logo in `public/`. Copy from `assets/components/`.

**Utility components** you can copy from `assets/components/` if useful:
- **KineticText** — animated text effects (typewriter, bounce, scale, wave, fade)
- **AnimatedGradient** — animated gradient backgrounds

Everything else — hook scenes, step overlays, closing cards, transitions, celebration moments — **design and build for this video.** Remotion gives you `useCurrentFrame()` as a universal timeline — anything you can express in React, you can animate frame-by-frame.

See [references/scene-patterns.md](references/scene-patterns.md) for patterns and creative direction.

### 7. Preview & Render

```bash
npx remotion studio
npx remotion render Video out/release-video.mp4
```

Verify by extracting key frames: `npx remotion still Video --frame=N out/frame.png`

---

## Brand Constants (read from project.config.md)

```ts
export const COLORS = {
  primary:   "{{COLOR_PRIMARY}}",
  secondary: "{{COLOR_SECONDARY}}",
  support:   "{{COLOR_SUPPORT}}",
  text:      "{{COLOR_TEXT}}",      // primary text on dark bg
  accent:    "{{COLOR_ACCENT}}",     // emphasis, sparingly
};
export const FONT_FAMILY = "{{FONT_FAMILY}}";
export const FPS = 30;
export const WIDTH = 1080;
export const HEIGHT = 1920;
```

Load the project font (weights as needed) via `@remotion/fonts` + `staticFile()`.

---

## Resources

- [project.config.md](project.config.md) — fill in per project (brand, voice, app URL)
- [references/scene-patterns.md](references/scene-patterns.md) — Recording scene patterns, Remotion primitives, creative direction
- [references/captures.md](references/captures.md) — Playwright screen recording guide
- [references/elevenlabs.md](references/elevenlabs.md) — ElevenLabs TTS API for voiceover
- [references/templates.md](references/templates.md) — Hook → Record → Close → Tagline rhythm
- [assets/components/](assets/components/) — TaglineScene, KineticText, AnimatedGradient
- [assets/public/](assets/public/) — drop your project's font + logo here
- [assets/scripts/generate-voiceover.mjs](assets/scripts/generate-voiceover.mjs) — ElevenLabs voiceover generation template
