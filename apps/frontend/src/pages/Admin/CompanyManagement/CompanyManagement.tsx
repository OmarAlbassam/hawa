import { useState, useEffect, useCallback } from 'react'
import { Plus, Pencil, Trash2, Loader2 } from 'lucide-react'
import { useAuth } from '../../../context/useAuth'
import {
  getCompanies,
  createCompany,
  updateCompany,
  deleteCompany,
} from '../../../services/adminService'
import type { Page, CompanyResponse } from '../../../types/admin'
import { formatDate } from '../../../utils/formatDate'
import DataTable from '../../../components/DataTable/DataTable'
import type { Column } from '../../../components/DataTable/DataTable'
import Modal from '../../../components/Modal/Modal'
import ConfirmDialog from '../../../components/ConfirmDialog/ConfirmDialog'
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

type ModalMode = 'create' | 'edit' | null

const CompanyManagement = (): React.JSX.Element => {
  const { accessToken } = useAuth()

  const [data, setData] = useState<Page<CompanyResponse> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [modalMode, setModalMode] = useState<ModalMode>(null)
  const [editingCompany, setEditingCompany] = useState<CompanyResponse | null>(null)
  const [formError, setFormError] = useState('')
  const [formLoading, setFormLoading] = useState(false)

  const [deletingCompany, setDeletingCompany] = useState<CompanyResponse | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  const fetchCompanies = useCallback(async () => {
    if (!accessToken) return
    setLoading(true)
    setError('')
    try {
      const result = await getCompanies(accessToken, page)
      setData(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load companies')
    } finally {
      setLoading(false)
    }
  }, [accessToken, page])

  useEffect(() => {
    fetchCompanies()
  }, [fetchCompanies])

  const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!accessToken) return

    const formData = new FormData(e.currentTarget)
    const companyName = formData.get('companyName') as string

    setFormError('')
    setFormLoading(true)

    try {
      if (modalMode === 'create') {
        await createCompany(accessToken, { companyName })
      } else if (modalMode === 'edit' && editingCompany) {
        await updateCompany(accessToken, editingCompany.companyId, { companyName })
      }
      setModalMode(null)
      setEditingCompany(null)
      fetchCompanies()
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Operation failed')
    } finally {
      setFormLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!accessToken || !deletingCompany) return
    setDeleteLoading(true)
    try {
      await deleteCompany(accessToken, deletingCompany.companyId)
      setDeletingCompany(null)
      fetchCompanies()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete company')
      setDeletingCompany(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  const columns: Column<CompanyResponse>[] = [
    {
      key: 'companyId',
      header: 'ID',
      render: (row) => (
        <span className="font-mono text-[11px] tracking-[0.04em] text-text-3">
          {row.companyId}
        </span>
      ),
    },
    {
      key: 'companyName',
      header: 'Company Name',
      render: (row) => (
        <span className="font-medium text-foreground">{row.companyName}</span>
      ),
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (row) => (
        <span className="text-muted-foreground">{formatDate(row.createdAt)}</span>
      ),
    },
    {
      key: 'updatedAt',
      header: 'Updated',
      render: (row) => (
        <span className="text-muted-foreground">{formatDate(row.updatedAt)}</span>
      ),
    },
    {
      key: 'actions',
      header: '',
      render: (row) => (
        <div className="flex items-center justify-end gap-1">
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => {
              setEditingCompany(row)
              setModalMode('edit')
              setFormError('')
            }}
            aria-label="Edit company"
          >
            <Pencil className="size-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-neg hover:bg-neg-bg"
            onClick={() => setDeletingCompany(row)}
            aria-label="Delete company"
          >
            <Trash2 className="size-3.5" />
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="space-y-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <span className="eyebrow">Admin · Companies</span>
          <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
            Companies
          </h1>
        </div>
        <Button
          onClick={() => {
            setEditingCompany(null)
            setModalMode('create')
            setFormError('')
          }}
        >
          <Plus />
          Create Company
        </Button>
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
          setModalMode(null)
          setEditingCompany(null)
        }}
        title={modalMode === 'create' ? 'Create Company' : 'Edit Company'}
        width="sm"
      >
        <form onSubmit={handleFormSubmit} className="space-y-4">
          {formError && (
            <div
              role="alert"
              className="rounded-md border border-neg/30 bg-neg-bg px-3 py-2 text-[12px] text-neg-text"
            >
              {formError}
            </div>
          )}
          <div className="space-y-1.5">
            <Label htmlFor="companyName">Company name</Label>
            <Input
              id="companyName"
              name="companyName"
              type="text"
              defaultValue={editingCompany?.companyName ?? ''}
              required
            />
          </div>
          <div className="flex items-center justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setModalMode(null)
                setEditingCompany(null)
              }}
              disabled={formLoading}
            >
              Cancel
            </Button>
            <Button type="submit" disabled={formLoading}>
              {formLoading && <Loader2 className="size-4 animate-spin" />}
              {formLoading
                ? 'Working…'
                : modalMode === 'create'
                  ? 'Create'
                  : 'Save changes'}
            </Button>
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
  )
}

export default CompanyManagement
