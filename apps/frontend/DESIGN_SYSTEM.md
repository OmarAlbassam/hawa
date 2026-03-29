# Hawa Design System

> Design system for the Hawa sentiment analysis dashboard. This document serves as the single source of truth for all visual and interaction decisions across the frontend.

---

## 1. Style Direction

**Style:** Data-Dense Dashboard
**Mood:** Professional, clean, data-focused, trustworthy
**Best For:** Business intelligence dashboards, analytics, data visualization

The UI prioritizes **data readability** and **scanability**. Every element serves the goal of helping marketing teams quickly understand brand sentiment. Decoration is minimal — whitespace, typography, and color do the heavy lifting.

---

## 2. Color System

### 2.1 Light Mode (Primary)

| Token | Hex | Usage |
|-------|-----|-------|
| `--color-primary` | `#E91E63` | Primary actions, active sidebar items, CTA buttons, score numbers, badges |
| `--color-primary-hover` | `#C2185B` | Primary hover state |
| `--color-primary-light` | `#FFF0F3` | Selected rows, highlighted cards, data source selection bg |
| `--color-secondary` | `#F06292` | Secondary buttons, supporting elements |
| `--color-accent` | `#E91E63` | Same as primary — Hawa uses a single-accent system |
| `--color-accent-hover` | `#C2185B` | Accent hover state |
| `--color-brand-purple` | `#4A1D6A` | Logo Arabic text only — not used in UI components |
| `--color-background` | `#F5F5F5` | Page background |
| `--color-surface` | `#FFFFFF` | Cards, sidebar, modals, input fields |
| `--color-text-primary` | `#1A1A1A` | Headings, body text |
| `--color-text-secondary` | `#757575` | Captions, labels, helper text, subtitles |
| `--color-text-muted` | `#9E9E9E` | Placeholders, disabled text |
| `--color-border` | `#E0E0E0` | Card borders, dividers, input borders |
| `--color-muted-bg` | `#F5F5F5` | Muted backgrounds, table stripes, empty states |
| `--color-ring` | `#E91E63` | Focus rings |

### 2.2 Semantic Colors

| Token | Hex | Usage |
|-------|-----|-------|
| `--color-success` | `#16A34A` | Positive sentiment, success states, confirmations |
| `--color-success-bg` | `#F0FDF4` | Success background |
| `--color-warning` | `#D97706` | Neutral/mixed sentiment, caution states |
| `--color-warning-bg` | `#FFFBEB` | Warning background |
| `--color-error` | `#DC2626` | Negative sentiment, errors, destructive actions |
| `--color-error-bg` | `#FEF2F2` | Error background |
| `--color-info` | `#0284C7` | Informational messages, tips |
| `--color-info-bg` | `#F0F9FF` | Info background |

### 2.3 Sentiment-Specific Palette

Used exclusively in charts and sentiment indicators:

| Score Range | Label | Color | Hex |
|-------------|-------|-------|-----|
| 0.0 - 1.0 | Very Negative | Red | `#EF4444` |
| 1.0 - 2.0 | Negative | Orange | `#F97316` |
| 2.0 - 3.0 | Neutral | Gray | `#94A3B8` |
| 3.0 - 4.0 | Positive | Light Green | `#22C55E` |
| 4.0 - 5.0 | Very Positive | Green | `#16A34A` |

### 2.4 Emotion Colors

| Emotion | Hex | Notes |
|---------|-----|-------|
| Joy | `#FBBF24` | Warm yellow |
| Anger | `#EF4444` | Red |
| Sadness | `#3B82F6` | Blue |
| Fear | `#8B5CF6` | Purple |
| Surprise | `#F97316` | Orange |
| Disgust | `#84CC16` | Lime |
| Trust | `#06B6D4` | Cyan |
| Anticipation | `#EC4899` | Pink |

### 2.5 Dark Mode (Future)

Dark mode will use desaturated, lighter tonal variants of the same palette. Design light mode first; dark mode tokens will be added as a second pass.

---

## 3. Typography

### 3.1 Font Selection

**Option A — Modern Professional (Recommended)**

| Role | Font | Why |
|------|------|-----|
| Headings | **Poppins** | Geometric, modern, professional feel. Strong visual hierarchy. |
| Body | **Open Sans** | Humanist sans-serif, excellent readability at small sizes. |

