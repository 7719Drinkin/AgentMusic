# Spotify Bridge Mode Design

## Purpose

This document defines the first Spotify integration mode for AgentMusic:

- A single developer-owned Spotify account acts as the bridge account.
- AgentMusic users log in only to AgentMusic.
- AgentMusic backend uses the bridge account's Spotify authorization to query metadata and control playback.

This mode is intended for:

- course design demo
- single-account showcase
- early-stage product validation

It is not the final multi-user Spotify account binding model.

## Official Spotify Constraints

This design is based on Spotify Web API official authorization and playback model:

- Private user resources require user authorization.
- Playback control endpoints require Spotify Premium.
- Redirect URI must be registered exactly in Spotify Developer Dashboard.
- Access token and refresh token must be handled by the backend, not exposed in frontend code.

Official references:

- https://developer.spotify.com/documentation/web-api/concepts/authorization
- https://developer.spotify.com/documentation/web-api/tutorials/code-flow
- https://developer.spotify.com/documentation/web-api/concepts/scopes
- https://developer.spotify.com/documentation/web-api/concepts/redirect_uri
- https://developer.spotify.com/documentation/web-api/reference/start-a-users-playback
- https://developer.spotify.com/documentation/web-api/reference/pause-a-users-playback

## Product Assumptions

### Accepted assumptions for AgentMusic

1. The developer will provide one Spotify bridge account.
2. The bridge account will be Premium.
3. All Spotify playback control in the current phase targets that single bridge account.
4. AgentMusic users do not log into Spotify directly in the current phase.
5. User-facing playback state, playlist history, chat memory, and preference data remain in AgentMusic's own MySQL and Redis.

### User data boundary

The following Spotify-side bridge-account resources must not be exposed to end users as if they were the users' own resources:

- bridge account saved library
- bridge account device list
- bridge account personal top items
- bridge account personal playback history

Instead:

- user preferences come from `users.preferences`
- user recommendation history comes from `playlists` and `playlist_tracks`
- user playback session comes from AgentMusic `sessions` and Redis session cache

This keeps the bridge account as an infrastructure dependency rather than user identity.

## Why Premium Is Required

For the current project scope, playback control is a core feature:

- play / pause
- next / previous
- seek
- repeat / shuffle
- playback status sync

Spotify official playback APIs for these actions require Premium.

Therefore, for AgentMusic's current planned functionality, the bridge account must be Premium.

## Required Developer Inputs

### Spotify Developer App

The developer must create a Spotify app in Spotify Developer Dashboard and provide:

- `client_id`
- `client_secret`
- registered `redirect_uri`

These are application credentials, not the plain Spotify account password for runtime use.

### Bridge account authorization

The bridge account must complete the Spotify authorization flow once so that AgentMusic backend can obtain:

- access token
- refresh token

The backend should then manage refresh automatically.

### Required scopes

Current bridge-mode baseline should request:

- `user-read-private`
- `user-read-email`
- `streaming`
- `user-read-playback-state`
- `user-read-currently-playing`
- `user-modify-playback-state`
- `playlist-read-private`
- `playlist-modify-private`
- `playlist-modify-public`
- `user-library-read`
- `user-top-read`

## Backend Architecture in Bridge Mode

### Layering

The Spotify bridge flow must follow this dependency direction:

```text
Controller
  -> Application Service
    -> Domain Service
      -> Spotify Client
```

Controller must not call Spotify HTTP endpoints directly.

### Current package mapping

- `controller`
  - HTTP boundary only
- `service.application`
  - use-case orchestration
- `service`
  - reusable domain capabilities
- `client`
  - Spotify HTTP boundary
- `repository`
  - MySQL / Redis storage boundary
- `planner`
  - intent parsing, plan creation, task execution orchestration

### Spotify bridge responsibilities

#### `SpotifyAuthClient`

- build authorization URL
- exchange authorization code for tokens
- refresh access token

#### `SpotifyCatalogClient`

- track lookup
- artist lookup
- track search

#### `SpotifyPlaybackClient`

- get playback state
- get available devices
- play track
- pause playback
- seek
- change playback mode

## Token Ownership Model

### Current phase

Only one token set is used:

- one bridge account access token
- one bridge account refresh token

The backend owns the refresh token and Spotify app secret. For Spotify Web Playback SDK only, the frontend may request a short-lived access token through the backend token-broker endpoint:

