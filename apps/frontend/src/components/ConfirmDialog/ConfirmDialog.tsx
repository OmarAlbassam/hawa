import Modal from "../Modal/Modal";
import "./ConfirmDialog.css";

interface ConfirmDialogProps {
  open: boolean;
  onClose: () => void;
  onConfirm: () => void;
  title: string;
  message: string;
  confirmLabel?: string;
  variant?: "primary" | "destructive";
  loading?: boolean;
}

const ConfirmDialog = ({
  open,
  onClose,
  onConfirm,
  title,
  message,
  confirmLabel = "Confirm",
  variant = "primary",
  loading = false,
}: ConfirmDialogProps): React.JSX.Element => {
  return (
    <Modal open={open} onClose={onClose} title={title} width="sm">
      <p className="confirm-dialog-message">{message}</p>
      <div className="confirm-dialog-actions">
        <button
          className="confirm-dialog-cancel"
          onClick={onClose}
          disabled={loading}
        >
          Cancel
        </button>
        <button
          className={`confirm-dialog-confirm confirm-dialog-confirm--${variant}`}
          onClick={onConfirm}
          disabled={loading}
        >
          {loading ? "..." : confirmLabel}
        </button>
      </div>
    </Modal>
  );
};

export default ConfirmDialog;