```css
@import url('https://fonts.googleapis.com/css2?family=Open+Sans:wght@400;500;600;700&family=Poppins:wght@500;600;700&display=swap');
```

**Option B — Friendly SaaS**

| Role | Font | Why |
|------|------|-----|
| All | **Plus Jakarta Sans** | Single versatile font, modern alternative to Inter. Clean and approachable. |

```css
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700&display=swap');
```

**Option C — Data-Dense Dashboard**

| Role | Font | Why |
|------|------|-----|
| Headings | **Fira Code** | Monospace feel for a technical, analytics-first product. |
| Body | **Fira Sans** | Pairs naturally. Great for data tables and labels. |

```css
@import url('https://fonts.googleapis.com/css2?family=Fira+Code:wght@400;500;600;700&family=Fira+Sans:wght@400;500;600;700&display=swap');
```

### 3.2 Type Scale

Based on a 1.25 ratio (Major Third), with `16px` base:

| Token | Size | Weight | Line Height | Usage |
|-------|------|--------|-------------|-------|
| `--text-xs` | 12px | 400 | 1.5 | Badges, fine print |
| `--text-sm` | 14px | 400 | 1.5 | Table cells, helper text, labels |
| `--text-base` | 16px | 400 | 1.6 | Body text, form inputs |
| `--text-lg` | 18px | 500 | 1.5 | Card titles, section labels |
| `--text-xl` | 20px | 600 | 1.4 | Section headings |
| `--text-2xl` | 24px | 600 | 1.3 | Page sub-headings |
| `--text-3xl` | 32px | 700 | 1.2 | Page headings |
| `--text-4xl` | 40px | 700 | 1.1 | KPI numbers, hero stats |

### 3.3 Font Weight Usage

| Weight | Value | Usage |
|--------|-------|-------|
| Regular | 400 | Body text, table cells |
| Medium | 500 | Labels, nav items, card titles |
| Semi-bold | 600 | Section headings, button text |
| Bold | 700 | Page headings, KPI numbers |

---

## 4. Spacing System

Based on a **4px / 8px** increment system:

| Token | Value | Usage |
|-------|-------|-------|
| `--space-1` | 4px | Tight internal padding (badge padding) |
| `--space-2` | 8px | Icon-to-text gap, compact padding |
| `--space-3` | 12px | Input padding, small gaps |
| `--space-4` | 16px | Standard card padding, list item gap |
| `--space-5` | 20px | Form field spacing |
| `--space-6` | 24px | Section gap within cards |
| `--space-8` | 32px | Between cards, section spacing |
| `--space-10` | 40px | Major section breaks |
| `--space-12` | 48px | Page-level section spacing |
| `--space-16` | 64px | Top-level layout spacing |

### 4.1 Layout Spacing

| Context | Padding/Gap |
|---------|-------------|
| Page padding (desktop) | 32px horizontal, 24px vertical |
| Page padding (mobile) | 16px horizontal, 16px vertical |
| Card internal padding | 24px (desktop), 16px (mobile) |
| Between cards | 24px |
| Sidebar width | 260px (expanded), 64px (collapsed) |
| Top bar height | 56px |

---

## 5. Layout System

### 5.1 Breakpoints

| Name | Width | Target |
|------|-------|--------|
| `sm` | 640px | Small phones |
| `md` | 768px | Tablets |
| `lg` | 1024px | Small laptops |
| `xl` | 1280px | Desktops |
| `2xl` | 1440px | Large screens |

### 5.2 Page Shell

```
+--------------------------------------------------+
| Top Bar (56px)                         [user] [?] |
+--------+-----------------------------------------+
|        |                                         |
| Side   |  Main Content Area                      |
| bar    |  max-width: 1280px                      |
| 260px  |  padding: 32px                          |
|        |                                         |
+--------+-----------------------------------------+
```

- **Sidebar**: Fixed left, 260px wide on desktop, collapsible to 64px (icon-only), hidden on mobile (hamburger toggle)
- **Top Bar**: Fixed top, 56px tall, contains logo, search, notifications, user menu
- **Content Area**: Scrollable, max-width 1280px, centered on large screens

