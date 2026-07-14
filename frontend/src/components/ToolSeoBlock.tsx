import { Link } from 'react-router-dom'
import seoContent from '../seo-content.json'
import { TOOLS } from '../tools'
import Seo, { SITE_URL } from './Seo'

type ToolSeo = {
  title: string
  description: string
  intro: string
  steps: string[]
  faqs: { q: string; a: string }[]
}

/**
 * Head metadata + answer-first visible content (intro, steps, FAQ) for a tool page,
 * with matching HowTo/FAQPage/Breadcrumb JSON-LD. The visible text and the markup
 * are generated from the same source so they always agree.
 */
export default function ToolSeoBlock({ slug, toolTitle }: { slug: string; toolTitle: string }) {
  const seo = (seoContent.tools as Record<string, ToolSeo>)[slug]
  if (!seo) return null

  const family = TOOLS.find((t) => t.slug === slug)?.family
  const related = TOOLS.filter((t) => t.family === family && t.slug !== slug).slice(0, 4)

  const jsonLd: object[] = [
    {
      '@context': 'https://schema.org',
      '@type': 'HowTo',
      name: `How to use ${toolTitle} on PDFHarbor`,
      step: seo.steps.map((text, i) => ({ '@type': 'HowToStep', position: i + 1, text })),
    },
    {
      '@context': 'https://schema.org',
      '@type': 'FAQPage',
      mainEntity: seo.faqs.map((f) => ({
        '@type': 'Question',
        name: f.q,
        acceptedAnswer: { '@type': 'Answer', text: f.a },
      })),
    },
    {
      '@context': 'https://schema.org',
      '@type': 'BreadcrumbList',
      itemListElement: [
        { '@type': 'ListItem', position: 1, name: 'PDFHarbor', item: `${SITE_URL}/` },
        { '@type': 'ListItem', position: 2, name: toolTitle, item: `${SITE_URL}/${slug}` },
      ],
    },
  ]

  return (
    <>
      <Seo title={seo.title} description={seo.description} path={`/${slug}`} jsonLd={jsonLd} />
      <section className="mx-auto mt-16 max-w-3xl border-t border-slate-200 pt-10 text-slate-600">
        <p className="leading-relaxed">{seo.intro}</p>

        <h2 className="mt-8 text-lg font-semibold text-slate-900">How it works</h2>
        <ol className="mt-3 list-decimal space-y-1.5 pl-5">
          {seo.steps.map((s) => <li key={s}>{s}</li>)}
        </ol>

        <h2 className="mt-8 text-lg font-semibold text-slate-900">Frequently asked questions</h2>
        <dl className="mt-3 space-y-5">
          {seo.faqs.map((f) => (
            <div key={f.q}>
              <dt className="font-medium text-slate-900">{f.q}</dt>
              <dd className="mt-1 leading-relaxed">{f.a}</dd>
            </div>
          ))}
        </dl>

        {related.length > 0 && (
          <>
            <h2 className="mt-8 text-lg font-semibold text-slate-900">Related tools</h2>
            <ul className="mt-3 flex flex-wrap gap-2">
              {related.map((t) => (
                <li key={t.slug}>
                  <Link to={`/${t.slug}`}
                        className="inline-block rounded-full border border-slate-200 bg-white px-4 py-1.5 text-sm text-slate-700 transition hover:border-indigo-300 hover:text-indigo-700">
                    {t.title}
                  </Link>
                </li>
              ))}
            </ul>
          </>
        )}
      </section>
    </>
  )
}
