# Hawa Design System

> Single source of truth for visual and interaction decisions across the Hawa frontend. Identity: editorial, minimal, warm-neutral. Built on shadcn/ui + Tailwind v4 + Radix.

---

## 1. Direction

**Mood:** Editorial · minimal · trustworthy · analytical
**Reference:** Linear / Vercel / Stripe Press — soft warm backdrop, thin borders, generous whitespace, monospace labels.

The UI prioritizes **clarity of data** and **calm composition**. Decoration is minimal: whitespace, typography, and a single accent do the heavy lifting. Color is reserved almost entirely for sentiment signal and the primary action.

---

## 2. Color System

All color is exposed via CSS custom properties in [src/index.css](src/index.css) and consumed through Tailwind tokens (`bg-card`, `text-foreground`, etc.). **Never hardcode hex values in components.**

### 2.1 Light mode (primary)

| Token | Hex | Usage |
|---|---|---|
| `--bg` / `bg-background` | `#F8F8F7` | Page background (warm off-white) |
| `--bg-2` / `bg-muted` | `#F2F1ED` | Sidebar, subtle fills, hover states |
| `--surface` / `bg-card` | `#FFFFFF` | Cards, modals, inputs |
| `--border-soft` / `border-border` | `#E5E4E0` | Card borders, dividers |
| `--border-strong` / `border-input` | `#D7D6D1` | Input borders, secondary buttons |
| `--text` / `text-foreground` | `#18181B` | Headings, body |
| `--text-2` / `text-muted-foreground` | `#71717A` | Captions, labels, helper text |
| `--text-3` / `text-text-3` | `#A1A1AA` | Placeholders, eyebrow labels |
| `--accent` / `bg-primary` | `#2563EB` | Primary action, focus ring, active nav |
| `--accent-hover` | `#1D4ED8` | Primary hover |
| `--accent-light` | `#EFF6FF` | Accent badge background |

### 2.2 Dark mode

Toggle via `class="dark"` on `<html>`. Hues match light; lightness inverted; warm-neutral preserved.

| Token | Hex | Usage |
|---|---|---|
| `--bg` | `#0B0B0C` | Page background |
| `--bg-2` | `#18181B` | Sidebar, muted fills |
| `--surface` | `#161618` | Cards |
| `--border-soft` | `#27272A` | Borders, dividers |
| `--border-strong` | `#3F3F46` | Inputs, secondary buttons |
| `--text` | `#FAFAFA` | Foreground |
| `--text-2` | `#A1A1AA` | Muted |
| `--text-3` | `#71717A` | Placeholder |
| `--accent` | `#60A5FA` | Lifted blue (AA on dark bg) |

### 2.3 Sentiment palette

| Sentiment | Light hex | Dark hex | Tokens |
|---|---|---|---|
| Positive | `#16A34A` | `#22C55E` | `bg-pos`, `bg-pos-bg`, `text-pos-text` |
| Neutral | `#D97706` | `#F59E0B` | `bg-neu`, `bg-neu-bg`, `text-neu-text` |
| Negative | `#DC2626` | `#F87171` | `bg-neg`, `bg-neg-bg`, `text-neg-text` |

Sentiment is **always** rendered with these tokens — never raw red/green/amber. Always pair the bar/text color with the matching `*-bg` for the chip background.

---

## 3. Typography

Three families, loaded from Google Fonts in [index.html](index.html):

| Family | Use | Token |
|---|---|---|
| **DM Sans** (600) | Display, headings, numbers, button labels on accent | `font-display` |
| **Inter** (400/500/600) | All UI text, body, labels | `font-sans` (default) |
| **JetBrains Mono** (400/500) | Eyebrow labels, specs, code, status text | `font-mono` |

### Scale

| Class / role | Size | Line-height | Letter-spacing | Weight |
|---|---|---|---|---|
| Display L | 56px | 1.05 | -0.035em | 600 display |
| Display M / page title | 32px | 1.15 | -0.025em | 600 display |
| Display S / card title | 24px | 1.2 | -0.02em | 600 display |
| Section title | 22px | 1.2 | -0.02em | 600 display |
| Body L | 20px | 1.5 | — | 400 sans |
| Body | 16px | 1.5 | — | 400 sans |
| **UI body (default)** | 14px | 1.5 | — | 400 sans |
| Control / label | 13px | 1 | -0.005em | 500 sans |
| Eyebrow / mono | 11px | 1 | 0.12–0.14em uppercase | 400 mono |

