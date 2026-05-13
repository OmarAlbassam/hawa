import { Badge as UIBadge } from '@/components/ui/badge'

interface BadgeProps {
  variant?: 'default' | 'primary' | 'success' | 'warning' | 'error' | 'info'
  children: React.ReactNode
}

const variantMap = {
  default: 'default',
  primary: 'accent',
  success: 'pos',
  warning: 'neu',
  error: 'neg',
  info: 'accent',
} as const

const Badge = ({ variant = 'default', children }: BadgeProps): React.JSX.Element => {
  return <UIBadge variant={variantMap[variant]}>{children}</UIBadge>
}

export default Badge
