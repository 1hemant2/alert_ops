import type { ReactNode } from 'react'

type IconName = 'overview' | 'members' | 'tasks' | 'flows' | 'escalations' | 'recovery' | 'audit' | 'menu' | 'close' | 'sidebar-collapse' | 'sidebar-expand' | 'signout'

// Renders the shared outline icon for workspace navigation and workflow states.
export function NavIcon({ name }: { name: IconName }) {
  const paths: Record<IconName, ReactNode> = {
    overview: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
    members: <><circle cx="9" cy="8" r="3" /><path d="M3.5 20c.5-3.1 2.5-5 5.5-5s5 1.9 5.5 5" /><path d="M16 5.5a3 3 0 0 1 0 5.8M17 15c2 .5 3.2 2 3.5 4.5" /></>,
    tasks: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h5" /><path d="m6.5 8 .7.7L8.5 7.5" /></>,
    flows: <><circle cx="5" cy="5" r="2" /><circle cx="19" cy="19" r="2" /><circle cx="19" cy="5" r="2" /><path d="M7 5h6a4 4 0 0 1 4 4v8M5 7v10a2 2 0 0 0 2 2h10" /></>,
    escalations: <path d="M12 3 4.5 13h5L8 21l11.5-11h-5L16 3z" />,
    recovery: <><path d="M4 7v5h5" /><path d="M20 17v-5h-5" /><path d="M5.6 12a7 7 0 0 0 12.8 3" /><path d="M18.4 12A7 7 0 0 0 5.6 9" /></>,
    audit: <><path d="M7 3.5h8l3 3V20a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4.5a1 1 0 0 1 1-1Z" /><path d="M14.5 3.5V7H18" /><path d="M9 11h6M9 15h6M9 18h3" /></>,
    menu: <><path d="M4 7h16M4 12h16M4 17h16" /></>,
    close: <><path d="m6 6 12 12M18 6 6 18" /></>,
    'sidebar-collapse': <><rect x="3.5" y="4" width="17" height="16" rx="2" /><path d="M9 4v16M15 9l-3 3 3 3" /></>,
    'sidebar-expand': <><rect x="3.5" y="4" width="17" height="16" rx="2" /><path d="M15 4v16M9 9l3 3-3 3" /></>,
    signout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" /></>,
  }

  return <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
