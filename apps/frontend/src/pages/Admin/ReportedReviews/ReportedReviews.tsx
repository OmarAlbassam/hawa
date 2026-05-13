import { useState, useEffect, useCallback } from 'react'
import { useAuth } from '../../../context/useAuth'
import { getReportedReviews } from '../../../services/adminService'
import type { Page, ReportedReviewResponse } from '../../../types/admin'
import DataTable from '../../../components/DataTable/DataTable'
import type { Column } from '../../../components/DataTable/DataTable'
import Badge from '../../../components/Badge/Badge'
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner'
import Modal from '../../../components/Modal/Modal'
import { formatDate } from '../../../utils/formatDate'

function truncate(text: string, max: number): string {
  return text.length > max ? text.slice(0, max) + '…' : text
}

const fmtScore = (value: number | null | undefined): string =>
  value == null ? '—' : Number(value).toFixed(1)

const toTitle = (key: string): string => key.charAt(0) + key.slice(1).toLowerCase()

const MetaRow = ({ k, v }: { k: string; v: React.ReactNode }) => (
  <div className="grid grid-cols-[100px_1fr] gap-3 py-1.5">
    <dt className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">{k}</dt>
    <dd className="text-[13px] text-foreground">{v}</dd>
  </div>
)

const ReportedReviews = (): React.JSX.Element => {
  const { accessToken } = useAuth()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<ReportedReviewResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [selected, setSelected] = useState<ReportedReviewResponse | null>(null)

  const fetchReviews = useCallback(async () => {
    if (!accessToken) return
    setLoading(true)
    setError('')
    try {
      const result = await getReportedReviews(accessToken, page)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load reviews')
    } finally {
      setLoading(false)
    }
  }, [accessToken, page])

  useEffect(() => {
    fetchReviews()
  }, [fetchReviews])

  const columns: Column<ReportedReviewResponse>[] = [
    {
      key: 'postText',
      header: 'Post',
      render: (row) => (
        <button
          type="button"
          onClick={() => setSelected(row)}
          title="View full post"
          className="block max-w-[360px] truncate text-left text-foreground transition-colors hover:text-primary hover:underline"
        >
          {row.post.postText || '(empty)'}
        </button>
      ),
    },
    { key: 'brandName', header: 'Brand' },
    { key: 'companyName', header: 'Company' },
    {
      key: 'reporter',
      header: 'Reporter',
      render: (row) => `${row.reporter.firstName} ${row.reporter.lastName}`,
    },
    {
      key: 'score',
      header: 'Score',
      render: (row) => (
        <span className="font-mono tabular-nums text-foreground">
          {row.review.score.toFixed(1)}
        </span>
      ),
    },
    {
      key: 'emotion',
      header: 'Emotion',
      render: (row) =>
        row.review.emotion ? (
          <Badge variant="info">{toTitle(row.review.emotion)}</Badge>
        ) : (
          '—'
        ),
    },
    {
      key: 'aspect',
      header: 'Aspect',
      render: (row) => <Badge variant="default">{toTitle(row.review.aspect)}</Badge>,
    },
    {
      key: 'brief',
      header: 'Feedback',
      render: (row) => (
        <span title={row.brief} className="text-muted-foreground">
          {truncate(row.brief, 60)}
        </span>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div>
        <span className="eyebrow">Admin · Reported Reviews</span>
        <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
          Reported Reviews
        </h1>
      </div>

      {error && <ErrorBanner message={error} onRetry={fetchReviews} />}

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        keyField="feedbackId"
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No reported reviews"
      />

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Post details"
        width="md"
      >
        {selected && (
          <div className="space-y-5">
            <dl className="rounded-md border border-border bg-muted/40 px-4 py-3">
              <MetaRow k="Sentiment" v={`${fmtScore(selected.review.score)} / 5`} />
              <MetaRow k="LLM Score" v={`${fmtScore(selected.review.llmScore)} / 5`} />
              <MetaRow
                k="Emotion"
                v={selected.review.emotion ? toTitle(selected.review.emotion) : '—'}
              />
              <MetaRow
                k="Aspect"
                v={selected.review.aspect ? toTitle(selected.review.aspect) : '—'}
              />
              <MetaRow k="Language" v={selected.post.language} />
              <MetaRow k="Collected" v={formatDate(selected.post.createdAt)} />
              {selected.post.postUrl && (
                <MetaRow
                  k="Source"
                  v={
                    <a
                      href={selected.post.postUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="break-all text-primary hover:underline"
                    >
                      {selected.post.postUrl}
                    </a>
                  }
                />
              )}
            </dl>

            <p className="whitespace-pre-wrap rounded-md border border-border bg-card p-4 text-[13px] leading-relaxed text-foreground">
              {selected.post.postText || '(empty)'}
            </p>

            <section className="rounded-md border border-border bg-muted/40 p-4">
              <h3 className="mb-3 eyebrow">Report</h3>
              <dl>
                <MetaRow k="Brand" v={selected.brandName} />
                <MetaRow k="Company" v={selected.companyName} />
                <MetaRow
                  k="Reporter"
                  v={`${selected.reporter.firstName} ${selected.reporter.lastName}`}
                />
                <MetaRow
                  k="Email"
                  v={
                    <a
                      href={`mailto:${selected.reporter.email}`}
                      className="text-primary hover:underline"
                    >
                      {selected.reporter.email}
                    </a>
                  }
                />
              </dl>
              <p className="mt-3 eyebrow">Reporter's feedback</p>
              <p className="mt-1 whitespace-pre-wrap text-[13px] leading-relaxed text-foreground">
                {selected.brief}
              </p>
            </section>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default ReportedReviews
