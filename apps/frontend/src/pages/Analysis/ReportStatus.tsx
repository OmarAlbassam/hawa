import { useState, useEffect, useCallback, useRef } from 'react'
import { useParams, useLocation, Link } from 'react-router-dom'
import { ArrowLeft, ChevronDown, ChevronUp, Loader2 } from 'lucide-react'
import FilteredPostsList from './FilteredPostsList'
import {
  getReportStatus,
  getReports,
  getReportOverview,
} from '../../services/reportService'
import type {
  AspectBreakdownItem,
  AspectEnum,
  EmotionEnum,
  ReportOverviewResponse,
  ReportResponse,
  ReportStatusResponse,
} from '../../types/report'
import Badge from '../../components/Badge/Badge'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import { formatDate } from '../../utils/formatDate'
import { statusBadgeVariant, isTerminalStatus } from '../../utils/reportStatus'
import { Button } from '@/components/ui/button'

const POLL_INTERVAL_MS = 2000

const sourceLabel = (source: ReportResponse['dataSource']): string =>
  source === 'CSV_UPLOAD' ? 'CSV upload' : 'Reddit'

const EMOTION_ORDER: EmotionEnum[] = [
  'JOY',
  'ANGER',
  'SADNESS',
  'FEAR',
  'SURPRISE',
  'DISGUST',
  'NEUTRAL',
]

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

const fmtScore = (value: number | null | undefined): string =>
  value == null ? '—' : Number(value).toFixed(1)

interface DistributionProps<K extends string> {
  entries: Array<[K, number]>
  colorMap: Record<K, string>
}

function Distribution<K extends string>({
  entries,
  colorMap,
}: DistributionProps<K>): React.JSX.Element {
  const max = Math.max(1, ...entries.map(([, v]) => v))
  return (
    <div className="space-y-2">
      {entries.map(([key, count]) => {
        const pct = (count / max) * 100
        return (
          <div className="grid grid-cols-[120px_1fr_48px] items-center gap-3" key={key}>
            <span className="text-[13px] text-foreground">{toTitle(key)}</span>
            <div className="h-2 rounded-full bg-muted">
              <div
                className="h-full rounded-full"
                style={{ width: `${pct}%`, backgroundColor: colorMap[key] }}
              />
            </div>
            <span className="text-right font-mono text-[12px] tabular-nums text-muted-foreground">
              {count}
            </span>
          </div>
        )
      })}
    </div>
  )
}

function AspectRow({ item }: { item: AspectBreakdownItem }): React.JSX.Element {
  const emotionEntries = EMOTION_ORDER.map(
    (k) => [k, item.emotionDistribution[k] ?? 0] as [EmotionEnum, number],
  )
  return (
    <div className="space-y-3 border-t border-border pt-4 first:border-t-0 first:pt-0">
      <div className="flex flex-wrap items-baseline gap-4">
        <div className="flex items-center gap-2.5">
          <span
            aria-hidden
            className="size-2.5 rounded-full"
            style={{ backgroundColor: ASPECT_COLORS[item.aspect] }}
          />
          <span className="font-display text-[15px] font-medium tracking-[-0.01em] text-foreground">
            {toTitle(item.aspect)}
          </span>
        </div>
        <div className="ml-auto flex gap-6">
          <div className="text-right">
            <div className="font-mono text-[10px] uppercase tracking-[0.1em] text-text-3">
              Posts
            </div>
            <div className="font-mono text-[14px] tabular-nums text-foreground">
              {item.postCount}
            </div>
          </div>
          <div className="text-right">
            <div className="font-mono text-[10px] uppercase tracking-[0.1em] text-text-3">
              Avg sentiment
            </div>
            <div className="font-mono text-[14px] tabular-nums text-foreground">
              {fmtScore(item.averageSentiment)}
              <span className="ml-1 text-text-3">/ 5</span>
            </div>
          </div>
        </div>
      </div>
      {item.postCount > 0 && (
        <Distribution entries={emotionEntries} colorMap={EMOTION_COLORS} />
      )}
    </div>
  )
}

