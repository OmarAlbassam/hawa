// Shared styling for native HTML <select> triggers across the app.
// Renders a custom chevron via inline SVG background-image (with appearance-none
// suppressing the browser's native arrow) so the chevron has consistent spacing
// from the right border instead of being crammed against it.
export const selectClass =
  "flex h-9 appearance-none rounded-md border border-input bg-card bg-no-repeat pl-3 pr-9 text-[13px] text-foreground transition-[border-color,box-shadow] focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/15 disabled:cursor-not-allowed disabled:opacity-50 bg-[length:0.875rem_0.875rem] bg-[right_0.875rem_center] bg-[url(\"data:image/svg+xml;utf8,<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24' fill='none' stroke='%23a1a1aa' stroke-width='2' stroke-linecap='round' stroke-linejoin='round'><polyline points='6 9 12 15 18 9'/></svg>\")]"
