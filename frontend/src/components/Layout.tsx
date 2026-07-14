import { Link, NavLink, Outlet } from 'react-router-dom'
import { BRAND } from '../brand'
import { useAuth } from '../lib/auth'

const navLink = ({ isActive }: { isActive: boolean }) =>
  `px-3 py-2 rounded-lg text-sm font-medium transition ${
    isActive ? 'text-indigo-700 bg-indigo-50' : 'text-slate-600 hover:text-slate-900 hover:bg-slate-100'
  }`

export default function Layout() {
  const { user, logout } = useAuth()
  return (
    <div className="min-h-screen flex flex-col">
      <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/85 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center gap-2 px-4">
          <Link to="/" className="flex items-center gap-2.5 mr-4">
            <img src="/logo.png" alt="" width={36} height={36} className="h-9 w-9 rounded-xl" />
            <span className="text-lg font-bold tracking-tight">{BRAND.name}</span>
          </Link>
          <nav className="hidden md:flex items-center gap-1">
            <NavLink to="/" end className={navLink}>Tools</NavLink>
            <NavLink to="/merge" className={navLink}>Merge</NavLink>
            <NavLink to="/edit" className={navLink}>Edit</NavLink>
            <NavLink to="/pdf-to-word" className={navLink}>PDF → Word</NavLink>
            <NavLink to="/sign" className={navLink}>eSign</NavLink>
          </nav>
          <div className="ml-auto flex items-center gap-2">
            {user ? (
              <>
                <NavLink to="/files" className={navLink}>My files</NavLink>
                <span className="hidden sm:block text-sm text-slate-500">{user.displayName}</span>
                <button onClick={logout} className="btn-secondary">Sign out</button>
              </>
            ) : (
              <>
                <NavLink to="/login" className={navLink}>Sign in</NavLink>
                <Link to="/register" className="btn-primary">Get started</Link>
              </>
            )}
          </div>
        </div>
      </header>
      <main className="flex-1">
        <Outlet />
      </main>
      <footer className="border-t border-slate-200 bg-white">
        <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 text-sm sm:grid-cols-3">
          <div>
            <p className="font-semibold text-slate-900">{BRAND.name}</p>
            <p className="mt-2 max-w-xs text-slate-500">{BRAND.tagline} Anonymous files self-destruct after 2 hours. No task limits, ever.</p>
          </div>
          <nav aria-label="Popular tools" className="grid grid-cols-2 gap-x-4 gap-y-1.5 text-slate-600">
            <Link to="/merge" className="hover:text-indigo-700">Merge PDF</Link>
            <Link to="/split" className="hover:text-indigo-700">Split PDF</Link>
            <Link to="/compress" className="hover:text-indigo-700">Compress PDF</Link>
            <Link to="/edit" className="hover:text-indigo-700">Edit PDF</Link>
            <Link to="/pdf-to-word" className="hover:text-indigo-700">PDF to Word</Link>
            <Link to="/sign" className="hover:text-indigo-700">eSign</Link>
          </nav>
          <nav aria-label="Company" className="flex flex-col gap-1.5 text-slate-600">
            <Link to="/about" className="hover:text-indigo-700">About</Link>
            <Link to="/privacy" className="hover:text-indigo-700">Privacy</Link>
            <Link to="/contact" className="hover:text-indigo-700">Contact</Link>
            <a href="https://github.com/JanNordskog/Onestoppdf" rel="noopener" className="hover:text-indigo-700">GitHub</a>
          </nav>
        </div>
      </footer>
    </div>
  )
}
