import { useEffect, useState } from 'react'
import { api, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto, JobDto } from '../lib/types'

export default function MyFiles() {
  const [tab, setTab] = useState<'files' | 'jobs'>('files')
  const [files, setFiles] = useState<DocumentDto[]>([])
  const [jobs, setJobs] = useState<JobDto[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    Promise.all([api<DocumentDto[]>('/api/me/files'), api<JobDto[]>('/api/me/jobs')])
      .then(([f, j]) => { setFiles(f); setJobs(j) })
      .catch((e) => setError((e as Error).message))
  }, [])

  async function remove(id: string) {
    await api(`/api/files/${id}`, { method: 'DELETE' })
    setFiles((prev) => prev.filter((f) => f.id !== id))
  }

  return (
    <div className="mx-auto max-w-4xl px-4 py-12">
      <h1 className="mb-6 text-3xl font-bold tracking-tight">My files</h1>
      <div className="mb-6 flex gap-2">
        <button onClick={() => setTab('files')} className={tab === 'files' ? 'btn-primary' : 'btn-secondary'}>Files ({files.length})</button>
        <button onClick={() => setTab('jobs')} className={tab === 'jobs' ? 'btn-primary' : 'btn-secondary'}>History ({jobs.length})</button>
      </div>
      {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

      {tab === 'files' ? (
        files.length === 0 ? (
          <p className="py-16 text-center text-slate-500">Nothing here yet — files you process while signed in stay in your account.</p>
        ) : (
          <ul className="space-y-2">
            {files.map((f) => (
              <li key={f.id} className="card flex items-center gap-4 px-5 py-3.5">
                <div className="min-w-0 flex-1">
                  <p className="truncate font-medium">{f.name}</p>
                  <p className="text-xs text-slate-500">
                    {formatBytes(f.sizeBytes)}{f.pageCount ? ` · ${f.pageCount} pages` : ''} · {new Date(f.createdAt).toLocaleString()}
                  </p>
                </div>
                <a className="btn-secondary" href={downloadUrl(f.id)}>Download</a>
                <button className="btn-secondary" onClick={() => remove(f.id)}>Delete</button>
              </li>
            ))}
          </ul>
        )
      ) : jobs.length === 0 ? (
        <p className="py-16 text-center text-slate-500">No tool runs recorded yet.</p>
      ) : (
        <ul className="space-y-2">
          {jobs.map((j) => (
            <li key={j.id} className="card flex items-center gap-4 px-5 py-3.5">
              <span className={`rounded-full px-2.5 py-0.5 text-xs font-semibold ${j.status === 'DONE' ? 'bg-emerald-100 text-emerald-700' : 'bg-red-100 text-red-700'}`}>
                {j.status}
              </span>
              <div className="min-w-0 flex-1">
                <p className="font-medium">{j.tool}</p>
                <p className="truncate text-xs text-slate-500">{j.inputNames} · {new Date(j.createdAt).toLocaleString()}</p>
              </div>
              {j.outputDocId && <a className="btn-secondary" href={downloadUrl(j.outputDocId)}>Download</a>}
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}
