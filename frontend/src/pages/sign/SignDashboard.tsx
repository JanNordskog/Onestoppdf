import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { api } from '../../lib/api'
import type { SignSummary } from '../../lib/types'
import { ToolIcon } from '../../tools'
import ToolSeoBlock from '../../components/ToolSeoBlock'

const STATUS_STYLE: Record<string, string> = {
  SENT: 'bg-amber-100 text-amber-700',
  COMPLETED: 'bg-emerald-100 text-emerald-700',
  CANCELLED: 'bg-slate-200 text-slate-600',
}

export default function SignDashboard() {
  const [items, setItems] = useState<SignSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api<SignSummary[]>('/api/sign/requests').then(setItems).catch((e) => setError((e as Error).message))
  }, [])

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <div className="mb-8 flex items-center gap-4">
        <ToolIcon family="sign" slug="sign" />
        <div className="flex-1">
          <h1 className="text-3xl font-bold tracking-tight">eSign</h1>
          <p className="text-slate-500">Unlimited signature requests with tamper-evident audit trails</p>
        </div>
        <Link to="/sign/new" className="btn-primary">New request</Link>
      </div>

      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {items.length === 0 && !error ? (
        <div className="card p-14 text-center">
          <p className="text-lg font-medium text-slate-700">No signature requests yet</p>
          <p className="mx-auto mt-2 max-w-md text-sm text-slate-500">
            Upload a PDF, place signature fields, and send links to your signers.
            No envelope limits — this isn't DocuSign.
          </p>
          <Link to="/sign/new" className="btn-primary mt-6 inline-flex">Create your first request</Link>
        </div>
      ) : (
        <ul className="space-y-2">
          {items.map((r) => (
            <li key={r.id}>
              <Link to={`/sign/r/${r.id}`} className="card flex items-center gap-4 px-5 py-4 transition hover:shadow-md">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{r.title}</p>
                  <p className="text-xs text-slate-500">{new Date(r.createdAt).toLocaleString()} · {r.signerSummary}</p>
                </div>
                <span className={`rounded-full px-3 py-1 text-xs font-semibold ${STATUS_STYLE[r.status] ?? 'bg-slate-100'}`}>{r.status}</span>
              </Link>
            </li>
          ))}
        </ul>
      )}
      <ToolSeoBlock slug="sign" toolTitle="eSign" />
    </div>
  )
}
