# AgentMusic Web Playback SDK Device Implementation

## Purpose

This document records the implementation plan and current status for turning the AgentMusic frontend bottom playback bar into a real Spotify playback device.

The goal is:

- The user should not need to open the official Spotify desktop client or Spotify Web Player.
- AgentMusic should create its own browser-based Spotify Connect device through Spotify Web Playback SDK.
- Existing backend playback APIs should continue to own playback orchestration, playlist context, session persistence, and error handling.

## Implementation Status

Status as of 2026-06-14: the core SDK device flow is implemented and manually verified.

Implemented:

- The backend authorization scope list includes `streaming`.
- The backend exposes `GET /api/auth/spotify/web-playback-token` as a short-lived token broker for Spotify Web Playback SDK.
- The frontend has a shared `SpotifyWebPlaybackProvider`, so Footer, Music Home, and Playlist Detail use one SDK player instance.
- The footer device panel can enable `AgentMusic Web Player` and route playback to it.
- Music Home track cards automatically enable/reuse the SDK device before playback.
- Playlist Detail play buttons automatically enable/reuse the SDK device before playback.
- Refreshing the frontend automatically reconnects the SDK device during the same browser session after the player has been enabled once.
- Backend playback routing accepts transient SDK device IDs and retries transfer/play while Spotify device visibility catches up.
- Device routing preserves a currently selected visible device; if the current device is missing, stale, or unavailable, the frontend enables/reuses `AgentMusic Web Player`.

Remaining follow-up work:

- Add frontend unit/integration tests with a mocked `window.Spotify`.
- Keep improving progress-bar smoothness with local ticking plus SDK/backend reconciliation.
- Continue treating the SDK `device_id` as transient; do not persist it as long-term configuration.

## Boundary

This plan does not bypass Spotify's playback system.

The frontend cannot fetch raw Spotify audio streams and decode them independently. The viable implementation is to use Spotify Web Playback SDK, which registers the current browser tab as a Spotify Connect device. The bottom bar then controls that SDK-backed device.

Required assumptions for the current project phase:

- The bridge Spotify account is Premium.
- Spotify app credentials and bridge tokens remain local/backend-owned.
- The user can click once to activate the web player, so browser autoplay restrictions are acceptable.
- Current bridge-mode single-account playback is still accepted for the course-design demo.

## Current Project State

The current footer is now a playback controller for an SDK-backed browser playback device. The browser tab becomes a Spotify Connect device named `AgentMusic Web Player`, while the backend remains responsible for playlist context, session persistence, and Spotify Web API orchestration.

Relevant frontend files:

- `agentmusic-frontend/src/component/footer/footer.jsx`
- `agentmusic-frontend/src/component/footer/footer-right.jsx`
- `agentmusic-frontend/src/component/footer/player/music-control-box.jsx`
- `agentmusic-frontend/src/component/footer/player/music-progress-bar.jsx`
- `agentmusic-frontend/src/context/SpotifyWebPlaybackContext.jsx`
- `agentmusic-frontend/src/hooks/useSpotifyWebPlayback.js`
- `agentmusic-frontend/src/api/spotifyAuth.js`
- `agentmusic-frontend/src/api/playback.js`

Relevant backend files:

- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/web/controller/PlaybackController.java`
- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/integration/spotify/SpotifyWebApiPlaybackClient.java`
- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/service/impl/DefaultBridgePlaybackControlService.java`
- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/integration/spotify/SpotifyWebApiAuthClient.java`
- `agentmusic-backend/src/main/java/com/agentmusic/agentmusic_backend/service/impl/DefaultSpotifyBridgeAuthService.java`

Backend-orchestrated playback flow:

```text
Footer UI
  -> frontend playback API
    -> backend PlaybackController
      -> PlaybackApplicationService
        -> BridgePlaybackControlService
          -> Spotify Web API
            -> existing Spotify Connect device
```

Implemented SDK playback flow:

```text
Footer UI
  -> Spotify Web Playback SDK creates browser device
  -> SDK ready event returns device_id
  -> frontend sends device_id to backend playback API
  -> backend Spotify Web API transfers/plays to that device_id
  -> SDK player_state_changed updates footer UI
  -> backend session remains the source of persisted app state
```

## Main Risks And Solutions

### Missing `streaming` scope

The backend authorization scope list must include `streaming`.

Solution:

- Add `streaming` to `SpotifyWebApiAuthClient.DEFAULT_SCOPES`.
- Reconnect the bridge account through `/api/auth/spotify/login`.
- Add backend status validation for missing Web Playback SDK scopes.

