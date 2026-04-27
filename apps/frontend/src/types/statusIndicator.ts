import type { EmotionEnum } from "./report";

export type SentimentCategory = "NEGATIVE" | "NEUTRAL" | "POSITIVE";

export interface SentimentBreakdown {
  negative: number;
  neutral: number;
  positive: number;
}

export interface TopEmotion {
  emotion: EmotionEnum;
  count: number;
  percentage: number;
}

export interface StatusIndicatorResponse {
  averageSentiment: number | null;
  sentimentCategory: SentimentCategory | null;
  sentimentBreakdown: SentimentBreakdown;
  dominantEmotion: EmotionEnum | null;
  topEmotions: TopEmotion[];
  emotionDiversity: number | null;
  summary: string | null;
  totalAnalyzedPosts: number;
}
