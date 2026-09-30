import { useEffect } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Outlet, Route, Routes, useParams } from 'react-router'
import { AppShell } from './components/AppShell'
import { SessionProvider } from './app/Session'
import { useSession } from './app/useSession'
import { PublicPage } from './pages/PublicPage'
import { TeamOverviewPage } from './pages/TeamOverviewPage'
import { LoginPage, RegisterPage, VerifyEmailPage } from './features/auth/AuthPages'
import { TeamPickerPage } from './features/teams/TeamPickerPage'
import { TeamMembersPage } from './features/teams/TeamMembersPage'
import { JoinTeamPage } from './features/teams/JoinTeamPage'
import { TasksPage } from './features/tasks/TasksPage'
import { FlowDetailPage } from './features/flows/FlowDetailPage'
import { FlowsPage } from './features/flows/FlowsPage'
import { EscalationDetailPage } from './features/escalations/EscalationDetailPage'
import { EscalationsPage } from './features/escalations/EscalationsPage'

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

function HomeRedirect() {
  const { token, team } = useSession()
  if (token && team) return <Navigate to={`/app/${team.id}`} replace />
  if (token) return <Navigate to="/teams" replace />
  return <PublicPage />
}

function NotFound() {
  return <main className="not-found"><span className="eyebrow">404 / ROUTE NOT FOUND</span><h1>This path is out of sequence.</h1><a className="button button-primary" href="/">Return to AlertOps</a></main>
}

function AppRoutes() {
  return <BrowserRouter><Routes>
    <Route path="/" element={<HomeRedirect />} />
    <Route path="/login" element={<LoginPage />} />
    <Route path="/register" element={<RegisterPage />} />
    <Route path="/verify-email" element={<VerifyEmailPage />} />
    <Route path="/join" element={<JoinTeamPage />} />
    <Route element={<RequireAuth />}>
      <Route path="/teams" element={<TeamPickerPage />} />
      <Route element={<RequireTeam />}>
        <Route path="/app/:teamId" element={<AppShell />}>
          <Route index element={<TeamOverviewPage />} />
          <Route path="members" element={<TeamMembersPage />} />
          <Route path="tasks" element={<TasksPage />} />
          <Route path="flows" element={<FlowsPage />} />
          <Route path="flows/:flowId" element={<FlowDetailPage />} />
          <Route path="escalations" element={<EscalationsPage />} />
          <Route path="escalations/:escalationId" element={<EscalationDetailPage />} />
        </Route>
      </Route>
    </Route>
    <Route path="*" element={<NotFound />} />
  </Routes></BrowserRouter>
}

export default function App() {
  useEffect(() => {
    document.documentElement.dataset.ready = 'true'
  }, [])
  return <QueryClientProvider client={queryClient}><SessionProvider><AppRoutes /></SessionProvider></QueryClientProvider>
}
