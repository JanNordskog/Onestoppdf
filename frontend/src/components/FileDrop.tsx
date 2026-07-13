import { useRef, useState, type DragEvent } from 'react'

interface Props {
  accept?: string
  multiple?: boolean
  onFiles: (files: File[]) => void
  label?: string
}

export default function FileDrop({ accept = 'application/pdf', multiple = false, onFiles, label }: Props) {
  const inputRef = useRef<HTMLInputElement>(null)
  const [over, setOver] = useState(false)

  function handleDrop(e: DragEvent) {
    e.preventDefault()
    setOver(false)
    const files = Array.from(e.dataTransfer.files)
    if (files.length) onFiles(multiple ? files : files.slice(0, 1))
  }

  return (
    <button
      type="button"
      onClick={() => inputRef.current?.click()}
      onDragOver={(e) => { e.preventDefault(); setOver(true) }}
      onDragLeave={() => setOver(false)}
      onDrop={handleDrop}
      className={`w-full rounded-2xl border-2 border-dashed px-6 py-14 text-center transition ${
        over ? 'border-indigo-500 bg-indigo-50' : 'border-slate-300 bg-white hover:border-indigo-400 hover:bg-slate-50'
      }`}
    >
      <div className="mx-auto mb-3 grid h-12 w-12 place-items-center rounded-2xl bg-indigo-100 text-indigo-600">
        <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
          <path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" /><polyline points="17 8 12 3 7 8" /><line x1="12" y1="3" x2="12" y2="15" />
        </svg>
      </div>
      <p className="font-semibold text-slate-800">{label ?? (multiple ? 'Drop files here or click to browse' : 'Drop a file here or click to browse')}</p>
      <p className="mt-1 text-sm text-slate-500">Up to 100 MB per file · processed locally on this server · auto-deleted</p>
      <input
        ref={inputRef}
        type="file"
        accept={accept}
        multiple={multiple}
        className="hidden"
        onChange={(e) => {
          const files = Array.from(e.target.files ?? [])
          if (files.length) onFiles(files)
          e.target.value = ''
        }}
      />
    </button>
  )
}
