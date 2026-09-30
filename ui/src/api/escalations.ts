import { jsonBody, request } from './client'
import type { Escalation, EscalationAcknowledgement, ExecutionState } from './types'

export function getEscalations(): Promise<Escalation[]> {
  return request<Escalation[]>('/api/v1/escalation/all?page=0&size=100&sortBy=createdAt&sortDir=desc')
}

export function getEscalation(escalationId: string): Promise<Escalation> {
  return request<Escalation>(`/api/v1/escalation?escalationId=${encodeURIComponent(escalationId)}`)
}

export function createEscalation(input: {
  escalationName: string
  taskId: string
  flowId: string
}): Promise<Escalation> {
  return request<Escalation>('/api/v1/escalation/create', {
    method: 'POST',
    body: jsonBody(input),
  })
}

export function startEscalation(escalationId: string): Promise<string> {
  return request<string>('/api/v1/escalation/start', {
    method: 'POST',
    body: jsonBody({ escalationId }),
  })
}

export function getExecutionStates(escalationId: string): Promise<ExecutionState[]> {
  return request<ExecutionState[]>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/execution-states`)
}

export function previewEscalationAcknowledgement(token: string): Promise<EscalationAcknowledgement> {
  return request<EscalationAcknowledgement>('/api/v1/escalation/acknowledgement/preview', {
    method: 'POST',
    public: true,
    body: jsonBody({ token }),
  })
}

export function acknowledgeEscalation(token: string): Promise<EscalationAcknowledgement> {
  return request<EscalationAcknowledgement>('/api/v1/escalation/acknowledgement/confirm', {
    method: 'POST',
    public: true,
    body: jsonBody({ token }),
  })
}