### 5.3 Grid System

Use CSS Grid for dashboard layouts:

| Layout | Grid | Usage |
|--------|------|-------|
| KPI row | `repeat(auto-fit, minmax(240px, 1fr))` | KPI stat cards |
| Dashboard charts | `repeat(2, 1fr)` on desktop, `1fr` on mobile | Chart panels |
| Full-width | `1fr` | Tables, post explorer |
| Sidebar + content | `260px 1fr` | App shell |

### 5.4 Z-Index Scale

| Layer | Value | Usage |
|-------|-------|-------|
| Base | 0 | Default content |
| Dropdown | 10 | Dropdowns, tooltips |
| Sticky | 20 | Sticky headers, filters |
| Sidebar | 30 | Sidebar navigation |
| Modal backdrop | 40 | Modal overlay |
| Modal | 50 | Modal content |
| Toast | 100 | Toast notifications |

---

## 6. Component Patterns

### 6.1 Buttons

| Variant | Background | Text | Border | Usage |
|---------|-----------|------|--------|-------|
| Primary | `--color-primary` | white | none | Main actions (Start Analysis, Save) |
| Secondary | transparent | `--color-primary` | `--color-primary` | Secondary actions (Cancel, Back) |
| Accent | `--color-accent` | white | none | High-visibility CTA |
| Ghost | transparent | `--color-text-secondary` | none | Tertiary actions, icon buttons |
| Destructive | `--color-error` | white | none | Delete, Remove |

**States:**
- Default: as described
- Hover: darken 10% or shift shade
- Active/Pressed: darken 15%
- Disabled: 50% opacity, `cursor: not-allowed`
- Loading: spinner replaces text, button disabled

**Sizing:**

| Size | Height | Padding | Font |
|------|--------|---------|------|
| Small | 32px | 12px 16px | 14px |
| Medium | 40px | 12px 20px | 14px |
| Large | 48px | 16px 24px | 16px |

### 6.2 Cards

- Background: `--color-surface`
- Border: 1px solid `--color-border`
- Border radius: 8px
- Padding: 24px
- Shadow: `0 1px 3px rgba(0,0,0,0.08)` (subtle)
- Hover (if interactive): `0 4px 12px rgba(0,0,0,0.1)`

### 6.3 Form Inputs

- Height: 40px
- Padding: 8px 12px
- Border: 1px solid `--color-border`
- Border radius: 6px
- Font: `--text-base`
- Focus: 2px ring in `--color-ring`, border shifts to `--color-primary`
- Error: border shifts to `--color-error`, error message below in `--color-error`
- Disabled: background `--color-muted-bg`, 50% opacity

### 6.4 Tables

- Header: `--color-muted-bg` background, `--text-sm` semi-bold, uppercase
- Rows: alternating white / `--color-muted-bg` (optional)
- Row hover: `--color-primary-light` background
- Cell padding: 12px 16px
- Border: 1px solid `--color-border` between rows
- Sortable columns: icon indicator, `cursor: pointer`

### 6.5 Badges / Tags

- Padding: 2px 8px
- Border radius: 9999px (pill)
- Font: `--text-xs`, medium weight
- Variants: use semantic colors with their background (e.g., success badge = `--color-success` text on `--color-success-bg`)

### 6.6 Modals

- Backdrop: `rgba(0, 0, 0, 0.5)`
- Content: `--color-surface`, 12px radius, 24px padding
- Max width: 480px (small), 640px (medium), 960px (large)
- Close button: top-right, ghost button with X icon
- Animation: fade in + scale from 0.95 to 1.0, 200ms ease-out

### 6.7 Toast Notifications

- Position: top-right
- Auto-dismiss: 4 seconds
- Variants: success, error, warning, info (using semantic colors)
- Border-left: 4px solid semantic color
- `aria-live="polite"`

---

## 7. Charts & Data Visualization

### 7.1 Recommended Library

**Recharts** — React-native, declarative, good TypeScript support, lightweight.

### 7.2 Chart Types for Hawa

