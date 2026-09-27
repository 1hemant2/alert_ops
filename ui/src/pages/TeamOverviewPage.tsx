import { Link, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { getTasks } from '../api/tasks'
import { getFlows, getFlowNodes } from '../api/flows'
import { getEscalations } from '../api/escalations'
import { formatDate } from '../lib/format'
import { Card, ErrorState, LoadingRows, PageHeader, StatusBadge } from '../components/Elements'

export function TeamOverviewPage() {
  const { teamId = '' } = useParams()
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const firstFlowId = flows.data?.[0]?.id
  const firstFlowNodes = useQuery({
    queryKey: ['flow-nodes', teamId, firstFlowId],
    queryFn: () => getFlowNodes(firstFlowId!),
    enabled: Boolean(firstFlowId),
  })
  const escalations = useQuery({ queryKey: ['escalations', teamId], queryFn: getEscalations, refetchInterval: query => query.state.data?.some(item => item.status === 'RUNNING') ? 4000 : false })
  const base = `/app/${teamId}`

  return <>
    <PageHeader eyebrow="CONTROL ROOM / OVERVIEW" title="Your response, in order." description="A durable escalation workflow from the first alert to the final handoff." action={<Link className="button button-primary" to={`${base}/escalations`}>New escalation <span>＋</span></Link>} />
    <div className="metrics-grid">
      <Metric label="Tasks" value={tasks.data?.length} loading={tasks.isPending} detail="Incident context" mark="T" />
      <Metric label="Flow templates" value={flows.data?.length} loading={flows.isPending} detail="Ordered response paths" mark="F" />
      <Metric label="Escalations" value={escalations.data?.length} loading={escalations.isPending} detail="Team executions" mark="E" />
      <Metric label="Currently running" value={escalations.data?.filter(item => item.status === 'RUNNING').length} loading={escalations.isPending} detail="Read from saved state" mark="↗" accent />
    </div>
    <div className="overview-columns">
      <Card className="journey-card">
        <div className="card-heading"><div><span className="eyebrow">FOUR STEPS TO A LIVE DEMO</span><h2>Set up an escalation</h2></div><span className="section-index">01 / 04</span></div>
        <div className="setup-steps">
          <SetupStep index="01" title="Create a task" description="Describe the issue users will be notified about." complete={Boolean(tasks.data?.length)} to={`${base}/tasks`} action="View tasks" />
          <SetupStep index="02" title="Build a flow" description="Choose the people and delays in your response path." complete={Boolean(flows.data?.length)} to={`${base}/flows`} action="View flows" />
          <SetupStep index="03" title="Add response nodes" description="Set each notification recipient and the delay before contacting them." complete={Boolean(firstFlowNodes.data?.length)} to={firstFlowId ? `${base}/flows/${firstFlowId}` : `${base}/flows`} action={firstFlowId ? 'Configure nodes' : 'Create a flow'} />
          <SetupStep index="04" title="Create escalation" description="Pair a task with a flow and start its execution." complete={Boolean(escalations.data?.length)} to={`${base}/escalations`} action="View escalations" />
        </div>
      </Card>
      <Card className="architecture-card">
        <div className="card-heading"><div><span className="eyebrow">UNDER THE HOOD</span><h2>What happens next</h2></div><span className="arch-stamp">ENGINEERING NOTE</span></div>
        <div className="architecture-flow">
          <div className="architecture-step"><span className="arch-icon">01</span><div><strong>Persist execution</strong><small>Postgres stores each node state.</small></div></div>
          <div className="arch-line" />
          <div className="architecture-step"><span className="arch-icon arch-queue">02</span><div><strong>Delay in the queue</strong><small>RabbitMQ advances nodes by their delay.</small></div></div>
          <div className="arch-line" />
          <div className="architecture-step"><span className="arch-icon arch-recover">03</span><div><strong>Resume on startup</strong><small>A reconciler republishes active work.</small></div></div>
        </div>
        <div className="delivery-note compact"><span>DELIVERY</span> Escalation nodes email their configured recipients through SMTP. SENT means SMTP accepted the message.</div>
      </Card>
    </div>
    <div className="recent-heading"><div><span className="eyebrow">RECENT ACTIVITY</span><h2>Latest escalations</h2></div><Link to={`${base}/escalations`} className="text-link">All escalations <span>→</span></Link></div>
    <Card className="recent-card">
      {escalations.isPending ? <LoadingRows /> : escalations.isError ? <ErrorState message={escalations.error.message} onRetry={() => void escalations.refetch()} /> : escalations.data.length === 0 ? <div className="subtle-empty">No escalations yet. Create a task and a flow, then start your first run.</div> : <div className="table-scroll"><table><thead><tr><th>ESCALATION</th><th>STATUS</th><th>CREATED</th><th /></tr></thead><tbody>{escalations.data.slice(0, 5).map(item => <tr key={item.id}><td><Link className="table-primary" to={`${base}/escalations/${item.id}`}>{item.name}</Link><small className="table-subtext">{item.id.slice(0, 8)}</small></td><td><StatusBadge status={item.status} /></td><td>{formatDate(item.createdAt)}</td><td className="table-arrow">→</td></tr>)}</tbody></table></div>}
    </Card>
  </>
}

function Metric({ label, value, loading, detail, mark, accent = false }: { label: string; value?: number; loading: boolean; detail: string; mark: string; accent?: boolean }) {
  return <Card className={`metric-card ${accent ? 'metric-accent' : ''}`}><div className="metric-top"><span>{label}</span><span className="metric-mark">{mark}</span></div><strong>{loading ? '—' : value ?? '—'}</strong><small>{detail}</small></Card>
}

function SetupStep({ index, title, description, complete, to, action }: { index: string; title: string; description: string; complete: boolean; to: string; action: string }) {
  return <div className="setup-step"><div className={`step-index ${complete ? 'step-done' : ''}`}>{complete ? '✓' : index}</div><div className="setup-copy"><div><strong>{title}</strong>{complete && <span className="complete-label">COMPLETE</span>}</div><p>{description}</p></div><Link to={to} className="step-action">{action} <span>→</span></Link></div>
}
