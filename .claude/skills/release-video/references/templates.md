# Video Rhythm

Not rigid templates — rhythms. The feature decides the shape.

## The Universal Structure

**Hook → Record → Close → Tagline**

Every video follows this arc, but how you execute each beat depends entirely on what you're showing.

### Hook
Set context. Create tension or curiosity. What problem exists? What's about to change?

The hook is your creative moment — let the feature decide the form. Text-only works for punchy problem statements (*"50 products. Same discount. ... 3 steps."*), but consider richer approaches when they serve the content: animated SVG icons, number counters, before/after contrasts, a quick recording clip teasing the result, kinetic typography with staggered reveals, or even a brief screen recording of the pain point. Match the energy to the feature — a workflow improvement earns directness, a major launch earns drama.

### Recording Scenes
Show the feature through real interactions. One clip per scene. Each scene gets:
- A **step indicator** (badge + title, floating at top)
- A **caption** (floating at bottom)
- **Voiceover** driving the timing

Natural UI focus — hover effects, selections, dialogs opening. No highlight boxes.

### Closing
Wrap it up. Where to find it, what to do next.

*"Available now — Products page"*

### Tagline
Fixed ending. Project logo + tagline lines from `project.config.md`. 120 frames. Always last.

---

## Adapting the Rhythm

| Feature type | Hook energy | Recording pace | Closing tone |
|---|---|---|---|
| Workflow change (bulk update, new filter) | Direct, problem-solution | Step by step, deliberate | "Available now" |
| New channel/integration | Celebratory, announcement | Capability showcase | "Ready to connect" |
| Major system launch | Weight, gravitas | Comprehensive tour | Impact statement |
| Small improvement | Quick, punchy | Single demo | Brief |

**The key:** let the feature tell you what it needs. A 3-step workflow calls for numbered steps. A new dashboard might want a scroll-through tour. A speed improvement might show before/after timing. Design for the content, not for a template.

---

## Script Format

Present to user for approval before generating voiceover:

```
Hook:     [text on screen]
          voice: "..."

Scene 1:  [what the recording shows]
          caption: "..."
          voice: "..."

Scene 2:  [what the recording shows]
          caption: "..."
          voice: "..."

Closing:  caption: "..."
          voice: "..."
```

Write in benefit language: "You can now..." not "New feature!" No marketing hooks.
