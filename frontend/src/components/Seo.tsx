import { useEffect } from 'react'

/** Base URL for canonicals/OG; set VITE_SITE_URL at build time for production. */
export const SITE_URL = ((import.meta.env.VITE_SITE_URL as string | undefined) ?? '').replace(/\/$/, '')

function setMeta(attr: 'name' | 'property', key: string, content: string) {
  let el = document.head.querySelector<HTMLMetaElement>(`meta[${attr}="${key}"]`)
  if (!el) {
    el = document.createElement('meta')
    el.setAttribute(attr, key)
    document.head.appendChild(el)
  }
  el.content = content
}

/** Per-route head manager: title, description, canonical, OG/Twitter and JSON-LD. */
export default function Seo({ title, description, path, jsonLd = [] }: {
  title: string
  description: string
  path: string
  jsonLd?: object[]
}) {
  useEffect(() => {
    document.title = title
    setMeta('name', 'description', description)
    setMeta('property', 'og:title', title)
    setMeta('property', 'og:description', description)
    setMeta('property', 'og:type', 'website')
    setMeta('property', 'og:site_name', 'PDFHarbor')
    setMeta('property', 'og:image', `${SITE_URL}/og.png`)
    setMeta('property', 'og:url', `${SITE_URL}${path}`)
    setMeta('name', 'twitter:card', 'summary_large_image')

    let link = document.head.querySelector<HTMLLinkElement>('link[rel="canonical"]')
    if (!link) {
      link = document.createElement('link')
      link.rel = 'canonical'
      document.head.appendChild(link)
    }
    link.href = `${SITE_URL}${path}`

    const scripts = jsonLd.map((obj) => {
      const s = document.createElement('script')
      s.type = 'application/ld+json'
      s.textContent = JSON.stringify(obj)
      document.head.appendChild(s)
      return s
    })
    return () => scripts.forEach((s) => s.remove())
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [title, description, path])

  return null
}
