import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createEscalation, getEscalations } from '../../api/escalations'
import { getFlows } from '../../api/flows'
import { getTasks } from '../../api/tasks'
import { Button, Card, EmptyState, ErrorState, Field, LoadingRows, PageHeader, StatusBadge } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function EscalationsPage() {
  const { teamId = '' } = useParams()
  const [name, setName] = useState('')
  const [taskId, setTaskId] = useState('')
  const [flowId, setFlowId] = useState('')
  const queryClient = useQueryClient()
  const escalations = useQuery({ queryKey: ['escalations', teamId], queryFn: getEscalations, refetchInterval: query => query.state.data?.some(item => item.status === 'RUNNING') ? 4000 : false })
  const tasks = useQuery({ queryKey: ['tasks', teamId], queryFn: getTasks })
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const create = useMutation({
    mutationFn: () => createEscalation({ escalationName: name.trim(), taskId, flowId }),
    onSuccess: async () => {
      setName('')
      await queryClient.invalidateQueries({ queryKey: ['escalations', teamId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  const canCreate = Boolean(tasks.data?.length && flows.data?.length)
  return <>
    <PageHeader eyebrow="EXECUTION / ESCALATIONS" title="Escalation runs" description="A run binds a task to an ordered flow, then persists the state of every step." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">LIVE + HISTORY</span><h2>Team escalations</h2></div><span className="count-pill">{escalations.data?.length ?? '—'}</span></div>
        {escalations.isPending ? <LoadingRows count={4} /> : escalations.isError ? <ErrorState message={escalations.error.message} onRetry={() => void escalations.refetch()} /> : escalations.data.length === 0 ? <EmptyState title="No escalations have run" description="Choose a task and a flow to create your first team escalation." /> : <div className="table-scroll"><table><thead><tr><th>NAME</th><th>STATUS</th><th>CREATED</th><th /></tr></thead><tbody>{escalations.data.map(item => <tr key={item.id}><td><Link className="table-primary" to={`/app/${teamId}/escalations/${item.id}`}>{item.name}</Link><small className="table-subtext">{item.id.slice(0, 8)}</small></td><td><StatusBadge status={item.status} /></td><td>{formatDate(item.createdAt)}</td><td><Link className="table-arrow" to={`/app/${teamId}/escalations/${item.id}`}>→</Link></td></tr>)}</tbody></table></div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW EXECUTION</span><h2>Configure a run</h2></div><span className="form-number">03</span></div>
        <p className="form-intro">Choose the incident context and the response path to run.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Escalation name"><input required maxLength={120} value={name} onChange={event => setName(event.target.value)} placeholder="API latency response" /></Field>
          <Field label="Task"><select required value={taskId} onChange={event => setTaskId(event.target.value)}><option value="">Choose a task</option>{tasks.data?.map(task => <option key={task.id} value={task.id}>{task.name}</option>)}</select></Field>
          <Field label="Flow"><select required value={flowId} onChange={event => setFlowId(event.target.value)}><option value="">Choose a flow</option>{flows.data?.map(flow => <option key={flow.id} value={flow.id}>{flow.name}</option>)}</select></Field>
          {!canCreate && <div className="form-hint">Create at least one task and one flow first.</div>}
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending || !canCreate}>{create.isPending ? 'Creating…' : 'Create escalation'} <span>→</span></Button>
        </form>
        <div className="side-callout"><span>BEFORE STARTING</span><p>Make sure the selected flow has at least one recipient node. A new run begins in IDLE.</p></div>
      </Card>
    </div>
  </>
}
