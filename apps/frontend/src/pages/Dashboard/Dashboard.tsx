import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { FileBarChart, MessageSquare, Tag, Play, ChevronRight } from 'lucide-react'
import { getDashboard } from '../../services/dashboardService'
import type { DashboardResponse } from '../../types/dashboard'
import StatCard from '../../components/StatCard/StatCard'
import Badge from '../../components/Badge/Badge'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import StatusIndicator from '../../components/StatusIndicator/StatusIndicator'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import { useBrandSelection } from '../../context/useBrandSelection'
import { formatDate } from '../../utils/formatDate'
import { Button } from '@/components/ui/button'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const statusBadgeVariant: Record<
  string,
  'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'
> = {
  PENDING: 'default',
  PROCESSING: 'info',
  COMPLETED: 'success',
  FAILED: 'error',
}

const Dashboard = (): React.JSX.Element => {
  const [data, setData] = useState<DashboardResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getDashboard())
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    loadData()
  }, [loadData])

  const { selectedBrand } = useBrandSelection()

  if (loading) return <PageSkeleton />
  if (error) return <ErrorBanner message={error} onRetry={loadData} />
  if (!data) return <></>

  return (
    <div className="space-y-8">
      <div className="flex items-end justify-between gap-4">
        <div>
          <span className="eyebrow">Marketing · Dashboard</span>
          <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
            Dashboard
          </h1>
        </div>
        <Button asChild>
          <Link to="/analyze">
            <Play />
            Start Analysis
          </Link>
        </Button>
      </div>

      {selectedBrand && (
        <StatusIndicator
          source={{ kind: 'brand', brandId: selectedBrand.brandId }}
          title={`${selectedBrand.brandName} — Brand health`}
        />
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-3">
        <StatCard label="Total Brands" value={data.stats.totalBrands} icon={Tag} variant="info" />
        <StatCard label="Total Reports" value={data.stats.totalReports} icon={FileBarChart} />
        <StatCard
          label="Posts Analyzed"
          value={data.stats.totalPostsAnalyzed}
          icon={MessageSquare}
          variant="success"
        />
      </div>

      <div className="grid grid-cols-1 gap-4 lg:grid-cols-2">
        <section className="rounded-md border border-border bg-card">
          <header className="flex items-center justify-between border-b border-border px-6 py-4">
            <h2 className="font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
              Your Brands
            </h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/brands">
                View all
                <ChevronRight />
              </Link>
            </Button>
          </header>
          <div className="p-2">
            {data.brands.length === 0 ? (
              <p className="px-4 py-6 text-[13px] text-muted-foreground">No brands yet.</p>
            ) : (
              <ul className="divide-y divide-border">
                {data.brands.map((brand) => (
                  <li key={brand.brandId}>
                    <Link
                      to={`/brands/${brand.brandId}`}
                      className="flex items-center gap-3 rounded-sm px-4 py-3 transition-colors hover:bg-muted"
                    >
                      <div className="flex flex-1 flex-col">
                        <span className="text-[13px] font-medium text-foreground">
                          {brand.brandName}
                        </span>
                        {brand.industry && (
                          <span className="text-[12px] text-text-3">{brand.industry}</span>
                        )}
                      </div>
                      {brand.statusIndicator != null && (
                        <span className="font-display text-[18px] font-semibold tabular-nums tracking-[-0.02em] text-foreground">
                          {brand.statusIndicator.toFixed(1)}
                        </span>
                      )}
                      <ChevronRight className="size-3.5 text-text-3" />
                    </Link>
                  </li>
                ))}
              </ul>
            )}
          </div>
        </section>

        <section className="rounded-md border border-border bg-card">
          <header className="flex items-center justify-between border-b border-border px-6 py-4">
            <h2 className="font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
              Recent Reports
            </h2>
            <Button asChild variant="ghost" size="sm">
              <Link to="/reports">
                View all
                <ChevronRight />
              </Link>
            </Button>
          </header>
          {data.recentReports.length === 0 ? (
            <p className="px-6 py-6 text-[13px] text-muted-foreground">No reports yet.</p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>Brand</TableHead>
                  <TableHead>Source</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Score</TableHead>
                  <TableHead>Created</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {data.recentReports.map((report) => (
                  <TableRow key={report.reportId}>
                    <TableCell className="font-medium text-foreground">
                      {report.brandName}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {report.dataSource === 'CSV_UPLOAD' ? 'CSV' : 'Reddit'}
                    </TableCell>
                    <TableCell>
                      <Badge variant={statusBadgeVariant[report.status]}>{report.status}</Badge>
                    </TableCell>
                    <TableCell className="font-mono tabular-nums text-foreground">
                      {report.score != null ? report.score.toFixed(1) : '—'}
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {formatDate(report.createdAt)}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </section>
      </div>
    </div>
  )
}

export default Dashboard
