import { useMemo, useState } from 'react'
import { Link, useParams } from 'react-router'
import { useQuery } from '@tanstack/react-query'
import { getAllEscalationHistory, getAllEscalations } from '../../api/escalations'
import type { EscalationHistoryAction, EscalationHistoryEvent } from '../../api/types'
import { Button, Card, EmptyState, ErrorState, LoadingRows, PageHeader } from '../../components/Elements'
import { NavIcon } from '../../components/NavIcon'
import { formatDate } from '../../lib/format'

const actionLabels: Record<EscalationHistoryAction, string> = {
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

const actionOptions: EscalationHistoryAction[] = Object.keys(actionLabels) as EscalationHistoryAction[]

interface TeamAuditEvent extends EscalationHistoryEvent {
  escalationId: string
  escalationName: string
  escalationStatus: string
}

const emptyAuditEvents: TeamAuditEvent[] = []

// Loads escalation history and attaches the owning run for the team audit list.
async function loadTeamAuditEvents(): Promise<TeamAuditEvent[]> {
  const escalations = await getAllEscalations()
  const histories = await Promise.all(escalations.map(async escalation => {
    const events = await getAllEscalationHistory(escalation.id)
    return events.map(event => ({
      ...event,
      escalationId: escalation.id,
      escalationName: escalation.name,
      escalationStatus: escalation.status,
    }))
  }))
  return histories.flat().sort((left, right) => Date.parse(right.occurredAt) - Date.parse(left.occurredAt))
}

// Converts an audit action into wording that is easy to scan in the timeline.
function auditActionLabel(action: EscalationHistoryAction): string {
  return actionLabels[action]
}

// Returns the human-readable actor already made safe by the backend response.
function auditActorLabel(event: TeamAuditEvent): string {
  return event.actorType === 'SYSTEM' ? 'ReplyTrail system' : event.actorEmail ?? 'Team member'
}

// Formats the safe supporting fields returned with an audit event.
function auditEventDetails(event: TeamAuditEvent): string {
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

// Resolves a date-only filter value to a local calendar boundary.
function localDateBoundary(value: string, endOfDay: boolean): number | undefined {
  if (!value) return undefined
  const [year, month, day] = value.split('-').map(Number)
  const date = new Date(year, month - 1, day, endOfDay ? 23 : 0, endOfDay ? 59 : 0, endOfDay ? 59 : 0, endOfDay ? 999 : 0)
  if (Number.isNaN(date.getTime()) || date.getFullYear() !== year || date.getMonth() !== month - 1 || date.getDate() !== day) return undefined
  return date.getTime()
}

// Checks whether an event matches the current audit filters.
function matchesAuditFilters(event: TeamAuditEvent, action: EscalationHistoryAction | 'ALL', search: string, fromDate: string, toDate: string): boolean {
  if (action !== 'ALL' && event.action !== action) return false
  const eventTimestamp = Date.parse(event.occurredAt)
  const fromTimestamp = localDateBoundary(fromDate, false)
  const toTimestamp = localDateBoundary(toDate, true)
  if (fromTimestamp !== undefined && (Number.isNaN(eventTimestamp) || eventTimestamp < fromTimestamp)) return false
  if (toTimestamp !== undefined && (Number.isNaN(eventTimestamp) || eventTimestamp > toTimestamp)) return false
  const query = search.trim().toLowerCase()
  if (!query) return true
  return [event.escalationName, event.action, auditActionLabel(event.action), auditActorLabel(event), event.reason ?? '', auditEventDetails(event)]
    .some(value => value.toLowerCase().includes(query))
}

// Renders one audit event with its owning escalation and safe event details.
function AuditEventRow({ event, teamId }: { event: TeamAuditEvent; teamId: string }) {
  const details = auditEventDetails(event)
  return <article className="audit-event-row">
    <div className="audit-event-marker"><NavIcon name="audit" /></div>
    <div className="audit-event-content">
      <div className="audit-event-heading">
        <div className="audit-event-title"><strong>{auditActionLabel(event.action)}</strong><span className={`audit-action-tag audit-action-${event.action.toLowerCase()}`}>{event.action.replaceAll('_', ' ')}</span></div>
        <time dateTime={event.occurredAt}>{formatDate(event.occurredAt)}</time>
      </div>
      <div className="audit-event-context"><Link to={`/app/${teamId}/escalations/${event.escalationId}`}>{event.escalationName}</Link><span>{event.escalationStatus.replaceAll('_', ' ')}</span></div>
      <p>{auditActorLabel(event)}{event.reason ? ` · ${event.reason}` : ''}</p>
      {details && <div className="audit-event-details">{details}</div>}
    </div>
  </article>
}

// Renders the team-wide view assembled from existing escalation history APIs.
export function AuditPage() {
  const { teamId = '' } = useParams()
  const [search, setSearch] = useState('')
  const [action, setAction] = useState<EscalationHistoryAction | 'ALL'>('ALL')
  const [fromDate, setFromDate] = useState('')
  const [toDate, setToDate] = useState('')
  const audit = useQuery({ queryKey: ['team-audit', teamId], queryFn: loadTeamAuditEvents, enabled: Boolean(teamId) })
  const events = audit.data ?? emptyAuditEvents
  const filteredEvents = useMemo(() => events.filter(event => matchesAuditFilters(event, action, search, fromDate, toDate)), [action, events, fromDate, search, toDate])
  const escalationCount = new Set(events.map(event => event.escalationId)).size
  const issueCount = events.filter(event => ['START_FAILED', 'NOTIFICATION_FAILED', 'NOTIFICATION_RETRY_SCHEDULED'].includes(event.action)).length
  const invalidDateRange = Boolean(fromDate && toDate && fromDate > toDate)
  const hasDateRange = Boolean(fromDate || toDate)

  return <>
    <PageHeader
      eyebrow="CONTROL ROOM / AUDIT"
      title="Audit trail"
      description="A complete view of the escalation events already saved for this workspace."
      action={<Button variant="secondary" disabled={audit.isFetching} onClick={() => void audit.refetch()}>{audit.isFetching ? 'Refreshing…' : 'Refresh activity'} <span>↻</span></Button>}
    />
    <Card className="audit-summary-card">
      <div className="audit-summary-copy"><div className="audit-summary-icon"><NavIcon name="audit" /></div><div><span className="eyebrow">DURABLE RECORD</span><h2>Review every handoff in one timeline.</h2><p>Browse the saved lifecycle of each escalation, including notifications, retries, acknowledgements, and resolution.</p></div></div>
      <div className="audit-summary-stats"><div><span>EVENTS</span><strong>{audit.isPending ? '—' : events.length}</strong></div><div><span>ESCALATIONS</span><strong>{audit.isPending ? '—' : escalationCount}</strong></div><div><span>DELIVERY ISSUES</span><strong>{audit.isPending ? '—' : issueCount}</strong></div></div>
    </Card>
    <Card className="audit-card">
      <div className="card-heading"><div><span className="eyebrow">ALL SAVED EVENTS</span><h2>What happened</h2></div><span className="count-pill">{filteredEvents.length} shown</span></div>
      <div className="audit-toolbar">
        <label className="audit-control"><span>Search activity</span><input value={search} onChange={event => setSearch(event.target.value)} placeholder="Escalation, actor, recipient…" /></label>
        <div className="audit-date-range"><label className="audit-control"><span>From date</span><input type="date" value={fromDate} onChange={event => setFromDate(event.target.value)} /></label><label className="audit-control"><span>To date</span><input type="date" value={toDate} onChange={event => setToDate(event.target.value)} /></label></div>
        <label className="audit-control audit-action-filter"><span>Event type</span><select value={action} onChange={event => setAction(event.target.value as EscalationHistoryAction | 'ALL')}><option value="ALL">All event types</option>{actionOptions.map(option => <option key={option} value={option}>{auditActionLabel(option)}</option>)}</select></label>
        {hasDateRange && <Button className="audit-range-clear" variant="quiet" type="button" onClick={() => { setFromDate(''); setToDate('') }}>Clear dates</Button>}
      </div>
      {invalidDateRange && <div className="audit-range-error" role="alert">The From date must be on or before the To date.</div>}
      {audit.isPending ? <LoadingRows count={5} /> : audit.isError ? <ErrorState message={audit.error.message} onRetry={() => void audit.refetch()} /> : events.length === 0 ? <EmptyState title="No audit events yet" description="Events will appear here when an escalation is created or progresses through its response path." /> : filteredEvents.length === 0 ? <div className="audit-filter-empty">No saved events match these filters.</div> : <div className="audit-event-list">{filteredEvents.map(event => <AuditEventRow event={event} teamId={teamId} key={event.id} />)}</div>}
    </Card>
  </>
}
