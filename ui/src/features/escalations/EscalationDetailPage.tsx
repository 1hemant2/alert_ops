import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getEscalation, getExecutionStates, startEscalation } from '../../api/escalations'
import { getFlow, getFlowNodes, nodeDelayMinutes, nodeName } from '../../api/flows'
import { getTasks } from '../../api/tasks'
import { Button, Card, ErrorState, InlineNotice, LoadingRows, PageHeader, StatusBadge } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function EscalationDetailPage() {
  const { teamId = '', escalationId = '' } = useParams()
  const queryClient = useQueryClient()
  const escalation = useQuery({
    queryKey: ['escalation', teamId, escalationId],
    queryFn: () => getEscalation(escalationId),
    refetchInterval: query => query.state.data?.status === 'RUNNING' ? 3000 : false,
  })
  const execution = useQuery({
    queryKey: ['execution-states', teamId, escalationId],
    queryFn: () => getExecutionStates(escalationId),
    enabled: Boolean(escalation.data),
    refetchInterval: escalation.data?.status === 'RUNNING' ? 3000 : false,
  })
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flow = useQuery({ queryKey: ['flow', teamId, escalation.data?.flowId], queryFn: () => getFlow(escalation.data!.flowId), enabled: Boolean(escalation.data?.flowId) })
  const nodes = useQuery({ queryKey: ['flow-nodes', teamId, escalation.data?.flowId], queryFn: () => getFlowNodes(escalation.data!.flowId), enabled: Boolean(escalation.data?.flowId) })
  const start = useMutation({
    mutationFn: () => startEscalation(escalationId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
        queryClient.invalidateQueries({ queryKey: ['execution-states', teamId, escalationId] }),
      ])
    },
  })

  if (escalation.isPending) return <LoadingRows count={4} />
  if (escalation.isError) return <ErrorState message={escalation.error.message} onRetry={() => void escalation.refetch()} />
  const item = escalation.data
  const task = tasks.data?.find(candidate => candidate.id === item.taskId)
  const completed = item.status === 'COMPLETED'

  return <>
    <div className="back-link-row"><Link to={`/app/${teamId}/escalations`}>← Escalations</Link><span> / </span><span>{item.name}</span></div>
    <PageHeader eyebrow={`EXECUTION / ${item.id.slice(0, 8).toUpperCase()}`} title={item.name} description="Durable progress for this team scoped escalation." action={<StatusBadge status={item.status} />} />
    <InlineNotice>Each active step emails its configured recipient with the task context. SENT means the email service accepted the message; it does not confirm delivery.</InlineNotice>
    <div className="detail-summary-grid">
      <Card className="detail-summary-card"><span className="eyebrow">TASK CONTEXT</span><strong>{task?.name ?? item.taskId.slice(0, 8)}</strong><p>{task?.description || 'Task details are not available.'}</p></Card>
      <Card className="detail-summary-card"><span className="eyebrow">ESCALATION PATH</span>{flow.data ? <Link className="detail-link" to={`/app/${teamId}/flows/${flow.data.id}`}>{flow.data.name} <span>↗</span></Link> : <strong>{item.flowId.slice(0, 8)}</strong>}<p>{nodes.data ? `${nodes.data.length} configured response ${nodes.data.length === 1 ? 'step' : 'steps'}` : 'Loading path configuration…'}</p></Card>
      <Card className="detail-summary-card"><span className="eyebrow">CREATED AT</span><strong>{formatDate(item.createdAt)}</strong><p>{completed && item.resolutionType ? `Resolution: ${item.resolutionType}` : 'Time shown in your local timezone.'}</p></Card>
    </div>
    <Card className="execution-card">
      <div className="card-heading"><div><span className="eyebrow">SAVED STEP PROGRESS</span><h2>Execution timeline</h2></div><Button variant="secondary" onClick={() => { void escalation.refetch(); void execution.refetch() }}>Refresh&nbsp; ↻</Button></div>
      {execution.isPending ? <LoadingRows count={3} /> : execution.isError ? <ErrorState message={execution.error.message} onRetry={() => void execution.refetch()} /> : execution.data.length === 0 ? <div className="prestart-state"><div className="prestart-illustration">01 <span>→</span> 02 <span>→</span> 03</div><div><strong>This escalation is ready to start.</strong><p>Starting it saves the path steps and schedules the first wait.</p></div>{item.status === 'IDLE' && <Button disabled={start.isPending || !nodes.data?.length} onClick={() => start.mutate()}>{start.isPending ? 'Starting…' : 'Start escalation'} <span>→</span></Button>}</div> : <div className="execution-timeline">{execution.data.map((state, index) => {
        const node = nodes.data?.find(candidate => candidate.id === state.nodeId)
        return <article className="execution-row" key={state.nodeId}><div className={`execution-index execution-${state.executionState.toLowerCase()}`}>{state.executionState === 'TERMINAL' ? '✓' : String(index + 1).padStart(2, '0')}</div><div className="execution-connector" /><div className="execution-copy"><div className="execution-title"><div><strong>{node ? nodeName(node) : `Response step ${index + 1}`}</strong><small>{state.userEmail}</small></div><div className="execution-badges"><StatusBadge status={state.executionState} /><StatusBadge status={state.notificationState} /></div></div><div className="execution-details"><span>WAIT&nbsp; {node ? `${nodeDelayMinutes(node)} MIN` : '—'}</span><span>ATTEMPTS&nbsp; {state.sendAttemptCount}</span><span>UPDATED&nbsp; {formatDate(state.updatedAt ?? state.createdAt)}</span></div></div></article>
      })}</div>}
      {start.error && <div className="form-error start-error" role="alert">{start.error.message}</div>}
      {item.status === 'RUNNING' && <div className="polling-note"><span className="live-dot" /> Refreshing saved state every 3 seconds while this run is active.</div>}
    </Card>
    <div className="last-updated">ESCALATION ID&nbsp; <code>{item.id}</code></div>
  </>
}