Important note:

Adding a new scope does not automatically upgrade an existing refresh token. The bridge account must complete authorization again.

### Token exposure

The frontend SDK needs an access token, but the frontend must not receive long-term credentials.

Solution:

- Keep `client_secret`, refresh token, and local env credentials backend-only.
- Add a backend token-broker endpoint that returns only the current short-lived access token.
- Reuse `DefaultSpotifyBridgeAuthService.getValidAccessToken()` so refresh remains centralized.
- Do not persist the access token in localStorage.
- Keep the token in memory only inside the SDK wrapper.

Implemented endpoint:

```text
GET /api/auth/spotify/web-playback-token
```

Response:

```json
{
  "accessToken": "short-lived-access-token",
  "expiresAt": "2026-05-21T12:00:00Z",
  "scopes": ["streaming", "user-read-private", "user-read-email"],
  "missingScopes": []
}
```

If required scopes are missing, return a structured error code such as:

```text
spotify-scope-missing
```

### Browser autoplay restriction

The browser may block playback unless activation happens inside a user gesture.

Solution:

- Add an explicit "Enable AgentMusic Player" state in the bottom bar.
- On the first user click, initialize or activate the SDK player.
- Call SDK activation before attempting transfer/play.
- Store pending playback intent if the user clicked play before the SDK device is ready.

### SDK device lifecycle

Spotify Web Playback SDK `device_id` is session-scoped and may change between browser sessions.

Solution:

- Do not store SDK `device_id` in env or long-term config.
- Keep it in frontend memory and Redux/app state for the current browser session.
- Send it to backend play/transfer APIs when playback starts.
- Backend may store it in the current playback session but must treat it as transient.
- If `not_ready` fires, mark the current device offline and require reconnection.

### Device visibility delay

The SDK `ready` event can return a `device_id` before `/me/player/devices` immediately reflects it.

Solution:

- Trust the SDK `ready` event for frontend readiness.
- Backend `transferPlayback` and `playTrack` should use small retry/backoff if Spotify reports the device as unavailable immediately after SDK ready.
- Device panel refresh can lag behind the actual SDK readiness.

### State consistency

There will be two sources of playback state:

- SDK real-time player state.
- Backend persisted session state.

Solution:

- Use SDK `player_state_changed` for immediate UI updates.
- Use backend session as persisted app state.
- Keep existing `/api/playback/{userId}/sync` as a reconciliation fallback.
- Prefer SDK events for progress bar ticking instead of aggressive polling.
- Reduce backend polling frequency after SDK state is stable.

### Queue and next-track behavior

AgentMusic playlists are local application playlists, not necessarily Spotify playlists.

Solution:

- Do not fully delegate next/previous behavior to Spotify's queue.
- Continue routing next/previous through backend `PlaybackApplicationService`.
- Backend chooses the next track from AgentMusic playlist context.
- Backend plays that selected track to the SDK `device_id`.

## Detailed Implementation Record

### Phase 1: Authorization and token broker

Status: completed.

1. Added `streaming` to the Spotify authorization scope list.
2. Added `GET /api/auth/spotify/web-playback-token`.
3. Return only short-lived access token metadata to the frontend.
4. Added structured error response for missing scope or expired bridge authorization.
5. Updated bridge-mode documentation to mention the Web Playback SDK token exception.

Expected result:

- Frontend can request a valid SDK token.
- Backend still owns refresh token and app secret.

### Phase 2: Frontend SDK wrapper

Status: completed.

1. Add a frontend module such as `src/api/spotifyWebPlayback.js` or `src/hooks/useSpotifyWebPlayback.js`.
2. Load `https://sdk.scdn.co/spotify-player.js` exactly once.
3. Create `new Spotify.Player(...)` with name `AgentMusic Web Player`.
4. Implement `getOAuthToken` by calling the backend token-broker endpoint.
5. Listen for SDK events:
   - `ready`
   - `not_ready`
   - `player_state_changed`
   - `initialization_error`
   - `authentication_error`
   - `account_error`
   - `playback_error`
   - `autoplay_failed`
6. Expose SDK state to the footer:
   - `deviceId`
   - `isReady`
   - `isConnecting`
   - `isActive`
   - `errorCode`
   - `errorMessage`

Expected result:

- The browser tab can become a Spotify Connect device.
- Footer can tell whether the AgentMusic player is usable.

### Phase 3: Footer integration

Status: completed.

