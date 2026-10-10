import { Link, useParams } from 'react-router'
import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { getTasks } from '../api/tasks'
import { getFlows, getFlowNodes } from '../api/flows'
import { getEscalations } from '../api/escalations'
import { formatDate } from '../lib/format'
import { Card, ErrorState, LoadingRows, PageHeader, StatusBadge } from '../components/Elements'
import { NavIcon } from '../components/NavIcon'

// Renders the overview metrics and workspace setup journey for a team.
export function TeamOverviewPage() {
  const { teamId = '' } = useParams()
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const firstFlowId = flows.data?.[0]?.id
  const firstFlowNodes = useQuery({
    queryKey: ['flow-nodes', teamId, firstFlowId],
    queryFn: () => {
      if (!firstFlowId) throw new Error('Flow id is required')
      return getFlowNodes(firstFlowId)
    },
    enabled: Boolean(firstFlowId),
  })
  const escalations = useQuery({ queryKey: ['escalations', teamId], queryFn: getEscalations, refetchInterval: query => query.state.data?.some(item => item.status === 'OPEN') ? 4000 : false })
  const base = `/app/${teamId}`

  return <>
    <PageHeader eyebrow="CONTROL ROOM / OVERVIEW" title="Keep every response on track." description="A reliable escalation workflow that keeps ownership clear from the first alert to the final handoff." action={<Link className="button button-primary" to={`${base}/escalations`}>New escalation <span>＋</span></Link>} />
    <div className="metrics-grid">
      <Metric label="Tasks" value={tasks.data?.length} loading={tasks.isPending} detail="Response context" icon={<NavIcon name="tasks" />} to={`${base}/tasks`} />
      <Metric label="Escalation paths" value={flows.data?.length} loading={flows.isPending} detail="Ordered response steps" icon={<NavIcon name="flows" />} to={`${base}/flows`} />
      <Metric label="Escalations" value={escalations.data?.length} loading={escalations.isPending} detail="Team executions" icon={<NavIcon name="escalations" />} to={`${base}/escalations`} />
      <Metric label="Currently running" value={escalations.data?.filter(item => item.status === 'OPEN').length} loading={escalations.isPending} detail="Read from saved state" icon={<NavIcon name="overview" />} to={`${base}/escalations`} accent />
    </div>
    <div className="overview-columns">
      <Card className="journey-card">
        <div className="card-heading"><div><span className="eyebrow">ESCALATION WORKSPACE</span><h2>Set up an escalation</h2></div><span className="section-index">01 / 04</span></div>
        <div className="setup-steps">
          <SetupStep index="01" icon={<NavIcon name="tasks" />} title="Create a task" description="Describe the issue users will be notified about." complete={Boolean(tasks.data?.length)} to={`${base}/tasks`} action="View tasks" />
          <SetupStep index="02" icon={<NavIcon name="flows" />} title="Create an escalation path" description="Choose the people to contact and set a wait time for each step." complete={Boolean(flows.data?.length)} to={`${base}/flows`} action="View paths" />
          <SetupStep index="03" icon={<NavIcon name="overview" />} title="Add response steps" description="Build the ordered route from the first contact to the next." complete={Boolean(firstFlowNodes.data?.length)} to={firstFlowId ? `${base}/flows/${firstFlowId}` : `${base}/flows`} action={firstFlowId ? 'Configure steps' : 'Create a path'} />
          <SetupStep index="04" icon={<NavIcon name="escalations" />} title="Create escalation" description="Pair a task with an escalation path and start its execution." complete={Boolean(escalations.data?.length)} to={`${base}/escalations`} action="View escalations" />
        </div>
      </Card>
      <Card className="architecture-card">
        <div className="card-heading"><div><span className="eyebrow">WORKFLOW RELIABILITY</span><h2>Reliable handoffs, built in</h2></div><span className="arch-stamp"><NavIcon name="recovery" /><span>Automatic recovery</span></span></div>
        <div className="architecture-flow">
          <div className="architecture-step"><span className="arch-icon"><NavIcon name="overview" /><span className="arch-step-number">01</span></span><div><strong>Every step is saved</strong><small>Escalation progress is recorded as work runs.</small></div></div>
          <div className="arch-line" />
          <div className="architecture-step"><span className="arch-icon arch-queue"><NavIcon name="flows" /><span className="arch-step-number">02</span></span><div><strong>Handoffs happen on time</strong><small>Recipients are contacted in your configured order.</small></div></div>
          <div className="arch-line" />
          <div className="architecture-step"><span className="arch-icon arch-recover"><NavIcon name="escalations" /><span className="arch-step-number">03</span></span><div><strong>Active work recovers</strong><small>In progress escalations resume after a restart.</small></div></div>
        </div>
        <div className="delivery-note compact"><span>EMAIL</span> Recipients get the configured escalation message. SENT means the email service accepted it.</div>
      </Card>
    </div>
    <div className="recent-heading"><div><span className="eyebrow">RECENT ACTIVITY</span><h2>Latest escalations</h2></div><Link to={`${base}/escalations`} className="text-link">All escalations <span>→</span></Link></div>
    <Card className="recent-card">
      {escalations.isPending ? <LoadingRows /> : escalations.isError ? <ErrorState message={escalations.error.message} onRetry={() => void escalations.refetch()} /> : escalations.data.length === 0 ? <div className="subtle-empty">No escalations yet. Create a task and an escalation path, then start your first run.</div> : <div className="table-scroll"><table><thead><tr><th>ESCALATION</th><th>STATUS</th><th>CREATED</th><th /></tr></thead><tbody>{escalations.data.slice(0, 5).map(item => <tr key={item.id}><td><Link className="table-primary" to={`${base}/escalations/${item.id}`}>{item.name}</Link><small className="table-subtext">{item.id.slice(0, 8)}</small></td><td><StatusBadge status={item.status} /></td><td>{formatDate(item.createdAt)}</td><td className="table-arrow">→</td></tr>)}</tbody></table></div>}
    </Card>
  </>
}

// Links an overview metric to the workspace area it summarizes.
function Metric({ label, value, loading, detail, icon, to, accent = false }: { label: string; value?: number; loading: boolean; detail: string; icon: ReactNode; to: string; accent?: boolean }) {
  return <Link to={to} className={`card metric-card metric-card-link ${accent ? 'metric-accent' : ''}`} aria-label={`${label}: ${loading ? 'loading' : value ?? 0}. ${detail}. Open ${label.toLowerCase()}.`}><div className="metric-top"><span>{label}</span><span className="metric-mark">{icon}</span></div><strong>{loading ? '—' : value ?? '—'}</strong><small>{detail}</small><span className="metric-card-arrow" aria-hidden="true">→</span></Link>
}

function SetupStep({ index, icon, title, description, complete, to, action }: { index: string; icon: ReactNode; title: string; description: string; complete: boolean; to: string; action: string }) {
  return <div className="setup-step"><div className={`step-index ${complete ? 'step-done' : ''}`} aria-hidden="true">{complete ? <span className="step-complete-mark">✓</span> : icon}<span className="step-index-label">{index}</span></div><div className="setup-copy"><div><strong>{title}</strong>{complete && <span className="complete-label">COMPLETE</span>}</div><p>{description}</p></div><Link to={to} className="step-action">{action} <span>→</span></Link></div>
}
