export interface LoginResponse {
  authenticated: boolean
  data: { 'jwt-token': string }
}

export interface RegisterRequest {
  name: string
  email: string
  password: string
}

export interface User {
  id: string
  name: string
  email: string
  createdAt?: string
  emailVerified?: boolean
}

export interface Team {
  id: string
  name: string
  teamId?: string
  teamName?: string
  role?: string
}

export interface SelectedTeam {
  id: string
  name: string
  role?: string
}

export interface TeamMember {
  memberId: string
  userId: string
  name: string
  email: string
  role: string
}

export interface TeamInviteRequest {
  email: string
  role: 'ADMIN' | 'USER'
  expiresInHours: number
}

export interface TeamInviteResponse {
  email: string
  role: string
  teamName: string
  expiresAt: string
}

export interface TeamInvitePreview extends TeamInviteResponse {}

export interface TeamInviteAcceptance {
  teamId: string
  teamName: string
  role: string
}

export interface Task {
  id: string
  name: string
  description: string
  source: string
  priority?: string | null
  category?: string | null
  referenceUrl?: string | null
  createdAt?: string
}

export interface WebhookConfiguration {
  id: string
  defaultFlowId: string
  name: string
  enabled: boolean
  createdAt: string
  updatedAt: string
  lastTriggeredAt?: string | null
  secret?: string | null
}

export interface WebhookEvent {
  id: string
  webhookId: string
  eventId: string
  receivedAt: string
  payload: Record<string, unknown>
  taskId: string
  escalationId: string
}

export interface Flow {
  id: string
  name: string
  teamId: string
  version: number
  createdAt?: string
  updatedAt?: string
  resolutionTimeoutEnabled: boolean
}

export interface FlowNode {
  id: string
  flowId: string
  name: string
  nodeName?: string
  duration: string | number
  durationInMinutes?: number
  resolutionTimeout?: string | number | null
  resolutionTimeoutInMinutes?: number | null
  email: string
  position: string | number
}

export interface Escalation {
  id: string
  name: string
  taskId: string
  flowId: string
  status: string
  resolutionType?: string | null
  issueSolvedBy?: string | null
  acknowledgedAt?: string | null
  acknowledgedStepId?: string | null
  resolutionDeadline?: string | null
  scheduledStartAt?: string | null
  scheduleTimezone?: string | null
  cancelledAt?: string | null
  createdAt: string
  updatedAt?: string
}

export interface EscalationManualActionRequest {
  expectedSourceStepId: string
  expectedTargetStepId: string
}

export interface EscalationManualAction {
  escalationName: string
  status: string
  sourceStepId?: string | null
  sourceRecipientEmail?: string | null
  targetStepId?: string | null
  targetRecipientEmail?: string | null
  actionDeadline?: string | null
  actionAvailable: boolean
  alreadyEscalated: boolean
  unavailableReason?: string | null
}

export interface EscalationAcknowledgement {
  escalationName: string
  recipientEmail: string
  status: string
  expiresAt: string
  acknowledgedAt?: string | null
  acknowledgedBy?: string | null
  alreadyAcknowledged: boolean
}

export interface ExecutionState {
  id: string
  nodeId: string
  position: string | number
  userEmail: string
  status: string
  sendAttemptCount: number
  dueAt?: string | null
  createdAt: string
  updatedAt?: string
}
