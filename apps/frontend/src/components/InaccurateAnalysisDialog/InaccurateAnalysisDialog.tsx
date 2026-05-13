import { useEffect, useState } from 'react'
import { CheckCircle2, Loader2 } from 'lucide-react'
import Modal from '../Modal/Modal'
import { submitFeedback } from '../../services/feedbackService'
import { FEEDBACK_BRIEF_MAX, FEEDBACK_BRIEF_MIN } from '../../types/feedback'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'

interface InaccurateAnalysisDialogProps {
  open: boolean
  reviewId: number | null
  onClose: () => void
}

const EMPTY_MESSAGE = 'Please enter a brief explanation.'
const TOO_SHORT_MESSAGE = `Please write at least ${FEEDBACK_BRIEF_MIN} characters.`
const TOO_LONG_MESSAGE = `Please keep it under ${FEEDBACK_BRIEF_MAX} characters.`

const InaccurateAnalysisDialog = ({
  open,
  reviewId,
  onClose,
}: InaccurateAnalysisDialogProps): React.JSX.Element => {
  const [brief, setBrief] = useState('')
  const [validationError, setValidationError] = useState<string | null>(null)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [success, setSuccess] = useState(false)

  useEffect(() => {
    if (open) {
      setBrief('')
      setValidationError(null)
      setSubmitError(null)
      setSubmitting(false)
      setSuccess(false)
    }
  }, [open])

  const validate = (value: string): string | null => {
    const trimmed = value.trim()
    if (trimmed.length === 0) return EMPTY_MESSAGE
    if (trimmed.length < FEEDBACK_BRIEF_MIN) return TOO_SHORT_MESSAGE
    if (trimmed.length > FEEDBACK_BRIEF_MAX) return TOO_LONG_MESSAGE
    return null
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (reviewId == null || submitting) return

    const error = validate(brief)
    if (error) {
      setValidationError(error)
      return
    }
    setValidationError(null)
    setSubmitError(null)
    setSubmitting(true)
    try {
      await submitFeedback(reviewId, { brief: brief.trim() })
      setSuccess(true)
    } catch (err) {
      setSubmitError(
        err instanceof Error ? err.message : 'Could not submit feedback. Try again later.',
      )
    } finally {
      setSubmitting(false)
    }
  }

  const remaining = FEEDBACK_BRIEF_MAX - brief.length
  const counterClass =
    remaining < 0
      ? 'text-neg-text'
      : remaining < 50
        ? 'text-neu-text'
        : 'text-text-3'

  return (
    <Modal open={open} onClose={onClose} title="Report inaccurate analysis" width="sm">
      {success ? (
        <div role="status" className="flex flex-col items-center gap-3 py-4 text-center">
          <CheckCircle2 className="size-9 text-pos" aria-hidden />
          <p className="font-display text-[16px] font-medium tracking-[-0.01em]">
            Thanks for the feedback
          </p>
          <p className="text-[13px] text-muted-foreground">
            Your note has been recorded and will help improve the model.
          </p>
          <div className="mt-2">
            <Button onClick={onClose}>Close</Button>
          </div>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <p className="text-[13px] text-muted-foreground">
            Briefly describe what the system got wrong (sentiment, emotion, or aspect). This is
            reviewed to improve future analyses.
          </p>

          <div className="space-y-1.5">
            <Label htmlFor="feedback-brief">Explanation</Label>
            <Textarea
              id="feedback-brief"
              value={brief}
              onChange={(e) => {
                setBrief(e.target.value)
                if (validationError) setValidationError(null)
              }}
              rows={5}
              maxLength={FEEDBACK_BRIEF_MAX + 50}
              placeholder="e.g. The post is sarcastic but was scored as positive."
              disabled={submitting}
              aria-invalid={validationError != null}
              aria-describedby="feedback-brief-help"
              className={cn(validationError && 'border-neg focus-visible:border-neg focus-visible:ring-neg/15')}
            />
            <div id="feedback-brief-help" className="flex justify-end">
              <span className={cn('font-mono text-[11px] tracking-[0.04em]', counterClass)}>
                {remaining < 0
                  ? `${Math.abs(remaining)} over limit`
                  : `${remaining} characters left`}
              </span>
            </div>
          </div>

          {validationError && (
            <p role="alert" className="text-[12px] text-neg-text">
              {validationError}
            </p>
          )}
          {submitError && (
            <p role="alert" className="text-[12px] text-neg-text">
              {submitError}
            </p>
          )}

          <div className="flex items-center justify-end gap-2 pt-2">
            <Button type="button" variant="secondary" onClick={onClose} disabled={submitting}>
              Cancel
            </Button>
            <Button type="submit" disabled={submitting || reviewId == null}>
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? 'Sending…' : 'Send Feedback'}
            </Button>
          </div>
        </form>
      )}
    </Modal>
  )
}

export default InaccurateAnalysisDialog
