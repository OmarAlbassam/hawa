# Scene Patterns

## Recording Scenes

The building blocks for screen recording videos. Video fills the entire frame — overlays float on top.

**Full-bleed video scene** — the workhorse:

```tsx
<AbsoluteFill style={{ backgroundColor: COLORS.primary }}>
  {/* Video fills entire frame */}
  <div style={{ position: "absolute", inset: 0, overflow: "hidden" }}>
    <OffthreadVideo
      src={staticFile(src)}
      startFrom={videoStartFrom}
      style={{ width: "100%", height: "100%", objectFit: "cover" }}
    />
  </div>

  {/* Top gradient — step header floats here */}
  <div style={{
    position: "absolute", top: 0, left: 0, right: 0, height: 220,
    background: `linear-gradient(to bottom, ${COLORS.primary} 0%, ${COLORS.primary}ee 35%, transparent 100%)`,
    display: "flex", alignItems: "center", justifyContent: "center", gap: 20,
  }}>
    {/* Badge + title */}
  </div>

  {/* Bottom gradient — caption floats here */}
  <div style={{
    position: "absolute", bottom: 0, left: 0, right: 0, height: 180,
    background: `linear-gradient(to top, ${COLORS.primary} 0%, ${COLORS.primary}ee 40%, transparent 100%)`,
    display: "flex", alignItems: "flex-end", justifyContent: "center", paddingBottom: 40,
  }}>
    <p style={{ fontFamily: FONT_FAMILY, fontSize: 34, fontWeight: 500, color: COLORS.text, textAlign: "center" }}>
      {caption}
    </p>
  </div>
</AbsoluteFill>
```

**Done/completion scene** — dimmed video with centered overlay:

```tsx
<AbsoluteFill style={{ backgroundColor: COLORS.primary }}>
  {/* Full-bleed video — dimmed + desaturated */}
  <div style={{ position: "absolute", inset: 0, opacity: 0.5, filter: "brightness(0.6) saturate(0.5)" }}>
    <OffthreadVideo src={staticFile(src)} startFrom={videoStartFrom}
      style={{ width: "100%", height: "100%", objectFit: "cover" }} />
  </div>

  {/* Radial gradient overlay */}
  <div style={{
    position: "absolute", inset: 0,
    background: `radial-gradient(ellipse at center, ${COLORS.primary}88, ${COLORS.primary})`,
  }} />

  {/* Centered text */}
  <div style={{ position: "absolute", inset: 0, display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center" }}>
    <h1 style={{ fontFamily: FONT_FAMILY, fontSize: 120, fontWeight: 800, color: COLORS.text }}>Done.</h1>
    <p style={{ fontFamily: FONT_FAMILY, fontSize: 34, color: COLORS.secondary }}>{closingLine}</p>
  </div>
</AbsoluteFill>
```

**Key principle:** Natural UI interactions (hover effects, selection highlights, scrolling) provide visual focus. No artificial highlight boxes or overlays pointing at things.

---

## Fixed Components

**TaglineScene** — Copy from `assets/components/`. Fixed 120-frame ending: project logo + tagline. No voiceover. Always the last scene. Pass `productName`, `subtitle`, `primaryColor`, `accentColor`, `fontFamily`, `logoFilename` as props (see `project.config.md`).

---

## Your Canvas

Remotion gives you React as a video engine. `useCurrentFrame()` is your universal timeline — every frame is a render, and anything you can build in React, you can animate.

**What you have access to:**

- **Any CSS** — transforms, clip-path, filters, blend modes, gradients, shadows, backdrop-filter. Animate any property frame-by-frame.
- **SVG** — draw paths, animate stroke-dashoffset for reveals, build custom shapes.
- **`<AbsoluteFill>`** — layer anything. Stack backgrounds, videos, overlays, text.
- **`<Sequence>`** and **`<Series>`** — choreograph timing. Nest sequences for complex scenes.
- **`spring()` + `interpolate()`** — physics-based easing. Adjust damping and stiffness to match the energy of the moment.
- **`<Audio>`** + `getAudioDurationInSeconds()` — sync scenes to voiceover duration.
- **`<OffthreadVideo>`** — embed screen recordings. `startFrom` to skip setup, `objectFit: "cover"` for full-bleed.
- **Math** — `Math.sin` for breathing, `%` for loops. Frame count is just a number.

Don't reach for a pre-built component when 10 lines of `interpolate` + JSX does exactly what the scene needs.

---

## Creative Brief

**Fixed constraints:**
- Brand colors and font from `project.config.md`, vertical 1080x1920
- Captions always visible (float over video with gradient overlays)
- End with closing scene → TaglineScene
- Language from `project.config.md` throughout

**Your job:**
- Design every scene from scratch — hook, steps, completion, closing, transitions
- Let the content tell you what it needs. A bulk update flow feels different from a new dashboard reveal. Design accordingly.
- Vary rhythm — alternate how things enter, mix transition types, change pacing
- If something deserves attention (a number, a before/after), invent the right way to show it for *this* video
- One standout moment per video hits harder than five

**The test:** If the viewer understood the feature and didn't notice the animation, you nailed it.
