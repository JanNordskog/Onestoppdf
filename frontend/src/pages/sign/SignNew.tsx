import { useRef, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import FileDrop from '../../components/FileDrop'
import PdfThumb from '../../components/PdfThumb'
import { api, apiUpload } from '../../lib/api'
import type { DocumentDto, SignDetail } from '../../lib/types'

interface NewSigner { name: string; email: string }
interface NewField { key: number; signerIndex: number; page: number; type: 'SIGNATURE' | 'NAME' | 'DATE' | 'TEXT'; x: number; y: number; w: number; h: number }

const SIGNER_COLORS = ['#4f46e5', '#e11d48', '#059669', '#d97706', '#0284c7']
let fieldSeq = 1

export default function SignNew() {
  const navigate = useNavigate()
  const [step, setStep] = useState(1)
  const [doc, setDoc] = useState<DocumentDto | null>(null)
  const [title, setTitle] = useState('')
  const [message, setMessage] = useState('')
  const [signers, setSigners] = useState<NewSigner[]>([{ name: '', email: '' }])
  const [fields, setFields] = useState<NewField[]>([])
  const [activeSigner, setActiveSigner] = useState(0)
  const [page, setPage] = useState(1)
  const [selected, setSelected] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const stageRef = useRef<HTMLDivElement>(null)
  const drag = useRef<{ key: number; mode: 'move' | 'resize'; startX: number; startY: number; orig: NewField } | null>(null)

  async function onFiles(files: File[]) {
    setError(null)
    try {
      const [uploaded] = await apiUpload(files)
      if (!uploaded.pageCount) throw new Error('That file is not a PDF')
      setDoc(uploaded)
      if (!title) setTitle(uploaded.name.replace(/\.pdf$/i, ''))
      setStep(2)
    } catch (e) {
      setError((e as Error).message)
    }
  }

  const signersValid = signers.length > 0 && signers.every((s) => s.name.trim() && /\S+@\S+\.\S+/.test(s.email))

  function addField(type: NewField['type']) {
    const f: NewField = { key: fieldSeq++, signerIndex: activeSigner, page, type, x: 0.1, y: 0.75, w: type === 'SIGNATURE' ? 0.28 : 0.18, h: type === 'SIGNATURE' ? 0.08 : 0.04 }
    setFields((prev) => [...prev, f])
    setSelected(f.key)
  }

  function update(key: number, patch: Partial<NewField>) {
    setFields((prev) => prev.map((f) => (f.key === key ? { ...f, ...patch } : f)))
  }

  function onPointerDown(e: React.PointerEvent, key: number, mode: 'move' | 'resize') {
    e.stopPropagation()
    const f = fields.find((x) => x.key === key)!
    drag.current = { key, mode, startX: e.clientX, startY: e.clientY, orig: { ...f } }
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
      update(key, { x: Math.min(Math.max(orig.x + dx, 0), 1 - orig.w), y: Math.min(Math.max(orig.y + dy, 0), 1 - orig.h) })
    } else {
      update(key, { w: Math.min(Math.max(orig.w + dx, 0.05), 1 - orig.x), h: Math.min(Math.max(orig.h + dy, 0.02), 1 - orig.y) })
    }
  }

  async function send() {
    if (!doc) return
    setBusy(true)
    setError(null)
    try {
      const detail = await api<SignDetail>('/api/sign/requests', {
        body: {
          documentId: doc.id, title, message: message || null,
          signers,
          fields: fields.map(({ key, ...rest }) => rest),
        },
      })
      navigate(`/sign/r/${detail.id}`)
    } catch (e) {
      setError((e as Error).message)
      setBusy(false)
    }
  }

  const pageFields = fields.filter((f) => f.page === page)

  return (
    <div className="mx-auto max-w-5xl px-4 py-10">
      <h1 className="mb-2 text-3xl font-bold tracking-tight">New signature request</h1>
      <p className="mb-8 text-slate-500">
        {['1 — Upload the document', '2 — Who signs?', '3 — Place fields & send'][step - 1]}
      </p>
      {error && <div className="mb-4 rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {step === 1 && <div className="mx-auto max-w-3xl"><FileDrop onFiles={onFiles} /></div>}

      {step === 2 && doc && (
        <div className="mx-auto max-w-2xl space-y-5">
          <div className="card space-y-4 p-6">
            <div>
              <label className="label">Request title</label>
              <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} />
            </div>
            <div>
              <label className="label">Message to signers (optional)</label>
              <textarea className="input" rows={2} value={message} onChange={(e) => setMessage(e.target.value)} />
            </div>
          </div>
          <div className="card space-y-3 p-6">
            {signers.map((s, i) => (
              <div key={i} className="flex gap-2">
                <span className="mt-2.5 h-3.5 w-3.5 shrink-0 rounded-full" style={{ background: SIGNER_COLORS[i % SIGNER_COLORS.length] }} />
                <input className="input" placeholder="Name" value={s.name}
                       onChange={(e) => setSigners(signers.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)))} />
                <input className="input" placeholder="email@example.com" type="email" value={s.email}
                       onChange={(e) => setSigners(signers.map((x, j) => (j === i ? { ...x, email: e.target.value } : x)))} />
                <button className="btn-secondary" disabled={signers.length === 1}
                        onClick={() => setSigners(signers.filter((_, j) => j !== i))}>✕</button>
              </div>
            ))}
            <button className="btn-secondary" disabled={signers.length >= 5}
                    onClick={() => setSigners([...signers, { name: '', email: '' }])}>+ Add signer</button>
          </div>
          <div className="flex justify-between">
            <button className="btn-secondary" onClick={() => setStep(1)}>Back</button>
            <button className="btn-primary" disabled={!signersValid || !title.trim()} onClick={() => setStep(3)}>Place fields →</button>
          </div>
        </div>
      )}

      {step === 3 && doc && (
        <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
          <div>
            <div className="mb-3 flex flex-wrap items-center gap-2">
              <select className="input !w-auto" value={activeSigner} onChange={(e) => setActiveSigner(parseInt(e.target.value, 10))}>
                {signers.map((s, i) => <option key={i} value={i}>{s.name}</option>)}
              </select>
              {(['SIGNATURE', 'NAME', 'DATE', 'TEXT'] as const).map((t) => (
                <button key={t} className="btn-secondary" onClick={() => addField(t)}>+ {t.toLowerCase()}</button>
              ))}
              <div className="ml-auto flex items-center gap-2 text-sm">
                <button className="btn-secondary !px-2.5" disabled={page <= 1} onClick={() => setPage(page - 1)}>‹</button>
                <span className="text-slate-600">{page} / {doc.pageCount}</span>
                <button className="btn-secondary !px-2.5" disabled={page >= (doc.pageCount ?? 1)} onClick={() => setPage(page + 1)}>›</button>
              </div>
            </div>
            <div ref={stageRef} className="relative select-none overflow-hidden rounded-xl border border-slate-300 shadow-sm"
                 onPointerMove={onPointerMove} onPointerUp={() => (drag.current = null)} onPointerDown={() => setSelected(null)}>
              <PdfThumb docId={doc.id} page={page} dpi={110} />
              {pageFields.map((f) => (
                <div key={f.key} onPointerDown={(e) => onPointerDown(e, f.key, 'move')}
                     className={`absolute flex cursor-move items-center justify-center rounded border-2 border-dashed bg-white/60 text-[10px] font-bold uppercase tracking-wide ${selected === f.key ? 'ring-2 ring-offset-1' : ''}`}
                     style={{ left: `${f.x * 100}%`, top: `${f.y * 100}%`, width: `${f.w * 100}%`, height: `${f.h * 100}%`,
                              borderColor: SIGNER_COLORS[f.signerIndex % SIGNER_COLORS.length], color: SIGNER_COLORS[f.signerIndex % SIGNER_COLORS.length] }}>
                  {f.type}
                  {selected === f.key && (
                    <span onPointerDown={(e) => onPointerDown(e, f.key, 'resize')}
                          className="absolute -bottom-1.5 -right-1.5 h-3.5 w-3.5 cursor-nwse-resize rounded-full border-2 border-white"
                          style={{ background: SIGNER_COLORS[f.signerIndex % SIGNER_COLORS.length] }} />
                  )}
                </div>
              ))}
            </div>
          </div>
          <div className="space-y-4">
            <div className="card p-4 text-sm text-slate-600">
              <p className="mb-2 font-semibold text-slate-800">{fields.length} field{fields.length === 1 ? '' : 's'} placed</p>
              <p>Pick a signer, add fields, drag them into place. Each signer fills only their own color.</p>
              {selected != null && (
                <button className="btn-secondary mt-3 w-full !text-red-600"
                        onClick={() => { setFields(fields.filter((f) => f.key !== selected)); setSelected(null) }}>
                  Delete selected field
                </button>
              )}
            </div>
            <button className="btn-primary w-full !py-3" disabled={busy || fields.length === 0} onClick={send}>
              {busy ? 'Sending…' : 'Send for signature'}
            </button>
            <button className="btn-secondary w-full" onClick={() => setStep(2)}>Back</button>
          </div>
        </div>
      )}
    </div>
  )
}
