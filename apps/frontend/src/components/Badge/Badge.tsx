import "./Badge.css";

interface BadgeProps {
  variant?: "default" | "primary" | "success" | "warning" | "error" | "info";
  children: React.ReactNode;
}

const Badge = ({ variant = "default", children }: BadgeProps): React.JSX.Element => {
  return <span className={`badge badge--${variant}`}>{children}</span>;
};

export default Badge;
