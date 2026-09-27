import { jsonBody, request } from './client'
import type { Task } from './types'

export function getTasks(): Promise<Task[]> {
  return request<Task[]>('/api/v1/task?page=0&size=100&sortBy=createdAt&sortDir=desc')
}

export function createTask(payload: Pick<Task, 'name' | 'description'>): Promise<string> {
  return request<string>('/api/v1/task', { method: 'POST', body: jsonBody(payload) })
}
