import { jsonBody, request } from './client'
import type { Escalation, EscalationAcknowledgement, EscalationHistoryPage, EscalationManualAction, EscalationManualActionRequest, EscalationResolution, ExecutionState } from './types'

// Loads the first page of team escalations for existing workspace screens.
export function getEscalations(): Promise<Escalation[]> {
  return getEscalationsPage(0, 100)
}

// Loads one page of team escalations for frontend history aggregation.
function getEscalationsPage(page: number, size: number): Promise<Escalation[]> {
  return request<Escalation[]>(`/api/v1/escalation/all?page=${page}&size=${size}&sortBy=createdAt&sortDir=desc`)
}

// Loads every available escalation page for the team audit view.
export async function getAllEscalations(): Promise<Escalation[]> {
  const escalations: Escalation[] = []
  let page = 0
  while (true) {
    const batch = await getEscalationsPage(page, 100)
    escalations.push(...batch)
    if (batch.length < 100) return escalations
    page += 1
  }
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

// Loads the saved lifecycle activity for one escalation page.
export function getEscalationHistory(escalationId: string, page = 0, size = 20): Promise<EscalationHistoryPage> {
  return request<EscalationHistoryPage>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/history?page=${page}&size=${size}`)
}

// Loads every saved history page for one escalation for the team audit view.
export async function getAllEscalationHistory(escalationId: string): Promise<EscalationHistoryPage['events']> {
  const events: EscalationHistoryPage['events'] = []
  let page = 0
  while (true) {
    const history = await getEscalationHistory(escalationId, page, 100)
    events.push(...history.events)
    if (history.last || history.totalPages <= page + 1) return events
    page += 1
  }
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

export function resolveEscalation(escalationId: string): Promise<EscalationResolution> {
  return request<EscalationResolution>(`/api/v1/escalation/${encodeURIComponent(escalationId)}/resolve`, {
    method: 'POST',
  })
}

export function resolveEscalationAsRecipient(token: string): Promise<EscalationResolution> {
  return request<EscalationResolution>('/api/v1/escalation/resolution/confirm', {
    method: 'POST',
    public: true,
    body: jsonBody({ token }),
  })
}
