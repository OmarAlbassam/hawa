import { useCallback, useEffect, useState } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { getReportPosts } from '../../services/reportService'
import type { IrrelevanceReason, PostListItemResponse } from '../../types/report'
import type { Page } from '../../types/page'
import Modal from '../../components/Modal/Modal'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const REASON_LABEL: Record<IrrelevanceReason, string> = {
  HOMONYM: 'Homonym',
  SPAM: 'Spam',
  EMPTY: 'Empty',
  WRONG_LANGUAGE: 'Wrong language',
  OTHER: 'Other',
}

const PAGE_SIZE = 10

interface Props {
  reportId: number
}

const FilteredPostsList = ({ reportId }: Props): React.JSX.Element => {
  const [page, setPage] = useState<Page<PostListItemResponse> | null>(null)
  const [pageNumber, setPageNumber] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [selected, setSelected] = useState<PostListItemResponse | null>(null)

  const load = useCallback(
    async (targetPage: number) => {
      setLoading(true)
      setError(null)
      try {
        const result = await getReportPosts(reportId, {
          relevance: 'IRRELEVANT',
          page: targetPage,
          size: PAGE_SIZE,
        })
        setPage(result)
        setPageNumber(targetPage)
      } catch (err) {
        setError(err instanceof Error ? err.message : 'Failed to load filtered posts')
      } finally {
        setLoading(false)
      }
    },
    [reportId],
  )

  useEffect(() => {
    void load(0)
  }, [load])

  if (loading && !page) {
    return <p className="text-[13px] text-muted-foreground">Loading filtered posts…</p>
  }

  if (error) {
    return (
      <div className="space-y-2 rounded-md border border-neg/30 bg-neg-bg p-3 text-[13px] text-neg-text">
        <p>{error}</p>
        <Button variant="ghost" size="sm" onClick={() => void load(pageNumber)}>
          Retry
        </Button>
      </div>
    )
  }

  if (!page || page.content.length === 0) {
    return <p className="text-[13px] text-muted-foreground">No filtered posts to show.</p>
  }

  return (
    <div className="space-y-3">
      <p className="text-[13px] text-muted-foreground">
        These posts were classified as not about the brand, so they are not counted in the
        sentiment score or distributions.
      </p>
      <div className="rounded-md border border-border bg-card">
        <Table>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              <TableHead>Post</TableHead>
              <TableHead>Reason</TableHead>
              <TableHead>Language</TableHead>
              <TableHead>Source</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {page.content.map((post) => (
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
                <TableCell>
                  <span className="font-mono text-[11px] uppercase tracking-[0.06em] text-text-3">
                    {post.irrelevanceReason ? REASON_LABEL[post.irrelevanceReason] : '-'}
                  </span>
                </TableCell>
                <TableCell className="text-muted-foreground">{post.language}</TableCell>
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
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {page.totalPages > 1 && (
          <div className="flex items-center justify-between border-t border-border px-3 py-2.5">
            <span className="text-[12px] text-muted-foreground">
              {page.totalElements} filtered post{page.totalElements !== 1 ? 's' : ''}
            </span>
            <div className="flex items-center gap-2">
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={() => void load(pageNumber - 1)}
                disabled={pageNumber === 0 || loading}
                aria-label="Previous page"
              >
                <ChevronLeft />
              </Button>
              <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                Page {pageNumber + 1} of {page.totalPages}
              </span>
              <Button
                variant="ghost"
                size="icon"
                className="h-7 w-7"
                onClick={() => void load(pageNumber + 1)}
                disabled={pageNumber + 1 >= page.totalPages || loading}
                aria-label="Next page"
              >
                <ChevronRight />
              </Button>
            </div>
          </div>
        )}
      </div>

      <Modal
        open={selected !== null}
        onClose={() => setSelected(null)}
        title="Filtered post"
        width="md"
      >
        {selected && (
          <div className="space-y-4">
            <dl className="rounded-md border border-border bg-muted/40 px-4 py-3">
              <div className="grid grid-cols-[100px_1fr] gap-3 py-1.5">
                <dt className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                  Reason
                </dt>
                <dd className="text-[13px] text-foreground">
                  {selected.irrelevanceReason ? REASON_LABEL[selected.irrelevanceReason] : '-'}
                </dd>
              </div>
              <div className="grid grid-cols-[100px_1fr] gap-3 py-1.5">
                <dt className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                  Language
                </dt>
                <dd className="text-[13px] text-foreground">{selected.language}</dd>
              </div>
              {selected.postUrl && (
                <div className="grid grid-cols-[100px_1fr] gap-3 py-1.5">
                  <dt className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                    Source
                  </dt>
                  <dd className="break-all text-[13px]">
                    <a
                      href={selected.postUrl}
                      target="_blank"
                      rel="noreferrer"
                      className="text-primary hover:underline"
                    >
                      {selected.postUrl}
                    </a>
                  </dd>
                </div>
              )}
            </dl>
            <p className="whitespace-pre-wrap rounded-md border border-border bg-card p-4 text-[13px] leading-relaxed text-foreground">
              {selected.postText || '(empty)'}
            </p>
          </div>
        )}
      </Modal>
    </div>
  )
}

export default FilteredPostsList
