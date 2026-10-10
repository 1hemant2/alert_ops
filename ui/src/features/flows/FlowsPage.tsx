import { useState, type FormEvent } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFlow, getFlows } from '../../api/flows'
import { Button, Card, EmptyState, ErrorState, Field, LoadingRows, PageHeader } from '../../components/Elements'
import { formatDate } from '../../lib/format'

// Shows saved response paths in a scrollable list with path creation controls.
export function FlowsPage() {
  const { teamId = '' } = useParams()
  const navigate = useNavigate()
  const [flowName, setFlowName] = useState('')
  const queryClient = useQueryClient()
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const create = useMutation({
    mutationFn: () => createFlow(flowName.trim()),
    onSuccess: async (flow) => {
      setFlowName('')
      await queryClient.invalidateQueries({ queryKey: ['flows', teamId] })
      navigate(`/app/${teamId}/flows/${flow.id}`)
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return <>
    <PageHeader eyebrow="POLICIES / ESCALATION PATHS" title="Escalation paths" description="Choose who gets notified and when. Every path keeps your response steps in a clear order." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">YOUR TEAM</span><h2>Response paths</h2></div><span className="count-pill">{flows.data?.length ?? '—'}</span></div>
        {flows.isPending ? <LoadingRows count={4} /> : flows.isError ? <ErrorState message={flows.error.message} onRetry={() => void flows.refetch()} /> : flows.data.length === 0 ? <EmptyState title="No escalation paths yet" description="Create a path, then add recipients and decide how long to wait before each step." /> : <div className="flow-list" role="region" aria-label="Escalation paths" tabIndex={0}>{flows.data.map(flow => <Link className="flow-row path-list-row" to={`/app/${teamId}/flows/${flow.id}`} key={flow.id}><span className="flow-symbol path-list-icon"><span /><span /><span /></span><span className="flow-row-copy"><strong>{flow.name}</strong><small>Updated {formatDate(flow.updatedAt ?? flow.createdAt)}</small></span><span className="flow-version">v{flow.version ?? 0}</span><span className="task-id">OPEN&nbsp; →</span></Link>)}</div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW PATH</span><h2>Create an escalation path</h2></div><span className="form-number">01</span></div>
        <p className="form-intro">Give this path a name. Next, add the people to contact and the wait time between steps.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Path name"><input required maxLength={120} value={flowName} onChange={event => setFlowName(event.target.value)} placeholder="Primary API response" /></Field>
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending}>{create.isPending ? 'Creating path…' : 'Create path and add steps'} <span>→</span></Button>
        </form>
        <div className="path-mini-preview" aria-label="Path preview: escalation starts, then response steps run in order">
          <div className="path-preview-heading"><span>PATH PREVIEW</span><small>CONNECTED SEQUENCE</small></div>
          <div className="path-mini-node path-mini-start"><span className="path-mini-dot">↗</span><span><strong>Escalation starts</strong><small>Task needs a response</small></span></div>
          <div className="path-mini-arrow" aria-hidden="true">↓</div>
          <div className="path-mini-node"><span className="path-mini-number">01</span><span><strong>First responder</strong><small>Choose a teammate</small></span></div>
          <div className="path-mini-arrow" aria-hidden="true">↓</div>
          <div className="path-mini-node path-mini-placeholder"><span className="path-mini-number">02</span><span><strong>Next step</strong><small>Set a wait time</small></span><span className="path-mini-plus">＋</span></div>
        </div>
      </Card>
    </div>
  </>
}
