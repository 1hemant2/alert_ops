import { jsonBody, request } from './client'
import type { Flow, FlowNode } from './types'

export function getFlows(): Promise<Flow[]> {
  return request<Flow[]>('/api/v1/flow/all?page=0&size=100&sortBy=createdAt&sortDir=desc')
}

export function getFlow(flowId: string): Promise<Flow> {
  return request<Flow>(`/api/v1/flow?flowId=${encodeURIComponent(flowId)}`)
}

export function createFlow(flowName: string): Promise<Flow> {
  return request<Flow>('/api/v1/flow', { method: 'POST', body: jsonBody({ flowName }) })
}

export function getFlowNodes(flowId: string): Promise<FlowNode[]> {
  return request<FlowNode[]>(`/api/v1/flow/node/all?flowId=${encodeURIComponent(flowId)}`)
}

export function createFlowNode(input: {
  flowId: string
  nodeName: string
  durationInMinutes: number
  email: string
}): Promise<FlowNode> {
  return request<FlowNode>('/api/v1/flow/node', { method: 'POST', body: jsonBody(input) })
}

export function reorderFlowNode(input: {
  nodeId: string
  afterNodeId: string | null
  version: number
}): Promise<FlowNode> {
  return request<FlowNode>('/api/v1/flow/node/reorder', {
    method: 'PATCH',
    body: jsonBody(input),
  })
}

export function updateFlowNode(nodeId: string, input: {
  nodeName: string
  durationInMinutes: number
  email: string
  version: number
}): Promise<FlowNode> {
  return request<FlowNode>(`/api/v1/flow/node/${encodeURIComponent(nodeId)}`, {
    method: 'PUT',
    body: jsonBody(input),
  })
}

export function deleteFlowNode(nodeId: string, version: number): Promise<void> {
  return request<void>(`/api/v1/flow/node/${encodeURIComponent(nodeId)}?version=${encodeURIComponent(version)}`, {
    method: 'DELETE',
  })
}

export function nodeName(node: FlowNode): string {
  return node.name ?? node.nodeName ?? 'Escalation step'
}

export function nodeDelayMinutes(node: FlowNode): number {
  if (typeof node.durationInMinutes === 'number') return node.durationInMinutes
  if (typeof node.duration === 'number') return Math.round(node.duration / 60)
  const duration = node.duration ?? ''
  const match = String(duration).match(/^PT(?:(\d+(?:\.\d+)?)H)?(?:(\d+(?:\.\d+)?)M)?(?:(\d+(?:\.\d+)?)S)?$/)
  if (!match) return 0
  return Math.round(Number(match[1] ?? 0) * 60 + Number(match[2] ?? 0) + Number(match[3] ?? 0) / 60)
}
