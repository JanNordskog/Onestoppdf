import { Link } from 'react-router-dom'
import { BRAND } from '../brand'
import { TOOLS, ToolIcon } from '../tools'

const PROMISES = [
  { title: 'No meters, ever', text: 'No "2 tasks per day", no file-size paywalls, no watermarks. Every tool, unlimited.' },
  { title: 'Privacy by architecture', text: 'Runs on your own server. Anonymous files self-destruct after 2 hours — verifiably.' },
  { title: 'Unlimited e-signing', text: 'Send documents for signature with audit trails. No envelope limits, no per-send fees.' },
]

export default function Home() {
  return (
    <>
      <section className="border-b border-slate-200 bg-white">
        <div className="mx-auto max-w-6xl px-4 py-20 text-center">
          <p className="mb-4 inline-block rounded-full bg-indigo-50 px-4 py-1.5 text-sm font-medium text-indigo-700">
            The one-stop PDF workshop
          </p>
          <h1 className="mx-auto max-w-3xl text-4xl font-extrabold tracking-tight sm:text-5xl">
            Every PDF tool you need.<br />
            <span className="text-indigo-600">None of the limits you hate.</span>
          </h1>
          <p className="mx-auto mt-5 max-w-2xl text-lg text-slate-600">
            Merge, split, compress, convert, edit and e-sign — {BRAND.name} does it all on your own
            server, free of daily caps, upsells and upload-and-pray privacy policies.
          </p>
          <div className="mt-8 flex justify-center gap-3">
            <Link to="/merge" className="btn-primary !px-7 !py-3 text-base">Start with a merge</Link>
            <Link to="/sign" className="btn-secondary !px-7 !py-3 text-base">Send for signature</Link>
          </div>
          <img src="/hero.png" alt="A miniature harbor folded from paper — cut-paper waves, origami boats and a paper lighthouse"
               className="mx-auto mt-12 w-full max-w-4xl rounded-2xl border border-slate-200 shadow-sm" />
        </div>
      </section>

      <section className="mx-auto max-w-6xl px-4 py-16">
        <h2 className="mb-8 text-2xl font-bold tracking-tight">All tools</h2>
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {TOOLS.map((t) => (
            <Link key={t.slug} to={`/${t.slug}`}
                  className="card group flex items-start gap-4 p-5 transition hover:-translate-y-0.5 hover:shadow-md">
              <ToolIcon family={t.family} slug={t.slug} />
              <div>
                <h3 className="font-semibold group-hover:text-indigo-700">{t.title}</h3>
                <p className="mt-0.5 text-sm text-slate-500">{t.subtitle}</p>
              </div>
            </Link>
          ))}
        </div>
      </section>

      <section className="border-t border-slate-200 bg-white">
        <div className="mx-auto grid max-w-6xl gap-8 px-4 py-16 sm:grid-cols-3">
          {PROMISES.map((p) => (
            <div key={p.title}>
              <h3 className="mb-2 font-semibold text-slate-900">{p.title}</h3>
              <p className="text-sm leading-relaxed text-slate-600">{p.text}</p>
            </div>
          ))}
        </div>
      </section>
    </>
  )
}
