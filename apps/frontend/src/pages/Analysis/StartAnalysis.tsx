import { useState, useEffect, useCallback, useRef } from 'react'
import { useLocation, useNavigate, Link } from 'react-router-dom'
import {
  ArrowLeft,
  ClipboardPaste,
  Download,
  Settings2,
  Upload,
  Loader2,
} from 'lucide-react'
import { getBrands, getBrand } from '../../services/brandService'
import { startAnalysis } from '../../services/reportService'
import {
  downloadDatasetTemplate,
  startAnalysisFromCsv,
  startAnalysisFromPaste,
} from '../../services/datasetService'
import { DatasetValidationFailed } from '../../types/dataset'
import type {
  DatasetValidationError,
  PastePreview,
  PasteColumnMapping,
} from '../../types/dataset'
import {
  KEYWORD_TYPE_LABELS,
  type BrandSummaryResponse,
  type KeywordInfo,
} from '../../types/brand'
import type { DataSource } from '../../types/dashboard'
import ErrorBanner from '../../components/ErrorBanner/ErrorBanner'
import PageSkeleton from '../../components/PageSkeleton/PageSkeleton'
import { useBrandSelection } from '../../context/useBrandSelection'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Slider } from '@/components/ui/slider'
import { Textarea } from '@/components/ui/textarea'
import { cn } from '@/lib/utils'
import { selectClass as baseSelectClass } from '@/lib/select-styles'

const MAX_POSTS_MIN = 1
const MAX_POSTS_MAX = 500
const clampMaxPosts = (n: number) => Math.min(MAX_POSTS_MAX, Math.max(MAX_POSTS_MIN, n))

interface LocationState {
  preselectedBrandId?: number
}

const MAX_FILE_BYTES = 10 * 1024 * 1024
const TEXT_CANDIDATES = ['text', 'post', 'post_text', 'content', 'body', 'message', 'tweet']
const URL_CANDIDATES = ['url', 'link', 'href', 'source']
const LANG_CANDIDATES = ['language', 'lang']

const selectClass = `${baseSelectClass} w-full`

function detectDelimiter(text: string): '\t' | ',' {
  const firstLine = text.split('\n')[0] ?? ''
  const tabs = (firstLine.match(/\t/g) ?? []).length
  const commas = (firstLine.match(/,/g) ?? []).length
  return tabs >= commas ? '\t' : ','
}

function parseCsvRows(raw: string, delimiter: string): string[][] {
  const rows: string[][] = []
  let field = ''
  let row: string[] = []
  let inQuotes = false
  for (let i = 0; i < raw.length; i++) {
    const c = raw[i]
    if (inQuotes) {
      if (c === '"') {
        if (raw[i + 1] === '"') {
          field += '"'
          i++
        } else {
          inQuotes = false
        }
      } else {
        field += c
      }
      continue
    }
    if (c === '"') {
      inQuotes = true
    } else if (c === delimiter) {
      row.push(field)
      field = ''
    } else if (c === '\n' || c === '\r') {
      row.push(field)
      field = ''
      if (row.some((v) => v.length > 0)) rows.push(row)
      row = []
      if (c === '\r' && raw[i + 1] === '\n') i++
    } else {
      field += c
    }
  }
  if (field.length > 0 || row.length > 0) {
    row.push(field)
    if (row.some((v) => v.length > 0)) rows.push(row)
  }
  return rows
}

function parsePasteText(raw: string): PastePreview {
  const delimiter = detectDelimiter(raw)
  const rows = parseCsvRows(raw, delimiter)
  if (rows.length === 0) {
    return {
      rawText: raw,
      headers: [],
      previewRows: [],
      mapping: { text: null, url: null, language: null },
    }
  }
  const headers = rows[0].map((h) => h.trim())
  const dataRows = rows.slice(1).map((r) => r.map((c) => c.trim()))
  const find = (candidates: string[]) =>
    headers.find((h) => candidates.includes(h.toLowerCase())) ?? null
  const mapping: PasteColumnMapping = {
    text: find(TEXT_CANDIDATES),
    url: find(URL_CANDIDATES),
    language: find(LANG_CANDIDATES),
  }
  return { rawText: raw, headers, previewRows: dataRows.slice(0, 5), mapping }
}

