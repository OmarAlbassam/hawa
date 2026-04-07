import { useState, useEffect, useCallback } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { useAuth } from "../../../context/useAuth";
import {
  getCompanies,
  createCompany,
  updateCompany,
  deleteCompany,
} from "../../../services/adminService";
import type { Page, CompanyResponse } from "../../../types/admin";
import { formatDate } from "../../../utils/formatDate";
import DataTable from "../../../components/DataTable/DataTable";
import type { Column } from "../../../components/DataTable/DataTable";
import Modal from "../../../components/Modal/Modal";
import ConfirmDialog from "../../../components/ConfirmDialog/ConfirmDialog";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import "./CompanyManagement.css";

type ModalMode = "create" | "edit" | null;

const CompanyManagement = (): React.JSX.Element => {
  const { accessToken } = useAuth();

  const [data, setData] = useState<Page<CompanyResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [editingCompany, setEditingCompany] = useState<CompanyResponse | null>(null);
  const [formError, setFormError] = useState("");
  const [formLoading, setFormLoading] = useState(false);

  const [deletingCompany, setDeletingCompany] = useState<CompanyResponse | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const fetchCompanies = useCallback(async () => {
    if (!accessToken) return;
    setLoading(true);
    setError("");
    try {
      const result = await getCompanies(accessToken, page);
      setData(result);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to load companies");
    } finally {
      setLoading(false);
    }
  }, [accessToken, page]);

  useEffect(() => {
    fetchCompanies();
  }, [fetchCompanies]);

  const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!accessToken) return;

    const formData = new FormData(e.currentTarget);
    const companyName = formData.get("companyName") as string;

    setFormError("");
    setFormLoading(true);

    try {
      if (modalMode === "create") {
        await createCompany(accessToken, { companyName });
      } else if (modalMode === "edit" && editingCompany) {
        await updateCompany(accessToken, editingCompany.companyId, { companyName });
      }
      setModalMode(null);
      setEditingCompany(null);
      fetchCompanies();
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!accessToken || !deletingCompany) return;
    setDeleteLoading(true);
    try {
      await deleteCompany(accessToken, deletingCompany.companyId);
      setDeletingCompany(null);
      fetchCompanies();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete company");
      setDeletingCompany(null);
    } finally {
      setDeleteLoading(false);
    }
  };

  const columns: Column<CompanyResponse>[] = [
    { key: "companyId", header: "ID" },
    { key: "companyName", header: "Company Name" },
    {
      key: "createdAt",
      header: "Created",
      render: (row) => formatDate(row.createdAt),
    },
    {
      key: "updatedAt",
      header: "Updated",
      render: (row) => formatDate(row.updatedAt),
    },
    {
      key: "actions",
      header: "",
      render: (row) => (
        <div className="companies-actions">
          <button
            className="companies-action-btn"
            onClick={() => {
              setEditingCompany(row);
              setModalMode("edit");
              setFormError("");
            }}
            aria-label="Edit company"
          >
            <Pencil size={16} />
          </button>
          <button
            className="companies-action-btn companies-action-btn--danger"
            onClick={() => setDeletingCompany(row)}
            aria-label="Delete company"
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="companies-page">
      <div className="companies-header">
        <h1 className="companies-title">Companies</h1>
        <button
          className="companies-create-btn"
          onClick={() => {
            setEditingCompany(null);
            setModalMode("create");
            setFormError("");
          }}
        >
          <Plus size={18} />
          Create Company
        </button>
      </div>

      {error && <ErrorBanner message={error} onRetry={fetchCompanies} />}

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        keyField="companyId"
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No companies found"
      />

      <Modal
        open={modalMode !== null}
        onClose={() => {
          setModalMode(null);
          setEditingCompany(null);
        }}
        title={modalMode === "create" ? "Create Company" : "Edit Company"}
        width="sm"
      >
        <form className="companies-form" onSubmit={handleFormSubmit}>
          {formError && (
            <div className="companies-form-error">{formError}</div>
          )}
          <label className="companies-form-field">
            <span className="companies-form-label">Company Name</span>
            <input
              name="companyName"
              type="text"
              className="companies-form-input"
              defaultValue={editingCompany?.companyName ?? ""}
              required
            />
          </label>
          <div className="companies-form-actions">
            <button
              type="button"
              className="companies-form-cancel"
              onClick={() => {
                setModalMode(null);
                setEditingCompany(null);
              }}
              disabled={formLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="companies-form-submit"
              disabled={formLoading}
            >
              {formLoading
                ? "..."
                : modalMode === "create"
                  ? "Create"
                  : "Save Changes"}
            </button>
          </div>
        </form>
      </Modal>

      <ConfirmDialog
        open={deletingCompany !== null}
        onClose={() => setDeletingCompany(null)}
        onConfirm={handleDelete}
        title="Delete Company"
        message={`Are you sure you want to delete "${deletingCompany?.companyName}"? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />
    </div>
  );
};

export default CompanyManagement;
