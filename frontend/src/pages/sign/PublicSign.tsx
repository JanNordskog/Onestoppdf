import { useEffect, useMemo, useState } from 'react'
import { useParams } from 'react-router-dom'
import PdfThumb from '../../components/PdfThumb'
import SignaturePad from '../../components/SignaturePad'
import { api } from '../../lib/api'
import type { PublicSignInfo } from '../../lib/types'
import { BRAND } from '../../brand'

export default function PublicSign() {
  const { token } = useParams()
  const [info, setInfo] = useState<PublicSignInfo | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [signature, setSignature] = useState<string | null>(null)
  const [textValues, setTextValues] = useState<Record<string, string>>({})
  const [showPad, setShowPad] = useState(false)
  const [busy, setBusy] = useState(false)

  useEffect(() => {
    api<PublicSignInfo>(`/api/public/sign/${token}`).then(setInfo).catch((e) => setError((e as Error).message))
  }, [token])

  const needsSignature = useMemo(() => info?.myFields.some((f) => f.type === 'SIGNATURE') ?? false, [info])
  const textFields = useMemo(() => info?.myFields.filter((f) => f.type === 'TEXT') ?? [], [info])
  const canComplete = info?.signerStatus === 'PENDING' && info.status === 'SENT'
    && (!needsSignature || !!signature)
    && textFields.every((f) => (textValues[f.id] ?? '').trim())

  async function complete() {
    setBusy(true)
    setError(null)
    try {
      const updated = await api<PublicSignInfo>(`/api/public/sign/${token}/complete`, {
        body: { signatureDataUrl: signature, textValues },
      })
      setInfo(updated)
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setBusy(false)
    }
  }

  if (error && !info) {
    return <div className="mx-auto max-w-lg px-4 py-24 text-center">
      <h1 className="text-2xl font-bold">This link isn't valid</h1>
      <p className="mt-2 text-slate-500">{error}</p>
    </div>
  }
  if (!info) return <div className="p-16 text-center text-slate-400">Loading…</div>

  const done = info.signerStatus === 'SIGNED'

  return (
    <div className="mx-auto max-w-4xl px-4 py-10">
      <p className="mb-1 text-sm font-medium text-indigo-600">{BRAND.name} · signature request</p>
      <h1 className="text-3xl font-bold tracking-tight">{info.requestTitle}</h1>
      <p className="mt-1 text-slate-500">
        {info.ownerName} asked <span className="font-medium text-slate-700">{info.signerName}</span> to sign this document.
      </p>
      {info.message && <p className="card mt-4 px-5 py-3 text-sm text-slate-600">"{info.message}"</p>}

      {done ? (
        <div className="card mt-8 p-8 text-center">
          <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full bg-emerald-100 text-emerald-600">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
          </div>
          <h2 className="text-xl font-semibold">
            {info.status === 'COMPLETED' ? 'All signatures collected!' : 'Thanks — your signature is in.'}
          </h2>
          <p className="mt-1 mb-6 text-slate-500">
            {info.status === 'COMPLETED'
              ? 'The final document includes a signature certificate page.'
              : 'You will be able to download the final document once every signer has signed.'}
          </p>
          <a href={`/api/public/sign/${token}/document`} className="btn-primary">Download document</a>
        </div>
      ) : (
        <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_280px]">
          <div className="space-y-6">
            {Array.from({ length: info.pageCount ?? 1 }, (_, i) => i + 1).map((p) => (
              <div key={p} className="relative overflow-hidden rounded-xl border border-slate-300 shadow-sm">
                <PdfThumb docId="" page={p} src={`/api/public/sign/${token}/pages/${p}`} />
                {info.myFields.filter((f) => f.page === p).map((f) => (
                  <div key={f.id}
                       onClick={() => f.type === 'SIGNATURE' && setShowPad(true)}
                       className={`absolute flex items-center justify-center rounded border-2 border-dashed text-[10px] font-bold uppercase ${
                         f.type === 'SIGNATURE' ? 'cursor-pointer border-rose-500 bg-rose-50/70 text-rose-600 hover:bg-rose-100/70' : 'border-indigo-400 bg-indigo-50/70 text-indigo-600'}`}
                       style={{ left: `${f.x * 100}%`, top: `${f.y * 100}%`, width: `${f.w * 100}%`, height: `${f.h * 100}%` }}>
                    {f.type === 'SIGNATURE' && signature
                      ? <img src={signature} className="h-full w-auto object-contain" />
                      : f.type === 'SIGNATURE' ? 'Click to sign' : f.type}
                  </div>
                ))}
              </div>
            ))}
          </div>

          <div className="space-y-4 lg:sticky lg:top-20 lg:self-start">
            {needsSignature && (
              <div className="card p-4">
                <p className="mb-2 text-sm font-semibold">Your signature</p>
                {signature ? (
                  <>
                    <img src={signature} className="mx-auto max-h-20 rounded border border-slate-200 bg-white p-2" />
                    <button className="btn-secondary mt-2 w-full" onClick={() => setShowPad(true)}>Redraw</button>
                  </>
                ) : (
                  <button className="btn-primary w-full" onClick={() => setShowPad(true)}>Draw signature</button>
                )}
              </div>
            )}
            {textFields.length > 0 && (
              <div className="card space-y-3 p-4">
                <p className="text-sm font-semibold">Fill in</p>
                {textFields.map((f, i) => (
                  <input key={f.id} className="input" placeholder={`Text field ${i + 1} (page ${f.page})`}
                         value={textValues[f.id] ?? ''}
                         onChange={(e) => setTextValues({ ...textValues, [f.id]: e.target.value })} />
                ))}
              </div>
            )}
            {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}
            <button className="btn-primary w-full !py-3" disabled={!canComplete || busy} onClick={complete}>
              {busy ? 'Submitting…' : 'Finish signing'}
            </button>
            <p className="text-xs leading-relaxed text-slate-400">
              By finishing you agree that your electronic signature is the legal equivalent of your handwritten
              signature. Your IP address and timestamp become part of the audit certificate.
            </p>
          </div>
        </div>
      )}

      {showPad && (
        <div className="fixed inset-0 z-50 grid place-items-center bg-slate-900/40 p-4" onClick={() => setShowPad(false)}>
          <div className="w-full max-w-lg" onClick={(e) => e.stopPropagation()}>
            <SignaturePad onCancel={() => setShowPad(false)}
                          onDone={(dataUrl) => { setSignature(dataUrl); setShowPad(false) }} />
          </div>
        </div>
      )}
    </div>
  )
}
