// Generate voiceover clips via ElevenLabs API.
// Usage: ELEVENLABS_API_KEY=sk_... ELEVENLABS_VOICE_ID=... node scripts/generate-voiceover.mjs
//
// Edit the `scenes` array below with your scene names and text.
// VOICE_ID is required — set via env var or hardcode below from project.config.md.

import fs from 'fs';
import path from 'path';

const API_KEY = process.env.ELEVENLABS_API_KEY;
if (!API_KEY) {
  console.error('Missing ELEVENLABS_API_KEY environment variable.');
  console.error(
    'Usage: ELEVENLABS_API_KEY=sk_... ELEVENLABS_VOICE_ID=... node scripts/generate-voiceover.mjs',
  );
  process.exit(1);
}
const VOICE_ID = process.env.ELEVENLABS_VOICE_ID;
if (!VOICE_ID) {
  console.error('Missing ELEVENLABS_VOICE_ID. See project.config.md → elevenlabs.voice_id.');
  process.exit(1);
}

const scenes = [
  // { name: "01-intro", text: "..." },
  // { name: "02-demo", text: "..." },
  // { name: "03-closing", text: "..." },
];

if (scenes.length === 0) {
  console.error(
    'No scenes defined. Edit the `scenes` array in this file before running.',
  );
  process.exit(1);
}

async function generate(scene) {
  const res = await fetch(
    `https://api.elevenlabs.io/v1/text-to-speech/${VOICE_ID}`,
    {
      method: 'POST',
      headers: { 'xi-api-key': API_KEY, 'Content-Type': 'application/json' },
      body: JSON.stringify({
        text: scene.text,
        model_id: 'eleven_v3',
        voice_settings: { stability: 0.5, similarity_boost: 0.75 },
      }),
    },
  );
  if (!res.ok)
    throw new Error(`ElevenLabs error: ${res.status} ${await res.text()}`);
  const buffer = Buffer.from(await res.arrayBuffer());
  const outPath = path.join('public', `voiceover-${scene.name}.mp3`);
  fs.writeFileSync(outPath, buffer);
  console.log(`Generated ${outPath} (${buffer.length} bytes)`);
}

for (const scene of scenes) {
  await generate(scene);
}
