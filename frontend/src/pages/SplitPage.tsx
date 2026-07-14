import { useState } from 'react'
import FileDrop from '../components/FileDrop'
import PdfThumb from '../components/PdfThumb'
import { api, apiUpload, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto } from '../lib/types'
import { ToolIcon } from '../tools'

export default function SplitPage() {
  const [doc, setDoc] = useState<DocumentDto | null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<DocumentDto | null>(null)

  async function onFiles(files: File[]) {
    setError(null)
    setBusy(true)
    try {
      const [uploaded] = await apiUpload(files)
      if (!uploaded.pageCount) throw new Error('That file is not a PDF')
      setDoc(uploaded)
      setSelected(new Set(Array.from({ length: uploaded.pageCount }, (_, i) => i + 1)))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  function toggle(page: number) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(page)) next.delete(page)
      else next.add(page)
      return next
    })
  }

  function setAll(on: boolean) {
    setSelected(on ? new Set(Array.from({ length: doc!.pageCount! }, (_, i) => i + 1)) : new Set())
  }

  function invert() {
    setSelected((prev) => new Set(
      Array.from({ length: doc!.pageCount! }, (_, i) => i + 1).filter((p) => !prev.has(p))))
  }

  async function run(ranges: string | null) {
    if (!doc) return
    setBusy(true)
    setError(null)
    try {
      setResult(await api<DocumentDto>('/api/tools/split', { body: { documentId: doc.id, ranges } }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const extract = () => run([...selected].sort((a, b) => a - b).join(','))

  if (result) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <div className="card p-8 text-center">
          <h2 className="text-xl font-semibold">Done!</h2>
          <p className="mt-1 mb-6 text-slate-500">
            {result.name} · {formatBytes(result.sizeBytes)}{result.pageCount ? ` · ${result.pageCount} pages` : ''}
          </p>
          <div className="flex justify-center gap-3">
            <a href={downloadUrl(result.id)} className="btn-primary">Download</a>
            <button onClick={() => { setDoc(null); setResult(null); setSelected(new Set()) }} className="btn-secondary">Split another</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <div className="mb-8 flex items-center gap-4">
        <ToolIcon family="pages" slug="split" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Split PDF</h1>
          <p className="text-slate-500">Click the pages you want to keep, then extract them</p>
        </div>
      </div>

      {!doc ? (
        <div className="mx-auto max-w-3xl">
          <FileDrop onFiles={onFiles} />
          {error && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        </div>
      ) : (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            <p className="text-sm text-slate-600">
              <span className="font-semibold">{doc.name}</span> · {selected.size} of {doc.pageCount} pages selected
            </p>
            <div className="ml-auto flex flex-wrap gap-2">
              <button className="btn-secondary" onClick={() => setAll(true)}>All</button>
              <button className="btn-secondary" onClick={() => setAll(false)}>None</button>
              <button className="btn-secondary" onClick={invert}>Invert</button>
              <button className="btn-secondary" disabled={busy} onClick={() => run(null)}
                      title="Every page becomes its own PDF, delivered as a ZIP">
                Split all to ZIP
              </button>
              <button className="btn-primary" disabled={busy || selected.size === 0} onClick={extract}>
                {busy ? 'Working…' : `Extract ${selected.size} page${selected.size === 1 ? '' : 's'}`}
              </button>
            </div>
          </div>
          {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {Array.from({ length: doc.pageCount! }, (_, i) => i + 1).map((page) => {
              const on = selected.has(page)
              return (
                <button key={page} onClick={() => toggle(page)}
                        className={`card relative overflow-hidden text-left transition ${
                          on ? 'ring-2 ring-indigo-500' : 'opacity-45 grayscale hover:opacity-75'}`}>
                  <PdfThumb docId={doc.id} page={page} dpi={50} className="pointer-events-none aspect-[3/4]" />
                  <span className={`absolute right-2 top-2 grid h-6 w-6 place-items-center rounded-full text-xs font-bold text-white ${
                    on ? 'bg-indigo-600' : 'bg-slate-400'}`}>
                    {on ? '✓' : '–'}
                  </span>
                  <span className="block px-2 py-1.5 text-xs font-medium text-slate-500">Page {page}</span>
                </button>
              )
            })}
          </div>
        </>
      )}
    </div>
  )
}
