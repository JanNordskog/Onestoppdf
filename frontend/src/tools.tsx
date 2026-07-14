export interface ToolField {
  name: string
  label: string
  type: 'text' | 'number' | 'password' | 'select'
  options?: { value: string; label: string }[]
  placeholder?: string
  default?: string
  help?: string
}

export interface ToolConfig {
  slug: string
  title: string
  subtitle: string
  family: 'pages' | 'convert' | 'secure' | 'edit' | 'sign'
  /** Custom-route tools render their own page; everything else uses <ToolPage>. */
  custom?: boolean
  accept?: string
  multiple?: boolean
  minFiles?: number
  fields?: ToolField[]
  endpoint?: string
  cta?: string
  buildBody?: (docIds: string[], v: Record<string, string>) => unknown
}

export const FAMILY_STYLES: Record<ToolConfig['family'], { tile: string; text: string }> = {
  pages: { tile: 'bg-indigo-100', text: 'text-indigo-600' },
  convert: { tile: 'bg-emerald-100', text: 'text-emerald-600' },
  secure: { tile: 'bg-amber-100', text: 'text-amber-600' },
  edit: { tile: 'bg-sky-100', text: 'text-sky-600' },
  sign: { tile: 'bg-rose-100', text: 'text-rose-600' },
}

const pagesOpt = (v: string) =>
  v.trim() ? v.split(',').map((s) => parseInt(s.trim(), 10)).filter((n) => !isNaN(n)) : null