const Fieldset = ({
  legend,
  children,
}: {
  legend: string
  children: React.ReactNode
}) => (
  <fieldset className="space-y-4 rounded-md border border-border bg-card p-6">
    <legend className="px-1 eyebrow">{legend}</legend>
    {children}
  </fieldset>
)

const StartAnalysis = (): React.JSX.Element => {
  const navigate = useNavigate()
  const location = useLocation()
  const { selectedBrandId: navbarBrandId, setSelectedBrandId: setNavbarBrandId } =
    useBrandSelection()
  const preselectedBrandId = (location.state as LocationState | null)?.preselectedBrandId

  const [brands, setBrands] = useState<BrandSummaryResponse[]>([])
  const [loadingBrands, setLoadingBrands] = useState(true)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [selectedBrandId, setSelectedBrandId] = useState<number | ''>(
    preselectedBrandId ?? navbarBrandId ?? '',
  )
  const [dataSource, setDataSource] = useState<DataSource>('REDDIT')
  const [includeComments, setIncludeComments] = useState(false)
  const [maxPosts, setMaxPosts] = useState(50)
  const [dateFrom, setDateFrom] = useState('')
  const [dateTo, setDateTo] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<string | null>(null)

  const [file, setFile] = useState<File | null>(null)
  const [csvErrors, setCsvErrors] = useState<DatasetValidationError[]>([])
  const [templateDownloading, setTemplateDownloading] = useState(false)
  const [templateError, setTemplateError] = useState<string | null>(null)
  const fileInputRef = useRef<HTMLInputElement | null>(null)

  const [datasetTab, setDatasetTab] = useState<'upload' | 'paste'>('upload')
  const [pastePreview, setPastePreview] = useState<PastePreview | null>(null)
  const pasteAreaRef = useRef<HTMLTextAreaElement | null>(null)
  const [brandKeywords, setBrandKeywords] = useState<KeywordInfo[]>([])
  const [loadingKeywords, setLoadingKeywords] = useState(false)
  const [keywordError, setKeywordError] = useState<string | null>(null)
  const [selectedKeywordIds, setSelectedKeywordIds] = useState<Set<number>>(new Set())

  const loadBrands = useCallback(async () => {
    setLoadingBrands(true)
    setLoadError(null)
    try {
      const page = await getBrands(0, 100)
      setBrands(page.content)
    } catch (err) {
      setLoadError(err instanceof Error ? err.message : 'Something went wrong')
    } finally {
      setLoadingBrands(false)
    }
  }, [])

  useEffect(() => {
    loadBrands()
  }, [loadBrands])

  useEffect(() => {
    if (!selectedBrandId) {
      setBrandKeywords([])
      setSelectedKeywordIds(new Set())
      setKeywordError(null)
      return
    }
    let cancelled = false
    setLoadingKeywords(true)
    setKeywordError(null)
    getBrand(Number(selectedBrandId))
      .then((detail) => {
        if (cancelled) return
        setBrandKeywords(detail.keywords)
        setSelectedKeywordIds(new Set(detail.keywords.map((k) => k.keywordId)))
      })
      .catch((err) => {
        if (cancelled) return
        setKeywordError(err instanceof Error ? err.message : 'Failed to load keywords')
        setBrandKeywords([])
        setSelectedKeywordIds(new Set())
      })
      .finally(() => {
        if (!cancelled) setLoadingKeywords(false)
      })
    return () => {
      cancelled = true
    }
  }, [selectedBrandId])

  const validDateRange = !dateFrom || !dateTo || new Date(dateFrom) <= new Date(dateTo)
  const backTarget = preselectedBrandId ? `/brands/${preselectedBrandId}` : '/dashboard'
  const backLabel = preselectedBrandId ? 'Back to brand' : 'Back to dashboard'

  const handleFileChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const picked = e.target.files?.[0] ?? null
    setSubmitError(null)
    setCsvErrors([])
    if (!picked) {
      setFile(null)
      return
    }
    const name = picked.name.toLowerCase()
    if (!name.endsWith('.csv')) {
      setSubmitError('Only .csv files are supported')
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      return
    }
    if (picked.size > MAX_FILE_BYTES) {
      setSubmitError('File too large (max 10 MB)')
      setFile(null)
      if (fileInputRef.current) fileInputRef.current.value = ''
      return
    }
    setFile(picked)
  }

  const handleDownloadTemplate = async () => {
    setTemplateDownloading(true)
    setTemplateError(null)
    try {
      await downloadDatasetTemplate()
    } catch (err) {
      setTemplateError(err instanceof Error ? err.message : 'Failed to download template')
    } finally {
      setTemplateDownloading(false)
    }
  }

  const handlePaste = (e: React.ClipboardEvent<HTMLTextAreaElement>) => {
    const text = e.clipboardData.getData('text')
    if (!text.trim()) return
    e.preventDefault()
    setSubmitError(null)
    setCsvErrors([])
    setPastePreview(parsePasteText(text))
  }

  const toggleKeyword = (keywordId: number) => {
    setSelectedKeywordIds((prev) => {
      const next = new Set(prev)
      if (next.has(keywordId)) next.delete(keywordId)
      else next.add(keywordId)
      return next
    })
  }

  const selectAllKeywords = () =>
    setSelectedKeywordIds(new Set(brandKeywords.map((k) => k.keywordId)))
  const clearKeywords = () => setSelectedKeywordIds(new Set())

  const submitPaste = (brandId: number) =>
    startAnalysisFromPaste(brandId, {
      rawText: pastePreview!.rawText,
      textColumn: pastePreview!.mapping.text as string,
      urlColumn: pastePreview!.mapping.url ?? undefined,
      languageColumn: pastePreview!.mapping.language ?? undefined,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
    })

  const submitUpload = (brandId: number) =>
    startAnalysisFromCsv(brandId, {
      file: file as File,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
    })

  const submitExternal = (brandId: number) =>
    startAnalysis(brandId, {
      dataSource,
      dateFrom: dateFrom || undefined,
      dateTo: dateTo || undefined,
      selectedKeywordIds: Array.from(selectedKeywordIds),
      includeComments: dataSource === 'REDDIT' ? includeComments : undefined,
      maxPosts,
    })

  const validateBeforeSubmit = (): boolean => {
    if (!selectedBrandId || !validDateRange) return false
    if (dataSource === 'CSV_UPLOAD') {
      if (datasetTab === 'upload' && !file) {
        setSubmitError('Select a CSV file to upload')
        return false
      }
      if (datasetTab === 'paste') {
        if (!pastePreview || !pastePreview.rawText.trim()) {
          setSubmitError('Paste your data first')
          return false
        }
        if (!pastePreview.mapping.text) {
          setSubmitError("Map a column to 'Text' before submitting")
          return false
        }
      }
    }
    if (selectedKeywordIds.size === 0) return false
    return true
  }

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!validateBeforeSubmit()) return
    setSubmitting(true)
    setSubmitError(null)
    setCsvErrors([])
    try {
      const brandId = Number(selectedBrandId)
      let report
      if (dataSource === 'CSV_UPLOAD' && datasetTab === 'paste') {
        report = await submitPaste(brandId)
      } else if (dataSource === 'CSV_UPLOAD') {
        report = await submitUpload(brandId)
      } else {
        report = await submitExternal(brandId)
      }
      navigate(`/reports/${report.reportId}`, { state: { report } })
    } catch (err) {
      if (err instanceof DatasetValidationFailed) {
        setCsvErrors(err.errors)
        setFile(null)
        if (fileInputRef.current) fileInputRef.current.value = ''
      } else {
        setSubmitError(err instanceof Error ? err.message : 'Failed to start analysis')
      }
    } finally {
      setSubmitting(false)
    }
  }

  if (loadingBrands) return <PageSkeleton />
  if (loadError) return <ErrorBanner message={loadError} onRetry={loadBrands} />

  const selectedBrand = brands.find((b) => b.brandId === selectedBrandId)
  const hasSelection = selectedKeywordIds.size > 0

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
        <Link to={backTarget}>
          <ArrowLeft />
          {backLabel}
        </Link>
      </Button>

      <header>
        <span className="eyebrow">Analysis</span>
        <h1 className="mt-2 font-display text-[32px] font-semibold leading-none tracking-[-0.025em] text-foreground">
          Start Analysis
        </h1>
        <p className="mt-3 text-[14px] text-muted-foreground">
          {selectedBrand ? (
            <>
              Run a sentiment analysis for{' '}
              <strong className="font-medium text-foreground">{selectedBrand.brandName}</strong>.
              Pick the keywords to use for this run.
            </>
          ) : (
            'Select a brand and configure the analysis below.'
          )}
        </p>
      </header>

      {brands.length === 0 ? (
        <div className="rounded-md border border-dashed border-border bg-card px-6 py-10 text-center">
          <p className="text-[14px] text-muted-foreground">You don't have any brands yet.</p>
          <Button asChild className="mt-4">
            <Link to="/brands">Go to Brands</Link>
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4">
          <Fieldset legend="Brand">
            <div className="space-y-1.5">
              <Label htmlFor="brandSelect">Select a brand</Label>
              <select
                id="brandSelect"
                className={selectClass}
                value={selectedBrandId}
                onChange={(e) => {
                  const next = e.target.value === '' ? '' : Number(e.target.value)
                  setSelectedBrandId(next)
                  if (next !== '') setNavbarBrandId(next)
                }}
                required
              >
                <option value="" disabled>
                  Choose a brand…
                </option>
                {brands.map((b) => (
                  <option key={b.brandId} value={b.brandId}>
                    {b.brandName}
                    {b.industry ? ` - ${b.industry}` : ''}
                  </option>
                ))}
              </select>
            </div>
            {selectedBrand && (
              <Button asChild variant="ghost" size="sm" className="-ml-2 w-fit">
                <Link to={`/brands/${selectedBrand.brandId}`}>
                  <Settings2 />
                  Configure keywords for {selectedBrand.brandName}
                </Link>
              </Button>
            )}
          </Fieldset>

          {selectedBrandId !== '' && (
            <Fieldset legend="Keywords">
              {loadingKeywords ? (
                <p className="text-[13px] text-muted-foreground">Loading keywords…</p>
              ) : keywordError ? (
                <p className="text-[13px] text-neg-text">{keywordError}</p>
              ) : brandKeywords.length === 0 ? (
                <p className="text-[13px] text-neg-text">
                  This brand has no keywords. Add at least one on the brand page before
                  starting an analysis.
                </p>
              ) : (
                <>
                  <p className="text-[13px] text-muted-foreground">
                    Only selected keywords will drive post collection and LLM context for this
                    run.
                  </p>
                  <div className="flex gap-3 text-[12px]">
                    <button
                      type="button"
                      onClick={selectAllKeywords}
                      className="text-primary hover:underline"
                    >
                      Select all
                    </button>
                    <button
                      type="button"
                      onClick={clearKeywords}
                      className="text-primary hover:underline"
                    >
                      Clear
                    </button>
                  </div>
                  <ul className="grid grid-cols-1 gap-2 sm:grid-cols-2">
                    {brandKeywords.map((kw) => (
                      <li key={kw.keywordId}>
                        <label className="flex cursor-pointer items-center gap-2.5 rounded-md border border-border bg-muted/40 px-3 py-2 transition-colors hover:bg-muted">
                          <input
                            type="checkbox"
                            checked={selectedKeywordIds.has(kw.keywordId)}
                            onChange={() => toggleKeyword(kw.keywordId)}
                            className="size-3.5 accent-primary"
                          />
                          <span className="flex-1 text-[13px] text-foreground">{kw.keyword}</span>
                          <span className="font-mono text-[10px] uppercase tracking-[0.06em] text-text-3">
                            {KEYWORD_TYPE_LABELS[kw.keywordType]}
                          </span>
                        </label>
                      </li>
                    ))}
                  </ul>
                  {!hasSelection && (
                    <p className="text-[13px] text-neg-text">Select at least one keyword.</p>
                  )}
                </>
              )}
            </Fieldset>
          )}

          <Fieldset legend="Data source">
            {(
              [
                {
                  value: 'REDDIT' as const,
                  title: 'Reddit',
                  desc: 'Collect posts from Reddit using the selected keywords.',
                },
                {
                  value: 'CSV_UPLOAD' as const,
                  title: 'Upload Custom Dataset',
                  desc: "Upload a CSV of posts you've collected yourself.",
                },
              ]
            ).map((opt) => {
              const active = dataSource === opt.value
              return (
                <label
                  key={opt.value}
                  className={cn(
                    'flex cursor-pointer gap-3 rounded-md border bg-card p-4 transition-colors',
                    active
                      ? 'border-primary ring-1 ring-primary/15'
                      : 'border-border hover:border-border-strong',
                  )}
                >
                  <input
                    type="radio"
                    name="dataSource"
                    value={opt.value}
                    checked={active}
                    onChange={() => {
                      setDataSource(opt.value)
                      if (opt.value === 'REDDIT') setCsvErrors([])
                    }}
                    className="mt-0.5 size-3.5 shrink-0 accent-primary"
                  />
                  <div className="flex flex-col gap-1">
                    <span className="text-[13px] font-medium text-foreground">{opt.title}</span>
                    <span className="text-[12px] text-muted-foreground">{opt.desc}</span>
                  </div>
                </label>
              )
            })}

            {dataSource === 'REDDIT' && (
              <div className="mt-1 space-y-3 border-l-2 border-border pl-4">
                <div className="space-y-2">
                  <div className="flex items-end justify-between gap-3">
                    <Label htmlFor="maxPosts">Posts to collect</Label>
                    <Input
                      id="maxPosts"
                      type="text"
                      inputMode="numeric"
                      pattern="[0-9]*"
                      aria-label="Posts to collect"
                      value={maxPosts}
                      onChange={(e) => {
                        const digits = e.target.value.replace(/\D/g, '')
                        if (digits === '') {
                          setMaxPosts(MAX_POSTS_MIN)
                          return
                        }
                        setMaxPosts(clampMaxPosts(parseInt(digits, 10)))
                      }}
                      className="w-20 text-right"
                    />
                  </div>
                  <Slider
                    min={MAX_POSTS_MIN}
                    max={MAX_POSTS_MAX}
                    step={1}
                    value={[maxPosts]}
                    onValueChange={(v) => setMaxPosts(clampMaxPosts(v[0] ?? MAX_POSTS_MIN))}
                    aria-label="Posts to collect slider"
                  />
                </div>
                <span className="eyebrow">Content to collect</span>
                {(
                  [
                    {
                      value: false,
                      title: 'Submissions only',
                      desc: 'Reddit posts (titles + bodies) that mention your keywords.',
                    },
                    {
                      value: true,
                      title: 'Submissions + Comments',
                      desc: 'Also pull top comments from each submission, counted against the same total.',
                    },
                  ] as const
                ).map((opt) => {
                  const active = includeComments === opt.value
                  return (
                    <label
                      key={String(opt.value)}
                      className={cn(
                        'flex cursor-pointer gap-3 rounded-md border bg-card p-3 transition-colors',
                        active
                          ? 'border-primary ring-1 ring-primary/15'
                          : 'border-border hover:border-border-strong',
                      )}
                    >
                      <input
                        type="radio"
                        name="redditContent"
                        checked={active}
                        onChange={() => setIncludeComments(opt.value)}
                        className="mt-0.5 size-3.5 shrink-0 accent-primary"
                      />
                      <div className="flex flex-col gap-1">
                        <span className="text-[13px] font-medium text-foreground">{opt.title}</span>
                        <span className="text-[12px] text-muted-foreground">{opt.desc}</span>
                      </div>
                    </label>
                  )
                })}
              </div>
            )}

            {dataSource === 'CSV_UPLOAD' && (
              <div className="space-y-4">
                <div className="inline-flex rounded-md bg-muted p-1">
                  {(['upload', 'paste'] as const).map((t) => {
                    const active = datasetTab === t
                    return (
                      <button
                        key={t}
                        type="button"
                        onClick={() => {
                          setDatasetTab(t)
                          setCsvErrors([])
                          setSubmitError(null)
                        }}
                        className={cn(
                          'inline-flex items-center gap-1.5 rounded-sm px-3 py-1.5 text-[12px] font-medium transition-all',
                          active
                            ? 'bg-card text-foreground shadow-sm'
                            : 'text-muted-foreground hover:text-foreground',
                        )}
                      >
                        {t === 'upload' ? (
                          <Upload className="size-3.5" />
                        ) : (
                          <ClipboardPaste className="size-3.5" />
                        )}
                        {t === 'upload' ? 'Upload file' : 'Paste data'}
                      </button>
                    )
                  })}
                </div>

                {datasetTab === 'upload' && (
                  <>
                    <div className="space-y-2">
                      <Button
                        type="button"
                        variant="secondary"
                        size="sm"
                        onClick={handleDownloadTemplate}
                        disabled={templateDownloading}
                      >
                        {templateDownloading ? (
                          <Loader2 className="size-4 animate-spin" />
                        ) : (
                          <Download />
                        )}
                        {templateDownloading ? 'Preparing…' : 'Download template'}
                      </Button>
                      <p className="text-[12px] text-muted-foreground">
                        Columns: <code className="font-mono text-[11px]">text</code> (required),{' '}
                        <code className="font-mono text-[11px]">url</code> (optional),{' '}
                        <code className="font-mono text-[11px]">language</code> (EN or AR,
                        defaults to EN). Max 10 MB.
                      </p>
                      {templateError && (
                        <p className="text-[12px] text-neg-text">{templateError}</p>
                      )}
                    </div>

                    <div className="space-y-1.5">
                      <Label>CSV file</Label>
                      <div className="flex flex-wrap items-center gap-3">
                        <label className="inline-flex h-9 cursor-pointer items-center gap-2 rounded-md border border-border-strong bg-card px-4 text-[13px] font-medium text-foreground transition-colors hover:bg-muted">
                          <Upload className="size-3.5" />
                          {file ? 'Choose another file' : 'Choose file'}
                          <input
                            ref={fileInputRef}
                            type="file"
                            accept=".csv,text/csv"
                            onChange={handleFileChange}
                            className="hidden"
                          />
                        </label>
                        <span className="text-[13px] text-muted-foreground">
                          {file ? file.name : 'No file selected'}
                        </span>
                      </div>
                    </div>
                  </>
                )}

                {datasetTab === 'paste' && (
                  <>
                    {!pastePreview ? (
                      <div className="flex flex-col items-center gap-3 rounded-md border border-dashed border-border bg-muted/30 p-8 text-center">
                        <ClipboardPaste className="size-6 text-text-3" />
                        <p className="text-[13px] text-muted-foreground">
                          Copy rows from Excel, Google Sheets, or a CSV file, then paste here
                        </p>
                        <Textarea
                          ref={pasteAreaRef}
                          placeholder="Paste your data here (Cmd+V / Ctrl+V)…"
                          rows={4}
                          onPaste={handlePaste}
                          readOnly
                          aria-label="Paste dataset"
                          className="w-full font-mono text-[12px]"
                        />
                      </div>
                    ) : (
                      <div className="space-y-3 rounded-md border border-border bg-muted/30 p-4">
                        <div className="flex items-center justify-between">
                          <span className="text-[12px] text-muted-foreground">
                            {pastePreview.headers.length} column
                            {pastePreview.headers.length !== 1 ? 's' : ''} detected · map each
                            to a field
                          </span>
                          <button
                            type="button"
                            onClick={() => {
                              setPastePreview(null)
                              setCsvErrors([])
                            }}
                            className="text-[12px] text-primary hover:underline"
                          >
                            Clear
                          </button>
                        </div>

                        <div className="grid grid-cols-1 gap-2 sm:grid-cols-3">
                          {(['text', 'url', 'language'] as const).map((field) => (
                            <div key={field} className="space-y-1.5">
                              <Label className="capitalize">
                                {field}
                                {field === 'text' && (
                                  <span className="ml-1 text-neg-text">*</span>
                                )}
                              </Label>
                              <select
                                className={selectClass}
                                value={pastePreview.mapping[field] ?? ''}
                                onChange={(e) => {
                                  const val = e.target.value || null
                                  setPastePreview((prev) =>
                                    prev
                                      ? { ...prev, mapping: { ...prev.mapping, [field]: val } }
                                      : prev,
                                  )
                                }}
                              >
                                <option value="">- ignored -</option>
                                {pastePreview.headers.map((h) => (
                                  <option key={h} value={h}>
                                    {h}
                                  </option>
                                ))}
                              </select>
                            </div>
                          ))}
                        </div>

                        {pastePreview.previewRows.length > 0 && (
                          <div className="rounded-md border border-border bg-card">
                            <div className="overflow-x-auto">
                              <table className="w-full text-[12px]">
                                <thead>
                                  <tr className="border-b border-border">
                                    {pastePreview.headers.map((h) => (
                                      <th
                                        key={h}
                                        className="px-3 py-2 text-left font-mono text-[10px] uppercase tracking-[0.1em] text-text-3"
                                      >
                                        {h}
                                      </th>
                                    ))}
                                  </tr>
                                </thead>
                                <tbody>
                                  {pastePreview.previewRows.map((row, i) => (
                                    <tr key={i} className="border-b border-border last:border-0">
                                      {pastePreview.headers.map((_, j) => (
                                        <td key={j} className="px-3 py-2 text-foreground">
                                          {row[j] ?? ''}
                                        </td>
                                      ))}
                                    </tr>
                                  ))}
                                </tbody>
                              </table>
                            </div>
                            <p className="border-t border-border px-3 py-2 text-[11px] text-muted-foreground">
                              Showing first {pastePreview.previewRows.length} rows
                            </p>
                          </div>
                        )}
                      </div>
                    )}
                  </>
                )}

                {csvErrors.length > 0 && (
                  <div
                    role="alert"
                    aria-live="polite"
                    className="rounded-md border border-neg/30 bg-neg-bg p-4"
                  >
                    <p className="mb-2 text-[13px] font-medium text-neg-text">
                      {datasetTab === 'paste'
                        ? "The pasted data couldn't be processed. Fix these issues:"
                        : "The uploaded file couldn't be processed. Fix these issues and select the file again:"}
                    </p>
                    <ul className="space-y-1 text-[12px] text-neg-text">
                      {csvErrors.map((err, i) => (
                        <li key={i} className="flex flex-wrap gap-1.5">
                          {err.field && (
                            <code className="rounded bg-neg/10 px-1.5 py-0.5 font-mono text-[11px]">
                              {err.field}
                            </code>
                          )}
                          <span>{err.message}</span>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            )}
          </Fieldset>

          <Fieldset legend="Date range (optional)">
            <div className="grid grid-cols-2 gap-3">
              <div className="space-y-1.5">
                <Label htmlFor="dateFrom">From</Label>
                <Input
                  id="dateFrom"
                  type="date"
                  value={dateFrom}
                  max={dateTo || undefined}
                  onChange={(e) => setDateFrom(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="dateTo">To</Label>
                <Input
                  id="dateTo"
                  type="date"
                  value={dateTo}
                  min={dateFrom || undefined}
                  onChange={(e) => setDateTo(e.target.value)}
                />
              </div>
            </div>
            {!validDateRange && (
              <p className="text-[13px] text-neg-text">
                End date must be on or after start date.
              </p>
            )}
          </Fieldset>

          {submitError && <ErrorBanner message={submitError} />}

          <div className="flex items-center justify-end gap-2 pt-2">
            <Button asChild variant="secondary">
              <Link to={backTarget}>Cancel</Link>
            </Button>
            <Button
              type="submit"
              disabled={
                submitting ||
                !validDateRange ||
                !selectedBrandId ||
                !hasSelection ||
                (dataSource === 'REDDIT' &&
                  (!Number.isInteger(maxPosts) || maxPosts < 1 || maxPosts > 500)) ||
                (dataSource === 'CSV_UPLOAD' && datasetTab === 'upload' && !file) ||
                (dataSource === 'CSV_UPLOAD' &&
                  datasetTab === 'paste' &&
                  (!pastePreview || !pastePreview.mapping.text))
              }
            >
              {submitting && <Loader2 className="size-4 animate-spin" />}
              {submitting ? 'Starting…' : 'Start analysis'}
            </Button>
          </div>
        </form>
      )}
    </div>
  )
}

export default StartAnalysis
