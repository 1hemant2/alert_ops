import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { cancelScheduledEscalation, escalateNow, getEscalation, getEscalationHistory, getExecutionStates, previewEscalateNow, resolveEscalation, rescheduleEscalation, startEscalation } from '../../api/escalations'
import type { EscalationHistoryEvent } from '../../api/types'
import { getFlow, getFlowNodes, nodeDelayMinutes, nodeName } from '../../api/flows'
import { getTasks } from '../../api/tasks'
import { Button, Card, ErrorState, InlineNotice, LoadingRows, PageHeader, StatusBadge } from '../../components/Elements'
import { formatDate } from '../../lib/format'

const ACTIVITY_PAGE_SIZE = 20

const activityActionLabels: Record<string, string> = {
  CREATED: 'Escalation created',
  SCHEDULED: 'Escalation scheduled',
  RESCHEDULED: 'Escalation rescheduled',
  STARTED: 'Escalation started',
  CANCELLED: 'Schedule cancelled',
  START_FAILED: 'Escalation could not start',
  NOTIFICATION_SENT: 'Notification sent',
  NOTIFICATION_FAILED: 'Notification delivery failed',
  NOTIFICATION_RETRY_SCHEDULED: 'Notification retry scheduled',
  ACKNOWLEDGED: 'Escalation acknowledged',
  ESCALATED_NOW: 'Escalated to the next recipient',
  RESOLVED: 'Escalation resolved',
  ACKNOWLEDGEMENT_EXPIRED: 'Acknowledgement wait expired',
  RESOLUTION_EXPIRED: 'Resolution wait expired',
  COMPLETED: 'Escalation completed',
}

interface ActivityGroup {
  key: string
  events: EscalationHistoryEvent[]
  isRetryGroup: boolean
}

// Identifies delivery failures and retries that belong in one expandable group.
function isRetryActivity(event: EscalationHistoryEvent): boolean {
  return event.action === 'NOTIFICATION_FAILED' || event.action === 'NOTIFICATION_RETRY_SCHEDULED'
}

// Keeps retry events together while preserving the order of their first occurrence.
function groupActivityEvents(events: EscalationHistoryEvent[]): ActivityGroup[] {
  const groups: ActivityGroup[] = []
  const retryGroups = new Map<string, ActivityGroup>()
  events.forEach(event => {
    if (!isRetryActivity(event)) {
      groups.push({ key: event.id, events: [event], isRetryGroup: false })
      return
    }
    const stepKey = event.details.executionStepId ?? event.id
    const existingGroup = retryGroups.get(stepKey)
    if (existingGroup) {
      existingGroup.events.push(event)
      return
    }
    const newGroup = { key: `retry-${stepKey}`, events: [event], isRetryGroup: true }
    retryGroups.set(stepKey, newGroup)
    groups.push(newGroup)
  })
  return groups
}

// Converts an audit action into short wording suitable for the activity timeline.
function activityLabel(event: EscalationHistoryEvent): string {
  return activityActionLabels[event.action] ?? event.action.replaceAll('_', ' ').toLowerCase()
}

// Describes the safe actor information returned by the history endpoint.
function activityActor(event: EscalationHistoryEvent): string {
  return event.actorType === 'SYSTEM' ? 'ReplyTrail system' : event.actorEmail ?? 'Team member'
}

// Formats safe step, recipient, attempt, and timing details without exposing raw metadata.
function activityDetails(event: EscalationHistoryEvent): string {
  const details = event.details
  const values = [
    details.recipientEmail ? `Recipient ${details.recipientEmail}` : '',
    details.executionStepId ? `Step ${details.executionStepId.slice(0, 8)}` : '',
    details.attempt ? `Attempt ${details.attempt}` : '',
    details.nextAttempt ? `Next attempt ${details.nextAttempt}` : '',
    details.retryAt ? `Retry at ${formatDate(details.retryAt)}` : '',
    details.acknowledgementTimeoutAt ? `Acknowledgement by ${formatDate(details.acknowledgementTimeoutAt)}` : '',
    details.timeoutAt ? `Timeout at ${formatDate(details.timeoutAt)}` : '',
    event.previousState && event.newState ? `${event.previousState} → ${event.newState}` : '',
  ]
  return values.filter(Boolean).join(' · ')
}

