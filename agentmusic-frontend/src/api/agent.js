import { API_BASE_URL, httpRequest } from './http'

export function fetchAgentHistory(userId, limit = 20) {
  return httpRequest(`/api/agent/history/${encodeURIComponent(userId)}?limit=${limit}`)
}

export function sendAgentChatMessage(payload) {
  return httpRequest('/api/agent/chat', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export async function sendAgentChatMessageStream(payload, handlers = {}) {
  const response = await fetch(`${API_BASE_URL}/api/agent/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'application/x-ndjson',
    },
    body: JSON.stringify(payload),
  })

  if (!response.ok || !response.body) {
    return sendAgentChatMessage(payload)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  let completedResponse = null
  const handleEvent = (event) => {
    if (!event) {
      return
    }

    if (event.type === 'status') {
      handlers.onStatus?.(event.payload?.message ?? '')
    } else if (event.type === 'reply-delta') {
      handlers.onDelta?.(event.payload?.delta ?? '')
    } else if (event.type === 'complete') {
      completedResponse = event.payload?.response ?? null
    } else if (event.type === 'error') {
      throw new Error(event.payload?.message || 'Agent request failed.')
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const lines = buffer.split('\n')
    buffer = lines.pop() ?? ''

    for (const line of lines) {
      handleEvent(parseStreamEvent(line))
    }
  }

  if (buffer.trim()) {
    handleEvent(parseStreamEvent(buffer))
  }

  if (!completedResponse) {
    throw new Error('Agent stream ended before completion.')
  }

  return completedResponse
}

function parseStreamEvent(line) {
  const trimmed = line.trim()
  if (!trimmed) {
    return null
  }

  try {
    return JSON.parse(trimmed)
  } catch {
    return null
  }
}
