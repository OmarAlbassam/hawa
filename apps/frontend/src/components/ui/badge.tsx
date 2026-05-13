import * as React from 'react'
import { cva, type VariantProps } from 'class-variance-authority'
import { cn } from '@/lib/utils'

const badgeVariants = cva(
  'inline-flex items-center gap-1.5 h-[22px] px-2 rounded-sm text-[11px] font-medium tracking-[0.01em]',
  {
    variants: {
      variant: {
        default: 'bg-muted text-muted-foreground',
        pos: 'bg-pos-bg text-pos-text',
        neu: 'bg-neu-bg text-neu-text',
        neg: 'bg-neg-bg text-neg-text',
        accent: 'bg-[var(--accent-light)] text-primary',
        outline: 'border border-border text-foreground',
      },
    },
    defaultVariants: { variant: 'default' },
  },
)

export interface BadgeProps
  extends React.HTMLAttributes<HTMLSpanElement>,
    VariantProps<typeof badgeVariants> {
  dot?: boolean
}

function Badge({ className, variant, dot, children, ...props }: BadgeProps) {
  const dotColor =
    variant === 'pos' ? 'bg-pos'
    : variant === 'neu' ? 'bg-neu'
    : variant === 'neg' ? 'bg-neg'
    : variant === 'accent' ? 'bg-primary'
    : 'bg-text-3'
  return (
    <span className={cn(badgeVariants({ variant }), className)} {...props}>
      {dot && <span className={cn('h-1.5 w-1.5 rounded-full', dotColor)} />}
      {children}
    </span>
  )
}

export { Badge, badgeVariants }
