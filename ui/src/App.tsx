import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Link, Navigate, Outlet, Route, Routes, useParams } from 'react-router'
import { SessionProvider } from './app/Session'
import { useSession } from './app/useSession'
import { LoginPage, RegisterPage } from './features/auth/AuthPages'
import { TeamPickerPage } from './features/teams/TeamPickerPage'
import { PublicPage } from './pages/PublicPage'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: { staleTime: 15_000, retry: 1, refetchOnWindowFocus: false },
    mutations: { retry: 0 },
  },
})

function RequireAuth() {
  const { token } = useSession()
  return token ? <Outlet /> : <Navigate to="/login" replace />
}

function RequireTeam() {
  const { token, team } = useSession()
  const { teamId } = useParams()
  if (!token) return <Navigate to="/login" replace />
  if (!team) return <Navigate to="/teams" replace />
  if (team.id !== teamId) return <Navigate to={`/app/${team.id}`} replace />
  return <Outlet />
}

function WorkspaceWelcome() {
  const { team } = useSession()
  return (
    <main className="team-picker">
      <div className="eyebrow">TEAM WORKSPACE</div>
      <h1>{team?.name}</h1>
      <p className="page-lede">Your team-scoped session is active. Task and flow setup is the next step.</p>
      <Link className="button button-primary" to="/teams">Switch team</Link>
    </main>
  )
}

function AppRoutes() {
  return <BrowserRouter><Routes>
    <Route path="/" element={<PublicPage />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route element={<RequireAuth />}>
      <Route path="/teams" element={<TeamPickerPage />} />
      <Route element={<RequireTeam />}>
        <Route path="/app/:teamId" element={<WorkspaceWelcome />} />
      </Route>
    </Route>
    <Route path="*" element={<main className="team-picker"><h1>Page not found</h1><Link to="/">Return to AlertOps</Link></main>} />
  </Routes></BrowserRouter>
}

export default function App() {
  return <QueryClientProvider client={queryClient}><SessionProvider><AppRoutes /></SessionProvider></QueryClientProvider>
}
