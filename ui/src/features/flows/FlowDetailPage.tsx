import { useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFlowNode, getFlow, getFlowNodes, nodeDelayMinutes, nodeName, reorderFlowNode } from '../../api/flows'
import { Button, Card, EmptyState, ErrorState, Field, InlineNotice, LoadingRows, PageHeader } from '../../components/Elements'
import { formatDate } from '../../lib/format'

export function FlowDetailPage() {
  const { teamId = '', flowId = '' } = useParams()
  const [nodeNameValue, setNodeNameValue] = useState('')
  const [email, setEmail] = useState('')
  const [delay, setDelay] = useState('5')
  const queryClient = useQueryClient()
  const flow = useQuery({ queryKey: ['flow', teamId, flowId], queryFn: () => getFlow(flowId) })
  const nodes = useQuery({ queryKey: ['flow-nodes', teamId, flowId], queryFn: () => getFlowNodes(flowId), enabled: Boolean(flowId) })
  const create = useMutation({
    mutationFn: () => createFlowNode({
      flowId,
      nodeName: nodeNameValue.trim(),
      durationInMinutes: Number(delay),
      email: email.trim(),
    }),
    onSuccess: async () => {
      setNodeNameValue('')
      setEmail('')
      await queryClient.invalidateQueries({ queryKey: ['flow-nodes', teamId, flowId] })
    },
  })
  const reorder = useMutation({
    mutationFn: (input: { nodeId: string; afterNodeId: string | null }) => reorderFlowNode({
      ...input,
      version: flow.data?.version ?? 0,
    }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['flow', teamId, flowId] }),
        queryClient.invalidateQueries({ queryKey: ['flow-nodes', teamId, flowId] }),
      ])
    },
    onError: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['flow', teamId, flowId] }),
        queryClient.invalidateQueries({ queryKey: ['flow-nodes', teamId, flowId] }),
      ])
    },
  })

  function submit(event: FormEvent) {
    event.preventDefault()
    create.mutate()
  }

  if (flow.isPending) return <LoadingRows count={4} />
  if (flow.isError) return <ErrorState message={flow.error.message} onRetry={() => void flow.refetch()} />

  return <>
    <div className="back-link-row"><Link to={`/app/${teamId}/flows`}>← Flow templates</Link><span> / </span><span>{flow.data.name}</span></div>
    <PageHeader eyebrow="FLOW BUILDER / ORDERED NODES" title={flow.data.name} description="Each node is persisted and scheduled in sequence when an escalation starts." action={<span className="version-pill">FLOW VERSION&nbsp; {flow.data.version ?? 0}</span>} />
    <InlineNotice tone="neutral">Delay values are scheduled by RabbitMQ. Move controls use the flow version so stale edits can be rejected safely.</InlineNotice>
    <div className="two-column-layout flow-detail-layout">
      <Card className="node-timeline-card">
        <div className="card-heading"><div><span className="eyebrow">EXECUTION ORDER</span><h2>Response nodes</h2></div><span className="count-pill">{nodes.data?.length ?? '—'} STEPS</span></div>
        {nodes.isPending ? <LoadingRows count={3} /> : nodes.isError ? <ErrorState message={nodes.error.message} onRetry={() => void nodes.refetch()} /> : nodes.data.length === 0 ? <EmptyState title="This flow has no nodes" description="Add at least one node before using the flow in an escalation." /> : <div className="node-timeline">{nodes.data.map((node, index) => <article className="timeline-item" key={node.id}><div className={`timeline-marker ${index === 0 ? 'timeline-marker-first' : ''}`}>{String(index + 1).padStart(2, '0')}</div><div className="timeline-line" /><div className="timeline-content"><div className="timeline-title"><div><strong>{nodeName(node)}</strong><small>{node.email}</small></div><span className="delay-chip">{index === 0 ? 'FIRST STEP' : `+ ${nodeDelayMinutes(node)} MIN`}</span></div><div className="timeline-meta"><span>DELAY BEFORE THIS STEP</span><strong>{nodeDelayMinutes(node)} min</strong></div><div className="node-order-actions"><button type="button" aria-label={`Move ${nodeName(node)} up`} title="Move up" disabled={index === 0 || reorder.isPending} onClick={() => reorder.mutate({ nodeId: node.id, afterNodeId: index < 2 ? null : nodes.data[index - 2].id })}>↑</button><button type="button" aria-label={`Move ${nodeName(node)} down`} title="Move down" disabled={index === nodes.data.length - 1 || reorder.isPending} onClick={() => reorder.mutate({ nodeId: node.id, afterNodeId: nodes.data[index + 1].id })}>↓</button></div></div></article>)}</div>}
        {reorder.error && <div className="form-error reorder-error" role="alert">{reorder.error.message} Refresh the flow and try again.</div>}
        <div className="timeline-caption"><span>⏱</span> The configured delay controls when this node becomes eligible for execution.</div>
      </Card>
      <Card className="side-form-card">
        <div className="card-heading"><div><span className="eyebrow">ADD TO FLOW</span><h2>New response node</h2></div><span className="form-number">+</span></div>
        <p className="form-intro">The recipient must already have an account and belong to this team.</p>
        <form onSubmit={submit} className="form-stack">
          <Field label="Step name"><input required maxLength={100} value={nodeNameValue} onChange={event => setNodeNameValue(event.target.value)} placeholder="Notify primary on-call" /></Field>
          <Field label="Responder email"><input required type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="oncall@company.com" /></Field>
          <Field label="Delay before this step" hint="The first step also uses this delay after the escalation starts."><div className="input-with-suffix"><input required type="number" min="0" max="10080" value={delay} onChange={event => setDelay(event.target.value)} /><span>minutes</span></div></Field>
          {create.error && <div className="form-error" role="alert">{create.error.message}</div>}
          <Button disabled={create.isPending || !flow.data}>{create.isPending ? 'Adding node…' : 'Add node'} <span>→</span></Button>
        </form>
        <div className="side-callout"><span>VERSIONED FLOW</span><p>This flow is currently at version {flow.data.version ?? 0}. Execution snapshots its node order when started.</p></div>
      </Card>
    </div>
    <div className="last-updated">LAST UPDATED&nbsp; {formatDate(flow.data.updatedAt ?? flow.data.createdAt)}</div>
  </>
}
