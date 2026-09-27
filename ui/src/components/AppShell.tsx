import { NavLink, Outlet, useNavigate } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useSession } from '../app/useSession'

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
          <span className="workspace-name"><strong>{team?.name ?? 'Select a team'}</strong><small>{team?.role?.replaceAll('_', ' ') ?? 'Team workspace'}</small></span>
          <span className="chevron">⌄</span>
        </button>
        <nav className="primary-nav" aria-label="Main navigation">
          <div className="nav-section-title">CONTROL ROOM</div>
          <NavLink end to={base} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph">◫</span>Overview</NavLink>
          <NavLink to={`${base}/tasks`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph">▤</span>Tasks</NavLink>
          <NavLink to={`${base}/flows`} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph">⌁</span>Escalation flows</NavLink>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-system"><span className="queue-mark">↗</span><span><strong>Queue workflow</strong><small>Delayed, persisted execution</small></span></div>
          <button className="nav-item logout-button" onClick={signOut}><span className="nav-glyph">↗</span>Sign out</button>
          <div className="sidebar-version">ALERTOPS <span>DEMO UI</span></div>
        </div>
      </aside>
      <main className="main-column">
        <header className="topbar">
          <div className="topbar-crumb"><span>Workspace</span><b>/</b><strong>{team?.name ?? 'AlertOps'}</strong></div>
          <div className="topbar-right"><span className="api-indicator"><i />TEAM SCOPED</span><button className="avatar-button" onClick={signOut} title="Sign out">↗</button></div>
        </header>
        <div className="page-content"><Outlet /></div>
      </main>
    </div>
  )
}
