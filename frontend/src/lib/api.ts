import type { DocumentDto } from './types'

const TOKEN_KEY = 'pdfharbor.token'

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string | null) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

function authHeaders(): Record<string, string> {
  const token = getToken()
  return token ? { Authorization: `Bearer ${token}` } : {}
}

export async function api<T>(path: string, opts: { method?: string; body?: unknown } = {}): Promise<T> {
  const res = await fetch(path, {
    method: opts.method ?? (opts.body !== undefined ? 'POST' : 'GET'),
    headers: {
      ...(opts.body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...authHeaders(),
    },
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  })
  if (res.status === 204) return undefined as T
  const data = await res.json().catch(() => ({ error: `Request failed (HTTP ${res.status})` }))
  if (!res.ok) throw new Error(data.error ?? `Request failed (HTTP ${res.status})`)
  return data as T
}

export async function apiUpload(files: File[]): Promise<DocumentDto[]> {
  const form = new FormData()
  files.forEach((f) => form.append('file', f))
  const res = await fetch('/api/files', { method: 'POST', headers: authHeaders(), body: form })
  const data = await res.json().catch(() => ({ error: `Upload failed (HTTP ${res.status})` }))
  if (!res.ok) throw new Error(data.error ?? `Upload failed (HTTP ${res.status})`)
  return data as DocumentDto[]
}

export const downloadUrl = (id: string) => `/api/files/${id}/download`
export const pageUrl = (id: string, n: number, dpi = 96) => `/api/files/${id}/pages/${n}?dpi=${dpi}`

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
}
