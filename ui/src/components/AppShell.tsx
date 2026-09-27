import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useSession } from '../app/useSession'
import { teamRoleLabel } from '../features/teams/teamRoles'
import { NavIcon } from './NavIcon'

export function AppShell() {
  const { team, logout } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  function signOut() {
    logout()
    queryClient.clear()
    navigate('/login')
  }

  const base = `/app/${team?.id ?? ''}`
  return (
    <div className="app-frame">
      <aside className="sidebar">
        <NavLink className="brand" to={base}>
          <span className="brand-icon"><b /><b /><b /></span>
          <span>ALERT<span>OPS</span></span>
        </NavLink>
        <div className="workspace-label">WORKSPACE</div>
        <button className="workspace-switch" onClick={() => navigate('/teams')}>
          <span className="workspace-avatar">{team?.name?.slice(0, 1).toUpperCase() ?? 'A'}</span>
          <span className="workspace-name"><strong>{team?.name ?? 'Select a team'}</strong><small>{team?.role ? teamRoleLabel(team.role) : 'Team workspace'}</small></span>
          <span className="chevron">⌄</span>
        </button>
        <nav className="primary-nav" aria-label="Main navigation">
          <div className="nav-section-title">WORKSPACE</div>
          <NavLink end to={base} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="overview" /></span>Overview</NavLink>
          <NavLink to={`${base}/members`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="members" /></span>Members</NavLink>
          <NavLink to={`${base}/tasks`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="tasks" /></span>Tasks</NavLink>
          <NavLink to={`${base}/flows`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="flows" /></span>Escalation paths</NavLink>
          <NavLink to={`${base}/escalations`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="escalations" /></span>Escalations</NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-system"><span className="queue-mark"><NavIcon name="escalations" /></span><span><strong>Escalation engine</strong><small>Ordered, durable handoffs</small></span></div>
          <button className="nav-item logout-button" onClick={signOut}><span className="nav-glyph"><NavIcon name="signout" /></span>Sign out</button>
          <div className="sidebar-version">ALERTOPS <span>WORKSPACE</span></div>
        </div>
      </aside>
      <main className="main-column">
        <header className="topbar">
          <div className="topbar-crumb"><span>Workspace</span><b>/</b><strong>{team?.name ?? 'AlertOps'}</strong></div>
          <div className="topbar-right"><span className="api-indicator"><i />TEAM WORKSPACE</span><button className="avatar-button" onClick={signOut} title="Sign out"><NavIcon name="signout" /></button></div>
        </header>
        <div className="page-content"><Outlet /></div>
      </main>
    </div>
  )
}
