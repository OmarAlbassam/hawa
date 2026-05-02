import { useState, useEffect, useRef } from "react";
import { Plus, Pencil, Trash2 } from "lucide-react";
import { useAuth } from "../../../context/useAuth";
import {
  getUsers,
  createUser,
  updateUser,
  deleteUser,
  getCompanies,
} from "../../../services/adminService";
import type {
  Page,
  AdminUserResponse,
  CreateUserRequest,
  UpdateUserRequest,
  CompanyResponse,
} from "../../../types/admin";
import { useDebounce } from "../../../hooks/useDebounce";
import { formatDate } from "../../../utils/formatDate";
import DataTable from "../../../components/DataTable/DataTable";
import type { Column } from "../../../components/DataTable/DataTable";
import Badge from "../../../components/Badge/Badge";
import Modal from "../../../components/Modal/Modal";
import ConfirmDialog from "../../../components/ConfirmDialog/ConfirmDialog";
import ErrorBanner from "../../../components/ErrorBanner/ErrorBanner";
import "./UserManagement.css";

type ModalMode = "create" | "edit" | null;

const UserManagement = (): React.JSX.Element => {
  const { accessToken, user: currentUser } = useAuth();

  // List state
  const [data, setData] = useState<Page<AdminUserResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [roleFilter, setRoleFilter] = useState<"" | "ADMIN" | "MARKETING_USER">("");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Companies for dropdowns
  const [companies, setCompanies] = useState<CompanyResponse[]>([]);

  // Modal state
  const [modalMode, setModalMode] = useState<ModalMode>(null);
  const [editingUser, setEditingUser] = useState<AdminUserResponse | null>(null);
  const [formRole, setFormRole] = useState<"ADMIN" | "MARKETING_USER">("MARKETING_USER");
  const [formError, setFormError] = useState("");
  const [formLoading, setFormLoading] = useState(false);

  // Delete state
  const [deletingUser, setDeletingUser] = useState<AdminUserResponse | null>(null);
  const [deleteLoading, setDeleteLoading] = useState(false);

  const debouncedSearch = useDebounce(search, 300);
  const [refreshKey, setRefreshKey] = useState(0);
  const prevFiltersRef = useRef({ search: debouncedSearch, role: roleFilter });

  useEffect(() => {
    if (!accessToken) return;

    const filtersChanged =
      prevFiltersRef.current.search !== debouncedSearch ||
      prevFiltersRef.current.role !== roleFilter;
    prevFiltersRef.current = { search: debouncedSearch, role: roleFilter };

    if (filtersChanged && page !== 0) {
      setPage(0);
      return;
    }

    setLoading(true);
    setError("");
    getUsers(accessToken, {
      search: debouncedSearch || undefined,
      role: roleFilter || undefined,
      page,
      size: 20,
      sort: "createdAt,desc",
    })
      .then(setData)
      .catch((err) =>
        setError(err instanceof Error ? err.message : "Failed to load users")
      )
      .finally(() => setLoading(false));
  }, [accessToken, debouncedSearch, roleFilter, page, refreshKey]);

  const loadCompanies = () => {
    if (!accessToken || companies.length > 0) return;
    getCompanies(accessToken, 0, 1000)
      .then((res) => setCompanies(res.content))
      .catch(() => {});
  };

  // Form submit
  const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    if (!accessToken) return;

    const formData = new FormData(e.currentTarget);
    setFormError("");

    const role = formRole;
    const companyIdRaw = formData.get("companyId");
    const companyId =
      role === "ADMIN"
        ? null
        : companyIdRaw
          ? Number(companyIdRaw)
          : null;

    if (role !== "ADMIN" && companyId === null) {
      setFormError("Company is required for marketing users");
      return;
    }

    setFormLoading(true);

    try {
      if (modalMode === "create") {
        const payload: CreateUserRequest = {
          firstName: formData.get("firstName") as string,
          lastName: formData.get("lastName") as string,
          email: formData.get("email") as string,
          password: formData.get("password") as string,
          role,
          companyId,
        };
        await createUser(accessToken, payload);
      } else if (modalMode === "edit" && editingUser) {
        const payload: UpdateUserRequest = {
          firstName: formData.get("firstName") as string,
          lastName: formData.get("lastName") as string,
          email: formData.get("email") as string,
          role,
          companyId,
        };
        await updateUser(accessToken, editingUser.userId, payload);
      }
      setModalMode(null);
      setEditingUser(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setFormError(err instanceof Error ? err.message : "Operation failed");
    } finally {
      setFormLoading(false);
    }
  };

  // Delete
  const handleDelete = async () => {
    if (!accessToken || !deletingUser) return;
    setDeleteLoading(true);
    try {
      await deleteUser(accessToken, deletingUser.userId);
      setDeletingUser(null);
      setRefreshKey((k) => k + 1);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Failed to delete user");
      setDeletingUser(null);
    } finally {
      setDeleteLoading(false);
    }
  };

  const openEdit = (user: AdminUserResponse) => {
    loadCompanies();
    setEditingUser(user);
    setFormRole(user.role);
    setModalMode("edit");
    setFormError("");
  };

  const openCreate = () => {
    loadCompanies();
    setEditingUser(null);
    setFormRole("MARKETING_USER");
    setModalMode("create");
    setFormError("");
  };

  const columns: Column<AdminUserResponse>[] = [
    {
      key: "name",
      header: "Name",
      render: (row) => `${row.firstName} ${row.lastName}`,
    },
    { key: "email", header: "Email" },
    {
      key: "role",
      header: "Role",
      render: (row) => (
        <Badge variant={row.role === "ADMIN" ? "primary" : "default"}>
          {row.role === "ADMIN" ? "Admin" : "Marketing"}
        </Badge>
      ),
    },
    {
      key: "company",
      header: "Company",
      render: (row) =>
        row.company?.companyName ??
        (row.role === "ADMIN" ? (
          <span className="users-platform-label">Platform</span>
        ) : (
          "—"
        )),
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
        <div className="users-actions">
          <button
            className="users-action-btn"
            onClick={() => openEdit(row)}
            aria-label="Edit user"
          >
            <Pencil size={16} />
          </button>
          {row.userId !== currentUser?.userId && (
            <button
              className="users-action-btn users-action-btn--danger"
              onClick={() => setDeletingUser(row)}
              aria-label="Delete user"
            >
              <Trash2 size={16} />
            </button>
          )}
        </div>
      ),
    },
  ];

  return (
    <div className="users-page">
      <div className="users-header">
        <h1 className="users-title">User Management</h1>
        <button className="users-create-btn" onClick={openCreate}>
          <Plus size={18} />
          Create User
        </button>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => setRefreshKey((k) => k + 1)} />}

      <div className="users-filters">
        <input
          className="users-search"
          type="text"
          placeholder="Search by name or email..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select
          className="users-role-filter"
          value={roleFilter}
          onChange={(e) =>
            setRoleFilter(e.target.value as "" | "ADMIN" | "MARKETING_USER")
          }
        >
          <option value="">All Roles</option>
          <option value="ADMIN">Admin</option>
          <option value="MARKETING_USER">Marketing User</option>
        </select>
      </div>

      <DataTable
        columns={columns}
        data={data?.content ?? []}
        keyField="userId"
        page={page}
        totalPages={data?.totalPages ?? 0}
        totalElements={data?.totalElements ?? 0}
        onPageChange={setPage}
        loading={loading}
        emptyMessage="No users found"
      />

      {/* Create / Edit Modal */}
      <Modal
        open={modalMode !== null}
        onClose={() => {
          setModalMode(null);
          setEditingUser(null);
        }}
        title={modalMode === "create" ? "Create User" : "Edit User"}
      >
        <form className="users-form" onSubmit={handleFormSubmit}>
          {formError && (
            <div className="users-form-error">{formError}</div>
          )}
          <div className="users-form-row">
            <label className="users-form-field">
              <span className="users-form-label">First Name</span>
              <input
                name="firstName"
                type="text"
                className="users-form-input"
                defaultValue={editingUser?.firstName ?? ""}
                required
              />
            </label>
            <label className="users-form-field">
              <span className="users-form-label">Last Name</span>
              <input
                name="lastName"
                type="text"
                className="users-form-input"
                defaultValue={editingUser?.lastName ?? ""}
                required
              />
            </label>
          </div>
          <label className="users-form-field">
            <span className="users-form-label">Email</span>
            <input
              name="email"
              type="email"
              className="users-form-input"
              defaultValue={editingUser?.email ?? ""}
              required
            />
          </label>
          {modalMode === "create" && (
            <label className="users-form-field">
              <span className="users-form-label">Password</span>
              <input
                name="password"
                type="password"
                className="users-form-input"
                minLength={8}
                required
              />
            </label>
          )}
          <div className="users-form-row">
            <label className="users-form-field">
              <span className="users-form-label">Role</span>
              <select
                name="role"
                className="users-form-input"
                value={formRole}
                onChange={(e) =>
                  setFormRole(e.target.value as "ADMIN" | "MARKETING_USER")
                }
                required
              >
                <option value="MARKETING_USER">Marketing User</option>
                <option value="ADMIN">Admin</option>
              </select>
            </label>
            {formRole === "ADMIN" ? (
              <div className="users-form-field users-form-admin-note">
                <span className="users-form-label">Company</span>
                <span className="users-form-static">
                  Platform admin — no company
                </span>
              </div>
            ) : (
              <label className="users-form-field">
                <span className="users-form-label">Company</span>
                <select
                  name="companyId"
                  className="users-form-input"
                  defaultValue={editingUser?.company?.companyId ?? ""}
                  required
                >
                  <option value="">Select company</option>
                  {companies.map((c) => (
                    <option key={c.companyId} value={c.companyId}>
                      {c.companyName}
                    </option>
                  ))}
                </select>
              </label>
            )}
          </div>
          <div className="users-form-actions">
            <button
              type="button"
              className="users-form-cancel"
              onClick={() => {
                setModalMode(null);
                setEditingUser(null);
              }}
              disabled={formLoading}
            >
              Cancel
            </button>
            <button
              type="submit"
              className="users-form-submit"
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

      {/* Delete Confirmation */}
      <ConfirmDialog
        open={deletingUser !== null}
        onClose={() => setDeletingUser(null)}
        onConfirm={handleDelete}
        title="Delete User"
        message={`Are you sure you want to delete ${deletingUser?.firstName} ${deletingUser?.lastName}? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />
    </div>
  );
};

export default UserManagement;
