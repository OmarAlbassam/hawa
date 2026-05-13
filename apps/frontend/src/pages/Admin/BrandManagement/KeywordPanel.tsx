import { useState, useEffect } from 'react'
import {
  Plus,
  Pencil,
  Trash2,
  Check,
  X,
  ChevronLeft,
  ChevronRight,
  Loader2,
} from 'lucide-react'
import {
  getKeywords,
  createKeyword,
  updateKeyword,
  deleteKeyword,
} from '../../../services/adminService'
import type { Page, KeywordResponse } from '../../../types/admin'
import {
  KEYWORD_TYPES,
  KEYWORD_TYPE_LABELS,
  type KeywordType,
} from '../../../types/brand'
import Badge from '../../../components/Badge/Badge'
import ConfirmDialog from '../../../components/ConfirmDialog/ConfirmDialog'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table'

const TYPE_BADGE_VARIANT: Record<KeywordType, 'primary' | 'info' | 'warning' | 'default'> = {
  BRAND_NAME: 'primary',
  PRODUCT: 'info',
  MISSPELLING: 'warning',
  OTHER: 'default',
}

const selectClass =
  'flex h-9 rounded-md border border-input bg-card px-3 text-[13px] text-foreground transition-[border-color,box-shadow] focus-visible:outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/15 disabled:cursor-not-allowed disabled:opacity-50'

interface KeywordPanelProps {
  brandId: number
  accessToken: string
}

