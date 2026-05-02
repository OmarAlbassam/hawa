import { useCurrentFrame, interpolate } from "remotion";
import React from "react";

type AnimatedGradientProps = {
  colors: string[];
  speed?: number;
  angle?: number;
};

export const AnimatedGradient: React.FC<AnimatedGradientProps> = ({
  colors,
  speed = 0.5,
  angle = 135,
}) => {
  const frame = useCurrentFrame();

  const shift = interpolate(
    Math.sin(frame * 0.02 * speed),
    [-1, 1],
    [0, 30]
  );

  const gradientStops = colors
    .map((color, index) => {
      const basePosition = (index / (colors.length - 1)) * 100;
      const animatedPosition = basePosition + shift * (index % 2 === 0 ? 1 : -1);
      return `${color} ${animatedPosition}%`;
    })
    .join(", ");

  return (
    <div
      style={{
        position: "absolute",
        top: 0,
        left: 0,
        width: "100%",
        height: "100%",
        background: `linear-gradient(${angle}deg, ${gradientStops})`,
      }}
    />
  );
};
