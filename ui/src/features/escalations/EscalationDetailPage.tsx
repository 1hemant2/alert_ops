import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelScheduledEscalation, getEscalation, getExecutionStates, rescheduleEscalation, startEscalation } from '../../api/escalations'
import { getFlow, getFlowNodes, nodeDelayMinutes, nodeName } from '../../api/flows'
import { getTasks } from '../../api/tasks'
import { Button, Card, ErrorState, InlineNotice, LoadingRows, PageHeader, StatusBadge } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function EscalationDetailPage() {
  const { teamId = '', escalationId = '' } = useParams()
  const [scheduleDate, setScheduleDate] = useState('')
  const [scheduleTime, setScheduleTime] = useState('')
  const [scheduleTimezone, setScheduleTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
  const queryClient = useQueryClient()
  const escalation = useQuery({
    queryKey: ['escalation', teamId, escalationId],
    queryFn: () => {
      if (!escalationId) throw new Error('Escalation id is required')
      return getEscalation(escalationId)
    },
    enabled: Boolean(escalationId),
    refetchInterval: query => query.state.data?.status === 'OPEN' ? 3000 : false,
  })
  const execution = useQuery({
    queryKey: ['execution-states', teamId, escalationId],
    queryFn: () => {
      if (!escalationId) throw new Error('Escalation id is required')
      return getExecutionStates(escalationId)
    },
    enabled: Boolean(escalationId && escalation.data),
    refetchInterval: escalation.data?.status === 'OPEN' ? 3000 : false,
  })
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flowId = escalation.data?.flowId
  const flow = useQuery({
    queryKey: ['flow', teamId, flowId],
    queryFn: () => {
      if (!flowId) throw new Error('Flow id is required')
      return getFlow(flowId)
    },
    enabled: Boolean(flowId),
  })
  const nodes = useQuery({
    queryKey: ['flow-nodes', teamId, flowId],
    queryFn: () => {
      if (!flowId) throw new Error('Flow id is required')
      return getFlowNodes(flowId)
    },
    enabled: Boolean(flowId),
  })
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
  const reschedule = useMutation({
    mutationFn: () => rescheduleEscalation(escalationId, { scheduleDate, scheduleTime, timezone: scheduleTimezone }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
      ])
      setScheduleDate('')
      setScheduleTime('')
    },
  })
  const cancel = useMutation({
    mutationFn: () => cancelScheduledEscalation(escalationId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
      ])
    },
  })

  if (escalation.isPending) return <LoadingRows count={4} />
  if (escalation.isError) return <ErrorState message={escalation.error.message} onRetry={() => void escalation.refetch()} />
  const item = escalation.data
  const task = tasks.data?.find(candidate => candidate.id === item.taskId)
  const completed = item.status === 'COMPLETED'
  const scheduled = item.status === 'SCHEDULED'

  return <>
    <div className="back-link-row"><Link to={`/app/${teamId}/escalations`}>← Escalations</Link><span> / </span><span>{item.name}</span></div>
    <PageHeader eyebrow={`EXECUTION / ${item.id.slice(0, 8).toUpperCase()}`} title={item.name} description="Durable progress for this team scoped escalation." action={<StatusBadge status={item.status} />} />
    <InlineNotice>Each active step emails its configured recipient with the task context. SENT means the email service accepted the message; it does not confirm delivery.</InlineNotice>
    {item.status === 'START_FAILED' && <InlineNotice tone="error"><strong>This scheduled escalation could not start.</strong> AlertOps exhausted its start retries. A notification was queued for the scheduler owner and team administrators; undelivered notifications retry and recover after restart.</InlineNotice>}
    {scheduled && item.scheduledStartAt && <Card className="schedule-management-card">
      <div className="card-heading"><div><span className="eyebrow">SCHEDULED START</span><h2>{formatDate(item.scheduledStartAt)}</h2></div><StatusBadge status="SCHEDULED" /></div>
      <p className="form-intro">Configured timezone: {item.scheduleTimezone ?? 'UTC'}. You can change or cancel this one-time start before it begins.</p>
      <form onSubmit={event => { event.preventDefault(); reschedule.mutate() }} className="form-stack">
        <div className="form-grid-two">
          <label className="field"><span>Date</span><input required type="date" value={scheduleDate} onChange={event => setScheduleDate(event.target.value)} /></label>
          <label className="field"><span>Time</span><input required type="time" value={scheduleTime} onChange={event => setScheduleTime(event.target.value)} /></label>
        </div>
        <label className="field"><span>Timezone</span><input required value={scheduleTimezone} onChange={event => setScheduleTimezone(event.target.value)} /></label>
        {(reschedule.error || cancel.error) && <div className="form-error" role="alert">{(reschedule.error ?? cancel.error)?.message}</div>}
        <div className="button-row"><Button variant="secondary" disabled={reschedule.isPending || cancel.isPending}>{reschedule.isPending ? 'Saving…' : 'Reschedule'}</Button><Button type="button" variant="danger" disabled={reschedule.isPending || cancel.isPending} onClick={() => cancel.mutate()}>{cancel.isPending ? 'Cancelling…' : 'Cancel schedule'}</Button></div>
      </form>
    </Card>}
    {item.resolutionType === 'ACKNOWLEDGED' && item.acknowledgedAt && <InlineNotice tone="success">Acknowledged by <strong>{item.issueSolvedBy}</strong> at {formatDate(item.acknowledgedAt)}. Remaining steps were stopped.</InlineNotice>}
    <div className="detail-summary-grid">
      <Card className="detail-summary-card"><span className="eyebrow">TASK CONTEXT</span><strong>{task?.name ?? item.taskId.slice(0, 8)}</strong><p>{task?.description || 'Task details are not available.'}</p></Card>
      <Card className="detail-summary-card"><span className="eyebrow">ESCALATION PATH</span>{flow.data ? <Link className="detail-link" to={`/app/${teamId}/flows/${flow.data.id}`}>{flow.data.name} <span>↗</span></Link> : <strong>{item.flowId.slice(0, 8)}</strong>}<p>{nodes.data ? `${nodes.data.length} configured response ${nodes.data.length === 1 ? 'step' : 'steps'}` : 'Loading path configuration…'}</p></Card>
      <Card className="detail-summary-card"><span className="eyebrow">CREATED AT</span><strong>{formatDate(item.createdAt)}</strong><p>{completed && item.resolutionType ? `Resolution: ${item.resolutionType}` : 'Time shown in your local timezone.'}</p></Card>
    </div>
    <Card className="execution-card">
      <div className="card-heading"><div><span className="eyebrow">SAVED STEP PROGRESS</span><h2>Execution timeline</h2></div><Button variant="secondary" onClick={() => { void escalation.refetch(); void execution.refetch() }}>Refresh&nbsp; ↻</Button></div>
      {execution.isPending ? <LoadingRows count={3} /> : execution.isError ? <ErrorState message={execution.error.message} onRetry={() => void execution.refetch()} /> : execution.data.length === 0 ? <div className="prestart-state"><div className="prestart-illustration">01 <span>→</span> 02 <span>→</span> 03</div><div><strong>{scheduled ? 'This escalation is scheduled to start later.' : 'This escalation is ready to start.'}</strong><p>{scheduled ? 'Start now begins it immediately and keeps the first step’s configured wait.' : 'Starting it saves the path steps and schedules the first wait.'}</p></div>{(item.status === 'IDLE' || scheduled) && <Button disabled={start.isPending || !nodes.data?.length} onClick={() => start.mutate()}>{start.isPending ? 'Starting…' : scheduled ? 'Start now' : 'Start escalation'} <span>→</span></Button>}</div> : <div className="execution-timeline">{execution.data.map((state, index) => {
        const node = nodes.data?.find(candidate => candidate.id === state.nodeId)
        const terminal = state.status === 'SENT' || state.status === 'FAILED' || state.status === 'SKIPPED'
        return <article className="execution-row" key={state.nodeId}><div className={`execution-index execution-${state.status.toLowerCase()}`}>{terminal ? '✓' : String(index + 1).padStart(2, '0')}</div><div className="execution-connector" /><div className="execution-copy"><div className="execution-title"><div><strong>{node ? nodeName(node) : `Response step ${index + 1}`}</strong><small>{state.userEmail}</small></div><div className="execution-badges"><StatusBadge status={state.status} /></div></div><div className="execution-details"><span>WAIT&nbsp; {node ? `${nodeDelayMinutes(node)} MIN` : '—'}</span><span>ATTEMPTS&nbsp; {state.sendAttemptCount}</span><span>UPDATED&nbsp; {formatDate(state.updatedAt ?? state.createdAt)}</span></div></div></article>
      })}</div>}
      {start.error && <div className="form-error start-error" role="alert">{start.error.message}</div>}
      {item.status === 'OPEN' && <div className="polling-note"><span className="live-dot" /> Refreshing saved state every 3 seconds while this run is active.</div>}
    </Card>
    <div className="last-updated">ESCALATION ID&nbsp; <code>{item.id}</code></div>
  </>
}