export const TOOLS: ToolConfig[] = [
  {
    slug: 'merge', title: 'Merge PDF', subtitle: 'Arrange every page across files, then merge', family: 'pages',
    custom: true,
  },
  {
    slug: 'split', title: 'Split PDF', subtitle: 'Pick the pages to keep, visually', family: 'pages',
    custom: true,
  },
  { slug: 'organize', title: 'Organize pages', subtitle: 'Reorder or delete pages visually', family: 'pages', custom: true },
  {
    slug: 'rotate', title: 'Rotate PDF', subtitle: 'Turn pages the right way up', family: 'pages',
    endpoint: '/api/tools/rotate', cta: 'Rotate',
    fields: [
      { name: 'degrees', label: 'Rotation', type: 'select', default: '90', options: [{ value: '90', label: '90° clockwise' }, { value: '180', label: '180°' }, { value: '270', label: '90° counter-clockwise' }] },
      { name: 'pages', label: 'Pages', type: 'text', placeholder: 'e.g. 1,3 — empty = all pages' },
    ],
    buildBody: (ids, v) => ({ documentId: ids[0], degrees: parseInt(v.degrees || '90', 10), pages: pagesOpt(v.pages || '') }),
  },
  {
    slug: 'compress', title: 'Compress PDF', subtitle: 'Shrink file size, you pick the trade-off', family: 'pages',
    endpoint: '/api/tools/compress', cta: 'Compress',
    fields: [{ name: 'level', label: 'Compression level', type: 'select', default: 'medium', options: [{ value: 'low', label: 'Light — best quality' }, { value: 'medium', label: 'Balanced' }, { value: 'high', label: 'Strong — smallest file' }] }],
    buildBody: (ids, v) => ({ documentId: ids[0], level: v.level || 'medium' }),
  },
  {
    slug: 'watermark', title: 'Watermark', subtitle: 'Stamp a diagonal text across every page', family: 'edit',
    endpoint: '/api/tools/watermark', cta: 'Add watermark',
    fields: [
      { name: 'text', label: 'Watermark text', type: 'text', placeholder: 'CONFIDENTIAL' },
      { name: 'fontSize', label: 'Font size', type: 'number', default: '48' },
      { name: 'opacity', label: 'Opacity (0–1)', type: 'number', default: '0.25' },
    ],
    buildBody: (ids, v) => ({ documentId: ids[0], text: v.text, fontSize: parseFloat(v.fontSize || '48'), opacity: parseFloat(v.opacity || '0.25') }),
  },
  {
    slug: 'page-numbers', title: 'Page numbers', subtitle: 'Add "n / total" footers', family: 'edit',
    endpoint: '/api/tools/page-numbers', cta: 'Number pages',
    fields: [{ name: 'startAt', label: 'Start counting at', type: 'number', default: '1' }],
    buildBody: (ids, v) => ({ documentId: ids[0], startAt: parseInt(v.startAt || '1', 10) }),
  },
  { slug: 'edit', title: 'Edit PDF', subtitle: 'Edit text in place, like a Word document', family: 'edit', custom: true },
  {
    slug: 'pdf-to-word', title: 'PDF to Word', subtitle: 'Extraction-grade .docx — honest, no cloud', family: 'convert',
    endpoint: '/api/tools/pdf-to-word', cta: 'Convert to Word',
    buildBody: (ids) => ({ documentId: ids[0] }),
  },
  {
    slug: 'pdf-to-text', title: 'PDF to text', subtitle: 'Pull the plain text out of any PDF', family: 'convert',
    endpoint: '/api/tools/pdf-to-text', cta: 'Extract text',
    buildBody: (ids) => ({ documentId: ids[0] }),
  },
  {
    slug: 'pdf-to-images', title: 'PDF to images', subtitle: 'Every page as a crisp PNG (ZIP)', family: 'convert',
    endpoint: '/api/tools/pdf-to-images', cta: 'Convert to images',
    fields: [{ name: 'dpi', label: 'Resolution (DPI)', type: 'number', default: '150' }],
    buildBody: (ids, v) => ({ documentId: ids[0], dpi: parseFloat(v.dpi || '150') }),
  },
  {
    slug: 'images-to-pdf', title: 'Images to PDF', subtitle: 'JPG or PNG photos into one PDF', family: 'convert',
    accept: 'image/jpeg,image/png', multiple: true, minFiles: 1,
    endpoint: '/api/tools/images-to-pdf', cta: 'Create PDF',
    buildBody: (ids) => ({ documentIds: ids }),
  },
  {
    slug: 'extract-form-data', title: 'Extract form data', subtitle: 'Filled form fields → CSV or JSON. Nobody else has this.', family: 'convert',
    endpoint: '/api/tools/extract-form-data', cta: 'Extract data',
    fields: [{ name: 'format', label: 'Output format', type: 'select', default: 'json', options: [{ value: 'json', label: 'JSON' }, { value: 'csv', label: 'CSV' }] }],
    buildBody: (ids, v) => ({ documentId: ids[0], format: v.format || 'json' }),
  },
  {
    slug: 'protect', title: 'Protect PDF', subtitle: 'AES-256 password encryption', family: 'secure',
    endpoint: '/api/tools/protect', cta: 'Protect',
    fields: [{ name: 'password', label: 'Password', type: 'password', placeholder: 'At least 4 characters' }],
    buildBody: (ids, v) => ({ documentId: ids[0], password: v.password }),
  },
  {
    slug: 'unlock', title: 'Unlock PDF', subtitle: 'Remove a password you know', family: 'secure',
    endpoint: '/api/tools/unlock', cta: 'Unlock',
    fields: [{ name: 'password', label: 'Current password', type: 'password' }],
    buildBody: (ids, v) => ({ documentId: ids[0], password: v.password }),
  },
  {
    slug: 'strip-metadata', title: 'Strip metadata', subtitle: 'Remove author, title and hidden XMP data', family: 'secure',
    endpoint: '/api/tools/strip-metadata', cta: 'Strip metadata',
    buildBody: (ids) => ({ documentId: ids[0] }),
  },
  { slug: 'sign', title: 'eSign', subtitle: 'Send for signature — unlimited, with audit trail', family: 'sign', custom: true },
]

export function ToolIcon({ family, slug, className = '' }: { family: ToolConfig['family']; slug?: string; className?: string }) {
  const s = FAMILY_STYLES[family]
  if (slug) {
    return (
      <img src={`/icons/${slug}.png`} alt=""
           className={`h-11 w-11 shrink-0 rounded-xl object-cover ${className}`} />
    )
  }
  const paths: Record<ToolConfig['family'], string> = {
    pages: 'M8 3h8a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zm2 5h4M10 12h4m-4 4h2',
    convert: 'M4 7h11m0 0-3-3m3 3-3 3m8 4H9m0 0 3 3m-3-3 3-3',
    secure: 'M12 3l7 3v5c0 4.5-3 8-7 10-4-2-7-5.5-7-10V6l7-3zm0 7v4',
    edit: 'M17 3a2.8 2.8 0 1 1 4 4L8 20l-5 1 1-5L17 3z',
    sign: 'M3 17c3-6 5-6 6-3s2 3 4-1 4-3 8 1M4 21h16',
  }
  return (
    <span className={`grid h-11 w-11 shrink-0 place-items-center rounded-xl ${s.tile} ${s.text} ${className}`}>
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round">
        <path d={paths[family]} />
      </svg>
    </span>
  )
}
