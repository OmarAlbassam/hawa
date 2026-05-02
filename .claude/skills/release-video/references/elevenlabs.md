# ElevenLabs TTS Integration

Generate voiceover for release videos. API key in `.env` as `ELEVENLABS_API_KEY`. Voice ID in `project.config.md` → `elevenlabs.voice_id`.

## Model & Settings

- **Model:** `eleven_v3`
- **Stability:** `0.5` (Natural) — v3 only accepts `0.0` (Creative), `0.5` (Natural), `1.0` (Robust)
- `similarity_boost`: 0.75
- v3 does NOT support `style` or `use_speaker_boost` params

```json
{
  "model_id": "eleven_v3",
  "voice_settings": { "stability": 0.5, "similarity_boost": 0.75 }
}
```

## Generate Speech

```bash
curl -X POST "https://api.elevenlabs.io/v1/text-to-speech/{{VOICE_ID}}" \
  -H "xi-api-key: $ELEVENLABS_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "text": "We added a new advanced filter on the reports page.",
    "model_id": "eleven_v3",
    "voice_settings": { "stability": 0.5, "similarity_boost": 0.75 }
  }' \
  --output voiceover-demo.mp3
```

## Voice

The voice is project-specific — see `project.config.md` → `elevenlabs.voice_id` and `voice_name`. Browse available voices via the ElevenLabs dashboard or `GET https://api.elevenlabs.io/v1/voices`. Always test with one sample sentence before generating all clips — voice character matters far more than the words.

## Workflow in Remotion

1. **Copy** `assets/scripts/generate-voiceover.mjs` → `scripts/`, fill in scenes array
2. **Run:** `ELEVENLABS_API_KEY=... node scripts/generate-voiceover.mjs`
3. **Get duration** for frame calculation:
   ```tsx
   import { getAudioDurationInSeconds } from "@remotion/media-utils";
   const seconds = await getAudioDurationInSeconds(staticFile("voiceover-01-hook.mp3"));
   const frames = Math.ceil(seconds * fps) + 15; // padding so scene doesn't feel cut short
   ```
4. **Add audio to scenes:**
   ```tsx
   <Series.Sequence durationInFrames={frames}>
     <MyScene src="clips/clip-01.webm" caption="..." videoStartFrom={95} />
     <Audio src={staticFile("voiceover-01-hook.mp3")} />
   </Series.Sequence>
   ```
