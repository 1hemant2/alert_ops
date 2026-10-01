import { jsonBody, request } from './client'
import type { WebhookConfiguration, WebhookEvent } from './types'

export function getWebhooks(): Promise<WebhookConfiguration[]> {
  return request<WebhookConfiguration[]>('/api/v1/team/webhooks')
}

export function createWebhook(input: { name: string; flowId: string }): Promise<WebhookConfiguration> {
  return request<WebhookConfiguration>('/api/v1/team/webhooks', { method: 'POST', body: jsonBody(input) })
}

export function rotateWebhook(webhookId: string): Promise<WebhookConfiguration> {
  return request<WebhookConfiguration>(`/api/v1/team/webhooks/${encodeURIComponent(webhookId)}/rotate`, { method: 'POST' })
}

export function updateWebhook(webhookId: string, enabled: boolean): Promise<WebhookConfiguration> {
  return request<WebhookConfiguration>(`/api/v1/team/webhooks/${encodeURIComponent(webhookId)}`, {
    method: 'PATCH',
    body: jsonBody({ enabled }),
  })
}

export function getWebhookEvents(webhookId: string): Promise<WebhookEvent[]> {
  return request<WebhookEvent[]>(`/api/v1/team/webhooks/${encodeURIComponent(webhookId)}/events`)
}