1. Add an "Enable AgentMusic Player" state to the bottom playback bar.
2. On the first play click:
   - activate SDK player
   - connect SDK player if needed
   - wait for `ready`
   - transfer playback to SDK `device_id`
   - start playback through existing backend API
3. Replace old "open Spotify Web Player or desktop client" messages.
4. Show device status with concise labels:
   - `AgentMusic Player off`
   - `Connecting`
   - `Ready`
   - `Playing here`
   - `Needs re-auth`
   - `Offline`
5. Prefer SDK device in the device panel once available.

Expected result:

- The bottom bar itself becomes the default playback destination.
- Users no longer need an external Spotify client during manual E2E.

### Phase 4: Backend playback routing

Status: completed.

1. Keep existing `deviceId` request fields.
2. Make web SDK device the preferred target when frontend supplies its `device_id`.
3. Add retry/backoff around transfer/play for newly registered SDK devices.
4. Keep `DefaultBridgePlaybackControlService.resolveTargetDeviceId(...)` compatible with transient SDK devices.
5. Adjust error messages from official-client wording to AgentMusic-player wording.

Expected result:

- Existing playback APIs work with the SDK device.
- Backend remains the orchestration layer.

### Phase 5: State synchronization

Status: implemented for SDK state updates and backend reconciliation. Progress-bar smoothing can still be improved.

1. Map SDK `player_state_changed` into current footer state:
   - current track Spotify ID
   - current position
   - duration
   - paused/playing
2. When SDK state changes, update frontend store immediately.
3. Reconcile backend session periodically or after meaningful events:
   - play
   - pause
   - seek
   - next
   - previous
   - track ended
4. Keep backend session as the persisted truth for playlist context.

Expected result:

- UI progress feels real-time.
- Backend remains consistent enough for page refresh and Agent operations.

### Phase 6: Auto-next and playlist context

Status: implemented through backend next/previous playback APIs while keeping AgentMusic playlist context authoritative.

1. Detect track end through SDK state or current footer timer.
2. Call backend `nextTrack(userId, webDeviceId)`.
3. Backend resolves the next track from AgentMusic playlist context.
4. Backend plays the resolved track to the same SDK device.
5. Preserve current playback mode:
   - sequential
   - list loop
   - single loop
   - shuffle

Expected result:

- Playback automatically advances through AgentMusic-generated playlists.
- Spotify queue does not override local playlist semantics.

### Phase 7: Testing

Status: backend automated tests and manual browser smoke tests completed. Frontend automated tests remain follow-up work.

Manual E2E checklist:

1. Close official Spotify desktop client and official Spotify Web Player.
2. Open AgentMusic frontend.
3. Click bottom bar "Enable AgentMusic Player".
4. Confirm device panel shows `AgentMusic Web Player`.
5. Ask Agent to recommend a playlist.
6. Confirm the recommended playlist starts playing in the browser.
7. Test pause/resume.
8. Test seek.
9. Test next/previous.
10. Test automatic next-track transition.
11. Refresh the page and confirm the player reconnects.
12. Close the page and confirm backend handles the SDK device becoming offline.

Automated tests:

1. Backend unit test for missing `streaming` scope.
2. Backend unit test for web playback token response.
3. Backend unit test for transient device play retry.
4. Frontend unit test with mocked `window.Spotify`.
5. Frontend integration test for footer state transitions.

## Next Priority

The original execution order has been completed for the core SDK device path:

1. Add `streaming` scope and reconnect bridge account.
2. Add backend web playback token endpoint.
3. Implement frontend SDK wrapper.
4. Connected footer play button to SDK activation and backend play.
5. Added SDK state synchronization.
6. Added auto-next behavior against local playlist context.
7. Polished device panel wording and visual states.
8. Documented manual E2E steps.

Next recommended work:

1. Add frontend tests for the shared Web Playback SDK provider and Footer/Home/Playlist playback flows.
2. Improve progress-bar ticking smoothness during SDK playback.
3. Keep an eye on Spotify API rate limits when running repeated manual E2E.

## Official References

- Spotify Web Playback SDK: https://developer.spotify.com/documentation/web-playback-sdk
- Spotify Web Playback SDK Reference: https://developer.spotify.com/documentation/web-playback-sdk/reference
- Transfer Playback: https://developer.spotify.com/documentation/web-api/reference/transfer-a-users-playback
- Start/Resume Playback: https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback
- Spotify Scopes: https://developer.spotify.com/documentation/web-api/concepts/scopes
