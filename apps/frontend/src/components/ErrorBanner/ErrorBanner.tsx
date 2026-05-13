import { AlertCircle } from 'lucide-react'
import { Button } from '@/components/ui/button'

interface ErrorBannerProps {
  message: string
  onRetry?: () => void
}

const ErrorBanner = ({ message, onRetry }: ErrorBannerProps): React.JSX.Element => (
  <div
    role="alert"
    className="flex items-start gap-3 rounded-md border border-neg/30 bg-neg-bg px-4 py-3"
  >
    <AlertCircle className="mt-0.5 size-4 shrink-0 text-neg" />
    <p className="flex-1 text-[13px] text-neg-text">{message}</p>
    {onRetry && (
      <Button variant="ghost" size="sm" onClick={onRetry} className="text-neg-text hover:bg-neg/10">
        Retry
      </Button>
    )}
  </div>
)

export default ErrorBanner
