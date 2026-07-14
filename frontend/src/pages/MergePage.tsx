import { useRef, useState } from 'react'
import FileDrop from '../components/FileDrop'
import PdfThumb from '../components/PdfThumb'
import { api, apiUpload, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto } from '../lib/types'
import { ToolIcon } from '../tools'

/** border + dot color per source file so pages stay visually traceable */
const COLORS: [string, string][] = [
  ['border-indigo-400', 'bg-indigo-500'],
  ['border-emerald-400', 'bg-emerald-500'],
  ['border-amber-400', 'bg-amber-500'],
  ['border-rose-400', 'bg-rose-500'],
  ['border-sky-400', 'bg-sky-500'],
  ['border-violet-400', 'bg-violet-500'],
  ['border-lime-400', 'bg-lime-500'],
  ['border-orange-400', 'bg-orange-500'],
]
const colorOf = (docIdx: number) => COLORS[docIdx % COLORS.length]

type PageRef = { key: number; docId: string; page: number }
let keySeq = 1

export default function MergePage() {
  const [docs, setDocs] = useState<DocumentDto[]>([])
  const [items, setItems] = useState<PageRef[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<DocumentDto | null>(null)
  const dragFrom = useRef<number | null>(null)

  async function onFiles(files: File[]) {
    setError(null)
    setBusy(true)
    try {
      const uploaded = await apiUpload(files)
      const bad = uploaded.find((d) => !d.pageCount)
      if (bad) throw new Error(`"${bad.name}" is not a PDF`)
      const newRefs = uploaded.flatMap((d) =>
        Array.from({ length: d.pageCount! }, (_, p) => ({ key: keySeq++, docId: d.id, page: p + 1 })))
      setDocs((prev) => [...prev, ...uploaded])
      setItems((prev) => [...prev, ...newRefs])
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  function move(i: number, delta: number) {
    setItems((prev) => {
      const t = i + delta
      if (t < 0 || t >= prev.length) return prev
      const next = [...prev]
      ;[next[i], next[t]] = [next[t], next[i]]
      return next
    })
  }

  function reorder(from: number, to: number) {
    setItems((prev) => {
      const next = [...prev]
      const [moved] = next.splice(from, 1)
      next.splice(to, 0, moved)
      return next
    })
  }

  function reset() {
    setItems(docs.flatMap((d) =>
      Array.from({ length: d.pageCount! }, (_, p) => ({ key: keySeq++, docId: d.id, page: p + 1 }))))
  }

  /** Alternate pages across files (A1 B1 A2 B2 …) — e.g. front/back scan piles. */
  function interleave() {
    setItems((prev) => {
      const buckets: PageRef[][] = []
      const byDoc = new Map<string, PageRef[]>()
      for (const p of prev) {
        if (!byDoc.has(p.docId)) {
          const arr: PageRef[] = []
          byDoc.set(p.docId, arr)
          buckets.push(arr)
        }
        byDoc.get(p.docId)!.push(p)
      }
      const out: PageRef[] = []
      for (let i = 0; buckets.some((b) => i < b.length); i++)
        for (const b of buckets) if (i < b.length) out.push(b[i])
      return out
    })
  }

  function removeFile(docId: string) {
    setItems((prev) => prev.filter((p) => p.docId !== docId))
  }

  async function mergeNow() {
    setBusy(true)
    setError(null)
    try {
      setResult(await api<DocumentDto>('/api/tools/merge-pages', {
        body: { items: items.map(({ docId, page }) => ({ documentId: docId, page })) },
      }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const fileOf = (docId: string) => docs.find((d) => d.id === docId)
  const docIdxOf = (docId: string) => Math.max(0, docs.findIndex((d) => d.id === docId))

  if (result) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <div className="card p-8 text-center">
          <h2 className="text-xl font-semibold">Merged!</h2>
          <p className="mt-1 mb-6 text-slate-500">{result.name} · {formatBytes(result.sizeBytes)} · {result.pageCount} pages</p>
          <div className="flex justify-center gap-3">
            <a href={downloadUrl(result.id)} className="btn-primary">Download</a>
            <button onClick={() => { setDocs([]); setItems([]); setResult(null) }} className="btn-secondary">Merge more</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <div className="mb-8 flex items-center gap-4">
        <ToolIcon family="pages" slug="merge" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Merge PDF</h1>
          <p className="text-slate-500">Drag pages into any order across files — you decide exactly how they merge</p>
        </div>
      </div>

      <div className="mx-auto mb-6 max-w-3xl">
        <FileDrop multiple onFiles={onFiles} />
      </div>
      {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {docs.length > 0 && (
        <>
          <div className="mb-4 flex flex-wrap items-center gap-2">
            {docs.map((d, j) => items.some((p) => p.docId === d.id) && (
              <span key={d.id} className="inline-flex items-center gap-2 rounded-full border border-slate-200 bg-white px-3 py-1 text-xs text-slate-600">
                <span className={`h-2.5 w-2.5 rounded-full ${colorOf(j)[1]}`} />
                <span className="max-w-40 truncate font-medium">{d.name}</span>
                <span>{items.filter((p) => p.docId === d.id).length} pg</span>
                <button onClick={() => removeFile(d.id)} className="text-red-400 hover:text-red-600" title="Remove this file's pages">✕</button>
              </span>
            ))}
            <div className="ml-auto flex gap-2">
              <button className="btn-secondary" onClick={reset}>Reset order</button>
              <button className="btn-secondary" disabled={new Set(items.map((p) => p.docId)).size < 2}
                      onClick={interleave} title="Alternate pages across files — great for front/back scan piles">
                Interleave
              </button>
              <button className="btn-primary" disabled={busy || items.length === 0} onClick={mergeNow}>
                {busy ? 'Working…' : `Merge ${items.length} page${items.length === 1 ? '' : 's'}`}
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {items.map((p, i) => (
              <div key={p.key} draggable
                   onDragStart={() => (dragFrom.current = i)}
                   onDragEnd={() => (dragFrom.current = null)}
                   onDragOver={(e) => {
                     e.preventDefault()
                     if (dragFrom.current !== null && dragFrom.current !== i) {
                       reorder(dragFrom.current, i)
                       dragFrom.current = i
                     }
                   }}
                   className={`card cursor-grab overflow-hidden border-t-4 active:cursor-grabbing ${colorOf(docIdxOf(p.docId))[0]}`}>
                <PdfThumb docId={p.docId} page={p.page} dpi={50} className="pointer-events-none aspect-[3/4]" />
                <div className="flex items-center justify-between px-2 py-1.5">
                  <span className="max-w-24 truncate text-xs font-medium text-slate-500"
                        title={`${fileOf(p.docId)?.name} — page ${p.page}`}>
                    #{i + 1} · p{p.page}
                  </span>
                  <div className="flex gap-0.5">
                    <IconBtn onClick={() => move(i, -1)} disabled={i === 0} label="←" />
                    <IconBtn onClick={() => move(i, 1)} disabled={i === items.length - 1} label="→" />
                    <IconBtn onClick={() => setItems(items.filter((_, x) => x !== i))} label="✕" danger />
                  </div>
                </div>
              </div>
            ))}
          </div>
        </>
      )}
    </div>
  )
}

function IconBtn({ onClick, disabled, label, danger }: { onClick: () => void; disabled?: boolean; label: string; danger?: boolean }) {
  return (
    <button onClick={onClick} disabled={disabled}
            className={`rounded-md px-1.5 py-0.5 text-xs transition disabled:opacity-30 ${danger ? 'text-red-500 hover:bg-red-50' : 'text-slate-600 hover:bg-slate-100'}`}>
      {label}
    </button>
  )
}
