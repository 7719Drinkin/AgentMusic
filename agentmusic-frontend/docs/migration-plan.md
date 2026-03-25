# AgentMusic Frontend Migration Plan

## Goal

Migrate selected UI structure and assets from `agentmusic-frontend-reference` into the new
`agentmusic-frontend` workspace without carrying over the legacy React 17 and react-scripts 4 toolchain.

## Migration Principles

- Keep the reference repository read-only during early migration.
- Rebuild the target app on modern dependencies first.
- Migrate by feature slice, not by copying the whole repository at once.
- Convert JavaScript modules to TypeScript-compatible React modules as they move.
- Preserve only reusable UI, assets, and interaction logic.
- Do not preserve legacy build tooling, Redux boilerplate, or brittle dependency choices by default.

## Reference Repository Inventory

Main source areas currently available in `agentmusic-frontend-reference/src`:

- `pages`
- `component/sidebar`
- `component/topnav`
- `component/footer`
- `component/cards`
- `component/playlist`
- `component/icons`
- `style`
- `icons`
- `image`

## First-Round Migration Scope

Round 1 should focus on static UI foundation only.

Included:

- app shell layout
- sidebar structure
- top navigation shell
- footer/player shell
- shared icons and static assets
- shared CSS variables and base spacing system

Excluded for now:

- Redux store migration
- routing migration
- audio playback logic
- search behavior
- Spotify authorization wiring
- Agent chat panel implementation
- real backend API calls

## Proposed Target Structure

```text
src/
  app/
  assets/
  components/
    layout/
    navigation/
    player/
    playlist/
    ui/
  features/
    agent/
    playback/
    playlists/
    search/
  pages/
  styles/
  types/
```

## Round 1 Task Breakdown

1. Create modern React baseline and keep it buildable at every step.
2. Extract design tokens from the reference CSS into `src/styles`.
3. Migrate icons and image assets into `src/assets`.
4. Rebuild the shell layout in typed React components.
5. Rebuild sidebar and topnav as static components.
6. Rebuild footer/player shell as static components.
7. Add placeholder page containers for home, library, and playlist views.
8. Confirm the app still builds before moving any data or Spotify logic.

## Risks

- Reference project uses old routing and state-management patterns.
- CSS module names and directory layout are inconsistent with the target architecture.
- Direct copy-paste may pull in assumptions about old store shape and hardcoded mock data.

## Immediate Next Step

Start with shared layout migration:

- `style/variables.css`
- `style/index.css`
- `component/sidebar/*`
- `component/topnav/*`
- `component/footer/*`

## Current Progress

Completed in the current round:

- a modern chat-first shell replaced the placeholder scaffold page
- the main information hierarchy now matches AgentMusic's product goal:
  - chat first
  - recommendation history second
  - playback shell third
- the new home screen keeps the reference project's shell structure concept:
  - sidebar
  - top area
  - right-side support panel
  - bottom player bar

Planned next migration slice:

- convert reference sidebar and topnav details into reusable typed components
- move shared visual tokens from the reference CSS into the new style system
- begin replacing placeholder recommendation cards with backend-facing DTO-compatible view models
