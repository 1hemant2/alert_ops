import { Fragment, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router'
import { Link, useLocation } from 'react-router'
import { useQueryClient } from '@tanstack/react-query'
import { useSession } from '../app/useSession'
import { teamRoleLabel } from '../features/teams/teamRoles'
import { NavIcon } from './NavIcon'
import { ThemeToggle } from './ThemeToggle'

type BreadcrumbItem = { label: string; to: string }

// Renders clickable parent routes for the current workspace location.
function WorkspaceBreadcrumbs({ teamId, teamName }: { teamId: string; teamName: string }) {
  const location = useLocation()
  const base = `/app/${teamId}`
  const crumbs: BreadcrumbItem[] = [
    { label: 'Workspace', to: '/teams' },
    { label: teamName, to: base },
  ]
  const sectionLabels: Record<string, string> = {
    members: 'Members',
    webhooks: 'Webhooks',
    audit: 'Audit trail',
    tasks: 'Tasks',
    flows: 'Escalation paths',
    escalations: 'Escalations',
  }
  const detailLabels: Record<string, string> = {
    tasks: 'Task details',
    flows: 'Path details',
    escalations: 'Escalation details',
  }
  const routeParts = location.pathname.slice(base.length).split('/').filter(Boolean)
  const section = routeParts[0]
  if (section && sectionLabels[section]) {
    crumbs.push({ label: sectionLabels[section], to: `${base}/${section}` })
    if (routeParts[1] && detailLabels[section]) {
      crumbs.push({ label: detailLabels[section], to: location.pathname })
    }
  }

  return (
    <nav className="topbar-crumb breadcrumb-nav" aria-label="Breadcrumb">
      {crumbs.map((crumb, index) => {
        const isCurrent = index === crumbs.length - 1
        return <Fragment key={`${crumb.label}-${index}`}>
          {index > 0 && <b aria-hidden="true">/</b>}
          {isCurrent ? <strong aria-current="page">{crumb.label}</strong> : <Link to={crumb.to}>{crumb.label}</Link>}
        </Fragment>
      })}
    </nav>
  )
}

// Renders the signed-in ReplyTrail workspace shell.
export function AppShell() {
  const { team, logout } = useSession()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [sidebarCollapsed, setSidebarCollapsed] = useState(false)
  const [mobileSidebarOpen, setMobileSidebarOpen] = useState(false)

  // Signs the current user out and clears cached workspace data.
  function signOut() {
    logout()
    queryClient.clear()
    navigate('/login')
  }

  // Toggles the desktop sidebar or opens the mobile navigation drawer.
  function toggleSidebar() {
    if (window.matchMedia('(max-width: 880px)').matches) {
      setMobileSidebarOpen(previous => !previous)
      return
    }
    setSidebarCollapsed(previous => !previous)
  }

  // Closes the navigation in the mode currently visible to the user.
  function closeSidebar() {
    if (window.matchMedia('(max-width: 880px)').matches) {
      setMobileSidebarOpen(false)
      return
    }
    setSidebarCollapsed(true)
  }

  const base = `/app/${team?.id ?? ''}`
  const isMobileViewport = typeof window !== 'undefined' && window.matchMedia('(max-width: 880px)').matches
  const frameClassName = `app-frame${sidebarCollapsed ? ' sidebar-collapsed' : ''}${mobileSidebarOpen ? ' sidebar-mobile-open' : ''}`
  const menuLabel = mobileSidebarOpen ? 'Close navigation menu' : sidebarCollapsed ? 'Expand sidebar' : isMobileViewport ? 'Open navigation menu' : 'Collapse sidebar'
  const menuIcon = mobileSidebarOpen ? 'close' : sidebarCollapsed ? 'sidebar-expand' : isMobileViewport ? 'menu' : 'sidebar-collapse'
  return (
    <div className={frameClassName}>
      <aside id="workspace-sidebar" className="sidebar">
        <div className="sidebar-header">
          <NavLink className="brand" to={base}>
            <span className="brand-icon"><b /><b /><b /></span>
            <span>REPLY<span>TRAIL</span></span>
          </NavLink>
          <button className="sidebar-menu-toggle sidebar-rail-toggle" type="button" aria-label={menuLabel} title={menuLabel} aria-controls="workspace-sidebar" aria-expanded={mobileSidebarOpen || (!isMobileViewport && !sidebarCollapsed)} onClick={toggleSidebar}><NavIcon name={menuIcon} /></button>
        </div>
        <div className="workspace-label">WORKSPACE</div>
        <button className="workspace-switch" title="Switch workspace" aria-label="Switch workspace" onClick={() => navigate('/teams')}>
          <span className="workspace-avatar">{team?.name?.slice(0, 1).toUpperCase() ?? 'A'}</span>
          <span className="workspace-name"><strong>{team?.name ?? 'Select a team'}</strong><small>{team?.role ? teamRoleLabel(team.role) : 'Team workspace'}</small></span>
          <span className="chevron">⌄</span>
        </button>
        <nav className="primary-nav" aria-label="Main navigation">
          <div className="nav-group">
            <div className="nav-section-title">MONITOR</div>
            <div className="nav-group-links">
              <NavLink end to={base} title="Overview" aria-label="Overview" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="overview" /></span>Overview</NavLink>
              <NavLink to={`${base}/escalations`} title="Escalations" aria-label="Escalations" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="escalations" /></span>Escalations</NavLink>
              <NavLink to={`${base}/audit`} title="Audit trail" aria-label="Audit trail" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="audit" /></span>Audit trail</NavLink>
            </div>
          </div>
          <div className="nav-group">
            <div className="nav-section-title">CONFIGURE</div>
            <div className="nav-group-links">
              <NavLink to={`${base}/tasks`} title="Tasks" aria-label="Tasks" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="tasks" /></span>Tasks</NavLink>
              <NavLink to={`${base}/flows`} title="Escalation paths" aria-label="Escalation paths" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="flows" /></span>Escalation paths</NavLink>
              <NavLink to={`${base}/webhooks`} title="Webhooks" aria-label="Webhooks" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="escalations" /></span>Webhooks</NavLink>
            </div>
          </div>
          <div className="nav-group">
            <div className="nav-section-title">TEAM</div>
            <div className="nav-group-links">
              <NavLink to={`${base}/members`} title="Members" aria-label="Members" className={({ isActive }) => `nav-item ${isActive ? 'active' : ''}`}><span className="nav-glyph"><NavIcon name="members" /></span>Members</NavLink>
            </div>
          </div>
        </nav>
        <div className="sidebar-bottom">
          <div className="sidebar-system" title="Escalation engine"><span className="queue-mark"><NavIcon name="escalations" /></span><span><strong>Escalation engine</strong><small>Ordered, durable handoffs</small></span></div>
          <button className="nav-item logout-button" title="Sign out" aria-label="Sign out" onClick={signOut}><span className="nav-glyph"><NavIcon name="signout" /></span>Sign out</button>
          <div className="sidebar-version">REPLYTRAIL <span>WORKSPACE</span></div>
        </div>
      </aside>
      <button className="sidebar-backdrop" type="button" aria-label="Close navigation menu" onClick={closeSidebar} />
        <main className="main-column">
        <header className="topbar">
          <div className="topbar-left"><button className="sidebar-menu-toggle sidebar-mobile-toggle" type="button" aria-label={menuLabel} title={menuLabel} aria-controls="workspace-sidebar" aria-expanded={mobileSidebarOpen || (!isMobileViewport && !sidebarCollapsed)} onClick={toggleSidebar}><NavIcon name={menuIcon} /></button><WorkspaceBreadcrumbs teamId={team?.id ?? ''} teamName={team?.name ?? 'ReplyTrail'} /></div>
          <div className="topbar-right"><ThemeToggle /><span className="api-indicator"><i />TEAM WORKSPACE</span><button className="avatar-button" onClick={signOut} title="Sign out"><NavIcon name="signout" /></button></div>
        </header>
        <div className="page-content"><Outlet /></div>
      </main>
    </div>
  )
}
