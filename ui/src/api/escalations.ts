import { jsonBody, request } from './client'
import type { Escalation, EscalationAcknowledgement, EscalationManualAction, EscalationManualActionRequest, ExecutionState } from './types'

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

export function scheduleEscalation(escalationId: string, schedule: {
  scheduleDate: string
  scheduleTime: string
  timezone: string
}): Promise<Escalation> {
  return request<Escalation>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/schedule`, {
    method: 'POST',
    body: jsonBody(schedule),
  })
}

export function rescheduleEscalation(escalationId: string, schedule: {
  scheduleDate: string
  scheduleTime: string
  timezone: string
}): Promise<Escalation> {
  return request<Escalation>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/reschedule`, {
    method: 'POST',
    body: jsonBody(schedule),
  })
}

export function cancelScheduledEscalation(escalationId: string): Promise<Escalation> {
  return request<Escalation>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/cancel`, {
    method: 'POST',
    body: jsonBody({}),
  })
}

export function startEscalation(escalationId: string): Promise<string> {
  return request<string>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/start`, {
    method: 'POST',
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

export function previewEscalateNow(
  escalationId: string,
  input: EscalationManualActionRequest,
): Promise<EscalationManualAction> {
  return request<EscalationManualAction>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/escalate-now/preview`, {
    method: 'POST',
    body: jsonBody(input),
  })
}

export function escalateNow(
  escalationId: string,
  input: EscalationManualActionRequest,
): Promise<EscalationManualAction> {
  return request<EscalationManualAction>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/escalate-now`, {
    method: 'POST',
    body: jsonBody(input),
  })
}

export function previewRecipientEscalateNow(token: string): Promise<EscalationManualAction> {
  return request<EscalationManualAction>('/api/v1/escalation/escalate-now/preview', {
    method: 'POST',
    public: true,
    body: jsonBody({ token }),
  })
}

export function confirmRecipientEscalateNow(token: string): Promise<EscalationManualAction> {
  return request<EscalationManualAction>('/api/v1/escalation/escalate-now/confirm', {
    method: 'POST',
    public: true,
    body: jsonBody({ token }),
  })
}
