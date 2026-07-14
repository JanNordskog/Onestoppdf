/**
 * Post-build SEO pass: writes a static HTML shell per route with correct
 * title, meta description, canonical, Open Graph tags and JSON-LD, plus
 * sitemap.xml, robots.txt and llms.txt. Crawlers and social scrapers get
 * real metadata without executing JavaScript; React hydrates as usual.
 *
 * Set SITE_URL when building for production:  SITE_URL=https://yourdomain.com npm run build
 */
import { mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = dirname(fileURLToPath(import.meta.url))
const dist = join(root, '..', 'dist')
const seo = JSON.parse(readFileSync(join(root, '..', 'src', 'seo-content.json'), 'utf8'))

const SITE_URL = (process.env.SITE_URL ?? 'http://localhost:4173').replace(/\/$/, '')
if (!process.env.SITE_URL) {
  console.warn('[prerender] SITE_URL not set — canonicals point at http://localhost:4173. Set SITE_URL for production builds.')
}

const esc = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/"/g, '&quot;')
const template = readFileSync(join(dist, 'index.html'), 'utf8')

function pageHtml(path, title, description, jsonLd) {
  const head = [
    `<link rel="canonical" href="${SITE_URL}${path}" />`,
    `<meta property="og:title" content="${esc(title)}" />`,
    `<meta property="og:description" content="${esc(description)}" />`,
    `<meta property="og:type" content="website" />`,
    `<meta property="og:site_name" content="PDFHarbor" />`,
    `<meta property="og:url" content="${SITE_URL}${path}" />`,
    `<meta property="og:image" content="${SITE_URL}/og.png" />`,
    `<meta name="twitter:card" content="summary_large_image" />`,
    ...jsonLd.map((o) => `<script type="application/ld+json">${JSON.stringify(o)}</script>`),
  ].join('\n    ')
  return template
    .replace(/<title>[\s\S]*?<\/title>/, `<title>${esc(title)}</title>`)
    .replace(/(<meta name="description" content=")[^"]*(")/, `$1${esc(description)}$2`)
    .replace('</head>', `    ${head}\n  </head>`)
}

const routes = []

// Home
routes.push({ path: '/', title: seo.site.title, description: seo.site.description, jsonLd: [{
  '@context': 'https://schema.org',
  '@type': 'WebApplication',
  name: 'PDFHarbor',
  url: `${SITE_URL}/`,
  applicationCategory: 'UtilitiesApplication',
  operatingSystem: 'Web',
  description: seo.site.description,
  offers: { '@type': 'Offer', price: '0', priceCurrency: 'USD' },
}] })

// Tool pages
for (const [slug, t] of Object.entries(seo.tools)) {
  routes.push({ path: `/${slug}`, title: t.title, description: t.description, jsonLd: [
    {
      '@context': 'https://schema.org',
      '@type': 'HowTo',
      name: `How to: ${t.title.split('—')[0].split('|')[0].trim()}`,
      step: t.steps.map((text, i) => ({ '@type': 'HowToStep', position: i + 1, text })),
    },
    {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: t.faqs.map((f) => ({
        '@type': 'Question', name: f.q, acceptedAnswer: { '@type': 'Answer', text: f.a },
      })),
    },
  ] })
}

for (const r of routes) {
  const html = pageHtml(r.path, r.title, r.description, r.jsonLd)
  const outDir = r.path === '/' ? dist : join(dist, r.path.slice(1))
  mkdirSync(outDir, { recursive: true })
  writeFileSync(join(outDir, 'index.html'), html)
}

// sitemap.xml
const today = new Date().toISOString().slice(0, 10)
writeFileSync(join(dist, 'sitemap.xml'),
  `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n` +
  routes.map((r) => `  <url><loc>${SITE_URL}${r.path}</loc><lastmod>${today}</lastmod></url>`).join('\n') +
  `\n</urlset>\n`)

// robots.txt — everyone welcome, including AI crawlers
writeFileSync(join(dist, 'robots.txt'),
  `User-agent: *\nAllow: /\n\nSitemap: ${SITE_URL}/sitemap.xml\n`)

// llms.txt — cheap machine-readable site guide for AI agents
writeFileSync(join(dist, 'llms.txt'),
  `# PDFHarbor\n\n> ${seo.site.tagline} Free self-hosted PDF toolkit: no task limits, no watermarks, ` +
  `no sign-up for tools; anonymous files auto-delete after 2 hours.\n\n## Tools\n\n` +
  Object.entries(seo.tools).map(([slug, t]) =>
    `- [${t.title.split('—')[0].split('|')[0].trim()}](${SITE_URL}/${slug}): ${t.description}`).join('\n') +
  `\n\n## Notes\n\n- E-signing supports multiple signers, field placement and tamper-evident audit trails, with no envelope limits.\n` +
  `- The PDF editor rewrites text in place: original glyphs are removed from the content stream and replacements are drawn in a matching font.\n`)

console.log(`[prerender] wrote ${routes.length} routes + sitemap.xml + robots.txt + llms.txt (base: ${SITE_URL})`)
