import { useState } from 'react'
import FileDrop from '../components/FileDrop'
import PdfThumb from '../components/PdfThumb'
import { api, apiUpload, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto } from '../lib/types'
import { ToolIcon } from '../tools'

export default function OrganizePage() {
  const [doc, setDoc] = useState<DocumentDto | null>(null)
  const [order, setOrder] = useState<number[]>([])
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
      setOrder(Array.from({ length: uploaded.pageCount }, (_, i) => i + 1))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  function move(index: number, delta: number) {
    setOrder((prev) => {
      const next = [...prev]
      const t = index + delta
      if (t < 0 || t >= next.length) return prev
      ;[next[index], next[t]] = [next[t], next[index]]
      return next
    })
  }

  async function save() {
    if (!doc) return
    setBusy(true)
    setError(null)
    try {
      setResult(await api<DocumentDto>('/api/tools/organize', { body: { documentId: doc.id, pages: order } }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const changed = doc?.pageCount !== order.length ||
    order.some((p, i) => p !== i + 1)

  return (
    <div className="mx-auto max-w-6xl px-4 py-12">
      <div className="mb-8 flex items-center gap-4">
        <ToolIcon family="pages" slug="organize" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Organize pages</h1>
          <p className="text-slate-500">Reorder or delete pages, then save a new PDF</p>
        </div>
      </div>

      {result ? (
        <div className="card mx-auto max-w-xl p-8 text-center">
          <h2 className="text-xl font-semibold">Done!</h2>
          <p className="mt-1 mb-6 text-slate-500">{result.name} · {formatBytes(result.sizeBytes)} · {result.pageCount} pages</p>
          <div className="flex justify-center gap-3">
            <a href={downloadUrl(result.id)} className="btn-primary">Download</a>
            <button onClick={() => { setDoc(null); setResult(null); setOrder([]) }} className="btn-secondary">Start over</button>
          </div>
        </div>
      ) : !doc ? (
        <div className="mx-auto max-w-3xl">
          <FileDrop onFiles={onFiles} />
          {error && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        </div>
      ) : (
        <>
          <div className="mb-6 flex flex-wrap items-center gap-3">
            <p className="text-sm text-slate-600"><span className="font-semibold">{doc.name}</span> · {order.length} of {doc.pageCount} pages kept</p>
            <div className="ml-auto flex gap-2">
              <button className="btn-secondary" onClick={() => setOrder(Array.from({ length: doc.pageCount! }, (_, i) => i + 1))}>Reset</button>
              <button className="btn-primary" disabled={busy || order.length === 0 || !changed} onClick={save}>
                {busy ? 'Saving…' : 'Save new PDF'}
              </button>
            </div>
          </div>
          {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
          <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-5">
            {order.map((pageNo, i) => (
              <div key={`${pageNo}-${i}`} className="card overflow-hidden">
                <PdfThumb docId={doc.id} page={pageNo} dpi={50} className="aspect-[3/4]" />
                <div className="flex items-center justify-between px-2 py-1.5">
                  <span className="text-xs font-medium text-slate-500">#{i + 1} · p{pageNo}</span>
                  <div className="flex gap-0.5">
                    <IconBtn onClick={() => move(i, -1)} disabled={i === 0} label="←" />
                    <IconBtn onClick={() => move(i, 1)} disabled={i === order.length - 1} label="→" />
                    <IconBtn onClick={() => setOrder(order.filter((_, x) => x !== i))} label="✕" danger />
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
