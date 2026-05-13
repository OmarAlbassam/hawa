import { useCallback, useEffect, useMemo, useState } from 'react'
import { getStatusIndicator } from '../../services/reportService'
import { getBrandStatusIndicator } from '../../services/brandService'
import type {
  AspectEnum,
  AspectShare,
  EmotionEnum,
  EmotionShare,
  StatusIndicatorResponse,
} from '../../types/report'
import type { BrandStatusIndicatorResponse } from '../../types/brand'
import ErrorBanner from '../ErrorBanner/ErrorBanner'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

export type StatusIndicatorSource =
  | { kind: 'report'; reportId: number }
  | { kind: 'brand'; brandId: number }

interface StatusIndicatorProps {
  source: StatusIndicatorSource
  title?: string
  className?: string
}

interface NormalizedStatus {
  averageSentiment: number | null
  analyzedPostCount: number
  topEmotions: EmotionShare[]
  topAspects: AspectShare[]
  summary: string | null
  meta: string
}

const EMOTION_COLORS: Record<EmotionEnum, string> = {
  JOY: '#FBBF24',
  ANGER: '#EF4444',
  SADNESS: '#3B82F6',
  FEAR: '#8B5CF6',
  SURPRISE: '#F97316',
  DISGUST: '#84CC16',
  NEUTRAL: '#9CA3AF',
}

const ASPECT_COLORS: Record<AspectEnum, string> = {
  PRODUCT: '#0284C7',
  SERVICE: '#16A34A',
  DELIVERY: '#D97706',
  PRICING: '#E91E63',
}

const toTitle = (key: string): string => key.charAt(0) + key.slice(1).toLowerCase()
const fmtPct = (value: number): string => `${Math.round(value * 100)}%`

type ToneToken = 'neg' | 'neu' | 'muted' | 'pos'

const scoreTone = (score: number): ToneToken => {
  if (score < 20) return 'neg'
  if (score < 40) return 'neu'
  if (score < 60) return 'muted'
  if (score < 80) return 'pos'
  return 'pos'
}

const toneColorVar: Record<ToneToken, string> = {
  neg: 'var(--neg)',
  neu: 'var(--neu)',
  muted: 'var(--text-3)',
  pos: 'var(--pos)',
}

const scoreLabel = (score: number): string => {
  if (score < 20) return 'Very Negative'
  if (score < 40) return 'Negative'
  if (score < 60) return 'Neutral'
  if (score < 80) return 'Positive'
  return 'Very Positive'
}

const normalizeReport = (data: StatusIndicatorResponse): NormalizedStatus => ({
  averageSentiment: data.averageSentiment,
  analyzedPostCount: data.analyzedPostCount,
  topEmotions: data.topEmotions,
  topAspects: data.topAspects,
  summary: data.summary,
  meta: `${data.analyzedPostCount.toLocaleString()} posts analyzed`,
})

const normalizeBrand = (data: BrandStatusIndicatorResponse): NormalizedStatus => ({
  averageSentiment: data.averageSentiment,
  analyzedPostCount: data.analyzedPostCount,
  topEmotions: data.topEmotions,
  topAspects: data.topAspects,
  summary: null,
  meta: `${data.completedReportCount.toLocaleString()} ${
    data.completedReportCount === 1 ? 'report' : 'reports'
  } · ${data.analyzedPostCount.toLocaleString()} posts`,
})

const ScoreDial = ({ score }: { score: number }): React.JSX.Element => {
  const tone = scoreTone(score)
  const color = toneColorVar[tone]
  const label = scoreLabel(score)
  return (
    <div
      role="img"
      aria-label={`Brand score ${score} of 100, ${label}`}
      className="flex flex-col items-center gap-3"
    >
      <div
        className="relative flex h-[148px] w-[148px] items-center justify-center rounded-full"
        style={{
          background: `conic-gradient(${color} ${score * 3.6}deg, var(--bg-2) 0deg)`,
        }}
        aria-hidden
      >
        <div className="flex h-[124px] w-[124px] flex-col items-center justify-center rounded-full bg-card">
          <span
            className="font-display text-[40px] font-semibold leading-none tracking-[-0.04em] tabular-nums"
            style={{ color }}
          >
            {score}
          </span>
          <span className="mt-1 font-mono text-[10px] uppercase tracking-[0.1em] text-text-3">
            / 100
          </span>
        </div>
      </div>
      <span
        className="font-display text-[13px] font-medium tracking-[-0.01em]"
        style={{ color }}
      >
        {label}
      </span>
    </div>
  )
}

