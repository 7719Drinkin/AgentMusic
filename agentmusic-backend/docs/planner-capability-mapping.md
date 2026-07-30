# Planner Capability Mapping

## Capability to Intent Mapping

| Product Capability | Recommended Intent | Notes |
|---|---|---|
| Play / pause / mode change | `PLAYBACK_CONTROL` | Single-step or short multi-step |
| Search and then play | `COMPOSITE_REQUEST` | Reserved for explicit search-first flows |
| Artist profile lookup | `ARTIST_LOOKUP` | Can expand into hot songs / related artists |
| Generic track search | `TRACK_LOOKUP` | Query-first path |
| Generate recommendation playlist only | `RECOMMEND_PLAYLIST` | Recommend-only path and reusable subflow |
| Recommend and play now | `PLAY_RECOMMENDATION` | Default recommendation path |
| Open historical playlist | `PLAYLIST_HISTORY_ACCESS` | Local data first |
| Queue management | `QUEUE_MANAGEMENT` | Later phase |

## Recommendation Relationship Rule

- `PLAY_RECOMMENDATION` includes the full `RECOMMEND_PLAYLIST` workflow
- `RECOMMEND_PLAYLIST` should be implemented so it can be reused internally
- plain recommendation language should default to `PLAY_RECOMMENDATION`
- only explicit "do not play yet / let me inspect first" requests should stay on `RECOMMEND_PLAYLIST`

## Priority Guidance

### Highest priority

- `PLAY_RECOMMENDATION`
- `RECOMMEND_PLAYLIST`
- `PLAYBACK_CONTROL`
- `PLAYLIST_HISTORY_ACCESS`

### Medium priority

- `TRACK_LOOKUP`
- `ARTIST_LOOKUP`

### Later priority

- `QUEUE_MANAGEMENT`
- richer conversational refinement policies
