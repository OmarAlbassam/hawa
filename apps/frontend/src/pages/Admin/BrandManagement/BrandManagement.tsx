import { useState, useEffect, useRef } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { useAuth } from "../../../context/useAuth";
import {
  getBrands,
  createBrand,
  updateBrand,
  deleteBrand,
  getCompanies,
} from "../../../services/adminService";
import type {
  Page,
  BrandResponse,
  CompanyResponse,
} from "../../../types/admin";
import { formatDate } from "../../../utils/formatDate";
import DataTable from "../../../components/DataTable/DataTable";
import type { Column } from "../../../components/DataTable/DataTable";
import Badge from "../../../components/Badge/Badge";
import Modal from "../../../components/Modal/Modal";
import ConfirmDialog from "../../../components/ConfirmDialog/ConfirmDialog";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import "./BrandManagement.css";

type ModalMode = "create" | "edit" | null;

const BrandManagement = (): React.JSX.Element => {
  const { accessToken } = useAuth();

  const [data, setData] = useState<Page<BrandResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [companyFilter, setCompanyFilter] = useState<number | "">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Companies list for filter & form dropdowns
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);

  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [editingBrand, setEditingBrand] = useState<BrandResponse | null>(null);
  const [formError, setFormError] = useState("");
  const [formLoading, setFormLoading] = useState(false);

  const [deletingBrand, setDeletingBrand] = useState<BrandResponse | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const [refreshKey, setRefreshKey] = useState(0);

  const loadCompanies = () => {
    if (!accessToken || companies.length > 0) return;
    getCompanies(accessToken, 0, 1000)
      .then((res) => setCompanies(res.content))
      .catch(() => {});
  };
  const prevFilterRef = useRef(companyFilter);

  useEffect(() => {
    if (!accessToken) return;

    const filterChanged = prevFilterRef.current !== companyFilter;
    prevFilterRef.current = companyFilter;

    if (filterChanged && page !== 0) {
      setPage(0);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError("");
    getBrands(accessToken, {
      companyId: companyFilter || undefined,
      page,
      size: 20,
    })
      .then((result) => {
        if (!cancelled) setData(result);
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : "Failed to load brands");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [accessToken, companyFilter, page, refreshKey]);

  const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!accessToken) return;

    const formData = new FormData(e.currentTarget);
    setFormError("");
    setFormLoading(true);

    try {
      if (modalMode === "create") {
        await createBrand(accessToken, {
          brandName: formData.get("brandName") as string,
          companyId: Number(formData.get("companyId")),
          industry: (formData.get("industry") as string) || undefined,
        });
      } else if (modalMode === "edit" && editingBrand) {
        await updateBrand(accessToken, editingBrand.brandId, {
          brandName: formData.get("brandName") as string,
          companyId: formData.get("companyId")
            ? Number(formData.get("companyId"))
            : null,
          industry: (formData.get("industry") as string) || null,
        });
      }
      setModalMode(null);
      setEditingBrand(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setFormLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!accessToken || !deletingBrand) return;
    setDeleteLoading(true);
    try {
      await deleteBrand(accessToken, deletingBrand.brandId);
      setDeletingBrand(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete brand");
      setDeletingBrand(null);
    } finally {
      setDeleteLoading(false);
    }
  };

  const columns: Column<BrandResponse>[] = [
    { key: "brandName", header: "Brand" },
    {
      key: "company",
      header: "Company",
      render: (row) => row.company.companyName,
    },
    {
      key: "industry",
      header: "Industry",
      render: (row) =>
        row.industry ? (
          <Badge variant="info">{row.industry}</Badge>
        ) : (
          "—"
        ),
    },
    {
      key: "statusIndicator",
      header: "Status",
      render: (row) =>
        row.statusIndicator != null ? row.statusIndicator.toFixed(1) : "—",
    },
    {
      key: "createdAt",
      header: "Created",
      render: (row) => formatDate(row.createdAt),
    },
    {
      key: "actions",
      header: "",
      render: (row) => (
        <div className="brands-actions">
          <button
            className="brands-action-btn"
            onClick={() => {
              loadCompanies();
              setEditingBrand(row);
              setModalMode("edit");
              setFormError("");
            }}
            aria-label="Edit brand"
          >
            <Pencil size={16} />
          </button>
          <button
            className="brands-action-btn brands-action-btn--danger"
            onClick={() => setDeletingBrand(row)}
            aria-label="Delete brand"
          >
            <Trash2 size={16} />
          </button>
        </div>
      ),
    },
  ];

  return (
    <div className="brands-page">
      <div className="brands-header">
        <h1 className="brands-title">Brands</h1>
        <button
          className="brands-create-btn"
          onClick={() => {
            loadCompanies();
            setEditingBrand(null);
            setModalMode("create");
            setFormError("");
          }}
        >
          <Plus size={18} />
          Create Brand
        </button>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => setRefreshKey((k) => k + 1)} />}

      <div className="brands-filters">
        <select
          className="brands-company-filter"
          value={companyFilter}
          onFocus={loadCompanies}
          onChange={(e) =>
            setCompanyFilter(e.target.value ? Number(e.target.value) : "")
          }
        >
          <option value="">All Companies</option>
          {companies.map((c) => (
            <option key={c.companyId} value={c.companyId}>
              {c.companyName}
            </option>
          ))}
        </select>
      </div>

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        keyField="brandId"
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No brands found"
      />

      <Modal
        open={modalMode !== null}
        onClose={() => {
          setModalMode(null);
          setEditingBrand(null);
        }}
        title={modalMode === "create" ? "Create Brand" : "Edit Brand"}
      >
        <form className="brands-form" onSubmit={handleFormSubmit}>
          {formError && (
            <div className="brands-form-error">{formError}</div>
          )}
          <label className="brands-form-field">
            <span className="brands-form-label">Brand Name</span>
            <input
              name="brandName"
              type="text"
              className="brands-form-input"
              defaultValue={editingBrand?.brandName ?? ""}
              required
            />
          </label>
          <div className="brands-form-row">
            <label className="brands-form-field">
              <span className="brands-form-label">Company</span>
              <select
                name="companyId"
                className="brands-form-input"
                defaultValue={editingBrand?.company.companyId ?? ""}
                required={modalMode === "create"}
              >
                {modalMode === "edit" && <option value="">Keep current</option>}
                {companies.map((c) => (
                  <option key={c.companyId} value={c.companyId}>
                    {c.companyName}
                  </option>
                ))}
              </select>
            </label>
            <label className="brands-form-field">
              <span className="brands-form-label">Industry</span>
              <input
                name="industry"
                type="text"
                className="brands-form-input"
                defaultValue={editingBrand?.industry ?? ""}
                placeholder="Optional"
              />
            </label>
          </div>
          <div className="brands-form-actions">
            <button
              type="button"
              className="brands-form-cancel"
              onClick={() => {
                setModalMode(null);
                setEditingBrand(null);
              }}
              disabled={formLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="brands-form-submit"
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
        open={deletingBrand !== null}
        onClose={() => setDeletingBrand(null)}
        onConfirm={handleDelete}
        title="Delete Brand"
        message={`Are you sure you want to delete "${deletingBrand?.brandName}"? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />
    </div>
  );
};

export default BrandManagement;
