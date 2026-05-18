import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useParams, useSearchParams } from 'react-router-dom'
import {
  AlertTriangle,
  ArrowLeft,
  Download,
  Loader2,
  ChevronLeft,
  ChevronRight,
} from 'lucide-react'
import { exportReportCsv, getReportPosts } from '../../services/reportService'
import InaccurateAnalysisDialog from '../../components/InaccurateAnalysisDialog/InaccurateAnalysisDialog'
import type {
  AspectEnum,
  EmotionEnum,
  IrrelevanceReason,
  LanguageEnum,
  PostListItemResponse,
  RelevanceStatus,
  ReportExportParams,
  ReportPostsParams,
} from '../../types/report'
import type { Page } from '../../types/page'
import Badge from '../../components/Badge/Badge'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import Modal from '../../components/Modal/Modal'
import { formatDate } from '../../utils/formatDate'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { selectClass as baseSelectClass } from '@/lib/select-styles'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const PAGE_SIZE = 20

const EMOTION_OPTIONS: EmotionEnum[] = [
  'JOY',
  'ANGER',
  'SADNESS',
  'FEAR',
  'SURPRISE',
  'DISGUST',
  'NEUTRAL',
]
const ASPECT_OPTIONS: AspectEnum[] = ['PRODUCT', 'SERVICE', 'DELIVERY', 'PRICING']
const LANGUAGE_OPTIONS: LanguageEnum[] = ['EN', 'AR']

const BASE_SORT_OPTIONS = [
  { value: 'createdAt,desc', label: 'Newest first' },
  { value: 'createdAt,asc', label: 'Oldest first' },
]
const RELEVANT_SORT_OPTIONS = [
  { value: 'score,desc', label: 'Highest sentiment' },
  { value: 'score,asc', label: 'Lowest sentiment' },
]
const DEFAULT_SORT = 'createdAt,desc'

const isSortAllowed = (sort: string, isIrrelevant: boolean): boolean => {
  if (!isIrrelevant) return true
  return BASE_SORT_OPTIONS.some((opt) => opt.value === sort)
}

const REASON_LABEL: Record<IrrelevanceReason, string> = {
  HOMONYM: 'Homonym',
  SPAM: 'Spam',
  EMPTY: 'Empty',
  WRONG_LANGUAGE: 'Wrong language',
  OTHER: 'Other',
}

const EMOTION_BADGE_VARIANT: Record<
  EmotionEnum,
  'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'
> = {
  JOY: 'success',
  ANGER: 'error',
  SADNESS: 'info',
  FEAR: 'warning',
  SURPRISE: 'primary',
  DISGUST: 'warning',
  NEUTRAL: 'default',
}

const selectClass = `${baseSelectClass} w-full`

const toTitle = (key: string): string => key.charAt(0) + key.slice(1).toLowerCase()
const fmtScore = (value: number | null | undefined): string =>
  value == null ? '-' : Number(value).toFixed(1)

interface Filters {
  relevance: RelevanceStatus
  sentimentMin: string
  sentimentMax: string
  emotion: EmotionEnum | ''
  aspect: AspectEnum | ''
  language: LanguageEnum | ''
  dateFrom: string
  dateTo: string
}

const EMPTY_FILTERS: Filters = {
  relevance: 'RELEVANT',
  sentimentMin: '',
  sentimentMax: '',
  emotion: '',
  aspect: '',
  language: '',
  dateFrom: '',
  dateTo: '',
}

const filtersFromSearch = (search: URLSearchParams): Filters => ({
  relevance: (search.get('relevance') as RelevanceStatus) || 'RELEVANT',
  sentimentMin: search.get('sentimentMin') ?? '',
  sentimentMax: search.get('sentimentMax') ?? '',
  emotion: (search.get('emotion') as EmotionEnum) || '',
  aspect: (search.get('aspect') as AspectEnum) || '',
  language: (search.get('language') as LanguageEnum) || '',
  dateFrom: search.get('dateFrom') ?? '',
  dateTo: search.get('dateTo') ?? '',
})

