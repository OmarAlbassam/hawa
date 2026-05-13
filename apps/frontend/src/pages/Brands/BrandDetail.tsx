import { useState, useEffect, useCallback } from 'react'
import { useParams, Link } from 'react-router-dom'
import { ArrowLeft, Play, FileBarChart } from 'lucide-react'
import { getBrand } from '../../services/brandService'
import type { BrandDetailResponse } from '../../types/brand'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import StatusIndicator from '../../components/StatusIndicator/StatusIndicator'
import KeywordPanel from './KeywordPanel'
import { useBrandSelection } from '../../context/useBrandSelection'
import { formatDate } from '../../utils/formatDate'
import { Button } from '@/components/ui/button'

const BrandDetail = (): React.JSX.Element => {
  const { brandId } = useParams<{ brandId: string }>()
  const { setSelectedBrandId } = useBrandSelection()
  const [brand, setBrand] = useState<BrandDetailResponse | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)

  const urlBrandId = brandId ? Number(brandId) : null

  useEffect(() => {
    if (urlBrandId != null) setSelectedBrandId(urlBrandId)
  }, [urlBrandId, setSelectedBrandId])

  const loadData = useCallback(async () => {
    if (!brandId) return
    setLoading(true)
    setError(null)
    try {
      setBrand(await getBrand(Number(brandId)))
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoading(false)
    }
  }, [brandId])

  useEffect(() => {
    loadData()
  }, [loadData])

  if (loading) return <PageSkeleton />
  if (error) return <ErrorBanner message={error} onRetry={loadData} />
  if (!brand) return <></>

  return (
    <div className="space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
        <Link to="/brands">
          <ArrowLeft />
          Back to Brands
        </Link>
      </Button>

      <header className="flex flex-wrap items-end justify-between gap-6 border-b border-border pb-6">
        <div>
          <span className="eyebrow">Brand</span>
          <h1 className="mt-2 font-display text-[36px] font-semibold leading-none tracking-[-0.03em] text-foreground">
            {brand.brandName}
          </h1>
          {brand.industry && (
            <p className="mt-2 text-[14px] text-muted-foreground">{brand.industry}</p>
          )}
        </div>
        {brand.statusIndicator != null && (
          <div className="text-right">
            <div className="font-display text-[44px] font-semibold leading-none tabular-nums tracking-[-0.04em] text-foreground">
              {brand.statusIndicator.toFixed(1)}
            </div>
            <div className="mt-1 font-mono text-[11px] uppercase tracking-[0.12em] text-text-3">
              Health Score
            </div>
          </div>
        )}
      </header>

      <div className="flex flex-wrap gap-x-6 gap-y-1 font-mono text-[11px] uppercase tracking-[0.06em] text-text-3">
        <span>Created {formatDate(brand.createdAt)}</span>
        <span>Updated {formatDate(brand.updatedAt)}</span>
      </div>

      <StatusIndicator
        source={{ kind: 'brand', brandId: brand.brandId }}
        title="Brand health overview"
      />

      <section className="rounded-md border border-border bg-card p-6">
        <h2 className="mb-4 font-display text-[16px] font-medium tracking-[-0.01em] text-foreground">
          Keywords
        </h2>
        <KeywordPanel brandId={brand.brandId} />
      </section>

      <div className="flex flex-wrap gap-2">
        <Button asChild>
          <Link to="/analyze" state={{ preselectedBrandId: brand.brandId }}>
            <Play />
            Start Analysis
          </Link>
        </Button>
        <Button asChild variant="secondary">
          <Link to="/reports">
            <FileBarChart />
            View Reports
          </Link>
        </Button>
      </div>
    </div>
  )
}

export default BrandDetail