const KeywordPanel = ({ brandId, accessToken }: KeywordPanelProps): React.JSX.Element => {
  const [data, setData] = useState<Page<KeywordResponse> | null>(null)
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [refreshKey, setRefreshKey] = useState(0)

  const [newKeyword, setNewKeyword] = useState('')
  const [newType, setNewType] = useState<KeywordType>('BRAND_NAME')
  const [addLoading, setAddLoading] = useState(false)
  const [addError, setAddError] = useState('')

  const [editingId, setEditingId] = useState<number | null>(null)
  const [editKeyword, setEditKeyword] = useState('')
  const [editType, setEditType] = useState<KeywordType>('BRAND_NAME')
  const [editLoading, setEditLoading] = useState(false)

  const [deletingKeyword, setDeletingKeyword] = useState<KeywordResponse | null>(null)
  const [deleteLoading, setDeleteLoading] = useState(false)

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError('')
    getKeywords(accessToken, brandId, page)
      .then((result) => {
        if (!cancelled) setData(result)
      })
      .catch((err) => {
        if (!cancelled)
          setError(err instanceof Error ? err.message : 'Failed to load keywords')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [accessToken, brandId, page, refreshKey])

  const handleAdd = async () => {
    const trimmed = newKeyword.trim()
    if (!trimmed) return
    setAddLoading(true)
    setAddError('')
    try {
      await createKeyword(accessToken, brandId, { keyword: trimmed, keywordType: newType })
      setNewKeyword('')
      setNewType('BRAND_NAME')
      setPage(0)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setAddError(err instanceof Error ? err.message : 'Failed to add keyword')
    } finally {
      setAddLoading(false)
    }
  }

  const handleEditStart = (kw: KeywordResponse) => {
    setEditingId(kw.keywordId)
    setEditKeyword(kw.keyword)
    setEditType(kw.keywordType)
  }

  const handleEditSave = async () => {
    if (editingId === null) return
    const trimmed = editKeyword.trim()
    if (!trimmed) return
    setEditLoading(true)
    try {
      await updateKeyword(accessToken, brandId, editingId, {
        keyword: trimmed,
        keywordType: editType,
      })
      setEditingId(null)
      setRefreshKey((k) => k + 1)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update keyword')
    } finally {
      setEditLoading(false)
    }
  }

  const handleEditCancel = () => setEditingId(null)

  const handleDelete = async () => {
    if (!deletingKeyword) return
    setDeleteLoading(true)
    try {
      await deleteKeyword(accessToken, brandId, deletingKeyword.keywordId)
      setDeletingKeyword(null)
      if (keywords.length === 1 && page > 0) {
        setPage((p) => p - 1)
      } else {
        setRefreshKey((k) => k + 1)
      }
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to delete keyword')
      setDeletingKeyword(null)
    } finally {
      setDeleteLoading(false)
    }
  }

  const keywords = data?.content ?? []
  const totalPages = data?.totalPages ?? 0
  const totalElements = data?.totalElements ?? 0

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-2 sm:flex-row">
        <Input
          type="text"
          placeholder="Enter keyword…"
          value={newKeyword}
          onChange={(e) => setNewKeyword(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault()
              handleAdd()
            }
          }}
          disabled={addLoading}
          className="flex-1"
        />
        <select
          className={selectClass}
          value={newType}
          onChange={(e) => setNewType(e.target.value as KeywordType)}
          disabled={addLoading}
        >
          {KEYWORD_TYPES.map((t) => (
            <option key={t} value={t}>
              {KEYWORD_TYPE_LABELS[t]}
            </option>
          ))}
        </select>
        <Button onClick={handleAdd} disabled={addLoading || !newKeyword.trim()}>
          {addLoading ? <Loader2 className="size-4 animate-spin" /> : <Plus />}
          Add
        </Button>
      </div>

      {addError && (
        <p role="alert" className="text-[12px] text-neg-text">
          {addError}
        </p>
      )}
      {error && (
        <p role="alert" className="text-[12px] text-neg-text">
          {error}
        </p>
      )}

      {loading ? (
        <div className="rounded-md border border-dashed border-border py-8 text-center text-[13px] text-muted-foreground">
          Loading…
        </div>
      ) : keywords.length === 0 ? (
        <div className="rounded-md border border-dashed border-border py-8 text-center text-[13px] text-muted-foreground">
          No keywords yet. Add one above.
        </div>
      ) : (
        <>
          <div className="rounded-md border border-border">
            <Table>
              <TableHeader>
                <TableRow className="hover:bg-transparent">
                  <TableHead>Keyword</TableHead>
                  <TableHead>Type</TableHead>
                  <TableHead className="text-right" />
                </TableRow>
              </TableHeader>
              <TableBody>
                {keywords.map((kw) =>
                  editingId === kw.keywordId ? (
                    <TableRow key={kw.keywordId}>
                      <TableCell>
                        <Input
                          type="text"
                          value={editKeyword}
                          onChange={(e) => setEditKeyword(e.target.value)}
                          onKeyDown={(e) => {
                            if (e.key === 'Enter') {
                              e.preventDefault()
                              handleEditSave()
                            }
                            if (e.key === 'Escape') handleEditCancel()
                          }}
                          disabled={editLoading}
                          autoFocus
                        />
                      </TableCell>
                      <TableCell>
                        <select
                          className={selectClass}
                          value={editType}
                          onChange={(e) => setEditType(e.target.value as KeywordType)}
                          disabled={editLoading}
                        >
                          {KEYWORD_TYPES.map((t) => (
                            <option key={t} value={t}>
                              {KEYWORD_TYPE_LABELS[t]}
                            </option>
                          ))}
                        </select>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-pos hover:bg-pos-bg"
                            onClick={handleEditSave}
                            disabled={editLoading || !editKeyword.trim()}
                            aria-label="Save"
                          >
                            <Check className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={handleEditCancel}
                            disabled={editLoading}
                            aria-label="Cancel"
                          >
                            <X className="size-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ) : (
                    <TableRow key={kw.keywordId}>
                      <TableCell className="font-medium text-foreground">{kw.keyword}</TableCell>
                      <TableCell>
                        <Badge variant={TYPE_BADGE_VARIANT[kw.keywordType]}>
                          {KEYWORD_TYPE_LABELS[kw.keywordType]}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7"
                            onClick={() => handleEditStart(kw)}
                            aria-label="Edit keyword"
                          >
                            <Pencil className="size-3.5" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            className="h-7 w-7 text-neg hover:bg-neg-bg"
                            onClick={() => setDeletingKeyword(kw)}
                            aria-label="Delete keyword"
                          >
                            <Trash2 className="size-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ),
                )}
              </TableBody>
            </Table>
          </div>

          {totalPages > 1 && (
            <div className="flex items-center justify-between">
              <span className="text-[12px] text-muted-foreground">
                {totalElements} keyword{totalElements !== 1 ? 's' : ''}
              </span>
              <div className="flex items-center gap-2">
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => setPage((p) => p - 1)}
                  disabled={page === 0}
                  aria-label="Previous page"
                >
                  <ChevronLeft />
                </Button>
                <span className="font-mono text-[11px] uppercase tracking-[0.1em] text-text-3">
                  Page {page + 1} of {totalPages}
                </span>
                <Button
                  variant="ghost"
                  size="icon"
                  className="h-7 w-7"
                  onClick={() => setPage((p) => p + 1)}
                  disabled={page >= totalPages - 1}
                  aria-label="Next page"
                >
                  <ChevronRight />
                </Button>
              </div>
            </div>
          )}
        </>
      )}

      <ConfirmDialog
        open={deletingKeyword !== null}
        onClose={() => setDeletingKeyword(null)}
        onConfirm={handleDelete}
        title="Delete Keyword"
        message={`Are you sure you want to delete "${deletingKeyword?.keyword}"?`}
        confirmLabel="Delete"
        variant="destructive"
        loading={deleteLoading}
      />
    </div>
  )
}

export default KeywordPanel
