import { useMemo, useState } from 'react'
import { api, apiUpload, downloadUrl, formatBytes } from '../lib/api'
import type { DocumentDto } from '../lib/types'
import { ToolIcon, type ToolConfig } from '../tools'
import FileDropLazy from './FileDrop'
import ToolSeoBlock from './ToolSeoBlock'

export default function ToolPage({ config }: { config: ToolConfig }) {
  const [docs, setDocs] = useState<DocumentDto[]>([])
  const [values, setValues] = useState<Record<string, string>>(() =>
    Object.fromEntries((config.fields ?? []).map((f) => [f.name, f.default ?? ''])))
  const [uploading, setUploading] = useState(false)
  const [running, setRunning] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<DocumentDto | null>(null)

  const minFiles = config.minFiles ?? 1
  const ready = docs.length >= minFiles && !running && !uploading
  const styles = useMemo(() => ({ multiple: config.multiple ?? false }), [config])

  async function onFiles(files: File[]) {
    setError(null)
    setUploading(true)
    try {
      const uploaded = await apiUpload(files)
      setDocs((prev) => (styles.multiple ? [...prev, ...uploaded] : uploaded))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setUploading(false)
    }
  }

  function move(index: number, delta: number) {
    setDocs((prev) => {
      const next = [...prev]
      const target = index + delta
      if (target < 0 || target >= next.length) return prev
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }

  async function run() {
    if (!config.endpoint || !config.buildBody) return
    setError(null)
    setRunning(true)
    try {
      const body = config.buildBody(docs.map((d) => d.id), values)
      setResult(await api<DocumentDto>(config.endpoint, { body }))
    } catch (e) {
      setError((e as Error).message)
    } finally {
      setRunning(false)
    }
  }

  function reset() {
    setDocs([])
    setResult(null)
    setError(null)
  }

  return (
    <div className="mx-auto max-w-3xl px-4 py-12">
      <div className="mb-8 flex items-center gap-4">
        <ToolIcon family={config.family} slug={config.slug} />
        <div>
          <h1 className="text-3xl font-bold tracking-tight">{config.title}</h1>
          <p className="text-slate-500">{config.subtitle}</p>
        </div>
      </div>

      {result ? (
        <div className="card p-8 text-center">
          <div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-full bg-emerald-100 text-emerald-600">
            <svg width="26" height="26" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round"><polyline points="20 6 9 17 4 12" /></svg>
          </div>
          <h2 className="text-xl font-semibold">Done!</h2>
          <p className="mt-1 text-slate-500">{result.name} · {formatBytes(result.sizeBytes)}</p>
          {config.slug === 'compress' && docs[0] && (
            result.sizeBytes < docs[0].sizeBytes * 0.98 ? (
              <p className="mt-1 text-sm font-medium text-emerald-600">
                {Math.round(100 - (100 * result.sizeBytes) / docs[0].sizeBytes)}% smaller
                (was {formatBytes(docs[0].sizeBytes)})
              </p>
            ) : (
              <p className="mt-1 text-sm text-slate-500">
                This PDF is already optimized — squeezing it further would visibly hurt quality,
                so we kept it intact.
              </p>
            )
          )}
          <div className="mb-6" />
          <div className="flex justify-center gap-3">
            <a href={downloadUrl(result.id)} className="btn-primary">Download</a>
            <button onClick={reset} className="btn-secondary">Process another</button>
          </div>
        </div>
      ) : (
        <div className="space-y-6">
          {(docs.length === 0 || styles.multiple) && (
            <FileDropLazy accept={config.accept} multiple={styles.multiple} onFiles={onFiles} />
          )}

          {docs.length > 0 && (
            <ul className="space-y-2">
              {docs.map((doc, i) => (
                <li key={doc.id} className="card flex items-center gap-3 px-4 py-3">
                  <span className="grid h-9 w-9 place-items-center rounded-lg bg-red-50 text-red-500 text-xs font-bold">
                    {doc.contentType.includes('pdf') ? 'PDF' : 'IMG'}
                  </span>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-medium">{doc.name}</p>
                    <p className="text-xs text-slate-500">{formatBytes(doc.sizeBytes)}{doc.pageCount ? ` · ${doc.pageCount} page${doc.pageCount > 1 ? 's' : ''}` : ''}</p>
                  </div>
                  {styles.multiple && (
                    <div className="flex gap-1">
                      <button onClick={() => move(i, -1)} disabled={i === 0} className="btn-secondary !px-2.5" title="Move up">↑</button>
                      <button onClick={() => move(i, 1)} disabled={i === docs.length - 1} className="btn-secondary !px-2.5" title="Move down">↓</button>
                    </div>
                  )}
                  <button onClick={() => setDocs(docs.filter((d) => d.id !== doc.id))} className="btn-secondary !px-2.5" title="Remove">✕</button>
                </li>
              ))}
            </ul>
          )}

          {docs.length > 0 && (config.fields ?? []).length > 0 && (
            <div className="card grid gap-4 p-6 sm:grid-cols-2">
              {config.fields!.map((f) => (
                <div key={f.name} className={f.type === 'text' ? 'sm:col-span-2' : ''}>
                  <label className="label" htmlFor={f.name}>{f.label}</label>
                  {f.type === 'select' ? (
                    <select id={f.name} className="input" value={values[f.name]}
                            onChange={(e) => setValues({ ...values, [f.name]: e.target.value })}>
                      {f.options!.map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
                    </select>
                  ) : (
                    <input id={f.name} type={f.type} className="input" value={values[f.name]}
                           placeholder={f.placeholder}
                           step={f.type === 'number' ? 'any' : undefined}
                           onChange={(e) => setValues({ ...values, [f.name]: e.target.value })} />
                  )}
                  {f.help && <p className="mt-1 text-xs text-slate-500">{f.help}</p>}
                </div>
              ))}
            </div>
          )}

          {error && <div className="rounded-xl border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">{error}</div>}

          {docs.length > 0 && (
            <button onClick={run} disabled={!ready} className="btn-primary w-full !py-3.5 text-base">
              {running ? <Spinner /> : null}
              {running ? 'Working…' : uploading ? 'Uploading…' : (config.cta ?? config.title)}
            </button>
          )}
        </div>
      )}
      <ToolSeoBlock slug={config.slug} toolTitle={config.title} />
    </div>
  )
}

function Spinner() {
  return <span className="h-4 w-4 animate-spin rounded-full border-2 border-white/40 border-t-white" />
}
