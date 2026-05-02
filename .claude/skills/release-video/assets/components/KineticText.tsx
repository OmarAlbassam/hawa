import {
  useCurrentFrame,
  useVideoConfig,
  spring,
  interpolate,
} from "remotion";
import React from "react";

type KineticTextProps = {
  text: string;
  fontFamily: string;
  fontSize?: number;
  fontWeight?: number;
  color?: string;
  delay?: number;
  staggerDelay?: number;
  effect?: "bounce" | "fade" | "scale" | "wave" | "typewriter";
  highlightWords?: string[];
  highlightColor?: string;
};

export const KineticText: React.FC<KineticTextProps> = ({
  text,
  fontFamily,
  fontSize = 80,
  fontWeight = 700,
  color = "#ffffff",
  delay = 0,
  staggerDelay = 2,
  effect = "bounce",
  highlightWords = [],
  highlightColor = "#f59e0b",
}) => {
  const frame = useCurrentFrame();
  const { fps } = useVideoConfig();

  const characters = text.split("");
  const words = text.split(" ");

  if (effect === "typewriter") {
    const charsToShow = Math.floor(
      interpolate(frame - delay, [0, characters.length * 2], [0, characters.length], {
        extrapolateLeft: "clamp",
        extrapolateRight: "clamp",
      })
    );

    return (
      <div style={{ fontFamily, fontSize, fontWeight, color, display: "flex", flexWrap: "wrap", justifyContent: "center" }}>
        {characters.slice(0, charsToShow).join("")}
        {frame > delay && charsToShow < characters.length && (
          <span style={{ opacity: Math.sin(frame * 0.3) > 0 ? 1 : 0, marginRight: 2 }}>|</span>
        )}
      </div>
    );
  }

  return (
    <div style={{ fontFamily, fontSize, fontWeight, color, display: "flex", flexWrap: "wrap", justifyContent: "center", gap: "0.3em" }}>
      {words.map((word, wordIndex) => {
        const isHighlighted = highlightWords.includes(word);
        const wordDelay = delay + wordIndex * staggerDelay * 3;

        return (
          <span key={wordIndex} style={{ display: "inline-flex", color: isHighlighted ? highlightColor : color, transform: isHighlighted ? "scale(1.1)" : "scale(1)" }}>
            {word.split("").map((char, charIndex) => {
              const charDelay = wordDelay + charIndex * staggerDelay;
              let animatedStyle: React.CSSProperties = {};

              if (effect === "bounce") {
                const charSpring = spring({ frame: frame - charDelay, fps, config: { damping: 12, stiffness: 200 } });
                animatedStyle = {
                  transform: `translateY(${interpolate(charSpring, [0, 1], [30, 0])}px)`,
                  opacity: interpolate(charSpring, [0, 1], [0, 1]),
                };
              } else if (effect === "scale") {
                const charSpring = spring({ frame: frame - charDelay, fps, config: { damping: 15, stiffness: 180 } });
                animatedStyle = {
                  transform: `scale(${interpolate(charSpring, [0, 1], [0, 1])})`,
                  opacity: interpolate(charSpring, [0, 1], [0, 1]),
                };
              } else if (effect === "wave") {
                const charSpring = spring({ frame: frame - charDelay, fps, config: { damping: 200 } });
                const waveY = Math.sin((frame - charDelay) * 0.15) * 5;
                animatedStyle = {
                  transform: `translateY(${charSpring > 0.5 ? waveY : 30 - charSpring * 60}px)`,
                  opacity: interpolate(charSpring, [0, 1], [0, 1]),
                };
              } else {
                const charSpring = spring({ frame: frame - charDelay, fps, config: { damping: 200 } });
                animatedStyle = { opacity: interpolate(charSpring, [0, 1], [0, 1]) };
              }

              return (
                <span key={charIndex} style={{ display: "inline-block", ...animatedStyle }}>
                  {char}
                </span>
              );
            })}
          </span>
        );
      })}
    </div>
  );
};
