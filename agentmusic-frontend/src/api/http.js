const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080'

const ERROR_CODE = {
  AUTHORIZATION: 'spotify-authorization',
  DEVICE_UNAVAILABLE: 'spotify-device-unavailable',
  DEVICE_RESTRICTED: 'spotify-device-restricted',
  NETWORK: 'spotify-network',
  PLAYBACK_CONFLICT: 'spotify-playback-conflict',
  INVALID_REQUEST: 'invalid-request',
  NOT_FOUND: 'not-found',
  SERVER_FAILURE: 'server-failure',
  REQUEST_FAILURE: 'request-failure',
}
const KNOWN_ERROR_CODES = new Set(Object.values(ERROR_CODE))

export async function httpRequest(path, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers ?? {}),
    },
    ...options,
  })

  if (!response.ok) {
    const payload = await readErrorPayload(response)
    throw createRequestError(path, response.status, payload)
  }

  const contentType = response.headers.get('content-type') ?? ''
  if (!contentType.includes('application/json')) {
    return null
  }

  return response.json()
}

export function getErrorMessage(error, fallbackMessage = 'Request failed.') {
  if (error && typeof error.message === 'string' && error.message.trim()) {
    return error.message
  }
  return fallbackMessage
}

export function getErrorCode(error) {
  if (error && typeof error.code === 'string' && error.code.trim()) {
    return error.code
  }
  return ERROR_CODE.REQUEST_FAILURE
}

async function readErrorPayload(response) {
  try {
    const contentType = response.headers.get('content-type') ?? ''
    if (contentType.includes('application/json')) {
      const body = await response.json()
      return {
        errorCode: typeof body?.code === 'string' ? body.code : '',
        detailMessage: typeof body?.message === 'string' ? body.message : '',
        rawBody: body,
      }
    }

    const text = await response.text()
    return {
      errorCode: '',
      detailMessage: typeof text === 'string' ? text : '',
      rawBody: text,
    }
  } catch {
    return {
      errorCode: '',
      detailMessage: '',
      rawBody: null,
    }
  }
}

function createRequestError(path, status, payload) {
  const backendCode = normalizeBackendErrorCode(payload?.errorCode)
  const detailMessage = payload?.detailMessage?.trim() ?? ''
  const code = backendCode || classifyErrorCode(path, status, detailMessage)
  const message = toUserMessage(path, status, code, detailMessage)
  const error = new Error(message)
  error.name = 'HttpRequestError'
  error.status = status
  error.path = path
  error.code = code
  error.detailMessage = detailMessage
  error.rawBody = payload?.rawBody ?? null
  return error
}

function normalizeBackendErrorCode(errorCode) {
  if (typeof errorCode !== 'string') {
    return ''
  }
  return KNOWN_ERROR_CODES.has(errorCode) ? errorCode : ''
}

function classifyErrorCode(path, status, detailMessage) {
  const detail = detailMessage.toLowerCase()
  const isPlaybackPath = path.startsWith('/api/playback/')

  if (
    detail.includes('invalid or expired spotify bridge authorization state') ||
    detail.includes('authorization expired') ||
    detail.includes('authorization is invalid') ||
    detail.includes('spotify bridge mode is disabled')
  ) {
    return ERROR_CODE.AUTHORIZATION
  }

  if (
    detail.includes('web player') ||
    detail.includes('desktop client online') ||
    detail.includes('target device') ||
    detail.includes('device is offline') ||
    detail.includes('device unavailable')
  ) {
    return ERROR_CODE.DEVICE_UNAVAILABLE
  }

  if (detail.includes('restricted')) {
    return ERROR_CODE.DEVICE_RESTRICTED
  }

  if (
    detail.includes('failed to resolve') ||
    detail.includes('dns') ||
    detail.includes('accounts.spotify.com') ||
    detail.includes('api.spotify.com') ||
    detail.includes('connection refused')
  ) {
    return ERROR_CODE.NETWORK
  }

  if (isPlaybackPath && status === 409) {
    return ERROR_CODE.PLAYBACK_CONFLICT
  }

  if (status === 400) {
    return ERROR_CODE.INVALID_REQUEST
  }

  if (status === 404) {
    return ERROR_CODE.NOT_FOUND
  }

  if (status >= 500) {
    return ERROR_CODE.SERVER_FAILURE
  }

  return ERROR_CODE.REQUEST_FAILURE
}

function toUserMessage(path, status, code, detailMessage) {
  switch (code) {
    case ERROR_CODE.AUTHORIZATION:
      return 'Spotify bridge authorization expired or is invalid. Reconnect the bridge account and try again.'
    case ERROR_CODE.DEVICE_UNAVAILABLE:
      return 'No active Spotify device is available. Keep the same bridge account Web Player or desktop client online.'
    case ERROR_CODE.DEVICE_RESTRICTED:
      return 'Detected Spotify devices are restricted. Switch to an active Web Player or desktop client and try again.'
    case ERROR_CODE.NETWORK:
      return 'Spotify service is temporarily unreachable. Check the network or DNS and try again.'
    case ERROR_CODE.PLAYBACK_CONFLICT:
      return 'Spotify playback request could not be completed. Check the active device and try again.'
    case ERROR_CODE.INVALID_REQUEST:
      return path.startsWith('/api/playback/')
        ? 'Playback request is invalid. Refresh the player and try again.'
        : 'Request parameters are invalid. Refresh the page and try again.'
    case ERROR_CODE.NOT_FOUND:
      if (path.startsWith('/api/playlists/')) {
        return 'Requested playlist was not found.'
      }
      if (path.startsWith('/api/playback/')) {
        return 'Requested playback resource is unavailable. Refresh the player and try again.'
      }
      return 'Requested resource was not found.'
    case ERROR_CODE.SERVER_FAILURE:
      return path.startsWith('/api/playback/')
        ? 'Spotify playback request failed on the server. Check the bridge authorization, active device, and network, then try again.'
        : `Server request failed (${status}). Try again shortly.`
    default:
      return detailMessage || `Request failed (${status}).`
  }
}

export { API_BASE_URL }