| Data | Chart Type | When |
|------|-----------|------|
| Sentiment distribution | **Donut Chart** | Overview of positive/negative/neutral split (max 5 segments) |
| Sentiment over time | **Line Chart / Area Chart** | Trend analysis across dates |
| Emotion breakdown | **Horizontal Bar Chart** | Compare emotion frequencies |
| Aspect comparison | **Bar Chart (vertical)** | Compare sentiment across aspects (product, service, pricing) |
| Brand health score | **KPI Card + Gauge** | Single metric with trend indicator |
| Post volume over time | **Area Chart** | Show activity trends |
| Multi-brand comparison | **Grouped Bar Chart** | Compare brands across dimensions |
| Top keywords | **Horizontal Bar Chart** | Ranked word frequency (not word cloud — poor a11y) |

### 7.3 Chart Color Palette

Use the sentiment palette (Section 2.3) for sentiment charts. For categorical charts, use this sequence:

```
#2563EB, #3B82F6, #06B6D4, #8B5CF6, #EC4899, #F97316, #22C55E, #FBBF24
```

### 7.4 Chart Guidelines

- Always include visible legends near the chart
- Tooltips on hover showing exact values
- Grid lines: `#E2E8F0` (subtle, low contrast)
- Axis labels: `--text-sm`, `--color-text-secondary`
- Provide tabular data fallback for accessibility
- Animate on enter (300ms ease-out), respect `prefers-reduced-motion`
- Show skeleton/shimmer while data loads
- Empty state: "No data available" message with guidance

---

## 8. Interaction Patterns

### 8.1 Transitions & Animation

| Type | Duration | Easing | Example |
|------|----------|--------|---------|
| Hover | 150ms | ease | Button color change, row highlight |
| Focus | 150ms | ease | Input focus ring |
| Expand/Collapse | 200ms | ease-out | Accordion, sidebar toggle |
| Modal enter | 200ms | ease-out | Fade + scale |
| Modal exit | 150ms | ease-in | Fade out |
| Page transition | 200ms | ease | Content fade |
| Chart enter | 300ms | ease-out | Data animation |
| Toast enter | 300ms | ease-out | Slide in from right |
| Toast exit | 200ms | ease-in | Fade out |

### 8.2 Loading States

| Duration | Pattern |
|----------|---------|
| < 300ms | No indicator |
| 300ms - 2s | Inline spinner or skeleton |
| > 2s | Full skeleton screen with shimmer |

### 8.3 Empty States

Every list/table/chart must handle empty state:
- Icon or illustration (optional)
- Short heading: "No posts found"
- Description: "Try adjusting your filters or upload a dataset to get started."
- Action button if applicable: "Upload Dataset"

### 8.4 Error States

- Inline errors below form fields
- Toast for async operation failures
- Full-page error for unrecoverable states (with retry action)
- Error messages: state cause + recovery path ("Unable to load report. Check your connection and try again.")

---

## 9. Icons

Use **Lucide React** (`lucide-react`) — consistent stroke width, tree-shakeable, MIT licensed.

- Size: 20px default, 16px in compact contexts, 24px in headers
- Stroke width: 1.5 (default)
- Color: inherit from parent text color
- Never use emojis as icons

---

## 10. Accessibility

- Contrast: 4.5:1 minimum for text, 3:1 for large text and UI elements
- Focus rings: 2px solid `--color-ring`, visible on all interactive elements
- Keyboard navigation: full tab support, logical tab order
- Screen readers: proper labels, `aria-live` for dynamic content, `alt` text for images
- `prefers-reduced-motion`: disable/reduce all animations
- Minimum touch target: 44x44px
- Color is never the sole indicator — always pair with text, icon, or pattern

---

## Decision Log

| Decision | Options Considered | Choice | Rationale |
|----------|-------------------|--------|-----------|
| Typography | Poppins+Open Sans, Plus Jakarta Sans, Fira Code+Fira Sans | **TBD — choose together** | See Section 3.1 |
| Chart library | Chart.js, Recharts, D3, ApexCharts | Recharts (recommended) | React-native, declarative, good TS support, lightweight |
| Icons | Heroicons, Lucide, Phosphor | Lucide | Consistent, tree-shakeable, large icon set |
| CSS approach | Plain CSS (per CLAUDE.md) | Plain CSS with CSS custom properties | Matches project conventions, variables enable theming |
