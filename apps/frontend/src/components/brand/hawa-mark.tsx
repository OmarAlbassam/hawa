import { cn } from '@/lib/utils'

interface HawaMarkProps extends React.SVGAttributes<SVGSVGElement> {
  className?: string
}

export function HawaMark({ className, ...props }: HawaMarkProps) {
  return (
    <svg
      viewBox="0 0 32 32"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      aria-hidden="true"
      className={cn('block text-foreground', className)}
      {...props}
    >
      <path
        d="M2 16 C 8 16, 8 8, 14 8 C 20 8, 20 24, 26 24 C 28.5 24, 29.5 22, 30 20"
        stroke="currentColor"
        strokeWidth="2.4"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  )
}

interface HawaLogoProps {
  className?: string
  markClassName?: string
  wordmarkClassName?: string
  showWordmark?: boolean
}

export function HawaLogo({
  className,
  markClassName,
  wordmarkClassName,
  showWordmark = true,
}: HawaLogoProps) {
  return (
    <div className={cn('flex items-center gap-2', className)}>
      <HawaMark className={cn('h-6 w-6', markClassName)} />
      {showWordmark && (
        <span
          className={cn(
            'font-display text-[18px] font-semibold tracking-[-0.04em] text-foreground',
            wordmarkClassName,
          )}
        >
          hawa
        </span>
      )}
    </div>
  )
}
