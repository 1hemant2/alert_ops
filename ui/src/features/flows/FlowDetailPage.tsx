import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent as ReactKeyboardEvent, type PointerEvent as ReactPointerEvent } from 'react'
import { Link, useParams } from 'react-router'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { createFlowNode, deleteFlowNode, getFlow, getFlowNodes, nodeDelayMinutes, nodeName, nodeResolutionTimeoutMinutes, reorderFlowNode, updateFlowNode, updateFlowTiming } from '../../api/flows'
import type { FlowNode } from '../../api/types'
import { Button, Card, ErrorState, Field, InlineNotice, LoadingRows, PageHeader } from '../../components/Elements'
import { formatDate } from '../../lib/format'

type DropTarget = { type: 'start' } | { type: 'node'; nodeId: string }
type DragPreview = { nodeId: string; title: string; index: number; x: number; y: number }

// Creates a distinguishable duplicate name without exceeding the step limit.
function duplicateStepName(node: FlowNode): string {
  const suffix = ' (copy)'
  return `${nodeName(node).slice(0, 100 - suffix.length)}${suffix}`
}

// Renders the ordered response route and its path-level settings.
export function FlowDetailPage() {
  const { teamId = '', flowId = '' } = useParams()
  const [nodeNameValue, setNodeNameValue] = useState('')
  const [email, setEmail] = useState('')
  const [delay, setDelay] = useState('5')
  const [resolutionTimeoutEnabled, setResolutionTimeoutEnabled] = useState(false)
  const [resolutionTimeouts, setResolutionTimeouts] = useState<Record<string, string>>({})
  const [timingLoadKey, setTimingLoadKey] = useState('')
  const [editingNodeId, setEditingNodeId] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<FlowNode | null>(null)
  const [openMenuId, setOpenMenuId] = useState<string | null>(null)
  const [addStepOpen, setAddStepOpen] = useState(false)
  const [dragPreview, setDragPreview] = useState<DragPreview | null>(null)
  const [dropTarget, setDropTarget] = useState<DropTarget | null>(null)
  const dragRef = useRef<DragPreview | null>(null)
  const activePointerIdRef = useRef<number | null>(null)
  const addStepRef = useRef<HTMLDivElement>(null)
  const pathTimingRef = useRef<HTMLDetailsElement>(null)
  const queryClient = useQueryClient()
  const flow = useQuery({ queryKey: ['flow', teamId, flowId], queryFn: () => getFlow(flowId) })
  const nodes = useQuery({ queryKey: ['flow-nodes', teamId, flowId], queryFn: () => getFlowNodes(flowId), enabled: Boolean(flowId) })
  const editingNode = (nodes.data ?? []).find(node => node.id === editingNodeId)
  const existingResolutionTimeout = editingNode ? nodeResolutionTimeoutMinutes(editingNode) ?? 10 : 10
  const create = useMutation({
    mutationFn: () => createFlowNode({
      flowId,
      nodeName: nodeNameValue.trim(),
      durationInMinutes: Number(delay),
      resolutionTimeoutInMinutes: flow.data?.resolutionTimeoutEnabled ? 10 : null,
      email: email.trim(),
    }),
    onSuccess: async () => {
      resetStepForm()
      await refreshFlow()
    },
  })
  const update = useMutation({
    mutationFn: () => updateFlowNode(editingNodeId!, {
      nodeName: nodeNameValue.trim(),
      durationInMinutes: Number(delay),
      resolutionTimeoutInMinutes: flow.data?.resolutionTimeoutEnabled
        ? existingResolutionTimeout
        : null,
      email: email.trim(),
      version: flow.data?.version ?? 0,
    }),
    onSuccess: async () => {
      resetStepForm()
      await refreshFlow()
    },
    onError: refreshFlow,
  })
  const duplicate = useMutation({
    mutationFn: async (node: FlowNode) => {
      const created = await createFlowNode({
        flowId,
        nodeName: duplicateStepName(node),
        durationInMinutes: nodeDelayMinutes(node),
        resolutionTimeoutInMinutes: flow.data?.resolutionTimeoutEnabled ? nodeResolutionTimeoutMinutes(node) ?? 10 : null,
        email: node.email,
      })
      const currentFlow = await getFlow(flowId)
      await reorderFlowNode({ nodeId: created.id, afterNodeId: node.id, version: currentFlow.version })
      return created
    },
    onSuccess: refreshFlow,
    onError: refreshFlow,
  })
  const remove = useMutation({
    mutationFn: (nodeId: string) => deleteFlowNode(nodeId, flow.data?.version ?? 0),
    onSuccess: async () => {
      setDeleteTarget(null)
      await refreshFlow()
    },
    onError: refreshFlow,
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
  const timing = useMutation({
    mutationFn: () => updateFlowTiming(flowId, {
      resolutionTimeoutEnabled,
      nodeTimings: resolutionTimeoutEnabled
        ? (nodes.data ?? []).map(node => ({
          nodeId: node.id,
          resolutionTimeoutInMinutes: Number(resolutionTimeouts[node.id] ?? 0),
        }))
        : [],
      version: flow.data?.version ?? 0,
    }),
    onSuccess: refreshFlow,
    onError: refreshFlow,
  })
  const stepWritePending = create.isPending || update.isPending || duplicate.isPending || remove.isPending || reorder.isPending || timing.isPending

  // Opens the timeout settings and brings them into view from the page header.
  function revealPathSettings() {
    const settings = pathTimingRef.current
    if (!settings) return
    settings.open = true
    requestAnimationFrame(() => settings.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }

  function refreshFlow() {
    return Promise.all([
      queryClient.invalidateQueries({ queryKey: ['flow', teamId, flowId] }),
      queryClient.invalidateQueries({ queryKey: ['flow-nodes', teamId, flowId] }),
    ])
  }

  // Resets the response-step editor without changing saved path settings.
  function resetStepForm() {
    setNodeNameValue('')
    setEmail('')
    setDelay('5')
    setEditingNodeId(null)
    setAddStepOpen(false)
    create.reset()
    update.reset()
  }

  useEffect(() => {
    if (!flow.data || !nodes.data) return
    const key = `${flow.data.id}:${flow.data.version}:${nodes.data.map(node => node.id).join(',')}`
    if (key === timingLoadKey) return
    setResolutionTimeoutEnabled(Boolean(flow.data.resolutionTimeoutEnabled))
    setResolutionTimeouts(Object.fromEntries(nodes.data.map(node => [
      node.id,
      String(nodeResolutionTimeoutMinutes(node) ?? 10),
    ])))
    setTimingLoadKey(key)
  }, [flow.data?.id, flow.data?.version, flow.data?.resolutionTimeoutEnabled, nodes.data, timingLoadKey])

  useEffect(() => {
    if (addStepOpen) addStepRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' })
  }, [addStepOpen])

  useEffect(() => {
    if (!openMenuId) return
    const closeMenu = (event: PointerEvent) => {
      if (!(event.target instanceof Element) || !event.target.closest('[data-step-actions]')) setOpenMenuId(null)
    }
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape') setOpenMenuId(null)
    }
    document.addEventListener('pointerdown', closeMenu)
    document.addEventListener('keydown', closeOnEscape)
    return () => {
      document.removeEventListener('pointerdown', closeMenu)
      document.removeEventListener('keydown', closeOnEscape)
    }
  }, [openMenuId])

  useEffect(() => {
    if (!deleteTarget) return
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && !remove.isPending) setDeleteTarget(null)
    }
    document.addEventListener('keydown', closeOnEscape)
    return () => document.removeEventListener('keydown', closeOnEscape)
  }, [deleteTarget, remove.isPending])

  function moveNodeAfter(nodeId: string, afterNodeId: string | null) {
    const current = nodes.data
    if (!current || reorder.isPending) return
    const movingNode = current.find(node => node.id === nodeId)
    if (!movingNode) return

    const remaining = current.filter(node => node.id !== nodeId)
    const afterIndex = afterNodeId === null ? -1 : remaining.findIndex(node => node.id === afterNodeId)
    if (afterNodeId !== null && afterIndex < 0) return
    const insertAt = afterNodeId === null ? 0 : afterIndex + 1

    const next = [...remaining]
    next.splice(insertAt, 0, movingNode)
    if (next.every((node, index) => node.id === current[index]?.id)) return
    reorder.mutate({ nodeId, afterNodeId })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    if (editingNodeId) update.mutate()
    else create.mutate()
  }

  // Opens the response-step editor while preserving its saved timeout setting.
  function startEditing(node: FlowNode) {
    setOpenMenuId(null)
    create.reset()
    update.reset()
    setEditingNodeId(node.id)
    setNodeNameValue(nodeName(node))
    setEmail(node.email)
    setDelay(String(nodeDelayMinutes(node)))
    setAddStepOpen(true)
  }

  function closeStepForm() {
    if (!stepWritePending) resetStepForm()
  }

  function startAddingStep() {
    if (stepWritePending) return
    resetStepForm()
    setAddStepOpen(true)
  }

  function readDropTarget(x: number, y: number): DropTarget | null {
    const element = document.elementFromPoint(x, y)
    if (!(element instanceof HTMLElement)) return null
    if (element.closest('[data-path-start]')) return { type: 'start' }
    const card = element.closest<HTMLElement>('[data-path-step-id]')
    const nodeId = card?.dataset.pathStepId
    return nodeId ? { type: 'node', nodeId } : null
  }

  function beginDrag(event: ReactPointerEvent<HTMLButtonElement>, node: FlowNode, index: number) {
    if (!event.isPrimary || event.button !== 0 || stepWritePending) return
    event.preventDefault()
    event.currentTarget.setPointerCapture(event.pointerId)
    activePointerIdRef.current = event.pointerId
    const preview = { nodeId: node.id, title: nodeName(node), index, x: event.clientX, y: event.clientY }
    dragRef.current = preview
    setDragPreview(preview)
    setDropTarget(null)
  }

  function continueDrag(event: ReactPointerEvent<HTMLButtonElement>) {
    if (activePointerIdRef.current !== event.pointerId || !dragRef.current) return
    const preview = { ...dragRef.current, x: event.clientX, y: event.clientY }
    dragRef.current = preview
    setDragPreview(preview)
    const target = readDropTarget(event.clientX, event.clientY)
    setDropTarget(target?.type === 'node' && target.nodeId === preview.nodeId ? null : target)
  }

  function finishDrag(event: ReactPointerEvent<HTMLButtonElement>, cancelled = false) {
    if (activePointerIdRef.current !== event.pointerId) return
    const preview = dragRef.current
    const target = cancelled ? null : readDropTarget(event.clientX, event.clientY)
    activePointerIdRef.current = null
    dragRef.current = null
    setDragPreview(null)
    setDropTarget(null)
    if (!preview || !target) return
    if (target.type === 'start') moveNodeAfter(preview.nodeId, null)
    else if (target.nodeId !== preview.nodeId) moveNodeAfter(preview.nodeId, target.nodeId)
  }

  function handleGripKeyDown(event: ReactKeyboardEvent<HTMLButtonElement>, node: FlowNode, index: number) {
    if (stepWritePending || !nodes.data) return
    const moveEarlier = event.key === 'ArrowLeft' || event.key === 'ArrowUp'
    const moveLater = event.key === 'ArrowRight' || event.key === 'ArrowDown'
    if (moveEarlier && index > 0) {
      event.preventDefault()
      moveNodeAfter(node.id, index < 2 ? null : nodes.data[index - 2].id)
    } else if (moveLater && index < nodes.data.length - 1) {
      event.preventDefault()
      moveNodeAfter(node.id, nodes.data[index + 1].id)
    }
  }

  if (flow.isPending) return <LoadingRows count={4} />
  if (flow.isError) return <ErrorState message={flow.error.message} onRetry={() => void flow.refetch()} />

  const activeDropLabel = dropTarget?.type === 'start'
    ? 'Drop here to make this the first step'
    : dropTarget?.type === 'node'
      ? `Drop after step ${String((nodes.data ?? []).findIndex(node => node.id === dropTarget.nodeId) + 1).padStart(2, '0')}`
      : 'Drop on a card to place after it, or on Start to make it first'

  return <div className="flow-detail-page">
    <div className="back-link-row"><Link to={`/app/${teamId}/flows`}>Back to escalation paths</Link><span> / </span><span>{flow.data.name}</span></div>
    <PageHeader eyebrow="ESCALATION PATH / BUILDER" title={flow.data.name} description="Choose who gets contacted, then arrange each handoff in order." action={<div className="path-page-actions">
      <span className="version-pill">VERSION&nbsp; {flow.data.version ?? 0}</span>
      <Button className="path-settings-button" variant="secondary" aria-label="Open resolution timeout settings" title="Open resolution timeout settings" onClick={revealPathSettings}>
        <span className="path-settings-icon" aria-hidden="true"><svg viewBox="0 0 20 20"><circle cx="10" cy="10" r="6.5" /><path d="M10 6v4l2.5 2" /></svg></span>
        Resolution timeout
        <span className="path-settings-status">{resolutionTimeoutEnabled ? 'ON' : 'OFF'}</span>
      </Button>
    </div>} />
    <InlineNotice tone="neutral">Follow the arrows through the path. Scroll horizontally to see more steps. Drag the grip to reorder; on a keyboard, focus a grip and use the arrow keys.</InlineNotice>
    {addStepOpen && <div id="add-path-step" className="path-add-panel" ref={addStepRef}><Card className="path-add-card">
      <div className="card-heading"><div><span className="eyebrow">BUILD THIS PATH</span><h2>{editingNodeId ? 'Edit response step' : 'Add a response step'}</h2></div><Button variant="quiet" disabled={stepWritePending} onClick={closeStepForm}>Close&nbsp; ×</Button></div>
      <p className="form-intro">Choose an existing team member, name their step, and set the wait time before contact.</p>
      <form onSubmit={submit} className="path-step-form">
        <Field label="Step name"><input required maxLength={100} value={nodeNameValue} onChange={event => setNodeNameValue(event.target.value)} placeholder="Notify primary on-call" /></Field>
        <Field label="Team member email"><input required type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="oncall@company.com" /></Field>
        <Field label="Wait before contact" hint="The first step starts after this wait time too."><div className="input-with-suffix"><input required type="number" min="0" max="10080" value={delay} onChange={event => setDelay(event.target.value)} /><span>minutes</span></div></Field>
        <Button disabled={stepWritePending || !flow.data}>{create.isPending ? 'Adding step…' : update.isPending ? 'Saving changes…' : editingNodeId ? 'Save changes' : 'Add to path'}</Button>
      </form>
      {(create.error || update.error) && <div className="form-error path-form-error" role="alert">{(create.error ?? update.error)?.message}</div>}
    </Card></div>}
    <div className="path-workspace-layout">
      <Card className="node-timeline-card path-builder-card">
        <div className="card-heading path-route-heading"><div><span className="eyebrow">YOUR RESPONSE ROUTE</span><h2>Who gets contacted</h2><p>Every handoff is shown in sequence.</p></div><div className="path-route-heading-tools"><span className="count-pill">{nodes.data?.length ?? '—'} STEPS</span><Button className="path-add-step-button" variant="secondary" disabled={stepWritePending} onClick={() => addStepOpen ? closeStepForm() : startAddingStep()}><span>{addStepOpen ? '×' : '＋'}</span>{addStepOpen ? 'Close form' : 'Add response step'}</Button></div></div>
        {nodes.isPending ? <LoadingRows count={3} /> : nodes.isError ? <ErrorState message={nodes.error.message} onRetry={() => void nodes.refetch()} /> : <div className="path-canvas">
          <div className={`path-origin ${dropTarget?.type === 'start' ? 'is-drop-target' : ''}`} data-path-start>
            <span className="path-origin-mark">S</span>
            <div><span className="path-kicker">START OF ROUTE</span><strong>Escalation starts</strong><small>A task needs a response</small></div>
            <span className="path-origin-tag">DROP HERE TO MAKE FIRST</span>
          </div>
          {nodes.data.length === 0
            ? <div className="path-empty-grid">
              <span className="path-step-number">01</span>
              <div><span className="step-order-label">FIRST STEP</span><strong>Your response route is empty</strong><small>Add the first teammate to define who responds.</small><button type="button" className="path-inline-action" onClick={startAddingStep}>Add the first step</button></div>
            </div>
            : <div id="response-step-rail" className="path-step-scroll-region" tabIndex={0} aria-label="Scrollable escalation steps">
              <div className="path-step-grid" role="list" aria-label="Ordered escalation steps">
                <div className="path-step-row" style={{ gridTemplateColumns: `repeat(${nodes.data.length}, minmax(0, var(--path-card-size)))` }}>
                  {nodes.data.map((node, index) => {
                    const isDragSource = dragPreview?.nodeId === node.id
                    const isDropTarget = dropTarget?.type === 'node' && dropTarget.nodeId === node.id
                    const isSequenceEnd = index === nodes.data.length - 1
                    return <article
                      key={node.id}
                      className={`path-step-card ${isDragSource ? 'is-dragging' : ''} ${isDropTarget ? 'is-drop-target' : ''} ${openMenuId === node.id ? 'has-open-menu' : ''}`}
                      data-path-step-id={node.id}
                      data-drop-hint={isDropTarget ? `DROP AFTER STEP ${String(index + 1).padStart(2, '0')}` : undefined}
                      role="listitem"
                    >
                      <div className="path-card-header">
                        <span className="path-step-number">{String(index + 1).padStart(2, '0')}</span>
                        <div className="path-card-tools">
                        <button
                          type="button"
                          className="step-drag-handle"
                          aria-label={`Reorder ${nodeName(node)}, step ${index + 1} of ${nodes.data.length}. Drag to reorder; use arrow keys to move earlier or later.`}
                          title="Drag to reorder · keyboard: use arrow keys"
                          disabled={stepWritePending}
                          onPointerDown={event => beginDrag(event, node, index)}
                          onPointerMove={continueDrag}
                          onPointerUp={event => finishDrag(event)}
                          onPointerCancel={event => finishDrag(event, true)}
                          onLostPointerCapture={event => finishDrag(event, true)}
                          onKeyDown={event => handleGripKeyDown(event, node, index)}
                        >
                          <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="7" cy="5" r="1.35"/><circle cx="13" cy="5" r="1.35"/><circle cx="7" cy="10" r="1.35"/><circle cx="13" cy="10" r="1.35"/><circle cx="7" cy="15" r="1.35"/><circle cx="13" cy="15" r="1.35"/></svg>
                        </button>
                        <div className="path-step-actions" data-step-actions>
                          <button type="button" className="path-step-menu-trigger" aria-label={`${openMenuId === node.id ? 'Close' : 'Open'} actions for ${nodeName(node)}`} title={`${openMenuId === node.id ? 'Close' : 'Open'} step actions`} aria-expanded={openMenuId === node.id} disabled={stepWritePending} onClick={() => setOpenMenuId(current => current === node.id ? null : node.id)}>
                            <svg viewBox="0 0 20 20" aria-hidden="true"><circle cx="10" cy="4" r="1.4"/><circle cx="10" cy="10" r="1.4"/><circle cx="10" cy="16" r="1.4"/></svg>
                          </button>
                          {openMenuId === node.id && <div className="path-step-menu" role="menu">
                            <button type="button" role="menuitem" title="Create a copy of this step" onClick={() => { setOpenMenuId(null); duplicate.mutate(node) }}><span className="menu-action-icon menu-duplicate-icon" aria-hidden="true"><svg viewBox="0 0 20 20"><rect x="6.5" y="6.5" width="9" height="9" rx="1.5"/><path d="M13.5 6.5V5A1.5 1.5 0 0 0 12 3.5H5A1.5 1.5 0 0 0 3.5 5v7A1.5 1.5 0 0 0 5 13.5h1.5"/></svg></span>Duplicate step</button>
                            <button type="button" role="menuitem" title="Change this step" onClick={() => startEditing(node)}><span className="menu-action-icon menu-edit-icon" aria-hidden="true"><svg viewBox="0 0 20 20"><path d="m4 16 .8-3.6L13.9 3.3a1.8 1.8 0 0 1 2.6 2.6L7.4 15.1 4 16Z"/><path d="m12.8 4.4 2.8 2.8"/></svg></span>Edit step</button>
                            <button type="button" role="menuitem" className="is-danger" title="Remove this step" onClick={() => { setOpenMenuId(null); remove.reset(); setDeleteTarget(node) }}><span className="menu-action-icon menu-delete-icon" aria-hidden="true"><svg viewBox="0 0 20 20"><path d="M4 6h12M8 6V4h4v2M6 6l.8 10h6.4L14 6M8.5 9v4M11.5 9v4"/></svg></span>Delete step</button>
                          </div>}
                        </div>
                        </div>
                      </div>
                      <span className="step-order-label">{index === 0 ? 'FIRST STEP' : `AFTER STEP ${String(index).padStart(2, '0')}`}</span>
                      <h3 title={nodeName(node)}>{nodeName(node)}</h3>
                      <div className="path-delay-block"><span>WAIT BEFORE CONTACT</span><strong>{nodeDelayMinutes(node)} <small>min</small></strong></div>
                      <div className="path-card-recipient"><span className="path-recipient-mark">{node.email.charAt(0).toUpperCase()}</span><div><small>CONTACT</small><strong title={node.email}>{node.email}</strong></div></div>
                      {!isSequenceEnd && <span className="path-step-connector" aria-hidden="true"><svg viewBox="0 0 40 20"><path d="M2 10h31M27 4l6 6-6 6" /></svg></span>}
                    </article>
                  })}
                </div>
              </div>
            </div>}
        </div>}
        {dragPreview && <div className="path-drag-preview" style={{ left: dragPreview.x, top: dragPreview.y }} aria-live="polite">
          <span>Moving step {String(dragPreview.index + 1).padStart(2, '0')}</span><strong>{dragPreview.title}</strong><small>{activeDropLabel}</small>
        </div>}
        {reorder.error && <div className="form-error reorder-error" role="alert">{reorder.error.message} The latest path has been loaded; try the reorder again.</div>}
        {duplicate.error && <div className="form-error reorder-error" role="alert">{duplicate.error.message} The latest path has been loaded; try duplicating the step again.</div>}
        <div className="timeline-caption"><span className="caption-dot" />Wait times control when each contact step becomes eligible to run. Order is saved by the server. Scroll horizontally to view additional steps.</div>
      </Card>
    </div>
    <details ref={pathTimingRef} id="path-settings" className="path-timing-disclosure">
      <summary className="path-timing-summary">
        <span><span className="eyebrow">PATH SETTINGS</span><strong>Resolution timeout</strong><small>Optional time limit after a recipient acknowledges a step.</small></span>
        <span className="path-timing-summary-meta"><span className="count-pill">{resolutionTimeoutEnabled ? 'ENABLED' : 'DISABLED'}</span><span className="path-disclosure-icon" aria-hidden="true">＋</span></span>
      </summary>
      <Card className="path-timing-card">
        <label className="checkbox-field"><input type="checkbox" checked={resolutionTimeoutEnabled} disabled={stepWritePending} onChange={event => setResolutionTimeoutEnabled(event.target.checked)} /><span>Enable resolution timeout for this path</span></label>
        {resolutionTimeoutEnabled && <div className="path-timing-grid">
          {(nodes.data ?? []).map((node, index) => <Field key={node.id} label={`Step ${String(index + 1).padStart(2, '0')} · ${nodeName(node)}`} hint="Time allowed after acknowledgement."><div className="input-with-suffix"><input required type="number" min="1" max="10080" value={resolutionTimeouts[node.id] ?? ''} disabled={stepWritePending} onChange={event => setResolutionTimeouts(current => ({ ...current, [node.id]: event.target.value }))} /><span>minutes</span></div></Field>)}
        </div>}
        <div className="path-timing-actions"><Button variant="secondary" disabled={stepWritePending || !flow.data || !nodes.data} onClick={() => timing.mutate()}>{timing.isPending ? 'Saving timing…' : 'Save timing settings'}</Button></div>
        {timing.error && <div className="form-error path-form-error" role="alert">{timing.error.message} The latest path has been loaded; review the timing settings before trying again.</div>}
      </Card>
    </details>
    {deleteTarget && <div className="path-confirm-backdrop" onMouseDown={event => { if (event.target === event.currentTarget && !remove.isPending) setDeleteTarget(null) }}>
      <section className="path-confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="delete-step-title">
        <span className="path-confirm-icon" aria-hidden="true">×</span>
        <h2 id="delete-step-title">Delete this response step?</h2>
        <p><strong>{nodeName(deleteTarget)}</strong> will be removed from future escalations. Runs already started keep their saved contact and wait settings.</p>
        {remove.error && <div className="form-error" role="alert">{remove.error.message} The latest path has been loaded; review it before trying again.</div>}
        <div className="path-confirm-actions"><Button variant="secondary" disabled={remove.isPending} onClick={() => setDeleteTarget(null)}>Keep step</Button><Button variant="danger" disabled={stepWritePending} onClick={() => remove.mutate(deleteTarget.id)}>{remove.isPending ? 'Deleting…' : 'Delete step'}</Button></div>
      </section>
    </div>}
    <div className="last-updated">LAST UPDATED&nbsp; {formatDate(flow.data.updatedAt ?? flow.data.createdAt)}</div>
  </div>
}
