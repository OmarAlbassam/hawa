import { useState, useEffect, useCallback } from "react";
import { useSearchParams, Link } from "react-router-dom";
import { getReports } from "../../services/reportService";
import type { Page } from "../../types/page";
import type { ReportResponse } from "../../types/report";
import type { ReportStatus } from "../../types/dashboard";
import Badge from "../../components/Badge/Badge";
import ErrorBanner from "../../components/ErrorBanner/ErrorBanner";
import { formatDate } from "../../utils/formatDate";
import { statusBadgeVariant } from "../../utils/reportStatus";
import "./ReportList.css";

const ReportList = (): React.JSX.Element => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(0);
  const [data, setData] = useState<Page<ReportResponse> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [statusFilter, setStatusFilter] = useState<ReportStatus | "">("");

  const brandIdParam = searchParams.get("brandId");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setData(
        await getReports({
          brandId: brandIdParam ? Number(brandIdParam) : undefined,
          status: statusFilter || undefined,
          page,
        })
      );
    } catch (err) {
      setError(err instanceof Error ? err.message : "Something went wrong");
    } finally {
      setLoading(false);
    }
  }, [page, statusFilter, brandIdParam]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const handleStatusChange = (value: string) => {
    setStatusFilter(value as ReportStatus | "");
    setPage(0);
  };

  const clearBrandFilter = () => {
    searchParams.delete("brandId");
    setSearchParams(searchParams);
    setPage(0);
  };

  return (
    <div className="report-list">
      <h1 className="report-list-title">Reports</h1>

      <div className="report-list-filters">
        <select
          className="report-list-select"
          value={statusFilter}
          onChange={(e) => handleStatusChange(e.target.value)}
        >
          <option value="">All statuses</option>
          <option value="PENDING">Pending</option>
          <option value="PROCESSING">Processing</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
        </select>
        {brandIdParam && (
          <button className="report-list-clear-btn" onClick={clearBrandFilter}>
            Clear brand filter
          </button>
        )}
      </div>

      {error && <ErrorBanner message={error} onRetry={loadData} />}

      {loading ? (
        <div className="report-list-loading">Loading reports...</div>
      ) : data && data.content.length === 0 ? (
        <div className="report-list-empty">
          <p>No reports found.</p>
        </div>
      ) : data && (
        <>
          <div className="report-list-table-wrapper">
            <table className="report-list-table">
              <thead>
                <tr>
                  <th>Brand</th>
                  <th>Source</th>
                  <th>Status</th>
                  <th>Score</th>
                  <th>Date Range</th>
                  <th>Created</th>
                  <th>Finished</th>
                </tr>
              </thead>
              <tbody>
                {data.content.map((report) => (
                  <tr key={report.reportId} className="report-list-row">
                    <td className="report-list-brand">
                      <Link
                        to={`/reports/${report.reportId}`}
                        state={{ report }}
                        className="report-list-row-link"
                      >
                        {report.brandName}
                      </Link>
                    </td>
                    <td>{report.dataSource === "CSV_UPLOAD" ? "CSV" : "Reddit"}</td>
                    <td>
                      <Badge variant={statusBadgeVariant[report.status]}>
                        {report.status}
                      </Badge>
                    </td>
                    <td>{report.score != null ? report.score.toFixed(1) : "—"}</td>
                    <td>
                      {report.dateFrom && report.dateTo
                        ? `${formatDate(report.dateFrom)} – ${formatDate(report.dateTo)}`
                        : "—"}
                    </td>
                    <td>{formatDate(report.createdAt)}</td>
                    <td>{report.finishedAt ? formatDate(report.finishedAt) : "—"}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="report-list-pagination">
            <button
              className="report-list-page-btn"
              disabled={data.number === 0}
              onClick={() => setPage((p) => p - 1)}
            >
              Previous
            </button>
            <span className="report-list-page-info">
              Page {data.number + 1} of {data.totalPages}
            </span>
            <button
              className="report-list-page-btn"
              disabled={data.number + 1 >= data.totalPages}
              onClick={() => setPage((p) => p + 1)}
            >
              Next
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default ReportList;
