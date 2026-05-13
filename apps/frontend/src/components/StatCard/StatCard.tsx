import type { LucideIcon } from 'lucide-react'
import { cn } from '@/lib/utils'

interface StatCardProps {
  label: string
  value: string | number
  icon?: LucideIcon
  variant?: 'default' | 'success' | 'warning' | 'error' | 'info'
}

const valueColor = {
  default: 'text-foreground',
  success: 'text-pos',
  warning: 'text-neu',
  error: 'text-neg',
  info: 'text-primary',
} as const

const iconColor = {
  default: 'text-text-3',
  success: 'text-pos',
  warning: 'text-neu',
  error: 'text-neg',
  info: 'text-primary',
} as const

const StatCard = ({
  label,
  value,
  icon: Icon,
  variant = 'default',
}: StatCardProps): React.JSX.Element => {
  return (
    <div className="rounded-md border border-border bg-muted/40 p-5">
      <div className="mb-3 flex items-center justify-between">
        <span className="text-[12px] text-muted-foreground">{label}</span>
        {Icon && <Icon className={cn('size-4', iconColor[variant])} />}
      </div>
      <div
        className={cn(
          'font-display text-[32px] font-semibold leading-none tracking-[-0.03em] tabular-nums',
          valueColor[variant],
        )}
      >
        {value}
      </div>
    </div>
  )
}

export default StatCard