Numbers (stats) use `font-variant-numeric: tabular-nums` and DM Sans 600.

---

## 4. Spacing & Layout

Tailwind defaults (4px base). Card paddings: 24px (`p-6`) for content blocks, 40px (`p-10`) for hero cards. Stat cards: 20px (`p-5`).

Section vertical rhythm: 56–112px between major sections on marketing surfaces; 24–32px inside app surfaces.

---

## 5. Radius & Depth

| Token | Value | Use |
|---|---|---|
| `rounded-sm` | 4px | Badges, small chips |
| `rounded-md` | 6px | Buttons, inputs |
| `rounded-md` (cards) | 8px | Cards, panels |
| `rounded-lg` | 12px | App shell |

Borders carry the weight — **shadows are minimal**. Use `shadow-sm` for elevated cards/popovers only. Dialogs use `shadow-lg`. No drop-shadows on flat surfaces.

---

## 6. Components

All primitives live in [src/components/ui/](src/components/ui/) and are shadcn-style (copy-paste, modifiable). Compose, don't re-roll.

### Buttons ([button.tsx](src/components/ui/button.tsx))
- `default` — accent fill, white text (primary action, one per view)
- `secondary` — surface fill, strong border (neutral action)
- `ghost` — transparent, hover muted (toolbar, nav)
- `outline` — strong border, transparent (alternative neutral)
- `destructive` — surface fill, red text + red hover bg
- Sizes: `default` (36px), `sm` (32px), `lg` (40px), `icon` (36×36)

### Inputs ([input.tsx](src/components/ui/input.tsx), [textarea.tsx](src/components/ui/textarea.tsx))
36px height, `border-input`, focus ring is 3px accent at 15% opacity + 1px solid accent border.

### Badges ([badge.tsx](src/components/ui/badge.tsx))
22px height, optional dot. Variants: `default` (muted), `pos`, `neu`, `neg`, `accent`, `outline`.

### Cards ([card.tsx](src/components/ui/card.tsx))
1px `border-border` on `bg-card`, `rounded-md` (8px). Header / Title / Description / Content / Footer slots.

### Overlays
Dialog, DropdownMenu, Tooltip — all Radix-backed, animated via `tw-animate-css`. Tooltip uses inverted (`bg-foreground text-background`) at 11px mono.

### Theming
- [ThemeProvider](src/components/theme-provider.tsx) wraps the app in [main.tsx](src/main.tsx)
- [ThemeToggle](src/components/theme-toggle.tsx) — dropdown with Light/Dark/System; persisted to `localStorage` (`hawa-theme`)
- Pre-paint script in [index.html](index.html) applies the saved theme before first render (prevents flash)

---

## 7. Iconography

- **Static:** [lucide-react](https://lucide.dev/) — default for all nav/utility icons. Size 16px (`size-4`) inside buttons, 14px in nav items.
- **Animated:** [lucide-animated.com](https://lucide-animated.com/) — copy individual components into `src/components/ui/icons/` as needed. Reserve for key moments (theme toggle, status indicators, success states), not bulk UI.

---

## 8. Voice

**Do**: short, specific, factual. *"3 brands analyzed last week."*
**Don't**: marketing-speak, exclamation points, hedged language. ~~*"Unlock powerful insights about your brand!"*~~

Sentence case in all UI labels. Title case only for proper nouns and page titles.

---

## 9. What changed from v1 (Pink/Poppins era)

- Primary accent: `#E91E63` (pink) → `#2563EB` (blue)
- Backdrop: `#F5F5F5` (cool gray) → `#F8F8F7` (warm off-white)
- Display font: Poppins → DM Sans
- Body font: Open Sans → Inter
- Added: JetBrains Mono for eyebrows/specs
- Added: full dark mode
- Added: shadcn/ui primitives — replaces hand-rolled components incrementally
