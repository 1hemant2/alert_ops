import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createEscalation, getEscalations, scheduleEscalation, startEscalation } from '../../api/escalations'
import { getFlows } from '../../api/flows'
import { getTasks } from '../../api/tasks'
import { Button, Card, EmptyState, ErrorState, Field, LoadingRows, PageHeader, StatusBadge } from '../../components/Elements'
import { formatDate } from '../../lib/format'

// Shows team escalations in a scrollable table with run creation controls.
export function EscalationsPage() {
  const { teamId = '' } = useParams()
  const [name, setName] = useState('')
  const [taskId, setTaskId] = useState('')
  const [flowId, setFlowId] = useState('')
  const [startMode, setStartMode] = useState<'IMMEDIATE' | 'SCHEDULED'>('IMMEDIATE')
  const [scheduleDate, setScheduleDate] = useState('')
  const [scheduleTime, setScheduleTime] = useState('')
  const [timezone, setTimezone] = useState(() => Intl.DateTimeFormat().resolvedOptions().timeZone || 'UTC')
  const queryClient = useQueryClient()
  const escalations = useQuery({ queryKey: ['escalations', teamId], queryFn: getEscalations, refetchInterval: query => query.state.data?.some(item => item.status === 'OPEN') ? 4000 : false })
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const create = useMutation({
    mutationFn: async () => {
      const created = await createEscalation({ escalationName: name.trim(), taskId, flowId })
      if (startMode === 'SCHEDULED') {
        return scheduleEscalation(created.id, { scheduleDate, scheduleTime, timezone })
      }
      return startEscalation(created.id)
    },
    onSuccess: async () => {
      setName('')
      setStartMode('IMMEDIATE')
      setScheduleDate('')
      setScheduleTime('')
      await queryClient.invalidateQueries({ queryKey: ['escalations', teamId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  const canCreate = Boolean(tasks.data?.length && flows.data?.length)
  return <>
    <PageHeader eyebrow="EXECUTION / ESCALATIONS" title="Escalation runs" description="A run pairs a task with an escalation path and saves the state of every response step." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">LIVE + HISTORY</span><h2>Team escalations</h2></div><span className="count-pill">{escalations.data?.length ?? '—'}</span></div>
        {escalations.isPending ? <LoadingRows count={4} /> : escalations.isError ? <ErrorState message={escalations.error.message} onRetry={() => void escalations.refetch()} /> : escalations.data.length === 0 ? <EmptyState title="No escalations have run" description="Choose a task and an escalation path to create your first team escalation." /> : <div className="table-scroll" role="region" aria-label="Escalations" tabIndex={0}><table><thead><tr><th>NAME</th><th>STATUS</th><th>CREATED</th><th /></tr></thead><tbody>{escalations.data.map(item => <tr key={item.id}><td><Link className="table-primary" to={`/app/${teamId}/escalations/${item.id}`}>{item.name}</Link><small className="table-subtext">{item.id.slice(0, 8)}</small></td><td><StatusBadge status={item.status} /></td><td>{formatDate(item.createdAt)}</td><td><Link className="table-arrow" to={`/app/${teamId}/escalations/${item.id}`}>→</Link></td></tr>)}</tbody></table></div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW EXECUTION</span><h2>Configure a run</h2></div><span className="form-number">03</span></div>
        <p className="form-intro">Choose the task context and the response path to run.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Escalation name"><input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="API latency response" /></Field>
          <Field label="Task"><select required value={taskId} onChange={event => setTaskId(event.target.value)}><option value="">Choose a task</option>{tasks.data?.map(task => <option key={task.id} value={task.id}>{task.name}</option>)}</select></Field>
          <Field label="Escalation path"><select required value={flowId} onChange={event => setFlowId(event.target.value)}><option value="">Choose a path</option>{flows.data?.map(flow => <option key={flow.id} value={flow.id}>{flow.name}</option>)}</select></Field>
          <Field label="When should escalation start?"><select value={startMode} onChange={event => setStartMode(event.target.value as 'IMMEDIATE' | 'SCHEDULED')}><option value="IMMEDIATE">Start immediately</option><option value="SCHEDULED">Schedule for later</option></select></Field>
          {startMode === 'SCHEDULED' && <>
            <div className="form-grid-two">
              <Field label="Date"><input required type="date" value={scheduleDate} onChange={event => setScheduleDate(event.target.value)} /></Field>
              <Field label="Time"><input required type="time" value={scheduleTime} onChange={event => setScheduleTime(event.target.value)} /></Field>
            </div>
            <Field label="Timezone" hint="Use an IANA timezone, for example Asia/Kolkata."><input required value={timezone} onChange={event => setTimezone(event.target.value)} placeholder="Asia/Kolkata" /></Field>
          </>}
          {!canCreate && <div className="form-hint">Create at least one task and one escalation path first.</div>}
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending || !canCreate}>{create.isPending ? 'Creating…' : startMode === 'SCHEDULED' ? 'Schedule escalation' : 'Start escalation'} <span>→</span></Button>
        </form>
        <div className="side-callout"><span>BEFORE STARTING</span><p>Make sure the selected path has at least one response step. Scheduled runs stay quiet until their configured time.</p></div>
      </Card>
    </div>
  </>
}
