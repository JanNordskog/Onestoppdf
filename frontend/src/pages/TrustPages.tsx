import { Link } from 'react-router-dom'
import Seo, { SITE_URL } from '../components/Seo'
import seoContent from '../seo-content.json'

const GITHUB = 'https://github.com/JanNordskog/Onestoppdf'

function Page({ children, title }: { children: React.ReactNode; title: string }) {
  return (
    <div className="mx-auto max-w-3xl px-4 py-14">
      <h1 className="text-3xl font-bold tracking-tight">{title}</h1>
      <div className="prose-slate mt-6 space-y-4 leading-relaxed text-slate-600 [&_h2]:mt-8 [&_h2]:text-lg [&_h2]:font-semibold [&_h2]:text-slate-900">
        {children}
      </div>
    </div>
  )
}

export function AboutPage() {
  return (
    <Page title="About PDFHarbor">
      <Seo title={seoContent.pages.about.title} description={seoContent.pages.about.description} path="/about"
           jsonLd={[{
             '@context': 'https://schema.org',
             '@type': 'AboutPage',
             name: 'About PDFHarbor',
             url: `${SITE_URL}/about`,
           }]} />
      <p>
        PDFHarbor exists because working with PDFs online became an obstacle course of meters:
        two free tasks per day, file-size paywalls, watermarks on your own documents, and upload
        forms that quietly ship your contracts to servers you know nothing about.
      </p>
      <p>
        We took the opposite deal. <strong>Every tool, unlimited, on one server.</strong> Merge,
        split, compress, convert, edit and e-sign — 17 tools with no daily caps, no premium tier
        and no watermarks. Anonymous files self-destruct after two hours, verifiably, because the
        whole stack is open for inspection.
      </p>
      <h2>How it works</h2>
      <p>
        PDFHarbor is a self-hosted web application: a Java backend does all PDF processing locally
        (built on the Apache PDFBox engine), a PostgreSQL database tracks documents and signature
        requests, and a React front-end runs in your browser. Nothing is sent to third-party
        services — <Link to="/privacy" className="text-indigo-600 hover:underline">the privacy page</Link>{' '}
        spells out exactly what is stored and for how long.
      </p>
      <h2>What makes it different</h2>
      <p>
        Page-level merge control with interleaving for double-sided scans, in-place text editing
        that removes the original characters rather than covering them, form-data extraction to
        CSV/JSON, and unlimited e-signing with tamper-evident audit trails — features the big
        sites either lack or meter. The project is open source
        on <a href={GITHUB} className="text-indigo-600 hover:underline" rel="noopener">GitHub</a>.
      </p>
    </Page>
  )
}

export function PrivacyPage() {
  return (
    <Page title="Privacy">
      <Seo title={seoContent.pages.privacy.title} description={seoContent.pages.privacy.description} path="/privacy" />
      <p>
        <strong>The short version: your files never leave the server that runs PDFHarbor, and
        anonymous uploads erase themselves after two hours.</strong>
      </p>
      <h2>Files</h2>
      <p>
        Uploaded documents are stored on the PDFHarbor server's own disk while they are being
        processed. They are never forwarded to third-party services, APIs or clouds. Files
        uploaded without an account are hard-deleted automatically two hours after upload.
        Files owned by an account stay until you delete them from My files.
      </p>
      <h2>Accounts</h2>
      <p>
        An account stores your email address, display name and a salted password hash — nothing
        else. Login uses a token kept in your browser's local storage. PDFHarbor sets no tracking
        cookies and runs no analytics or advertising scripts.
      </p>
      <h2>E-signing</h2>
      <p>
        Signature requests store the signer names and email addresses you enter, the signed
        document, and an audit trail (timestamps, viewing and signing events, IP addresses) —
        that audit trail is the legal backbone of an e-signature, so it is kept with the
        completed document.
      </p>
      <h2>Self-hosting</h2>
      <p>
        PDFHarbor is open source. If you run your own instance, your data lives entirely on your
        own hardware and this policy is enforced by architecture rather than promises.
      </p>
    </Page>
  )
}

export function ContactPage() {
  return (
    <Page title="Contact">
      <Seo title={seoContent.pages.contact.title} description={seoContent.pages.contact.description} path="/contact"
           jsonLd={[{
             '@context': 'https://schema.org',
             '@type': 'ContactPage',
             name: 'Contact PDFHarbor',
             url: `${SITE_URL}/contact`,
           }]} />
      <p>
        Found a bug, missing a tool, or curious about self-hosting PDFHarbor? The project lives
        on GitHub — issues and pull requests are welcome:
      </p>
      <p>
        <a href={`${GITHUB}/issues`} className="btn-primary inline-flex" rel="noopener">
          Open an issue on GitHub
        </a>
      </p>
      <p>
        For anything about a document you processed: PDFHarbor staff cannot see, recover or
        decrypt your files — anonymous uploads are deleted after two hours and passwords are
        never stored.
      </p>
    </Page>
  )
}