- `GET /api/auth/spotify/web-playback-token`

This endpoint must not expose the refresh token or client secret.

### Web Playback SDK token exception

Spotify Web Playback SDK requires a browser-side access token with `streaming`, `user-read-private`, and `user-read-email`. AgentMusic supports this through a backend token broker:

- the frontend receives only a short-lived access token
- the refresh token remains backend-owned
- the SDK token is kept in browser memory only
- missing SDK scopes are reported with the structured code `spotify-scope-missing`

The bridge account must be re-authorized whenever new required scopes are added. Existing refresh tokens are not automatically upgraded by changing backend configuration.

### Storage recommendation

Do not keep bridge account tokens in source code.

Recommended storage:

- local-only config for development
- environment variables in deployment
- encrypted persistent storage if long-term server deployment is used

### Password handling

AgentMusic backend should not require storing the bridge account password in source code.

Correct process:

1. Developer creates Spotify app.
2. Developer authorizes the bridge account once through Spotify official login page.
3. Backend receives authorization code.
4. Backend exchanges code for tokens.
5. Backend keeps only tokens and app credentials.

## Current Bridge Mode Implementation

### Implemented support

The current backend supports the bridge-mode architecture:

1. Controllers are already separated from service logic.
2. Application services already exist and are the correct insertion point for Spotify orchestration.
3. Spotify clients already exist as interfaces.
4. Local playback session and recommendation history are already modeled in domain, DTO, repository, and service layers.
5. MySQL/Redis design already separates local user state from external Spotify metadata cache.
6. Spotify authorization, token persistence, refresh, catalog search, and playback clients are implemented for the bridge account.
7. The Web Playback SDK token broker is implemented for the frontend browser playback device.
8. Backend playback routing accepts transient SDK device IDs and retries transfer/play while Spotify Connect device visibility catches up.

### Current gaps

The remaining bridge-mode gaps are product and hardening work rather than core integration blockers:

1. frontend automated tests for Web Playback SDK state transitions
2. smoother progress-bar ticking during SDK playback
3. continued Spotify rate-limit hardening for repeated recommendation searches
4. future migration from one bridge account to per-user Spotify account binding

## Web Playback SDK Device Model

The current frontend can create its own Spotify Connect device named `AgentMusic Web Player` through Spotify Web Playback SDK.

Runtime behavior:

1. `SpotifyWebPlaybackProvider` owns one shared SDK player instance for the frontend app.
2. Footer, Music Home, and Playlist Detail reuse that shared SDK instance.
3. When playback is requested from Music Home or Playlist Detail, the frontend first preserves the currently selected visible device if it is available.
4. If the current device is missing, stale, or unavailable, the frontend enables or reuses `AgentMusic Web Player`.
5. After SDK `ready`, the frontend sends the transient SDK `device_id` to backend playback endpoints.
6. The backend keeps AgentMusic playlist/session state authoritative and controls Spotify playback through Web API.
7. Refreshing the frontend automatically reconnects the SDK device during the same browser session after the player has been enabled once.

Important boundary:

- The SDK `device_id` is session-scoped and must not be stored as permanent configuration.
- The frontend still cannot fetch or decode raw Spotify audio streams.
- Spotify Premium remains required for playback.

## Bridge Mode and Local State Sync

### Spotify-side state

Spotify-side bridge account is used only as the external playback and metadata provider.

### AgentMusic-side state

User-visible state should come from AgentMusic storage:

- `sessions` and Redis session cache for user playback state
- `playlists` and `playlist_tracks` for user historical recommendation playlists
- `chat_messages` for conversation memory
- `users.preferences` for personalization

This allows:

- preserving per-user experience
- preventing leakage of bridge account internals
- keeping a migration path toward future per-user Spotify binding

## Planner Requirement

Bridge mode still requires a planner.

Why:

- one user request often maps to multiple actions
- query and playback control may be chained together
- the agent must choose between local data and Spotify lookups

Recommended planner flow:

1. classify intent
2. extract parameters
3. generate ordered plan steps
4. execute plan steps through application services
5. persist chat/session/playlist side effects

## Migration Path for Future Multi-User Spotify Binding

Bridge mode should be treated as phase 1.

Future phase 2 can replace bridge token ownership with per-user Spotify authorization while keeping:

- controller structure
- application service structure
- service structure
- planner structure

That is why Spotify access should stay behind client and service interfaces from the start.