interface ChipListProps<T extends EmotionShare | AspectShare> {
  heading: string
  items: T[]
  getKey: (item: T) => string
  getColor: (item: T) => string
  emptyMessage: string
}

function ChipList<T extends EmotionShare | AspectShare>({
  heading,
  items,
  getKey,
  getColor,
  emptyMessage,
}: ChipListProps<T>): React.JSX.Element {
  return (
    <div className="space-y-3">
      <h3 className="eyebrow">{heading}</h3>
      {items.length === 0 ? (
        <p className="text-[13px] text-muted-foreground">{emptyMessage}</p>
      ) : (
        <ul className="flex flex-wrap gap-2">
          {items.map((item) => {
            const key = getKey(item)
            const color = getColor(item)
            return (
              <li
                key={key}
                className="flex items-center gap-2 rounded-sm border px-2 py-1 text-[12px]"
                style={{
                  borderColor: `${color}55`,
                  color,
                  backgroundColor: `${color}1A`,
                }}
              >
                <span className="font-medium">{toTitle(key)}</span>
                <span className="font-mono text-[11px] opacity-70">
                  {item.count} · {fmtPct(item.percentage)}
                </span>
              </li>
            )
          })}
        </ul>
      )}
    </div>
  )
}

const StatusIndicator = ({
  source,
  title = 'Brand Status Indicator',
  className,
}: StatusIndicatorProps): React.JSX.Element => {
  const [data, setData] = useState<NormalizedStatus | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const sourceKey =
    source.kind === 'report' ? `report:${source.reportId}` : `brand:${source.brandId}`

  const load = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      if (source.kind === 'report') {
        setData(normalizeReport(await getStatusIndicator(source.reportId)))
      } else {
        setData(normalizeBrand(await getBrandStatusIndicator(source.brandId)))
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load status indicator')
    } finally {
      setLoading(false)
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sourceKey])

  useEffect(() => {
    load()
  }, [load])

  const score = useMemo(() => {
    if (!data || data.averageSentiment == null) return null
    return Math.round((data.averageSentiment / 5) * 100)
  }, [data])

  const Shell = ({ children }: { children: React.ReactNode }) => (
    <section className={cn('rounded-md border border-border bg-card p-6', className)}>
      {children}
    </section>
  )

  const Header = ({ meta }: { meta?: string }) => (
    <header className="mb-5 flex items-baseline justify-between gap-4 border-b border-border pb-4">
      <h2 className="font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
        {title}
      </h2>
      {meta && <span className="eyebrow">{meta}</span>}
    </header>
  )

  if (loading) {
    return (
      <Shell>
        <Header />
        <Skeleton className="h-48 w-full" />
      </Shell>
    )
  }

  if (error) {
    return (
      <Shell>
        <Header />
        <ErrorBanner message={error} onRetry={load} />
      </Shell>
    )
  }

  if (!data) return <></>

  if (data.analyzedPostCount === 0 || score == null) {
    return (
      <Shell>
        <Header />
        <p className="text-[13px] text-muted-foreground">
          {source.kind === 'brand'
            ? 'No completed reports yet for this brand.'
            : 'No analyzed posts yet for this report.'}
        </p>
      </Shell>
    )
  }

  return (
    <Shell>
      <Header meta={data.meta} />

      <div className="grid grid-cols-1 gap-8 md:grid-cols-[auto_1fr] md:items-center">
        <ScoreDial score={score} />

        <div className="space-y-6">
          <ChipList
            heading="Top emotions"
            items={data.topEmotions}
            getKey={(item) => item.emotion}
            getColor={(item) => EMOTION_COLORS[item.emotion]}
            emptyMessage="No emotions detected."
          />
          <ChipList
            heading="Top aspects"
            items={data.topAspects}
            getKey={(item) => item.aspect}
            getColor={(item) => ASPECT_COLORS[item.aspect]}
            emptyMessage="No aspects detected."
          />
        </div>
      </div>

      {data.summary && (
        <p className="mt-6 border-t border-border pt-4 text-[13px] leading-relaxed text-muted-foreground">
          {data.summary}
        </p>
      )}
    </Shell>
  )
}

export default StatusIndicator
