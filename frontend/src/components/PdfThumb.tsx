import { useState } from 'react'
import { pageUrl } from '../lib/api'

interface Props {
  docId: string
  page: number
  dpi?: number
  className?: string
  /** Override the image URL entirely (used by the public signer view). */
  src?: string
}

/** Server-rendered PDF page image with a light loading shimmer. */
export default function PdfThumb({ docId, page, dpi = 96, className = '', src }: Props) {
  const [loaded, setLoaded] = useState(false)
  return (
    <div className={`relative overflow-hidden bg-slate-100 ${className}`}>
      {!loaded && <div className="absolute inset-0 animate-pulse bg-slate-200" />}
      <img
        src={src ?? pageUrl(docId, page, dpi)}
        alt={`Page ${page}`}
        draggable={false}
        onLoad={() => setLoaded(true)}
        className={`block w-full select-none transition-opacity ${loaded ? 'opacity-100' : 'opacity-0'}`}
      />
    </div>
  )
}
