# Frontend audit — refactor plan

Audit of `apps/frontend/` against vercel-react-best-practices and web-interface-guidelines. Five PRs, each self-contained. Branch names follow the monorepo convention `<app>/<type>/<short-desc>` from `CLAUDE.md`.

Finding codes (A#, B#, C#) refer to the audit report.

---

## PR 1 — frontend/a11y/focus-visible-global
Findings: A8
Files:
  - src/index.css
Acceptance:
  - [ ] Global `:focus-visible { outline: 2px solid var(--color-ring); outline-offset: 2px; }` baseline added
  - [ ] Tab traversal shows a visible ring on every interactive element in Login, Dashboard, and Admin/Users
  - [ ] Mouse click does NOT produce a focus ring (`:focus-visible`, not `:focus`)
  - [ ] No regressions in components that already style focus (`.login-submit`)
  - [ ] Keyboard-only walkthrough of Login → Dashboard → Admin/Users recorded (screen capture attached to PR)
Baseline: <Lighthouse a11y score on /dashboard and /admin/users — fill before>
Target:   <same routes — fill after>

---

## PR 2 — frontend/perf/critical-loading
Findings: B1, A9
Files:
  - src/index.css (remove `@import url('https://fonts.googleapis.com/...')`)
  - index.html (add preconnect + stylesheet link in `<head>`)
  - src/components/AdminLayout/AdminLayout.tsx (logo `<img>`)
  - src/components/MarketingLayout/MarketingLayout.tsx (logo `<img>`)
  - src/pages/Login/Login.tsx (logo `<img>`)
Acceptance:
  - [ ] Fonts load via `<link rel="preconnect">` to `fonts.googleapis.com` + `fonts.gstatic.com` (crossorigin) and `<link rel="stylesheet" href="...&display=swap">`
  - [ ] `@import` removed from `index.css`
  - [ ] All three logo `<img>` tags have explicit `width` and `height` attributes matching rendered aspect
  - [ ] Network waterfall (Chrome DevTools, Fast 3G, cache disabled) shows fonts fetched in parallel with CSS, not serialized after CSS parse
Baseline: <LCP + CLS on /login via Lighthouse; waterfall screenshot — fill before>
Target:   <LCP drops ≥ 200 ms; CLS = 0 on /login; waterfall screenshot attached>

---

## PR 3 — frontend/a11y/modal-semantics
Findings: A1, A2
Files:
  - src/components/Modal/Modal.tsx
  - (consumer pages only if Modal's public API needs to change — keep minimal)
Acceptance:
  - [ ] Modal root has `role="dialog"`, `aria-modal="true"`, `aria-labelledby` pointing to the `<h2>` title id
  - [ ] On open: focus moves into the modal (first focusable element or close button)
  - [ ] Tab / Shift-Tab stay within the modal (focus trap)
  - [ ] Esc closes (verify not regressed)
  - [ ] Backdrop click closes (verify not regressed)
  - [ ] On close: focus returns to the element that opened the modal
  - [ ] Manual keyboard QA in PR description:
    - [ ] Open Create User modal, Tab through all fields, Shift-Tab back, Esc to close, focus returns to "Create User" trigger
    - [ ] Open Delete-confirm dialog via keyboard, Enter confirms, Esc cancels
    - [ ] VoiceOver announces the dialog on open (macOS)
  - [ ] axe-core reports 0 modal-related violations with a modal open
Baseline: <axe violations on /admin/users with Create-User modal open — list rule ids>
Target:   <0 modal-related axe violations; manual SR test passes>

---

## PR 4 — frontend/refactor/link-navigation
Findings: A3, A4, A5, C2
Files:
  - src/pages/Dashboard/Dashboard.tsx (brand items, "View all" buttons, Start Analysis)
  - src/pages/Brands/BrandList.tsx (brand cards)
  - src/pages/Brands/BrandDetail.tsx (back, Start Analysis, View Reports)
  - src/pages/Reports/ReportList.tsx (table rows — preserve `state={ report }` pre-hydration)
  - src/pages/Analysis/StartAnalysis.tsx (back, cancel, keywords button)
  - src/pages/Analysis/ReportStatus.tsx (back, View all reports, Back to brands)
  - Corresponding `.css` if button styles need to apply to `<Link>`
Acceptance:
  - [ ] Every `onClick={() => navigate(url)}` used for *pure* navigation replaced with `<Link to={url}>`; imperative post-async transitions (post-login redirect, post-submit navigate) stay on `navigate()`
  - [ ] Cmd+click (macOS) / Ctrl+click (Win) opens in a new tab on each converted element
  - [ ] Middle-click opens in a new tab
  - [ ] Right-click → "Copy link address" yields a valid URL
  - [ ] Screen reader announces these elements as links (not buttons)
  - [ ] Visual parity: no hover/active/focus-state regressions vs. `main`
Baseline: <list of currently-broken shortcuts per element — fill before>
Target:   <all listed shortcuts work; no visual diffs>

---

## PR 5 — frontend/perf/route-splitting
Findings: B2
Files:
  - src/router.tsx (convert page imports to `React.lazy`, wrap `<Routes>` in `<Suspense>`)
  - src/components/PageSkeleton/ (new — minimal loading fallback)
  - vite.config.ts (optional: `build.rollupOptions.output.manualChunks` for vendor split)
Acceptance:
  - [ ] All 13 page components converted to `React.lazy`
  - [ ] `<Suspense fallback={<PageSkeleton />}>` wraps `<Routes>`
  - [ ] DevTools Network panel shows a separate chunk loaded per visited route
  - [ ] Admin pages are NOT loaded on `/dashboard` (verify sequence: login → /dashboard → no `Admin*` chunks in network)
  - [ ] Skeleton fallback matches rough page shape — no layout flash
  - [ ] `npm run build` passes; no new TypeScript errors
Baseline: <`dist/assets/*.js` initial-chunk size + `vite-bundle-visualizer` screenshot — fill before>
Target:   <initial chunk shrinks ≥ 30 %; bundle-visualizer screenshot attached>

---

## Merge sequence

`1 → 2 → 3 → 4 → 5`.

- **PR 1** is CSS-only — merge first to de-risk the rest.
- **PR 2** touches `<head>` and three logos — orthogonal to everything downstream.
- **PR 3** changes Modal internals without touching callers — safe before PR 4.
- **PR 4** edits most page files; merge before PR 5 so lazy-import churn doesn't collide.
- **PR 5** only edits `router.tsx` + adds a skeleton — clean if merged last.
