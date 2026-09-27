import type { ReactNode } from 'react'

type IconName = 'overview' | 'members' | 'tasks' | 'flows' | 'escalations' | 'signout'

export function NavIcon({ name }: { name: IconName }) {
  const paths: Record<IconName, ReactNode> = {
    overview: <><rect x="3.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="3.5" width="7" height="7" rx="1.5" /><rect x="3.5" y="13.5" width="7" height="7" rx="1.5" /><rect x="13.5" y="13.5" width="7" height="7" rx="1.5" /></>,
    members: <><circle cx="9" cy="8" r="3" /><path d="M3.5 20c.5-3.1 2.5-5 5.5-5s5 1.9 5.5 5" /><path d="M16 5.5a3 3 0 0 1 0 5.8M17 15c2 .5 3.2 2 3.5 4.5" /></>,
    tasks: <><rect x="4" y="3" width="16" height="18" rx="2" /><path d="M8 8h8M8 12h8M8 16h5" /><path d="m6.5 8 .7.7L8.5 7.5" /></>,
    flows: <><circle cx="5" cy="5" r="2" /><circle cx="19" cy="19" r="2" /><circle cx="19" cy="5" r="2" /><path d="M7 5h6a4 4 0 0 1 4 4v8M5 7v10a2 2 0 0 0 2 2h10" /></>,
    escalations: <path d="M12 3 4.5 13h5L8 21l11.5-11h-5L16 3z" />,
    signout: <><path d="M10 17l5-5-5-5M15 12H3" /><path d="M12 3h6a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-6" /></>,
  }

  return <svg className="nav-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">{paths[name]}</svg>
}
