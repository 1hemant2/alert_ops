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

export interface Task {
  id: string
  name: string
  description: string
  createdAt?: string
}

export interface Flow {
  id: string
  name: string
  teamId: string
  version: number
  createdAt?: string
  updatedAt?: string
}

export interface FlowNode {
  id: string
  flowId: string
  name: string
  nodeName?: string
  duration: string | number
  durationInMinutes?: number
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
  createdAt: string
  updatedAt?: string
}

export interface ExecutionState {
  nodeId: string
  position: string | number
  userEmail: string
  executionState: string
  notificationState: string
  sendAttemptCount: number
  createdAt: string
  updatedAt?: string
}
