import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFlow, getFlows } from '../../api/flows'
import { Button, Card, EmptyState, ErrorState, Field, LoadingRows, PageHeader } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function FlowsPage() {
  const { teamId = '' } = useParams()
  const [flowName, setFlowName] = useState('')
  const queryClient = useQueryClient()
  const flows = useQuery({ queryKey: ['flows', teamId], queryFn: getFlows })
  const create = useMutation({
    mutationFn: () => createFlow(flowName.trim()),
    onSuccess: async () => {
      setFlowName('')
      await queryClient.invalidateQueries({ queryKey: ['flows', teamId] })
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  return <>
    <PageHeader eyebrow="CONFIGURATION / FLOWS" title="Escalation flows" description="Build a predictable response path from ordered recipients and delay windows." />
    <div className="two-column-layout">
      <Card className="main-list-card">
        <div className="card-heading"><div><span className="eyebrow">TEAM FLOWS</span><h2>Flow templates</h2></div><span className="count-pill">{flows.data?.length ?? '—'}</span></div>
        {flows.isPending ? <LoadingRows count={4} /> : flows.isError ? <ErrorState message={flows.error.message} onRetry={() => void flows.refetch()} /> : flows.data.length === 0 ? <EmptyState title="No response paths yet" description="Create a flow and add a few responder nodes in the order they should run." /> : <div className="flow-list">{flows.data.map(flow => <Link className="flow-row" to={`/app/${teamId}/flows/${flow.id}`} key={flow.id}><span className="flow-symbol">⌁</span><span className="flow-row-copy"><strong>{flow.name}</strong><small>Updated {formatDate(flow.updatedAt ?? flow.createdAt)}</small></span><span className="flow-version">v{flow.version ?? 0}</span><span className="task-id">OPEN&nbsp; →</span></Link>)}</div>}
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">NEW FLOW</span><h2>Start a response path</h2></div><span className="form-number">02</span></div>
        <p className="form-intro">A flow is a reusable set of recipient and delay instructions.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Flow name"><input required maxLength={120} value={flowName} onChange={event => setFlowName(event.target.value)} placeholder="Primary API response" /></Field>
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending}>{create.isPending ? 'Creating flow…' : 'Create flow'} <span>→</span></Button>
        </form>
        <div className="side-callout"><span>FLOW BUILDER</span><p>Next, add nodes with the responder email and the delay before that step runs.</p></div>
      </Card>
    </div>
  </>
}
