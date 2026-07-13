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
            <img src="/logo.png" alt="" className="h-9 w-9 rounded-xl" />
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
        <div className="mx-auto max-w-6xl px-4 py-8 flex flex-col sm:flex-row items-center justify-between gap-3 text-sm text-slate-500">
          <span>{BRAND.name} — {BRAND.tagline}</span>
          <span>Anonymous files self-destruct after 2 hours. No task limits, ever.</span>
        </div>
      </footer>
    </div>
  )
}
