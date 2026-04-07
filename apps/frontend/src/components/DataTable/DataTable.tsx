import { ChevronLeft, ChevronRight } from "lucide-react";
import "./DataTable.css";

export interface Column<T> {
  key: string;
  header: string;
  render?: (row: T) => React.ReactNode;
}

interface DataTableProps<T> {
  columns: Column<T>[];
  data: T[];
  keyField: keyof T;
  page: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
  loading?: boolean;
  emptyMessage?: string;
}

function DataTable<T>({
  columns,
  data,
  keyField,
  page,
  totalPages,
  totalElements,
  onPageChange,
  loading = false,
  emptyMessage = "No data found",
}: DataTableProps<T>): React.JSX.Element {
  return (
    <div className="data-table-wrapper">
      <div className="data-table-scroll">
        <table className="data-table">
          <thead>
            <tr>
              {columns.map((col) => (
                <th key={col.key} className="data-table-th">
                  {col.header}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr>
                <td colSpan={columns.length} className="data-table-empty">
                  Loading...
                </td>
              </tr>
            ) : data.length === 0 ? (
              <tr>
                <td colSpan={columns.length} className="data-table-empty">
                  {emptyMessage}
                </td>
              </tr>
            ) : (
              data.map((row) => (
                <tr key={String(row[keyField])} className="data-table-row">
                  {columns.map((col) => (
                    <td key={col.key} className="data-table-td">
                      {col.render
                        ? col.render(row)
                        : String((row as Record<string, unknown>)[col.key] ?? "")}
                    </td>
                  ))}
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
      <div className="data-table-pagination">
        <span className="data-table-info">
          {totalElements} result{totalElements !== 1 ? "s" : ""}
        </span>
        <div className="data-table-page-controls">
          <button
            className="data-table-page-btn"
            disabled={page === 0}
            onClick={() => onPageChange(page - 1)}
            aria-label="Previous page"
          >
            <ChevronLeft size={16} />
          </button>
          <span className="data-table-page-info">
            Page {page + 1} of {Math.max(totalPages, 1)}
          </span>
          <button
            className="data-table-page-btn"
            disabled={page >= totalPages - 1}
            onClick={() => onPageChange(page + 1)}
            aria-label="Next page"
          >
            <ChevronRight size={16} />
          </button>
        </div>
      </div>
    </div>
  );
}

export default DataTable;
