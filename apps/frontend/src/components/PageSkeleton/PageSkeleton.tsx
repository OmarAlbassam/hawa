import { Skeleton } from '@/components/ui/skeleton'

const PageSkeleton = (): React.JSX.Element => {
  return (
    <div role="status" aria-busy="true" aria-label="Loading page" className="space-y-6">
      <Skeleton className="h-8 w-64" />
      <Skeleton className="h-4 w-96" />
      <div className="grid grid-cols-1 gap-4 md:grid-cols-3">
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
        <Skeleton className="h-28" />
      </div>
      <Skeleton className="h-72" />
    </div>
  )
}

export default PageSkeleton