// Renders one saved activity event with its actor, time, and safe supporting details.
function ActivityEventEntry({ event }: { event: EscalationHistoryEvent }) {
  const details = activityDetails(event)
  return <article className="activity-entry">
    <div className="activity-marker" />
    <div className="activity-copy">
      <div className="activity-title"><strong>{activityLabel(event)}</strong><time dateTime={event.occurredAt}>{formatDate(event.occurredAt)}</time></div>
      <p>{activityActor(event)}{event.reason ? ` · ${event.reason}` : ''}</p>
      {details && <div className="activity-details">{details}</div>}
    </div>
  </article>
}

// Explains the current saved state of one execution step.
function stepStatusExplanation(status: string): string {
  switch (status) {
    case 'PENDING': return 'Waiting for an earlier step to finish.'
    case 'SCHEDULED': return 'Waiting until its saved response time.'
    case 'PAUSED': return 'Paused while the current recipient resolves the escalation.'
    case 'SENDING': return 'Sending the notification.'
    case 'SENT': return 'Notification accepted; waiting for acknowledgement.'
    case 'FAILED': return 'Notification delivery failed after its retry attempts.'
    case 'SKIPPED': return 'Skipped because the escalation finished before this step was sent.'
    default: return 'Saved step state.'
  }
}

// Displays optional saved task metadata without hiding older rows that lack it.
function taskMetadataValue(value?: string | null): string {
  return value?.trim() || 'Not provided'
}

