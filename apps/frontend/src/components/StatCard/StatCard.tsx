import type { LucideIcon } from "lucide-react";
import "./StatCard.css";

interface StatCardProps {
  label: string;
  value: string | number;
  icon?: LucideIcon;
  variant?: "default" | "success" | "warning" | "error" | "info";
}

const StatCard = ({ label, value, icon: Icon, variant = "default" }: StatCardProps): React.JSX.Element => {
  return (
    <div className={`stat-card stat-card--${variant}`}>
      <div className="stat-card-header">
        <span className="stat-card-label">{label}</span>
        {Icon && (
          <div className={`stat-card-icon stat-card-icon--${variant}`}>
            <Icon size={20} />
          </div>
        )}
      </div>
      <span className="stat-card-value">{value}</span>
    </div>
  );
};

export default StatCard;
