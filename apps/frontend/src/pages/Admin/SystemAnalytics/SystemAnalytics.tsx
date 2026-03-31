import { useState, useEffect, useCallback } from "react";
import { Users, Building2, FileText, MessageSquare, Clock, Loader, CheckCircle, XCircle } from "lucide-react";
import { useAuth } from "../../../context/useAuth";
import { getAnalytics } from "../../../services/adminService";
import type { SystemAnalyticsResponse } from "../../../types/admin";
import StatCard from "../../../components/StatCard/StatCard";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import "./SystemAnalytics.css";

const SystemAnalytics = (): React.JSX.Element => {
  const { accessToken } = useAuth();
  const [data, setData] = useState<SystemAnalyticsResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchAnalytics = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError("");
    try {
      const result = await getAnalytics(accessToken);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load analytics");
    } finally {
      setLoading(false);
    }
  }, [accessToken]);

  useEffect(() => {
    fetchAnalytics();
  }, [fetchAnalytics]);

  if (loading) {
    return <div className="analytics-loading">Loading analytics...</div>;
  }

  return (
    <div className="analytics-page">
      <h1 className="analytics-title">System Analytics</h1>

      {error && <ErrorBanner message={error} onRetry={fetchAnalytics} />}

      {data && (
        <>
          <section className="analytics-section">
            <h2 className="analytics-section-title">Overview</h2>
            <div className="analytics-grid">
              <StatCard label="Total Users" value={data.totalUsers} icon={Users} />
              <StatCard label="Total Companies" value={data.totalCompanies} icon={Building2} />
              <StatCard label="Total Reports" value={data.totalReports} icon={FileText} />
              <StatCard label="Posts Analyzed" value={data.totalPostsAnalyzed} icon={MessageSquare} />
            </div>
          </section>

          <section className="analytics-section">
            <h2 className="analytics-section-title">Reports by Status</h2>
            <div className="analytics-grid">
              <StatCard
                label="Pending"
                value={data.reportsByStatus.PENDING}
                icon={Clock}
                variant="info"
              />
              <StatCard
                label="Processing"
                value={data.reportsByStatus.PROCESSING}
                icon={Loader}
                variant="warning"
              />
              <StatCard
                label="Completed"
                value={data.reportsByStatus.COMPLETED}
                icon={CheckCircle}
                variant="success"
              />
              <StatCard
                label="Failed"
                value={data.reportsByStatus.FAILED}
                icon={XCircle}
                variant="error"
              />
            </div>
          </section>
        </>
      )}
    </div>
  );
};

export default SystemAnalytics;
