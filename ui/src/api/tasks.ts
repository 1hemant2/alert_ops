import { jsonBody, request } from './client'
import type { Task } from './types'

// Loads one team-scoped task for the detail and edit screen.
export function getTask(taskId: string): Promise<Task> {
  return request<Task | null>(`/api/v1/task/${encodeURIComponent(taskId)}`).then(task => {
    if (!task) throw new Error('This task could not be found in the current team.')
    return task
  })
}

// Loads the team's task library for selectors and task management.
export function getTasks(): Promise<Task[]> {
  return request<Task[]>('/api/v1/task?page=0&size=100&sortBy=createdAt&sortDir=desc')
}

// Creates a task with the complete user-provided context.
export function createTask(payload: Pick<Task, 'name' | 'description' | 'source' | 'priority' | 'category' | 'referenceUrl'>): Promise<string> {
  return request<string>('/api/v1/task', { method: 'POST', body: jsonBody(payload) })
}

// Updates the task title through the existing task endpoint.
function updateTaskName(taskId: string, name: string): Promise<Task> {
  return request<Task>('/api/v1/task/name', {
    method: 'PUT',
    body: jsonBody({ id: taskId, name }),
  })
}

// Updates the task description through the existing task endpoint.
function updateTaskDescription(taskId: string, description: string): Promise<Task> {
  return request<Task>('/api/v1/task/description', {
    method: 'PUT',
    body: jsonBody({ id: taskId, description }),
  })
}

// Updates task source and optional metadata through the existing task endpoint.
function updateTaskDetails(taskId: string, payload: Pick<Task, 'source' | 'priority' | 'category' | 'referenceUrl'>): Promise<Task> {
  return request<Task>('/api/v1/task/details', {
    method: 'PUT',
    body: jsonBody({ id: taskId, ...payload }),
  })
}

// Saves all task fields using the existing focused task update operations.
export async function updateTask(taskId: string, payload: Pick<Task, 'name' | 'description' | 'source' | 'priority' | 'category' | 'referenceUrl'>): Promise<Task> {
  await updateTaskName(taskId, payload.name)
  await updateTaskDescription(taskId, payload.description)
  return updateTaskDetails(taskId, {
    source: payload.source,
    priority: payload.priority,
    category: payload.category,
    referenceUrl: payload.referenceUrl,
  })
}
