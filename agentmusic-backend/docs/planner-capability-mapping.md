# Planner Capability Mapping

## Capability to Intent Mapping

| Product Capability | Recommended Intent | Notes |
|---|---|---|
| Play / pause / mode change | `PLAYBACK_CONTROL` | Single-step or short multi-step |
| Search and then play | `COMPOSITE_REQUEST` | Search + playback |
| Artist profile lookup | `ARTIST_LOOKUP` | Can expand into hot songs / related artists |
| Generic track search | `TRACK_LOOKUP` | Query-first path |
| Generate recommendation playlist | `RECOMMEND_PLAYLIST` | Core differentiator |
| Recommend and play now | `PLAY_RECOMMENDATION` | Core differentiator with playback |
| Open historical playlist | `PLAYLIST_HISTORY_ACCESS` | Local data first |
| Queue management | `QUEUE_MANAGEMENT` | Later phase |

## Priority Guidance

### Highest priority

- `RECOMMEND_PLAYLIST`
- `PLAY_RECOMMENDATION`
- `PLAYBACK_CONTROL`
- `PLAYLIST_HISTORY_ACCESS`

### Medium priority

- `TRACK_LOOKUP`
- `ARTIST_LOOKUP`

### Later priority

- `QUEUE_MANAGEMENT`
- richer conversational refinement policies

