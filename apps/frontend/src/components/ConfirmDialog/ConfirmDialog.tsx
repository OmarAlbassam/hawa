import { Loader2 } from 'lucide-react'
import Modal from '../Modal/Modal'
import { Button } from '@/components/ui/button'

interface ConfirmDialogProps {
  open: boolean
  onClose: () => void
  onConfirm: () => void
  title: string
  message: string
  confirmLabel?: string
  variant?: 'primary' | 'destructive'
  loading?: boolean
}

const ConfirmDialog = ({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = 'Confirm',
  variant = 'primary',
  loading = false,
}: ConfirmDialogProps): React.JSX.Element => {
  return (
    <Modal open={open} onClose={onClose} title={title} width="sm">
      <p className="text-[13px] text-muted-foreground">{message}</p>
      <div className="mt-6 flex items-center justify-end gap-2">
        <Button variant="secondary" onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          variant={variant === 'destructive' ? 'destructive' : 'default'}
          onClick={onConfirm}
          disabled={loading}
        >
          {loading && <Loader2 className="size-4 animate-spin" />}
          {loading ? 'Working…' : confirmLabel}
        </Button>
      </div>
    </Modal>
  )
}

export default ConfirmDialog
