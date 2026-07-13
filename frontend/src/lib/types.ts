export interface DocumentDto {
  id: string
  name: string
  contentType: string
  sizeBytes: number
  pageCount: number | null
  createdAt: string
  expiresAt: string | null
}

export interface User {
  id: string
  email: string
  displayName: string
}

export interface AuthResponse {
  token: string
  user: User
}

export interface JobDto {
  id: string
  tool: string
  status: string
  inputNames: string
  outputDocId: string | null
  createdAt: string
}

/** A word on a PDF page; box bottom edge = text baseline (from /api/files/{id}/pages/{n}/words). */
export interface PageWord {
  text: string
  x: number
  y: number
  w: number
  h: number
  fontSize: number
  fontName: string
  bold: boolean
  italic: boolean
  family: 'serif' | 'sans' | 'mono'
  color: string
}

/** Editor overlay element; x/y/w/h are 0..1 page fractions, origin top-left. */
export interface EditElement {
  page: number
  type: 'text' | 'highlight' | 'rect' | 'image' | 'replace-text'
  x: number
  y: number
  w: number
  h: number
  text?: string
  fontSize?: number
  color?: string
  imageDataUrl?: string
  /** replace-text only: the word being replaced, so the backend can strip it from the text layer. */
  originalText?: string
  /** replace-text only: original word styling, so the replacement is rendered to look identical. */
  bold?: boolean
  italic?: boolean
  family?: 'serif' | 'sans' | 'mono'
}

export interface SignerDto {
  id: string
  name: string
  email: string
  status: 'PENDING' | 'SIGNED'
  signedAt: string | null
  signUrl: string
}

export interface AuditDto {
  at: string
  actor: string
  action: string
  detail: string | null
}

export interface SignSummary {
  id: string
  title: string
  status: string
  createdAt: string
  signerSummary: string
}

export interface SignDetail {
  id: string
  title: string
  message: string | null
  status: string
  document: DocumentDto
  finalDocument: DocumentDto | null
  signers: SignerDto[]
  audit: AuditDto[]
}

export interface PublicField {
  id: string
  page: number
  type: 'SIGNATURE' | 'NAME' | 'DATE' | 'TEXT'
  x: number
  y: number
  w: number
  h: number
}

export interface PublicSignInfo {
  requestTitle: string
  message: string | null
  ownerName: string
  status: string
  signerName: string
  signerStatus: string
  pageCount: number | null
  myFields: PublicField[]
}