const parseNumber = (value: string): number | undefined => {
  if (value === '') return undefined
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : undefined
}

const filtersToQuery = (filters: Filters, page: number, sort: string): ReportPostsParams => ({
  relevance: filters.relevance,
  sentimentMin: parseNumber(filters.sentimentMin),
  sentimentMax: parseNumber(filters.sentimentMax),
  emotion: filters.emotion || undefined,
  aspect: filters.aspect || undefined,
  language: filters.language || undefined,
  dateFrom: filters.dateFrom || undefined,
  dateTo: filters.dateTo || undefined,
  page,
  size: PAGE_SIZE,
  sort,
})

const filtersToExportQuery = (filters: Filters): ReportExportParams => ({
  sentimentMin: parseNumber(filters.sentimentMin),
  sentimentMax: parseNumber(filters.sentimentMax),
  emotion: filters.emotion || undefined,
  aspect: filters.aspect || undefined,
  language: filters.language || undefined,
  dateFrom: filters.dateFrom || undefined,
  dateTo: filters.dateTo || undefined,
})

const triggerBlobDownload = (blob: Blob, filename: string): void => {
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  document.body.appendChild(link)
  link.click()
  document.body.removeChild(link)
  URL.revokeObjectURL(url)
}

const MetaRow = ({ k, v }: { k: string; v: React.ReactNode }) => (
  <div className="grid grid-cols-[100px_1fr] gap-3 py-1.5">
    <dt className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">{k}</dt>
    <dd className="text-[13px] text-foreground">{v}</dd>
  </div>
)

