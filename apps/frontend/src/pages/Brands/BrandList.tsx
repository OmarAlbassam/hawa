import { useState, useEffect, useCallback } from 'react'
import { Link } from 'react-router-dom'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { getBrands } from '../../services/brandService'
import type { Page } from '../../types/page'
import type { BrandSummaryResponse } from '../../types/brand'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import { useBrandSelection } from '../../context/useBrandSelection'
import { formatDate } from '../../utils/formatDate'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const BrandList = (): React.JSX.Element => {
  const { selectedBrandId, setSelectedBrandId } = useBrandSelection()
  const [page, setPage] = useState(0)
  const [data, setData] = useState<Page<BrandSummaryResponse> | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      setData(await getBrands(page))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }, [page])

  useEffect(() => {
    loadData()
  }, [loadData])

  return (
    <div className="space-y-6">
      <div>
        <span className="eyebrow">Marketing · Brands</span>
        <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
          Brands
        </h1>
      </div>

      {error && <ErrorBanner message={error} onRetry={loadData} />}

      {loading ? (
        <PageSkeleton />
      ) : data && data.content.length === 0 ? (
        <div className="rounded-md border border-dashed border-border bg-card px-6 py-12 text-center text-[13px] text-muted-foreground">
          No brands found.
        </div>
      ) : (
        data && (
          <>
            <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3">
              {data.content.map((brand) => {
                const active = brand.brandId === selectedBrandId
                return (
                  <Link
                    key={brand.brandId}
                    to={`/brands/${brand.brandId}`}
                    onClick={() => setSelectedBrandId(brand.brandId)}
                    aria-current={active ? 'true' : undefined}
                    className={cn(
                      'group flex flex-col gap-3 rounded-md border bg-card p-5 transition-colors',
                      active
                        ? 'border-primary ring-1 ring-primary/15'
                        : 'border-border hover:border-border-strong hover:bg-muted/30',
                    )}
                  >
                    <div className="flex items-start justify-between gap-3">
                      <h3 className="font-display text-[18px] font-semibold tracking-[-0.02em] text-foreground">
                        {brand.brandName}
                      </h3>
                      {brand.statusIndicator != null && (
                        <span className="font-display text-[24px] font-semibold tabular-nums tracking-[-0.025em] text-foreground">
                          {brand.statusIndicator.toFixed(1)}
                        </span>
                      )}
                    </div>
                    {brand.industry && (
                      <span className="text-[12px] text-muted-foreground">{brand.industry}</span>
                    )}
                    <div className="mt-auto flex items-center justify-between border-t border-border pt-3 font-mono text-[11px] uppercase tracking-[0.06em] text-text-3">
                      <span>
                        {brand.keywordCount} keyword{brand.keywordCount !== 1 ? 's' : ''}
                      </span>
                      <span>{formatDate(brand.createdAt)}</span>
                    </div>
                  </Link>
                )
              })}
            </div>

            <div className="flex items-center justify-between rounded-md border border-border bg-card px-3 py-2.5">
              <span className="text-[12px] text-muted-foreground">
                {data.totalElements} brand{data.totalElements !== 1 ? 's' : ''}
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
          </>
        )
      )}
    </div>
  )
}

export default BrandList
