import { useState, useEffect, useRef } from 'react'
import { Plus, Pencil, Trash2, Tags, Loader2 } from 'lucide-react'
import { useAuth } from '../../../context/useAuth'
import {
  getBrands,
  createBrand,
  updateBrand,
  deleteBrand,
  getCompanies,
} from '../../../services/adminService'
import type { Page, BrandResponse, CompanyResponse } from '../../../types/admin'
import { formatDate } from '../../../utils/formatDate'
import DataTable from '../../../components/DataTable/DataTable'
import type { Column } from '../../../components/DataTable/DataTable'
import Badge from '../../../components/Badge/Badge'
import Modal from '../../../components/Modal/Modal'
import ConfirmDialog from '../../../components/ConfirmDialog/ConfirmDialog'
import ErrorBanner from '../../../components/ErrorBanner/ErrorBanner'
import KeywordPanel from './KeywordPanel'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { selectClass as baseSelectClass } from '@/lib/select-styles'

type ModalMode = 'create' | 'edit' | null

const selectClass = `${baseSelectClass} w-full`

const BrandManagement = (): React.JSX.Element => {
  const { accessToken } = useAuth()

  const [data, setData] = useState<Page<BrandResponse> | null>(null)
  const [page, setPage] = useState(0)
  const [companyFilter, setCompanyFilter] = useState<number | ''>('')
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')

  const [companies, setCompanies] = useState<CompanyResponse[]>([])

  const [modalMode, setModalMode] = useState<ModalMode>(null)
  const [editingBrand, setEditingBrand] = useState<BrandResponse | null>(null)
  const [formError, setFormError] = useState('')
  const [formLoading, setFormLoading] = useState(false)

  const [deletingBrand, setDeletingBrand] = useState<BrandResponse | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  const [keywordsBrand, setKeywordsBrand] = useState<BrandResponse | null>(null)

  const [refreshKey, setRefreshKey] = useState(0)

  const loadCompanies = () => {
    if (!accessToken || companies.length > 0) return
    getCompanies(accessToken, 0, 1000)
      .then((res) => setCompanies(res.content))
      .catch(() => {})
  }
  const prevFilterRef = useRef(companyFilter)

  useEffect(() => {
    if (!accessToken) return

    const filterChanged = prevFilterRef.current !== companyFilter
    prevFilterRef.current = companyFilter

    if (filterChanged && page !== 0) {
      setPage(0)
      return
    }

    let cancelled = false
    setLoading(true)
    setError('')
    getBrands(accessToken, {
      companyId: companyFilter || undefined,
      page,
      size: 20,
    })
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : 'Failed to load brands')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [accessToken, companyFilter, page, refreshKey])

  const handleFormSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!accessToken) return

    const formData = new FormData(e.currentTarget)
    setFormError('')
    setFormLoading(true)

    try {
      if (modalMode === 'create') {
        await createBrand(accessToken, {
          brandName: formData.get('brandName') as string,
          companyId: Number(formData.get('companyId')),
          industry: (formData.get('industry') as string) || undefined,
        })
      } else if (modalMode === 'edit' && editingBrand) {
        await updateBrand(accessToken, editingBrand.brandId, {
          brandName: formData.get('brandName') as string,
          companyId: formData.get('companyId')
            ? Number(formData.get('companyId'))
            : null,
          industry: (formData.get('industry') as string) || null,
        })
      }
      setModalMode(null)
      setEditingBrand(null)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setFormError(err instanceof Error ? err.message : 'Operation failed')
    } finally {
      setFormLoading(false)
    }
  }

  const handleDelete = async () => {
    if (!accessToken || !deletingBrand) return
    setDeleteLoading(true)
    try {
      await deleteBrand(accessToken, deletingBrand.brandId)
      setDeletingBrand(null)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete brand')
      setDeletingBrand(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  const columns: Column<BrandResponse>[] = [
    {
      key: 'brandName',
      header: 'Brand',
      render: (row) => <span className="font-medium text-foreground">{row.brandName}</span>,
    },
    {
      key: 'company',
      header: 'Company',
      render: (row) => <span className="text-muted-foreground">{row.company.companyName}</span>,
    },
    {
      key: 'industry',
      header: 'Industry',
      render: (row) => (row.industry ? <Badge variant="info">{row.industry}</Badge> : '-'),
    },
    {
      key: 'statusIndicator',
      header: 'Status',
      render: (row) => (
        <span className="font-mono tabular-nums text-foreground">
          {row.statusIndicator != null ? row.statusIndicator.toFixed(1) : '-'}
        </span>
      ),
    },
    {
      key: 'keywordsCount',
      header: 'Keywords',
      render: (row) => <Badge variant="default">{row.keywordsCount}</Badge>,
    },
    {
      key: 'createdAt',
      header: 'Created',
      render: (row) => (
        <span className="text-muted-foreground">{formatDate(row.createdAt)}</span>
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
            onClick={() => setKeywordsBrand(row)}
            aria-label="Manage keywords"
          >
            <Tags className="size-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7"
            onClick={() => {
              loadCompanies()
              setEditingBrand(row)
              setModalMode('edit')
              setFormError('')
            }}
            aria-label="Edit brand"
          >
            <Pencil className="size-3.5" />
          </Button>
          <Button
            variant="ghost"
            size="icon"
            className="h-7 w-7 text-neg hover:bg-neg-bg"
            onClick={() => setDeletingBrand(row)}
            aria-label="Delete brand"
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
          <span className="eyebrow">Admin · Brands</span>
          <h1 className="mt-2 font-display text-[28px] font-semibold tracking-[-0.025em] text-foreground">
            Brands
          </h1>
        </div>
        <Button
          onClick={() => {
            loadCompanies()
            setEditingBrand(null)
            setModalMode('create')
            setFormError('')
          }}
        >
          <Plus />
          Create Brand
        </Button>
      </div>

      {error && <ErrorBanner message={error} onRetry={() => setRefreshKey((k) => k + 1)} />}

      <div className="flex flex-wrap items-center gap-3">
        <select
          className={`${selectClass} sm:w-64`}
          value={companyFilter}
          onFocus={loadCompanies}
          onChange={(e) => setCompanyFilter(e.target.value ? Number(e.target.value) : '')}
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
          setModalMode(null)
          setEditingBrand(null)
        }}
        title={modalMode === 'create' ? 'Create Brand' : 'Edit Brand'}
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
            <Label htmlFor="brandName">Brand name</Label>
            <Input
              id="brandName"
              name="brandName"
              type="text"
              defaultValue={editingBrand?.brandName ?? ''}
              required
            />
          </div>
          <div className="grid grid-cols-2 gap-3">
            <div className="space-y-1.5">
              <Label htmlFor="companyId">Company</Label>
              <select
                id="companyId"
                name="companyId"
                className={selectClass}
                defaultValue={editingBrand?.company.companyId ?? ''}
                required={modalMode === 'create'}
              >
                {modalMode === 'edit' && <option value="">Keep current</option>}
                {companies.map((c) => (
                  <option key={c.companyId} value={c.companyId}>
                    {c.companyName}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="industry">Industry</Label>
              <Input
                id="industry"
                name="industry"
                type="text"
                defaultValue={editingBrand?.industry ?? ''}
                placeholder="Optional"
              />
            </div>
          </div>
          <div className="flex items-center justify-end gap-2 pt-2">
            <Button
              type="button"
              variant="secondary"
              onClick={() => {
                setModalMode(null)
                setEditingBrand(null)
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
        open={deletingBrand !== null}
        onClose={() => setDeletingBrand(null)}
        onConfirm={handleDelete}
        title="Delete Brand"
        message={`Are you sure you want to delete "${deletingBrand?.brandName}"? This action cannot be undone.`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />

      <Modal
        open={keywordsBrand !== null}
        onClose={() => {
          setKeywordsBrand(null)
          setRefreshKey((k) => k + 1)
        }}
        title={`Keywords - ${keywordsBrand?.brandName}`}
        width="lg"
      >
        {keywordsBrand && accessToken && (
          <KeywordPanel brandId={keywordsBrand.brandId} accessToken={accessToken} />
        )}
      </Modal>
    </div>
  )
}

export default BrandManagement
