import { httpRequest } from './http'

export function fetchPlaybackSession(userId) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/session`)
}

export function syncPlaybackSession(userId) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/sync`, {
    method: 'POST',
  })
}

export function playTrack(userId, payload) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/play`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

export function pausePlayback(userId, deviceId = null) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/pause`, {
    method: 'POST',
    body: JSON.stringify({ deviceId }),
  })
}

export function nextTrack(userId, deviceId = null) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/next`, {
    method: 'POST',
    body: JSON.stringify({ deviceId }),
  })
}

export function previousTrack(userId, deviceId = null) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/previous`, {
    method: 'POST',
    body: JSON.stringify({ deviceId }),
  })
}

export function seekPlayback(userId, positionMs, deviceId = null) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/seek`, {
    method: 'POST',
    body: JSON.stringify({ positionMs, deviceId }),
  })
}

export function changePlaybackMode(userId, playbackMode, deviceId = null) {
  return httpRequest(`/api/playback/${encodeURIComponent(userId)}/mode`, {
    method: 'POST',
    body: JSON.stringify({ playbackMode, deviceId }),
  })
}
