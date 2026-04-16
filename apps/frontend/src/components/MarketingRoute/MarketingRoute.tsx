import { Navigate } from "react-router-dom";
import { useAuth } from "../../context/useAuth";

interface MarketingRouteProps {
  children: React.ReactNode;
}

const MarketingRoute = ({ children }: MarketingRouteProps): React.JSX.Element => {
  const { isAuthenticated, user } = useAuth();

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (user?.role !== "MARKETING_USER") {
    return <Navigate to="/admin" replace />;
  }

  return <>{children}</>;
};

export default MarketingRoute;
