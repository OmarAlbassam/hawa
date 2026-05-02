# Brand Assets — Drop your project's font + logo here

This directory is intentionally empty in the template repo. Per-project, drop in:

1. **Font files** — the brand font used in the video. Typical: Regular, Medium, Bold, ExtraBold weights as `.ttf` or `.woff2`. Filenames listed in `project.config.md → font_files`.
2. **Logo** — used in the closing `TaglineScene`. Typical: PNG with transparent background, dark-mode variant (since the closing scene is on a dark primary color). Filename listed in `project.config.md → logo`.

The skill copies these files into the generated Remotion project's `public/` directory, where Remotion's `staticFile()` and `@remotion/fonts` can load them.

> Do **not** check brand-specific assets into this template repo. Per-project assets belong in the consuming project (or in a project-specific override of this skill).
