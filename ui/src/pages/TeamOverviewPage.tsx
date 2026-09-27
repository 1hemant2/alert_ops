import { Link, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { getFlows } from '../api/flows'
import { getTasks } from '../api/tasks'
import { Card, PageHeader } from '../components/Elements'

export function TeamOverviewPage() {
  const { teamId = '' } = useParams()
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const base = `/app/${teamId}`

  return <>
    <PageHeader eyebrow="CONTROL ROOM / OVERVIEW" title="Build your response path." description="Start with incident context, then arrange the people and delays in a flow." />
    <div className="metrics-grid">
      <Card className="metric-card"><div className="metric-top"><span>Tasks</span><span className="metric-mark">T</span></div><strong>{tasks.isPending ? '—' : tasks.data?.length ?? 0}</strong><small>Incident context</small></Card>
      <Card className="metric-card"><div className="metric-top"><span>Flow templates</span><span className="metric-mark">F</span></div><strong>{flows.isPending ? '—' : flows.data?.length ?? 0}</strong><small>Ordered response paths</small></Card>
    </div>
    <Card className="journey-card">
      <div className="card-heading"><div><span className="eyebrow">CONFIGURE THE WORKFLOW</span><h2>Set up an escalation</h2></div><span className="section-index">01 / 03</span></div>
      <div className="setup-steps">
        <div className="setup-step"><span className={`step-index ${tasks.data?.length ? 'step-done' : ''}`}>{tasks.data?.length ? '✓' : '01'}</span><div className="setup-copy"><strong>Create a task</strong><p>Describe the incident responders need to resolve.</p></div><Link className="step-action" to={`${base}/tasks`}>View tasks →</Link></div>
        <div className="setup-step"><span className={`step-index ${flows.data?.length ? 'step-done' : ''}`}>{flows.data?.length ? '✓' : '02'}</span><div className="setup-copy"><strong>Build a flow</strong><p>Choose people and delays in the response path.</p></div><Link className="step-action" to={`${base}/flows`}>View flows →</Link></div>
        <div className="setup-step"><span className="step-index">03</span><div className="setup-copy"><strong>Add ordered nodes</strong><p>Set recipient emails and time delays for each step.</p></div><Link className="step-action" to={`${base}/flows`}>Configure flow →</Link></div>
      </div>
    </Card>
  </>
}