// Displays the current escalation, saved step progress, and activity history.
// Renders the saved escalation progress and activity history.
export function EscalationDetailPage() {
  const { teamId = '', escalationId = '' } = useParams()
  const [scheduleDate, setScheduleDate] = useState('')
  const [scheduleTime, setScheduleTime] = useState('')
  const [scheduleTimezone, setScheduleTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
  const queryClient = useQueryClient()
  const activityHistoryKey = ['escalation-history', teamId, escalationId]
  // Refreshes saved activity after an escalation action changes its history.
  const invalidateActivityHistory = () => queryClient.invalidateQueries({ queryKey: activityHistoryKey })
  const escalation = useQuery({
    queryKey: ['escalation', teamId, escalationId],
    queryFn: () => {
      if (!escalationId) throw new Error('Escalation id is required')
      return getEscalation(escalationId)
    },
    enabled: Boolean(escalationId),
    refetchInterval: query => ['OPEN', 'ACKNOWLEDGED'].includes(query.state.data?.status ?? '') ? 3000 : false,
  })
  const execution = useQuery({
    queryKey: ['execution-states', teamId, escalationId],
    queryFn: () => {
      if (!escalationId) throw new Error('Escalation id is required')
      return getExecutionStates(escalationId)
    },
    enabled: Boolean(escalationId && escalation.data),
    refetchInterval: ['OPEN', 'ACKNOWLEDGED'].includes(escalation.data?.status ?? '') ? 3000 : false,
  })
  const activityHistory = useInfiniteQuery({
    queryKey: activityHistoryKey,
    queryFn: ({ pageParam }) => {
      if (!escalationId) throw new Error('Escalation id is required')
      return getEscalationHistory(escalationId, pageParam, ACTIVITY_PAGE_SIZE)
    },
    initialPageParam: 0,
    getNextPageParam: lastPage => lastPage.last ? undefined : lastPage.page + 1,
    enabled: Boolean(escalationId && escalation.data),
    refetchInterval: ['OPEN', 'ACKNOWLEDGED'].includes(escalation.data?.status ?? '') ? 3000 : false,
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
        invalidateActivityHistory(),
      ])
    },
  })
  const reschedule = useMutation({
    mutationFn: () => rescheduleEscalation(escalationId, { scheduleDate, scheduleTime, timezone: scheduleTimezone }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
        invalidateActivityHistory(),
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
        invalidateActivityHistory(),
      ])
    },
  })
  const actionSourceStepId = escalation.data?.status === 'ACKNOWLEDGED'
    ? escalation.data.acknowledgedStepId ?? ''
    : [...(execution.data ?? [])].reverse().find(state => state.status === 'SENT')?.id ?? ''
  const actionTarget = (execution.data ?? []).find(state => ['PAUSED', 'PENDING', 'SCHEDULED'].includes(state.status))
  const actionTargetStepId = actionTarget?.id ?? ''
  const manualActionRequest = actionSourceStepId && actionTargetStepId
    ? { expectedSourceStepId: actionSourceStepId, expectedTargetStepId: actionTargetStepId }
    : null
  const [showEscalateNow, setShowEscalateNow] = useState(false)
  const manualPreview = useQuery({
    queryKey: ['escalate-now-preview', teamId, escalationId, actionSourceStepId, actionTargetStepId],
    queryFn: () => previewEscalateNow(escalationId, manualActionRequest!),
    enabled: showEscalateNow && Boolean(manualActionRequest),
  })
  const manualAction = useMutation({
    mutationFn: () => escalateNow(escalationId, manualActionRequest!),
    onSuccess: async () => {
      setShowEscalateNow(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
        queryClient.invalidateQueries({ queryKey: ['execution-states', teamId, escalationId] }),
        invalidateActivityHistory(),
      ])
    },
  })
  const resolution = useMutation({
    mutationFn: () => resolveEscalation(escalationId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['escalation', teamId, escalationId] }),
        queryClient.invalidateQueries({ queryKey: ['escalations', teamId] }),
        queryClient.invalidateQueries({ queryKey: ['execution-states', teamId, escalationId] }),
        invalidateActivityHistory(),
      ])
    },
  })

  if (escalation.isPending) return <LoadingRows count={4} />
  if (escalation.isError) return <ErrorState message={escalation.error.message} onRetry={() => void escalation.refetch()} />
  const item = escalation.data
  const task = tasks.data?.find(candidate => candidate.id === item.taskId)
  const savedTask = execution.data?.[0]
  const taskName = savedTask ? savedTask.taskName : task?.name
  const taskDescription = savedTask ? savedTask.taskDetails : task?.description
  const taskSource = savedTask ? savedTask.taskSource : task?.source
  const taskPriority = savedTask ? savedTask.taskPriority : task?.priority
  const taskCategory = savedTask ? savedTask.taskCategory : task?.category
  const taskReferenceUrl = savedTask ? savedTask.taskReferenceUrl : task?.referenceUrl
  const completed = item.status === 'COMPLETED'
  const scheduled = item.status === 'SCHEDULED'
  const canOfferEscalateNow = (item.status === 'OPEN' || item.status === 'ACKNOWLEDGED') && Boolean(manualActionRequest)
  const activityEvents = activityHistory.data?.pages.flatMap(page => page.events) ?? []
  const activityGroups = groupActivityEvents(activityEvents)
  const activityEventCount = activityHistory.data?.pages[0]?.totalEvents ?? 0

  return <>
    <div className="back-link-row"><Link to={`/app/${teamId}/escalations`}>← Escalations</Link><span> / </span><span>{item.name}</span></div>
    <PageHeader eyebrow={`EXECUTION / ${item.id.slice(0, 8).toUpperCase()}`} title={item.name} description="Durable progress for this team scoped escalation." action={<StatusBadge status={item.status} />} />
    <InlineNotice>Each active step emails its configured recipient with the task context. SENT means the email service accepted the message; it does not confirm delivery.</InlineNotice>
    {item.status === 'START_FAILED' && <InlineNotice tone="error"><strong>This scheduled escalation could not start.</strong> ReplyTrail exhausted its start retries. A notification was queued for the scheduler owner and team administrators; undelivered notifications retry and recover after restart.</InlineNotice>}
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
    {item.status === 'ACKNOWLEDGED' && <Card className="resolution-action-card">
      <div className="card-heading"><div><span className="eyebrow">ACTIVE RESOLUTION</span><h2>Someone is working on this escalation</h2></div><StatusBadge status="ACKNOWLEDGED" /></div>
      <p className="form-intro">Acknowledged by <strong>{item.issueSolvedBy ?? 'the current recipient'}</strong>{item.acknowledgedAt ? ` at ${formatDate(item.acknowledgedAt)}` : ''}. The next step is paused until the issue is resolved or the deadline is reached.</p>
      <div className="acknowledgement-details"><span>RESOLUTION DEADLINE</span><strong>{item.resolutionDeadline ? formatDate(item.resolutionDeadline) : 'Saved deadline unavailable'}</strong></div>
      {resolution.error && <div className="form-error" role="alert">{resolution.error.message}</div>}
      <Button disabled={resolution.isPending} onClick={() => resolution.mutate()}>{resolution.isPending ? 'Resolving…' : 'Resolve escalation'}</Button>
    </Card>}
    {resolution.data && <InlineNotice tone="success">Escalation resolved by <strong>{resolution.data.resolvedBy ?? 'the current team member'}</strong>{resolution.data.resolvedAt ? ` at ${formatDate(resolution.data.resolvedAt)}` : ''}. Remaining response steps were skipped.</InlineNotice>}
    {canOfferEscalateNow && <Card className="manual-action-card">
      <div className="card-heading"><div><span className="eyebrow">MANUAL ACTION</span><h2>Escalate now</h2></div><StatusBadge status={item.status} /></div>
      <p className="form-intro">Remove the current wait and notify {actionTarget?.userEmail ?? 'the next recipient'} immediately. The saved step order stays unchanged.</p>
      {!showEscalateNow && <Button variant="secondary" onClick={() => setShowEscalateNow(true)}>Review next notification <span>→</span></Button>}
      {showEscalateNow && <>
        {manualPreview.isPending && <p role="status">Checking the current next step…</p>}
        {manualPreview.isError && <div className="form-error" role="alert">{manualPreview.error.message}</div>}
        {manualPreview.data && <div className="acknowledgement-details"><span>NEXT RECIPIENT</span><strong>{manualPreview.data.targetRecipientEmail ?? 'Unavailable'}</strong>{manualPreview.data.actionDeadline && <><span>ACTION AVAILABLE UNTIL</span><strong>{formatDate(manualPreview.data.actionDeadline)}</strong></>}</div>}
        {manualPreview.data && !manualPreview.data.actionAvailable && <div className="form-error" role="alert">{manualPreview.data.unavailableReason ?? 'This action is no longer available.'}</div>}
        {manualAction.error && <div className="form-error" role="alert">{manualAction.error.message}</div>}
        <div className="button-row"><Button variant="secondary" disabled={manualAction.isPending} onClick={() => setShowEscalateNow(false)}>Cancel</Button><Button disabled={manualAction.isPending || !manualPreview.data?.actionAvailable} onClick={() => manualAction.mutate()}>{manualAction.isPending ? 'Scheduling…' : item.status === 'ACKNOWLEDGED' ? 'Confirm and escalate now' : 'Escalate now'}</Button></div>
      </>}
    </Card>}
    <div className="detail-summary-grid">
      <Card className="detail-summary-card task-context-summary"><span className="eyebrow">TASK CONTEXT</span><strong>{taskName ?? item.taskId.slice(0, 8)}</strong><p>{taskDescription || 'Task details are not available.'}</p><div className="task-context-meta"><span>Source <strong>{taskMetadataValue(taskSource)}</strong></span><span>Priority <strong>{taskMetadataValue(taskPriority)}</strong></span><span>Category <strong>{taskMetadataValue(taskCategory)}</strong></span><span>Reference <strong>{taskReferenceUrl ? <a href={taskReferenceUrl} target="_blank" rel="noreferrer">Open reference ↗</a> : 'Not provided'}</strong></span></div><small>{savedTask ? 'Saved from the task when this run started.' : 'Current task context before this run starts.'}</small></Card>
      <Card className="detail-summary-card"><span className="eyebrow">ESCALATION PATH</span>{flow.data ? <Link className="detail-link" to={`/app/${teamId}/flows/${flow.data.id}`}>{flow.data.name} <span>↗</span></Link> : <strong>{item.flowId.slice(0, 8)}</strong>}<p>{nodes.data ? `${nodes.data.length} configured response ${nodes.data.length === 1 ? 'step' : 'steps'}` : 'Loading path configuration…'}</p></Card>
      <Card className="detail-summary-card"><span className="eyebrow">CREATED AT</span><strong>{formatDate(item.createdAt)}</strong><p>{completed && item.resolutionType ? `Resolution: ${item.resolutionType}` : 'Time shown in your local timezone.'}</p></Card>
    </div>
    <Card className="execution-card">
      <div className="card-heading"><div><span className="eyebrow">SAVED STEP PROGRESS</span><h2>Step progress</h2></div><Button variant="secondary" onClick={() => { void escalation.refetch(); void execution.refetch(); void activityHistory.refetch() }}>Refresh&nbsp; ↻</Button></div>
      {execution.isPending ? <LoadingRows count={3} /> : execution.isError ? <ErrorState message={execution.error.message} onRetry={() => void execution.refetch()} /> : execution.data.length === 0 ? <div className="prestart-state"><div className="prestart-illustration">01 <span>→</span> 02 <span>→</span> 03</div><div><strong>{scheduled ? 'This escalation is scheduled to start later.' : 'This escalation is ready to start.'}</strong><p>{scheduled ? 'Start now begins it immediately and keeps the first step’s configured wait.' : 'Starting it saves the path steps and schedules the first wait.'}</p></div>{(item.status === 'IDLE' || scheduled) && <Button disabled={start.isPending || !nodes.data?.length} onClick={() => start.mutate()}>{start.isPending ? 'Starting…' : scheduled ? 'Start now' : 'Start escalation'} <span>→</span></Button>}</div> : <div className="execution-timeline">{execution.data.map((state, index) => {
        const node = nodes.data?.find(candidate => candidate.id === state.nodeId)
        const terminal = state.status === 'SENT' || state.status === 'FAILED' || state.status === 'SKIPPED'
        return <article className="execution-row" key={state.nodeId}><div className={`execution-index execution-${state.status.toLowerCase()}`}>{terminal ? '✓' : String(index + 1).padStart(2, '0')}</div><div className="execution-connector" /><div className="execution-copy"><div className="execution-title"><div><strong>{node ? nodeName(node) : `Response step ${index + 1}`}</strong><small>{state.userEmail}</small></div><div className="execution-badges"><StatusBadge status={state.status} /></div></div><div className="execution-details"><span>WAIT&nbsp; {node ? `${nodeDelayMinutes(node)} MIN` : '—'}</span><span>ATTEMPTS&nbsp; {state.sendAttemptCount}</span><span>UPDATED&nbsp; {formatDate(state.updatedAt ?? state.createdAt)}</span></div><p className="execution-explanation">{stepStatusExplanation(state.status)}{state.dueAt && ['PENDING', 'SCHEDULED'].includes(state.status) ? ` Due ${formatDate(state.dueAt)}.` : ''}</p></div></article>
      })}</div>}
      {start.error && <div className="form-error start-error" role="alert">{start.error.message}</div>}
      {['OPEN', 'ACKNOWLEDGED'].includes(item.status) && <div className="polling-note"><span className="live-dot" /> Refreshing status, step progress, and activity every 3 seconds while this run is active.</div>}
    </Card>
    <Card className="activity-card">
      <div className="card-heading">
        <div><span className="eyebrow">ACTIVITY HISTORY</span><h2>What happened</h2></div>
        {activityHistory.data && <span className="count-pill">{activityEventCount} events</span>}
      </div>
      {activityHistory.isPending ? <LoadingRows count={3} /> : activityHistory.isError ? <ErrorState message={activityHistory.error.message} onRetry={() => void activityHistory.refetch()} /> : activityEvents.length === 0 ? <div className="activity-empty">No activity has been recorded yet.</div> : <>
        <div className="activity-list">
          {activityGroups.map(group => {
            if (!group.isRetryGroup) return <ActivityEventEntry event={group.events[0]!} key={group.key} />
            return <details className="activity-retry-group" key={group.key}>
              <summary><span><strong>Delivery retry activity</strong><small>{group.events[0]?.details.recipientEmail ?? 'Recipient unavailable'}</small></span><em>{group.events.length} saved events</em></summary>
              <div className="activity-retry-events">{group.events.map(event => <ActivityEventEntry event={event} key={event.id} />)}</div>
            </details>
          })}
        </div>
        {activityHistory.hasNextPage && <div className="activity-load-more"><Button variant="secondary" disabled={activityHistory.isFetchingNextPage} onClick={() => void activityHistory.fetchNextPage()}>{activityHistory.isFetchingNextPage ? 'Loading…' : 'Load more activity'}</Button></div>}
      </>}
    </Card>
    <div className="last-updated">ESCALATION ID&nbsp; <code>{item.id}</code></div>
  </>
}