const PostsList = (): React.JSX.Element => {
  const { reportId: reportIdParam } = useParams<{ reportId: string }>()
  const reportId = reportIdParam ? Number(reportIdParam) : NaN
  const [searchParams, setSearchParams] = useSearchParams()

  const appliedFilters = useMemo(() => filtersFromSearch(searchParams), [searchParams])
  const pageNumber = Number(searchParams.get('page') ?? '0')
  const rawSort = searchParams.get('sort') ?? DEFAULT_SORT
  const sort = isSortAllowed(rawSort, appliedFilters.relevance === 'IRRELEVANT')
    ? rawSort
    : DEFAULT_SORT

  const [draft, setDraft] = useState<Filters>(appliedFilters)
  const [data, setData] = useState<Page<PostListItemResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<PostListItemResponse | null>(null)
  const [feedbackOpen, setFeedbackOpen] = useState(false)
  const [exporting, setExporting] = useState(false)
  const [exportError, setExportError] = useState<string | null>(null)

  useEffect(() => {
    setDraft(appliedFilters)
  }, [appliedFilters])

  const load = useCallback(async () => {
    if (Number.isNaN(reportId)) return
    setLoading(true)
    setError(null)
    try {
      const result = await getReportPosts(
        reportId,
        filtersToQuery(appliedFilters, pageNumber, sort),
      )
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load posts')
    } finally {
      setLoading(false)
    }
  }, [reportId, appliedFilters, pageNumber, sort])

  useEffect(() => {
    void load()
  }, [load])

  const writeSearch = (
    next: Partial<{ filters: Filters; page: number; sort: string }>,
  ) => {
    const nextParams = new URLSearchParams(searchParams)
    if (next.filters) {
      const f = next.filters
      const writeOrDelete = (key: string, value: string) => {
        if (value) nextParams.set(key, value)
        else nextParams.delete(key)
      }
      writeOrDelete('relevance', f.relevance)
      writeOrDelete('sentimentMin', f.sentimentMin)
      writeOrDelete('sentimentMax', f.sentimentMax)
      writeOrDelete('emotion', f.emotion)
      writeOrDelete('aspect', f.aspect)
      writeOrDelete('language', f.language)
      writeOrDelete('dateFrom', f.dateFrom)
      writeOrDelete('dateTo', f.dateTo)
    }
    if (next.page != null) nextParams.set('page', String(next.page))
    if (next.sort) nextParams.set('sort', next.sort)
    setSearchParams(nextParams, { replace: false })
  }

  const applyFilters = (e?: React.FormEvent) => {
    e?.preventDefault()
    const draftIsIrrelevant = draft.relevance === 'IRRELEVANT'
    const nextSort = isSortAllowed(sort, draftIsIrrelevant) ? undefined : DEFAULT_SORT
    writeSearch({ filters: draft, page: 0, sort: nextSort })
  }

  const clearFilters = () => {
    setDraft(EMPTY_FILTERS)
    writeSearch({ filters: EMPTY_FILTERS, page: 0 })
  }

  const goToPage = (next: number) => writeSearch({ page: next })
  const changeSort = (value: string) => writeSearch({ sort: value, page: 0 })

  const handleExport = async () => {
    if (Number.isNaN(reportId) || exporting) return
    setExporting(true)
    setExportError(null)
    try {
      const { blob, filename } = await exportReportCsv(
        reportId,
        filtersToExportQuery(appliedFilters),
      )
      triggerBlobDownload(blob, filename)
    } catch (err) {
      setExportError(err instanceof Error ? err.message : 'Failed to export CSV')
    } finally {
      setExporting(false)
    }
  }

  if (Number.isNaN(reportId)) return <ErrorBanner message="Invalid report id" />

  const isIrrelevant = appliedFilters.relevance === 'IRRELEVANT'
  const sortOptions = isIrrelevant
    ? BASE_SORT_OPTIONS
    : [...BASE_SORT_OPTIONS, ...RELEVANT_SORT_OPTIONS]

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
        <Link to={`/reports/${reportId}`}>
          <ArrowLeft />
          Back to report
        </Link>
      </Button>

      <header>
        <span className="eyebrow">Report posts</span>
        <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
          Analyzed posts
        </h1>
        {data && (
          <p className="mt-2 text-[13px] text-muted-foreground">
            {data.totalElements} {data.totalElements === 1 ? 'post' : 'posts'} match the
            current filters
          </p>
        )}
      </header>

      <form
        onSubmit={applyFilters}
        className="space-y-4 rounded-md border border-border bg-card p-5"
      >
        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          <div className="space-y-1.5">
            <Label>Relevance</Label>
            <select
              className={selectClass}
              value={draft.relevance}
              onChange={(e) =>
                setDraft({ ...draft, relevance: e.target.value as RelevanceStatus })
              }
            >
              <option value="RELEVANT">Relevant</option>
              <option value="IRRELEVANT">Filtered out</option>
            </select>
          </div>

          <div className="space-y-1.5">
            <Label>Language</Label>
            <select
              className={selectClass}
              value={draft.language}
              onChange={(e) =>
                setDraft({ ...draft, language: e.target.value as LanguageEnum | '' })
              }
            >
              <option value="">Any</option>
              {LANGUAGE_OPTIONS.map((lang) => (
                <option key={lang} value={lang}>
                  {lang}
                </option>
              ))}
            </select>
          </div>

          <div className="space-y-1.5">
            <Label>Date from</Label>
            <Input
              type="date"
              value={draft.dateFrom}
              onChange={(e) => setDraft({ ...draft, dateFrom: e.target.value })}
            />
          </div>

          <div className="space-y-1.5">
            <Label>Date to</Label>
            <Input
              type="date"
              value={draft.dateTo}
              onChange={(e) => setDraft({ ...draft, dateTo: e.target.value })}
            />
          </div>

          {!isIrrelevant && (
            <>
              <div className="space-y-1.5">
                <Label>Emotion</Label>
                <select
                  className={selectClass}
                  value={draft.emotion}
                  onChange={(e) =>
                    setDraft({ ...draft, emotion: e.target.value as EmotionEnum | '' })
                  }
                >
                  <option value="">Any</option>
                  {EMOTION_OPTIONS.map((e) => (
                    <option key={e} value={e}>
                      {toTitle(e)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5">
                <Label>Aspect</Label>
                <select
                  className={selectClass}
                  value={draft.aspect}
                  onChange={(e) =>
                    setDraft({ ...draft, aspect: e.target.value as AspectEnum | '' })
                  }
                >
                  <option value="">Any</option>
                  {ASPECT_OPTIONS.map((a) => (
                    <option key={a} value={a}>
                      {toTitle(a)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-1.5 sm:col-span-2">
                <Label>Sentiment</Label>
                <div className="flex items-center gap-2">
                  <Input
                    type="number"
                    min="0"
                    max="5"
                    step="0.5"
                    placeholder="min"
                    value={draft.sentimentMin}
                    onChange={(e) => setDraft({ ...draft, sentimentMin: e.target.value })}
                  />
                  <span className="text-text-3">–</span>
                  <Input
                    type="number"
                    min="0"
                    max="5"
                    step="0.5"
                    placeholder="max"
                    value={draft.sentimentMax}
                    onChange={(e) => setDraft({ ...draft, sentimentMax: e.target.value })}
                  />
                </div>
              </div>
            </>
          )}
        </div>

        <div className="flex flex-wrap items-center gap-2 border-t border-border pt-4">
          <Button type="submit">Apply filters</Button>
          <Button type="button" variant="secondary" onClick={clearFilters}>
            Clear
          </Button>
          <Button
            type="button"
            variant="secondary"
            onClick={handleExport}
            disabled={exporting}
            aria-busy={exporting}
            title="Download all matching posts as CSV"
          >
            {exporting ? <Loader2 className="size-4 animate-spin" /> : <Download />}
            {exporting ? 'Exporting…' : 'Export CSV'}
          </Button>

          <div className="ml-auto flex items-center gap-2">
            <Label htmlFor="posts-list-sort-select" className="text-text-3">
              Sort
            </Label>
            <select
              id="posts-list-sort-select"
              value={sort}
              onChange={(e) => changeSort(e.target.value)}
              className={`${selectClass} w-auto`}
            >
              {sortOptions.map((opt) => (
                <option key={opt.value} value={opt.value}>
                  {opt.label}
                </option>
              ))}
            </select>
          </div>
        </div>
      </form>

      {error && <ErrorBanner message={error} onRetry={load} />}
      {exportError && <ErrorBanner message={exportError} onRetry={handleExport} />}

      {loading && !data && (
        <p className="text-[13px] text-muted-foreground">Loading posts…</p>
      )}

      {data && data.content.length === 0 && !loading && (
        <div className="rounded-md border border-dashed border-border bg-card px-6 py-12 text-center text-[13px] text-muted-foreground">
          No posts match the current filters.
        </div>
      )}

      {data && data.content.length > 0 && (
        <div className="rounded-md border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow className="hover:bg-transparent">
                <TableHead>Post</TableHead>
                {isIrrelevant ? (
                  <>
                    <TableHead>Reason</TableHead>
                    <TableHead>Language</TableHead>
                    <TableHead>Source</TableHead>
                    <TableHead>Collected</TableHead>
                  </>
                ) : (
                  <>
                    <TableHead>Score</TableHead>
                    <TableHead>Emotion</TableHead>
                    <TableHead>Aspect</TableHead>
                    <TableHead>Language</TableHead>
                    <TableHead>Collected</TableHead>
                  </>
                )}
              </TableRow>
            </TableHeader>
            <TableBody>
              {data.content.map((post) => (
                <TableRow key={post.postId}>
                  <TableCell>
                    <button
                      type="button"
                      onClick={() => setSelected(post)}
                      title="View full post"
                      className="block max-w-[440px] truncate text-left text-foreground transition-colors hover:text-primary hover:underline"
                    >
                      {post.postText || '(empty)'}
                    </button>
                  </TableCell>
                  {isIrrelevant ? (
                    <>
                      <TableCell>
                        <span className="font-mono text-[11px] uppercase tracking-[0.06em] text-text-3">
                          {post.irrelevanceReason
                            ? REASON_LABEL[post.irrelevanceReason]
                            : '-'}
                        </span>
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {post.language}
                      </TableCell>
                      <TableCell>
                        {post.postUrl ? (
                          <a
                            href={post.postUrl}
                            target="_blank"
                            rel="noreferrer"
                            className="text-primary hover:underline"
                          >
                            View
                          </a>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDate(post.createdAt)}
                      </TableCell>
                    </>
                  ) : (
                    <>
                      <TableCell className="font-mono tabular-nums text-foreground">
                        {fmtScore(post.score)}
                      </TableCell>
                      <TableCell>
                        {post.emotion ? (
                          <Badge variant={EMOTION_BADGE_VARIANT[post.emotion]}>
                            {toTitle(post.emotion)}
                          </Badge>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell>
                        {post.aspect ? (
                          <Badge variant="default">{toTitle(post.aspect)}</Badge>
                        ) : (
                          '-'
                        )}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {post.language}
                      </TableCell>
                      <TableCell className="text-muted-foreground">
                        {formatDate(post.createdAt)}
                      </TableCell>
                    </>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>

          {data.totalPages > 1 && (
            <div className="flex items-center justify-between border-t border-border px-3 py-2.5">
              <span className="text-[12px] text-muted-foreground">
                {data.totalElements} post{data.totalElements !== 1 ? 's' : ''}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => goToPage(pageNumber - 1)}
                  disabled={pageNumber === 0 || loading}
                  aria-label="Previous page"
                >
                  <ChevronLeft />
                </Button>
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                  Page {pageNumber + 1} of {data.totalPages}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => goToPage(pageNumber + 1)}
                  disabled={pageNumber + 1 >= data.totalPages || loading}
                  aria-label="Next page"
                >
                  <ChevronRight />
                </Button>
              </div>
            </div>
          )}
        </div>
      )}

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Post details"
        width="md"
      >
        {selected && (
          <div className="space-y-4">
            <dl className="rounded-md border border-border bg-muted/40 px-4 py-3">
              {selected.relevanceStatus === 'RELEVANT' ? (
                <>
                  <MetaRow k="Sentiment" v={`${fmtScore(selected.score)} / 5`} />
                  <MetaRow
                    k="Emotion"
                    v={selected.emotion ? toTitle(selected.emotion) : '-'}
                  />
                  <MetaRow
                    k="Aspect"
                    v={selected.aspect ? toTitle(selected.aspect) : '-'}
                  />
                </>
              ) : (
                <MetaRow
                  k="Reason"
                  v={
                    selected.irrelevanceReason
                      ? REASON_LABEL[selected.irrelevanceReason]
                      : '-'
                  }
                />
              )}
              <MetaRow k="Language" v={selected.language} />
              <MetaRow k="Collected" v={formatDate(selected.createdAt)} />
              {selected.postUrl && (
                <MetaRow
                  k="Source"
                  v={
                    <a
                      href={selected.postUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="break-all text-primary hover:underline"
                    >
                      {selected.postUrl}
                    </a>
                  }
                />
              )}
            </dl>
            <p className="whitespace-pre-wrap rounded-md border border-border bg-card p-4 text-[13px] leading-relaxed text-foreground">
              {selected.postText || '(empty)'}
            </p>
            <div className="flex justify-end">
              <Button
                variant="destructive"
                onClick={() => setFeedbackOpen(true)}
                disabled={selected.reviewId == null}
                title={
                  selected.reviewId == null
                    ? 'Feedback is only available for analyzed posts'
                    : 'Report that this analysis is inaccurate'
                }
              >
                <AlertTriangle />
                Inaccurate Analysis
              </Button>
            </div>
          </div>
        )}
      </Modal>

      <InaccurateAnalysisDialog
        open={feedbackOpen}
        reviewId={selected?.reviewId ?? null}
        onClose={() => setFeedbackOpen(false)}
      />
    </div>
  )
}

export default PostsList
