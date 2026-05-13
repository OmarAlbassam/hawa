import { useNavigate } from 'react-router-dom'
import { ArrowLeft } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { HawaMark } from '@/components/brand/hawa-mark'

const NotFound = (): React.JSX.Element => {
  const navigate = useNavigate()

  return (
    <div className="flex min-h-[70vh] items-center justify-center px-4">
      <div className="w-full max-w-md text-center">
        <HawaMark className="mx-auto mb-8 size-10 text-text-3" />
        <p className="eyebrow">Error 404</p>
        <h1 className="mt-3 font-display text-[56px] font-semibold leading-none tracking-[-0.04em] text-foreground">
          Not found
        </h1>
        <p className="mt-4 text-[14px] text-muted-foreground">
          The page you're looking for doesn't exist or has been moved.
        </p>
        <div className="mt-8">
          <Button variant="secondary" onClick={() => navigate(-1)}>
            <ArrowLeft />
            Go back
          </Button>
        </div>
      </div>
    </div>
  )
}

export default NotFound
