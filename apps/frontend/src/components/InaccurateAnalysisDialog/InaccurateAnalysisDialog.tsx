import { useEffect, useState } from "react";
import { CheckCircle2 } from "lucide-react";
import Modal from "../Modal/Modal";
import { submitFeedback } from "../../services/feedbackService";
import {
  FEEDBACK_BRIEF_MAX,
  FEEDBACK_BRIEF_MIN,
} from "../../types/feedback";
import "./InaccurateAnalysisDialog.css";

interface InaccurateAnalysisDialogProps {
  open: boolean;
  reviewId: number | null;
  onClose: () => void;
}

const EMPTY_MESSAGE = "Please enter a brief explanation.";
const TOO_SHORT_MESSAGE = `Please write at least ${FEEDBACK_BRIEF_MIN} characters.`;
const TOO_LONG_MESSAGE = `Please keep it under ${FEEDBACK_BRIEF_MAX} characters.`;

const InaccurateAnalysisDialog = ({
  open,
  reviewId,
  onClose,
}: InaccurateAnalysisDialogProps): React.JSX.Element => {
  const [brief, setBrief] = useState("");
  const [validationError, setValidationError] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [success, setSuccess] = useState(false);

  useEffect(() => {
    if (open) {
      setBrief("");
      setValidationError(null);
      setSubmitError(null);
      setSubmitting(false);
      setSuccess(false);
    }
  }, [open]);

  const validate = (value: string): string | null => {
    const trimmed = value.trim();
    if (trimmed.length === 0) return EMPTY_MESSAGE;
    if (trimmed.length < FEEDBACK_BRIEF_MIN) return TOO_SHORT_MESSAGE;
    if (trimmed.length > FEEDBACK_BRIEF_MAX) return TOO_LONG_MESSAGE;
    return null;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (reviewId == null || submitting) return;

    const error = validate(brief);
    if (error) {
      setValidationError(error);
      return;
    }
    setValidationError(null);
    setSubmitError(null);
    setSubmitting(true);
    try {
      await submitFeedback(reviewId, { brief: brief.trim() });
      setSuccess(true);
    } catch (err) {
      setSubmitError(
        err instanceof Error
          ? err.message
          : "Could not submit feedback. Try again later."
      );
    } finally {
      setSubmitting(false);
    }
  };

  const remaining = FEEDBACK_BRIEF_MAX - brief.length;
  const remainingClass =
    remaining < 0
      ? "inaccurate-feedback-counter inaccurate-feedback-counter--error"
      : remaining < 50
        ? "inaccurate-feedback-counter inaccurate-feedback-counter--warn"
        : "inaccurate-feedback-counter";

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Report inaccurate analysis"
      width="sm"
    >
      {success ? (
        <div className="inaccurate-feedback-success" role="status">
          <CheckCircle2
            size={40}
            className="inaccurate-feedback-success-icon"
            aria-hidden
          />
          <p className="inaccurate-feedback-success-title">
            Thanks for the feedback
          </p>
          <p className="inaccurate-feedback-success-message">
            Your note has been recorded and will help improve the model.
          </p>
          <div className="inaccurate-feedback-actions">
            <button
              type="button"
              className="inaccurate-feedback-primary"
              onClick={onClose}
            >
              Close
            </button>
          </div>
        </div>
      ) : (
        <form className="inaccurate-feedback-form" onSubmit={handleSubmit}>
          <p className="inaccurate-feedback-help">
            Briefly describe what the system got wrong (sentiment, emotion, or
            aspect). This is reviewed to improve future analyses.
          </p>

          <label className="inaccurate-feedback-label" htmlFor="feedback-brief">
            Explanation
          </label>
          <textarea
            id="feedback-brief"
            className={
              validationError
                ? "inaccurate-feedback-textarea inaccurate-feedback-textarea--error"
                : "inaccurate-feedback-textarea"
            }
            value={brief}
            onChange={(e) => {
              setBrief(e.target.value);
              if (validationError) setValidationError(null);
            }}
            rows={5}
            maxLength={FEEDBACK_BRIEF_MAX + 50}
            placeholder="e.g. The post is sarcastic but was scored as positive."
            disabled={submitting}
            aria-invalid={validationError != null}
            aria-describedby="feedback-brief-help"
          />
          <div id="feedback-brief-help" className="inaccurate-feedback-meta">
            <span className={remainingClass}>
              {remaining < 0
                ? `${Math.abs(remaining)} over limit`
                : `${remaining} characters left`}
            </span>
          </div>

          {validationError && (
            <p className="inaccurate-feedback-error" role="alert">
              {validationError}
            </p>
          )}
          {submitError && (
            <p className="inaccurate-feedback-error" role="alert">
              {submitError}
            </p>
          )}

          <div className="inaccurate-feedback-actions">
            <button
              type="button"
              className="inaccurate-feedback-secondary"
              onClick={onClose}
              disabled={submitting}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="inaccurate-feedback-primary"
              disabled={submitting || reviewId == null}
              aria-busy={submitting}
            >
              {submitting ? "Sending…" : "Send Feedback"}
            </button>
          </div>
        </form>
      )}
    </Modal>
  );
};

export default InaccurateAnalysisDialog;
