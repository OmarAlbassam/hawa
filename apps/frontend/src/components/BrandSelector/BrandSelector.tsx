import { matchPath, useLocation, useNavigate } from 'react-router-dom'
import { Check, ChevronDown, Tag } from 'lucide-react'
import { useBrandSelection } from '../../context/useBrandSelection'
import { Button } from '@/components/ui/button'
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu'

const BrandSelector = (): React.JSX.Element | null => {
  const { brands, selectedBrand, setSelectedBrandId, loading } = useBrandSelection()
  const navigate = useNavigate()
  const location = useLocation()

  if (loading && brands.length === 0) {
    return (
      <div className="flex h-9 items-center gap-2 rounded-md border border-border bg-card px-3 text-[13px] text-muted-foreground">
        <Tag className="size-3.5 text-text-3" />
        <span>Loading brands…</span>
      </div>
    )
  }

  if (brands.length === 0) return null

  if (brands.length === 1) {
    return (
      <div className="flex h-9 items-center gap-2 rounded-md border border-border bg-card px-3 text-[13px] text-foreground">
        <Tag className="size-3.5 text-text-3" />
        <span>{brands[0].brandName}</span>
      </div>
    )
  }

  const handleSelect = (brandId: number) => {
    setSelectedBrandId(brandId)
    if (matchPath('/brands/:brandId', location.pathname)) {
      navigate(`/brands/${brandId}`, { replace: true })
    }
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="secondary" className="gap-2 pr-5">
          <Tag className="size-3.5 text-text-3" />
          <span>{selectedBrand?.brandName ?? 'Select a brand'}</span>
          <ChevronDown className="size-3.5 text-text-3" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="min-w-[240px]">
        <DropdownMenuLabel>Brands</DropdownMenuLabel>
        <DropdownMenuSeparator />
        {brands.map((brand) => {
          const active = brand.brandId === selectedBrand?.brandId
          return (
            <DropdownMenuItem
              key={brand.brandId}
              onClick={() => handleSelect(brand.brandId)}
              className="flex items-center justify-between"
            >
              <div className="flex flex-col">
                <span className={active ? 'font-medium text-foreground' : 'text-foreground'}>
                  {brand.brandName}
                </span>
                {brand.industry && (
                  <span className="text-[11px] text-text-3">{brand.industry}</span>
                )}
              </div>
              {active && <Check className="size-4 text-primary" />}
            </DropdownMenuItem>
          )
        })}
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

export default BrandSelector
