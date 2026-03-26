import { httpRequest } from './http'

export function fetchAgentHistory(userId, limit = 20) {
  return httpRequest(`/api/agent/history/${encodeURIComponent(userId)}?limit=${limit}`)
}

export function sendAgentChatMessage(payload) {
  return httpRequest('/api/agent/chat', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
