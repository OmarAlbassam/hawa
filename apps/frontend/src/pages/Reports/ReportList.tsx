import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { getReports } from '../../services/reportService'
import type { Page } from '../../types/page'
import type { ReportResponse } from '../../types/report'
import type { ReportStatus } from '../../types/dashboard'
import Badge from '../../components/Badge/Badge'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import { useBrandSelection } from '../../context/useBrandSelection'
import { formatDate } from '../../utils/formatDate'
import { statusBadgeVariant } from '../../utils/reportStatus'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'
import { selectClass } from '@/lib/select-styles'

const ReportList = (): React.JSX.Element => {
  const { selectedBrand, selectedBrandId } = useBrandSelection()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<ReportResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [statusFilter, setStatusFilter] = useState<ReportStatus | ''>('')
  const [prevBrandId, setPrevBrandId] = useState(selectedBrandId)

  if (prevBrandId !== selectedBrandId) {
    setPrevBrandId(selectedBrandId)
    setPage(0)
  }

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(
        await getReports({
          brandId: selectedBrandId ?? undefined,
          status: statusFilter || undefined,
          page,
        }),
      )
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }, [page, statusFilter, selectedBrandId])

  useEffect(() => {
    loadData()
  }, [loadData])

  const handleStatusChange = (value: string) => {
    setStatusFilter(value as ReportStatus | '')
    setPage(0)
  }

  return (
    <div className="space-y-6">
      <div>
        <span className="eyebrow">Marketing · Reports</span>
        <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
          Reports{selectedBrand ? ` - ${selectedBrand.brandName}` : ''}
        </h1>
      </div>

      <div className="flex flex-wrap items-center gap-3">
        <select
          className={`${selectClass} sm:w-56`}
          value={statusFilter}
          onChange={(e) => handleStatusChange(e.target.value)}
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSING">Processing</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      {error && <ErrorBanner message={error} onRetry={loadData} />}

      {loading ? (
        <PageSkeleton />
      ) : data && data.content.length === 0 ? (
        <div className="rounded-md border border-dashed border-border bg-card px-6 py-12 text-center text-[13px] text-muted-foreground">
          {selectedBrand ? `No reports yet for ${selectedBrand.brandName}.` : 'No reports found.'}
        </div>
      ) : (
        data && (
          <div className="rounded-md border border-border bg-card">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>Brand</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Score</TableHead>
                  <TableHead>Date Range</TableHead>
                  <TableHead>Created</TableHead>
                  <TableHead>Finished</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.content.map((report) => (
                  <TableRow key={report.reportId}>
                    <TableCell>
                      <Link
                        to={`/reports/${report.reportId}`}
                        state={{ report }}
                        className="font-medium text-foreground hover:underline"
                      >
                        {report.brandName}
                      </Link>
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {report.dataSource === 'CSV_UPLOAD' ? 'CSV' : 'Reddit'}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusBadgeVariant[report.status]}>{report.status}</Badge>
                    </TableCell>
                    <TableCell className="font-mono tabular-nums text-foreground">
                      {report.score != null ? report.score.toFixed(1) : '-'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {report.dateFrom && report.dateTo
                        ? `${formatDate(report.dateFrom)} – ${formatDate(report.dateTo)}`
                        : '-'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(report.createdAt)}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {report.finishedAt ? formatDate(report.finishedAt) : '-'}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>

            <div className="flex items-center justify-between border-t border-border px-3 py-2.5">
              <span className="text-[12px] text-muted-foreground">
                {data.totalElements} report{data.totalElements !== 1 ? 's' : ''}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  disabled={data.number === 0}
                  onClick={() => setPage((p) => p - 1)}
                  aria-label="Previous page"
                >
                  <ChevronLeft />
                </Button>
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                  Page {data.number + 1} of {Math.max(data.totalPages, 1)}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  disabled={data.number + 1 >= data.totalPages}
                  onClick={() => setPage((p) => p + 1)}
                  aria-label="Next page"
                >
                  <ChevronRight />
                </Button>
              </div>
            </div>
          </div>
        )
      )}
    </div>
  )
}

export default ReportList