const ReportStatus = (): React.JSX.Element => {
  const { reportId: reportIdParam } = useParams<{ reportId: string }>()
  const location = useLocation()
  const reportId = reportIdParam ? Number(reportIdParam) : NaN

  const seed = (location.state as { report?: ReportResponse } | null)?.report

  const [report, setReport] = useState<ReportResponse | null>(seed ?? null)
  const [status, setStatus] = useState<ReportStatusResponse | null>(
    seed
      ? {
          reportId: seed.reportId,
          status: seed.status,
          createdAt: seed.createdAt,
          finishedAt: seed.finishedAt,
          failureReason: null,
        }
      : null,
  )
  const [overview, setOverview] = useState<ReportOverviewResponse | null>(null)
  const [overviewError, setOverviewError] = useState<string | null>(null)
  const [overviewReportId, setOverviewReportId] = useState<number>(reportId)
  const [showFiltered, setShowFiltered] = useState(false)
  if (overviewReportId !== reportId) {
    setOverviewReportId(reportId)
    setOverview(null)
    setOverviewError(null)
    setShowFiltered(false)
  }
  const [error, setError] = useState<string | null>(null)
  const [initialLoading, setInitialLoading] = useState(!seed)
  const mountedRef = useRef(true)

  useEffect(() => {
    mountedRef.current = true
    return () => {
      mountedRef.current = false
    }
  }, [])

  const fetchStatus = useCallback(async (): Promise<ReportStatusResponse | null> => {
    if (Number.isNaN(reportId)) return null
    try {
      const next = await getReportStatus(reportId)
      if (mountedRef.current) {
        setStatus(next)
        setError(null)
      }
      return next
    } catch (err) {
      if (mountedRef.current) {
        setError(err instanceof Error ? err.message : 'Failed to fetch report status')
      }
      return null
    }
  }, [reportId])

  const hydrateReportFromList = useCallback(async () => {
    if (Number.isNaN(reportId)) return
    try {
      const page = await getReports({ size: 50 })
      if (!mountedRef.current) return
      const match = page.content.find((r) => r.reportId === reportId)
      if (match) setReport(match)
    } catch {
      // non-fatal
    }
  }, [reportId])

  useEffect(() => {
    if (Number.isNaN(reportId)) return
    let cancelled = false
    ;(async () => {
      const first = await fetchStatus()
      if (cancelled || !mountedRef.current) return
      setInitialLoading(false)
      if (!seed && first && !isTerminalStatus(first.status)) {
        await hydrateReportFromList()
      }
    })()
    return () => {
      cancelled = true
    }
  }, [reportId, fetchStatus, hydrateReportFromList, seed])

  useEffect(() => {
    if (!status || isTerminalStatus(status.status)) return
    const id = window.setInterval(fetchStatus, POLL_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [status, fetchStatus])

  const currentStatus = status?.status ?? report?.status ?? 'PENDING'

  const fetchOverview = useCallback(async () => {
    if (Number.isNaN(reportId)) return
    try {
      const data = await getReportOverview(reportId)
      if (mountedRef.current) {
        setOverview(data)
        setOverviewError(null)
      }
    } catch (err) {
      if (mountedRef.current) {
        setOverviewError(err instanceof Error ? err.message : 'Failed to load overview')
      }
    }
  }, [reportId])

  useEffect(() => {
    if (Number.isNaN(reportId)) return
    if (currentStatus !== 'COMPLETED') return
    if (overview != null || overviewError != null) return
    let cancelled = false
    ;(async () => {
      try {
        const data = await getReportOverview(reportId)
        if (!cancelled && mountedRef.current) {
          setOverview(data)
          setOverviewError(null)
        }
      } catch (err) {
        if (!cancelled && mountedRef.current) {
          setOverviewError(err instanceof Error ? err.message : 'Failed to load overview')
        }
      }
    })()
    return () => {
      cancelled = true
    }
  }, [currentStatus, overview, overviewError, reportId])

  if (Number.isNaN(reportId)) {
    return <ErrorBanner message="Invalid report id" />
  }

  if (initialLoading) return <PageSkeleton />

  const headerInfo = overview ?? report
  const brandName = headerInfo?.brandName
  const source = headerInfo?.dataSource

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
        <Link to="/reports">
          <ArrowLeft />
          Back to Reports
        </Link>
      </Button>

      <header className="flex flex-wrap items-start justify-between gap-4 border-b border-border pb-6">
        <div>
          <span className="eyebrow">Report</span>
          <h1 className="mt-2 font-display text-[32px] font-semibold leading-none tracking-[-0.025em] text-foreground">
            {brandName ? `${brandName} analysis` : `Report #${reportId}`}
          </h1>
          <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1 font-mono text-[11px] uppercase tracking-[0.06em] text-text-3">
            {source && <span>Source: {sourceLabel(source)}</span>}
            {headerInfo?.dateFrom && headerInfo?.dateTo && (
              <span>
                {formatDate(headerInfo.dateFrom)} – {formatDate(headerInfo.dateTo)}
              </span>
            )}
            {status?.createdAt && <span>Started {formatDate(status.createdAt)}</span>}
          </div>
        </div>
        <Badge variant={statusBadgeVariant[currentStatus]}>{currentStatus}</Badge>
      </header>

      {error && <ErrorBanner message={error} onRetry={fetchStatus} />}

      {!isTerminalStatus(currentStatus) && (
        <div className="flex items-center gap-4 rounded-md border border-border bg-card p-6">
          <Loader2 className="size-5 shrink-0 animate-spin text-primary" aria-hidden />
          <div>
            <p className="font-display text-[15px] font-medium tracking-[-0.01em] text-foreground">
              {currentStatus === 'PENDING'
                ? 'Waiting for the job to start…'
                : 'Analyzing posts…'}
            </p>
            <p className="mt-1 text-[13px] text-muted-foreground">
              This page refreshes automatically. You can leave and come back later — the run
              continues in the background.
            </p>
          </div>
        </div>
      )}

      {currentStatus === 'COMPLETED' && (
        <>
          {overviewError && <ErrorBanner message={overviewError} onRetry={fetchOverview} />}
          {!overview && !overviewError && <PageSkeleton />}
          {overview && (
            <>
              <section className="rounded-md border border-border bg-card p-6">
                <h2 className="mb-4 font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
                  Overview
                </h2>
                <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
                  <div className="rounded-md border border-border bg-muted/40 p-5">
                    <div className="text-[12px] text-muted-foreground">Sentiment score</div>
                    <div className="mt-2 font-display text-[32px] font-semibold leading-none tabular-nums tracking-[-0.03em] text-foreground">
                      {fmtScore(overview.averageSentiment)}
                      <span className="ml-1 font-mono text-[11px] text-text-3">/ 5</span>
                    </div>
                  </div>
                  <Link
                    to={`/reports/${reportId}/posts`}
                    className="group rounded-md border border-border bg-muted/40 p-5 transition-colors hover:border-border-strong"
                  >
                    <div className="text-[12px] text-muted-foreground">Posts analyzed</div>
                    <div className="mt-2 font-display text-[32px] font-semibold leading-none tabular-nums tracking-[-0.03em] text-primary">
                      {overview.analyzedPosts}
                    </div>
                    <div className="mt-2 font-mono text-[11px] uppercase tracking-[0.1em] text-text-3 group-hover:text-primary">
                      View all posts →
                    </div>
                  </Link>
                  <div className="rounded-md border border-border bg-muted/40 p-5">
                    <div className="text-[12px] text-muted-foreground">Filtered out</div>
                    <div className="mt-2 font-display text-[32px] font-semibold leading-none tabular-nums tracking-[-0.03em] text-foreground">
                      {overview.filteredOutCount}
                    </div>
                  </div>
                </div>
                {status?.finishedAt && (
                  <p className="mt-4 font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                    Completed {formatDate(status.finishedAt)}
                  </p>
                )}
              </section>

              <section className="rounded-md border border-border bg-card p-6">
                <h2 className="mb-4 font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
                  Emotion distribution
                </h2>
                <Distribution
                  entries={EMOTION_ORDER.map((k) => [k, overview.emotionDistribution[k] ?? 0])}
                  colorMap={EMOTION_COLORS}
                />
              </section>

              <section className="rounded-md border border-border bg-card p-6">
                <h2 className="mb-4 font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
                  Aspect breakdown
                </h2>
                <div className="space-y-5">
                  {overview.aspects.map((item) => (
                    <AspectRow key={item.aspect} item={item} />
                  ))}
                </div>
              </section>

              {overview.filteredOutCount > 0 && (
                <section className="rounded-md border border-border bg-card p-6">
                  <button
                    type="button"
                    onClick={() => setShowFiltered((v) => !v)}
                    aria-expanded={showFiltered}
                    className="flex w-full items-center gap-2 text-left text-[13px] font-medium text-foreground transition-colors hover:text-primary"
                  >
                    {showFiltered ? (
                      <ChevronUp className="size-4 text-text-3" />
                    ) : (
                      <ChevronDown className="size-4 text-text-3" />
                    )}
                    Want to see filtered-out posts? ({overview.filteredOutCount})
                  </button>
                  {showFiltered && (
                    <div className="mt-4">
                      <FilteredPostsList reportId={reportId} />
                    </div>
                  )}
                </section>
              )}
            </>
          )}
        </>
      )}

      {currentStatus === 'FAILED' && (
        <ErrorBanner
          message={
            status?.failureReason || 'Analysis failed. Please try again or contact support.'
          }
        />
      )}

      <div className="flex flex-wrap gap-2">
        <Button asChild variant="secondary">
          <Link to="/reports">View all reports</Link>
        </Button>
        <Button asChild>
          <Link to="/brands">Back to brands</Link>
        </Button>
      </div>
    </div>
  )
}

export default ReportStatus
