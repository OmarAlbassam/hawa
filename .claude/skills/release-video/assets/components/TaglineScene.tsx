import {
  AbsoluteFill,
  useCurrentFrame,
  useVideoConfig,
  spring,
  interpolate,
  Img,
  staticFile,
} from 'remotion';
import React from 'react';

type TaglineSceneProps = {
  productName: string;
  subtitle: string;
  logoFilename: string;
  primaryColor: string;
  textColor?: string;
  subtitleColor?: string;
  fontFamily: string;
};

/**
 * Standard ending scene — project logo + tagline.
 * Always the final scene. ~120 frames (4s).
 * All copy and brand values come from project.config.md.
 * Requires `logoFilename` to exist in public/.
 */
export const TaglineScene: React.FC<TaglineSceneProps> = ({
  productName,
  subtitle,
  logoFilename,
  primaryColor,
  textColor = '#ffffff',
  subtitleColor = '#9ca3af',
  fontFamily,
}) => {
  const frame = useCurrentFrame();
  const { fps, durationInFrames } = useVideoConfig();

  const logoSpring = spring({ frame, fps, config: { damping: 200 } });
  const logoOpacity = interpolate(logoSpring, [0, 1], [0, 1]);
  const logoScale = interpolate(logoSpring, [0, 1], [0.95, 1]);

  const taglineSpring = spring({
    frame,
    fps,
    delay: 15,
    config: { damping: 200 },
  });
  const taglineOpacity = interpolate(taglineSpring, [0, 1], [0, 1]);
  const taglineY = interpolate(taglineSpring, [0, 1], [20, 0]);

  const breathePhase = (frame % 60) / 60;
  const breatheScale = 1 + Math.sin(breathePhase * Math.PI * 2) * 0.015;

  const fadeOut = interpolate(
    frame,
    [durationInFrames - 15, durationInFrames],
    [1, 0],
    { extrapolateLeft: 'clamp', extrapolateRight: 'clamp' },
  );

  return (
    <AbsoluteFill
      style={{
        backgroundColor: primaryColor,
        justifyContent: 'center',
        alignItems: 'center',
        opacity: fadeOut,
      }}
    >
      <div
        style={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          gap: 50,
        }}
      >
        <div
          style={{
            opacity: logoOpacity,
            transform: `scale(${logoScale * breatheScale})`,
          }}
        >
          <Img
            src={staticFile(logoFilename)}
            style={{ width: 380, height: 'auto', objectFit: 'contain' }}
          />
        </div>
        <div
          style={{
            display: 'flex',
            flexDirection: 'column',
            alignItems: 'center',
            gap: 12,
            opacity: taglineOpacity,
            transform: `translateY(${taglineY}px)`,
          }}
        >
          <h1
            style={{
              fontFamily,
              fontSize: 52,
              fontWeight: 500,
              color: textColor,
              textAlign: 'center',
              margin: 0,
              letterSpacing: 2,
            }}
          >
            {productName}
          </h1>
          <h2
            style={{
              fontFamily,
              fontSize: 40,
              fontWeight: 800,
              color: subtitleColor,
              textAlign: 'center',
              margin: 0,
              textTransform: 'uppercase',
              letterSpacing: 4,
            }}
          >
            {subtitle}
          </h2>
        </div>
      </div>
    </AbsoluteFill>
  );
};
