import { useEffect, useRef, useState } from 'react'
import FileDrop from '../components/FileDrop'
import PdfThumb from '../components/PdfThumb'
import SignaturePad from '../components/SignaturePad'
import { api, apiUpload, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto, EditElement, PageWord } from '../lib/types'
import { ToolIcon } from '../tools'

type El = EditElement & { key: number }
let keySeq = 1

const CSS_FAMILY: Record<string, string> = {
  serif: 'Georgia, "Times New Roman", serif',
  sans: 'Arial, Helvetica, sans-serif',
  mono: '"Courier New", monospace',
}
const cssFamily = (f?: string) => CSS_FAMILY[f ?? 'sans'] ?? CSS_FAMILY.sans

export default function EditorPage() {
  const [doc, setDoc] = useState<DocumentDto | null>(null)
  const [page, setPage] = useState(1)
  const [els, setEls] = useState<El[]>([])
  const [selected, setSelected] = useState<number | null>(null)
  const [showPad, setShowPad] = useState(false)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<DocumentDto | null>(null)
  const [words, setWords] = useState<Record<number, PageWord[]>>({})
  const [editingWord, setEditingWord] = useState<{ word: PageWord; value: string } | null>(null)
  const [stageH, setStageH] = useState(0)
  const stageRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ key: number; mode: 'move' | 'resize'; startX: number; startY: number; orig: El } | null>(null)

  useEffect(() => {
    if (!doc || words[page]) return
    api<PageWord[]>(`/api/files/${doc.id}/pages/${page}/words`)
      .then((ws) => setWords((prev) => ({ ...prev, [page]: ws })))
      .catch(() => setWords((prev) => ({ ...prev, [page]: [] })))
  }, [doc, page])

  useEffect(() => {
    if (!doc || !stageRef.current) return
    const observer = new ResizeObserver(([entry]) => setStageH(entry.contentRect.height))
    observer.observe(stageRef.current)
    return () => observer.disconnect()
  }, [doc])

  async function onFiles(files: File[]) {
    setError(null)
    try {
      const [uploaded] = await apiUpload(files)
      if (!uploaded.pageCount) throw new Error('That file is not a PDF')
      setDoc(uploaded)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  function add(partial: Omit<El, 'key' | 'page'>) {
    const el: El = { ...partial, key: keySeq++, page }
    setEls((prev) => [...prev, el])
    setSelected(el.key)
  }

  function addImageFile(file: File) {
    const reader = new FileReader()
    reader.onload = () => add({ type: 'image', x: 0.1, y: 0.1, w: 0.25, h: 0.12, imageDataUrl: reader.result as string })
    reader.readAsDataURL(file)
  }

  function update(key: number, patch: Partial<El>) {
    setEls((prev) => prev.map((e) => (e.key === key ? { ...e, ...patch } : e)))
  }

  function onPointerDown(e: React.PointerEvent, key: number, mode: 'move' | 'resize') {
    e.stopPropagation()
    const el = els.find((x) => x.key === key)!
    drag.current = { key, mode, startX: e.clientX, startY: e.clientY, orig: { ...el } }
    setSelected(key)
    ;(e.currentTarget as HTMLElement).setPointerCapture(e.pointerId)
  }

  function onPointerMove(e: React.PointerEvent) {
    if (!drag.current || !stageRef.current) return
    const rect = stageRef.current.getBoundingClientRect()
    const dx = (e.clientX - drag.current.startX) / rect.width
    const dy = (e.clientY - drag.current.startY) / rect.height
    const { key, mode, orig } = drag.current
    if (mode === 'move') {
      update(key, {
        x: Math.min(Math.max(orig.x + dx, 0), 1 - orig.w),
        y: Math.min(Math.max(orig.y + dy, 0), 1 - orig.h),
      })
    } else {
      update(key, {
        w: Math.min(Math.max(orig.w + dx, 0.03), 1 - orig.x),
        h: Math.min(Math.max(orig.h + dy, 0.015), 1 - orig.y),
      })
    }
  }

  function onDoubleClick(e: React.MouseEvent) {
    if (!stageRef.current) return
    const rect = stageRef.current.getBoundingClientRect()
    const fx = (e.clientX - rect.left) / rect.width
    const fy = (e.clientY - rect.top) / rect.height
    const hit = (words[page] ?? []).find((w) =>
      fx >= w.x - 0.004 && fx <= w.x + w.w + 0.004 &&
      fy >= w.y - w.h * 0.3 && fy <= w.y + w.h * 1.3)
    if (hit) {
      setSelected(null)
      setEditingWord({ word: hit, value: hit.text })
    }
  }

  function commitWordEdit() {
    if (!editingWord) return
    const { word, value } = editingWord
    setEditingWord(null)
    if (!value.trim() || value === word.text) return
    add({ type: 'replace-text', x: word.x, y: word.y, w: word.w, h: word.h,
          text: value, fontSize: word.fontSize, color: word.color || '#111111',
          originalText: word.text, bold: word.bold, italic: word.italic, family: word.family })
  }

  async function save() {
    if (!doc || els.length === 0) return
    setBusy(true)
    setError(null)
    try {
      const elements = els.map(({ key, ...rest }) => rest)
      setResult(await api<DocumentDto>('/api/tools/edit', { body: { documentId: doc.id, elements } }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  const sel = els.find((e) => e.key === selected) ?? null
  const pageEls = els.filter((e) => e.page === page)

  if (result) {
    return (
      <div className="mx-auto max-w-xl px-4 py-16">
        <div className="card p-8 text-center">
          <h2 className="text-xl font-semibold">Edits applied!</h2>
          <p className="mt-1 mb-6 text-slate-500">{result.name} · {formatBytes(result.sizeBytes)}</p>
          <div className="flex justify-center gap-3">
            <a href={downloadUrl(result.id)} className="btn-primary">Download</a>
            <button onClick={() => { setDoc(null); setEls([]); setResult(null); setPage(1) }} className="btn-secondary">Edit another</button>
          </div>
        </div>
      </div>
    )
  }

  return (
    <div className="mx-auto max-w-6xl px-4 py-10">
      <div className="mb-6 flex items-center gap-4">
        <ToolIcon family="edit" slug="edit" />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">Edit PDF</h1>
          <p className="text-slate-500">Add text, highlights, boxes, images and your signature</p>
        </div>
      </div>

      {!doc ? (
        <div className="mx-auto max-w-3xl">
          <FileDrop onFiles={onFiles} />
          {error && <div className="mt-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
        </div>
      ) : (
        <div className="grid gap-6 lg:grid-cols-[1fr_290px]">
          <div>
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <button className="btn-secondary" onClick={() => add({ type: 'text', x: 0.1, y: 0.1, w: 0.3, h: 0.05, text: 'New text', fontSize: 14, color: '#111111' })}>+ Text</button>
              <button className="btn-secondary" onClick={() => add({ type: 'highlight', x: 0.1, y: 0.2, w: 0.3, h: 0.04, color: '#ffeb3b' })}>+ Highlight</button>
              <button className="btn-secondary" onClick={() => add({ type: 'rect', x: 0.1, y: 0.3, w: 0.25, h: 0.1, color: '#dc2626' })}>+ Box</button>
              <label className="btn-secondary cursor-pointer">+ Image
                <input type="file" accept="image/png,image/jpeg" className="hidden"
                       onChange={(e) => { const f = e.target.files?.[0]; if (f) addImageFile(f); e.target.value = '' }} />
              </label>
              <button className="btn-secondary" onClick={() => setShowPad(true)}>+ Signature</button>
              <div className="ml-auto flex items-center gap-2 text-sm">
                <button className="btn-secondary !px-2.5" disabled={page <= 1} onClick={() => setPage(page - 1)}>‹</button>
                <span className="text-slate-600">{page} / {doc.pageCount}</span>
                <button className="btn-secondary !px-2.5" disabled={page >= (doc.pageCount ?? 1)} onClick={() => setPage(page + 1)}>›</button>
              </div>
            </div>

            <div ref={stageRef} className="relative select-none overflow-hidden rounded-xl border border-slate-300 shadow-sm"
                 onPointerMove={onPointerMove} onPointerUp={() => (drag.current = null)}
                 onPointerDown={() => setSelected(null)} onDoubleClick={onDoubleClick}>
              <PdfThumb docId={doc.id} page={page} dpi={110} />
              {pageEls.map((el) => (
                <div key={el.key}
                     onPointerDown={(e) => onPointerDown(e, el.key, 'move')}
                     className={`absolute cursor-move ${selected === el.key ? 'ring-2 ring-indigo-500' : 'ring-1 ring-transparent hover:ring-indigo-300'}`}
                     style={{ left: `${el.x * 100}%`, top: `${el.y * 100}%`, width: `${el.w * 100}%`, height: `${el.h * 100}%` }}>
                  {el.type === 'text' && (
                    <span className="block h-full w-full overflow-hidden whitespace-pre leading-tight" style={{ color: el.color, fontSize: `${(el.fontSize ?? 14) * 1.15}px` }}>{el.text}</span>
                  )}
                  {el.type === 'replace-text' && (
                    <span className="flex h-full w-full items-end overflow-visible whitespace-nowrap bg-white leading-none"
                          style={{ color: el.color, fontSize: `${Math.max(el.h * stageH * 0.95, 8)}px`,
                                   fontFamily: cssFamily(el.family),
                                   fontWeight: el.bold ? 700 : 400,
                                   fontStyle: el.italic ? 'italic' : 'normal',
                                   boxShadow: '0 0 0 3px white' }}>{el.text}</span>
                  )}
                  {el.type === 'highlight' && <span className="block h-full w-full" style={{ background: el.color, opacity: 0.35 }} />}
                  {el.type === 'rect' && <span className="block h-full w-full border-2" style={{ borderColor: el.color }} />}
                  {el.type === 'image' && <img src={el.imageDataUrl} className="h-full w-full object-fill" draggable={false} />}
                  {selected === el.key && (
                    <span onPointerDown={(e) => onPointerDown(e, el.key, 'resize')}
                          className="absolute -bottom-1.5 -right-1.5 h-3.5 w-3.5 cursor-nwse-resize rounded-full border-2 border-white bg-indigo-600" />
                  )}
                </div>
              ))}
              {editingWord && (
                <input autoFocus value={editingWord.value}
                       onChange={(e) => setEditingWord({ ...editingWord, value: e.target.value })}
                       onBlur={commitWordEdit}
                       onKeyDown={(e) => {
                         if (e.key === 'Enter') commitWordEdit()
                         if (e.key === 'Escape') setEditingWord(null)
                       }}
                       onPointerDown={(e) => e.stopPropagation()}
                       className="absolute z-10 rounded border-2 border-indigo-500 bg-white px-1 leading-none shadow-lg outline-none"
                       style={{ left: `${editingWord.word.x * 100}%`, top: `${(editingWord.word.y - editingWord.word.h * 0.3) * 100}%`,
                                width: `${Math.max(editingWord.word.w * 100 + 4, 14)}%`,
                                fontSize: `${Math.max(editingWord.word.h * stageH * 0.95, 10)}px`,
                                height: `${editingWord.word.h * stageH * 1.9}px`,
                                color: editingWord.word.color || '#111111',
                                fontFamily: cssFamily(editingWord.word.family),
                                fontWeight: editingWord.word.bold ? 700 : 400,
                                fontStyle: editingWord.word.italic ? 'italic' : 'normal' }} />
              )}
            </div>
          </div>

          <div className="space-y-4">
            <div className="card p-4">
              <p className="mb-3 text-sm font-semibold text-slate-700">{sel ? `Selected: ${sel.type}` : `${els.length} element${els.length === 1 ? '' : 's'} added`}</p>
              {sel ? (
                <div className="space-y-3">
                  {(sel.type === 'text' || sel.type === 'replace-text') && (
                    <>
                      <textarea className="input" rows={3} value={sel.text}
                                onChange={(e) => update(sel.key, { text: e.target.value })} />
                      <div className="flex gap-2">
                        <input type="number" className="input" value={sel.fontSize}
                               onChange={(e) => update(sel.key, { fontSize: parseFloat(e.target.value) || 14 })} />
                        <input type="color" className="h-10 w-14 rounded-lg border border-slate-300" value={sel.color}
                               onChange={(e) => update(sel.key, { color: e.target.value })} />
                      </div>
                    </>
                  )}
                  {(sel.type === 'highlight' || sel.type === 'rect') && (
                    <input type="color" className="h-10 w-full rounded-lg border border-slate-300" value={sel.color}
                           onChange={(e) => update(sel.key, { color: e.target.value })} />
                  )}
                  <button className="btn-secondary w-full !text-red-600"
                          onClick={() => { setEls(els.filter((e) => e.key !== sel.key)); setSelected(null) }}>
                    Delete element
                  </button>
                </div>
              ) : (
                <p className="text-sm text-slate-500">
                  <span className="font-medium text-slate-700">Double-click any word</span> on the page to
                  edit its text in place. Or add elements with the toolbar and drag them into position.
                </p>
              )}
            </div>
            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
            <button className="btn-primary w-full !py-3" disabled={busy || els.length === 0} onClick={save}>
              {busy ? 'Applying…' : `Apply ${els.length} edit${els.length === 1 ? '' : 's'} & download`}
            </button>
          </div>
        </div>
      )}

      {showPad && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4" onClick={() => setShowPad(false)}>
          <div className="w-full max-w-lg" onClick={(e) => e.stopPropagation()}>
            <SignaturePad onCancel={() => setShowPad(false)}
                          onDone={(dataUrl) => { add({ type: 'image', x: 0.55, y: 0.8, w: 0.3, h: 0.1, imageDataUrl: dataUrl }); setShowPad(false) }} />
          </div>
        </div>
      )}
    </div>
  )
}
