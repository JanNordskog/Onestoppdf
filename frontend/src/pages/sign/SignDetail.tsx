import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { api, downloadUrl } from '../../lib/api'
import type { SignDetail as Detail } from '../../lib/types'

export default function SignDetail() {
  const { id } = useParams()
  const [detail, setDetail] = useState<Detail | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [copied, setCopied] = useState<string | null>(null)

  function load() {
    api<Detail>(`/api/sign/requests/${id}`).then(setDetail).catch((e) => setError((e as Error).message))
  }
  useEffect(load, [id])

  async function cancel() {
    await api(`/api/sign/requests/${id}/cancel`, { method: 'POST', body: {} })
    load()
  }

  async function copy(url: string, signerId: string) {
    await navigator.clipboard.writeText(url)
    setCopied(signerId)
    setTimeout(() => setCopied(null), 1500)
  }

  if (error) return <div className="mx-auto max-w-3xl px-4 py-12"><div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div></div>
  if (!detail) return <div className="p-16 text-center text-slate-400">Loading…</div>

  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <Link to="/sign" className="text-sm text-indigo-600">← All requests</Link>
      <div className="mt-3 mb-8 flex flex-wrap items-center gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{detail.title}</h1>
        <span className={`rounded-full px-3 py-1 text-xs font-semibold ${
          detail.status === 'COMPLETED' ? 'bg-emerald-100 text-emerald-700' : detail.status === 'SENT' ? 'bg-amber-100 text-amber-700' : 'bg-slate-200 text-slate-600'}`}>
          {detail.status}
        </span>
        <div className="ml-auto flex gap-2">
          {detail.finalDocument && <a href={downloadUrl(detail.finalDocument.id)} className="btn-primary">Download signed PDF</a>}
          {detail.status === 'SENT' && <button onClick={cancel} className="btn-secondary !text-red-600">Cancel request</button>}
        </div>
      </div>

      <div className="card mb-6 divide-y divide-slate-100">
        {detail.signers.map((s) => (
          <div key={s.id} className="flex flex-wrap items-center gap-3 px-5 py-4">
            <div className="min-w-0 flex-1">
              <p className="font-medium">{s.name}</p>
              <p className="text-xs text-slate-500">{s.email}{s.signedAt ? ` · signed ${new Date(s.signedAt).toLocaleString()}` : ''}</p>
            </div>
            <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${s.status === 'SIGNED' ? 'bg-emerald-100 text-emerald-700' : 'bg-amber-100 text-amber-700'}`}>{s.status}</span>
            {s.status === 'PENDING' && detail.status === 'SENT' && (
              <button className="btn-secondary" onClick={() => copy(s.signUrl, s.id)}>
                {copied === s.id ? 'Copied!' : 'Copy sign link'}
              </button>
            )}
          </div>
        ))}
      </div>

      <h2 className="mb-3 text-lg font-semibold">Audit trail</h2>
      <div className="card px-5 py-2">
        {detail.audit.map((a, i) => (
          <div key={i} className="flex gap-3 border-b border-slate-100 py-3 text-sm last:border-0">
            <span className="w-40 shrink-0 text-slate-400">{new Date(a.at).toLocaleString()}</span>
            <span><span className="font-medium">{a.actor}</span> {a.action}{a.detail ? ` — ${a.detail}` : ''}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
