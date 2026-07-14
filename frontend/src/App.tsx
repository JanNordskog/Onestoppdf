import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Layout from './components/Layout'
import ToolPage from './components/ToolPage'
import { AuthProvider, RequireAuth } from './lib/auth'
import Home from './pages/Home'
import { LoginPage, RegisterPage } from './pages/AuthPages'
import { AboutPage, ContactPage, PrivacyPage } from './pages/TrustPages'
import MyFiles from './pages/MyFiles'
import MergePage from './pages/MergePage'
import OrganizePage from './pages/OrganizePage'
import SplitPage from './pages/SplitPage'
import EditorPage from './pages/EditorPage'
import SignDashboard from './pages/sign/SignDashboard'
import SignNew from './pages/sign/SignNew'
import SignDetail from './pages/sign/SignDetail'
import PublicSign from './pages/sign/PublicSign'
import { TOOLS } from './tools'

export default function App() {
  return (
    <AuthProvider>
      <BrowserRouter>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/" element={<Home />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/about" element={<AboutPage />} />
            <Route path="/privacy" element={<PrivacyPage />} />
            <Route path="/contact" element={<ContactPage />} />
            <Route path="/files" element={<RequireAuth><MyFiles /></RequireAuth>} />
            <Route path="/merge" element={<MergePage />} />
            <Route path="/split" element={<SplitPage />} />
            <Route path="/organize" element={<OrganizePage />} />
            <Route path="/edit" element={<EditorPage />} />
            <Route path="/sign" element={<RequireAuth><SignDashboard /></RequireAuth>} />
            <Route path="/sign/new" element={<RequireAuth><SignNew /></RequireAuth>} />
            <Route path="/sign/r/:id" element={<RequireAuth><SignDetail /></RequireAuth>} />
            <Route path="/sign/t/:token" element={<PublicSign />} />
            {TOOLS.filter((t) => !t.custom).map((t) => (
              <Route key={t.slug} path={`/${t.slug}`} element={<ToolPage config={t} />} />
            ))}
            <Route path="*" element={<div className="py-24 text-center text-slate-500">Page not found</div>} />
          </Route>
        </Routes>
      </BrowserRouter>
    </AuthProvider>
  )
}
