import React from "react";
import "./ErrorBanner.css";

interface ErrorBannerProps {
  message: string;
  onRetry?: () => void;
}

const ErrorBanner = ({ message, onRetry }: ErrorBannerProps): React.JSX.Element => (
  <div className="ErrorBanner">
    <p className="ErrorBanner-message">{message}</p>
    {onRetry && (
      <button className="ErrorBanner-retry" onClick={onRetry}>
        Retry
      </button>
    )}
  </div>
);

export default ErrorBanner;
