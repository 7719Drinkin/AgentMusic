# Planner Design Specification

## Purpose

This document defines the target planner design for AgentMusic.

The planner must be designed from the actual user-facing agent capabilities first, and only then be optimized.

That means:

1. identify what the agent must really do in the product
2. map those capabilities into planner intents, steps, and execution rules
3. implement a stable baseline planner
4. optimize parsing, ranking, and execution only after the baseline is correct

## Product-Derived Agent Capability Set

Based on current project documents, the agent must support these high-priority capabilities.

### A. Playback control

- play track
- pause
- previous / next
- change playback mode
- seek
- control queue

### B. Music discovery and information lookup

- search tracks
- search artists
- get track metadata
- get artist information
- get related artists
- get hot songs

### C. Recommendation and playlist generation

- generate recommendation playlist from natural-language intent
- support mood / genre / language / artist / scene based recommendation
- save recommendation results as historical playlist versions
- allow users to switch back to older recommendation playlists

### D. Context-aware conversation

- understand current listening intent
- use recent chat history
- use long-term user preferences
- use historical playlist versions

### E. Local state synchronization

- sync local playback session
- sync generated playlist history
- persist chat messages

## Planner Scope Definition

The planner is responsible for:

- intent classification
- parameter extraction
- plan creation
- choosing whether to read local state, local cache, or Spotify bridge services
- sequencing tool or service invocation

The planner is not responsible for:

- direct HTTP access
- direct database access
- direct controller concerns
- token management

## Planner Architecture

Recommended planner architecture:

```text
User Request
  -> Intent Layer
  -> Parameter Extraction Layer
  -> Plan Builder
  -> Task Executor
  -> Result Composer
```

### 1. Intent Layer

The intent layer maps a request into one primary intent, with optional secondary intents.

Recommended main intents:

- `PLAYBACK_CONTROL`
- `TRACK_LOOKUP`
- `ARTIST_LOOKUP`
- `RECOMMEND_PLAYLIST`
- `PLAY_RECOMMENDATION`
- `PLAYLIST_HISTORY_ACCESS`
- `QUEUE_MANAGEMENT`
- `CHAT_ONLY`
- `COMPOSITE_REQUEST`

Intent relationship rule:

- `PLAY_RECOMMENDATION` is the default user-facing recommendation intent
- `RECOMMEND_PLAYLIST` is the recommend-only variant and a reusable subflow
- `PLAY_RECOMMENDATION` must reuse the same recommendation-generation path as `RECOMMEND_PLAYLIST`
- only explicit "recommend only / do not play yet" requests should stop at `RECOMMEND_PLAYLIST`

### 2. Parameter Extraction Layer

The extractor should build a normalized request object with fields such as:

- `artistName`
- `trackName`
- `playlistName`
- `genre`
- `language`
- `mood`
- `scene`
- `playbackMode`
- `durationPreference`
- `recommendationSize`
- `requiresImmediatePlayback`

This layer should also decide whether the request contains:

- hard constraints
- soft preferences
- execution directives

## Planner Step Taxonomy

Recommended reusable step types:

- `READ_USER_PREFERENCES`
- `READ_CHAT_CONTEXT`
- `READ_LOCAL_SESSION`
- `READ_PLAYLIST_HISTORY`
- `LOOKUP_TRACK`
- `LOOKUP_ARTIST`
- `SEARCH_TRACKS`
- `SEARCH_ARTISTS`
- `GENERATE_RECOMMENDATION_CANDIDATES`
- `RANK_RECOMMENDATION_CANDIDATES`
- `CREATE_RECOMMENDATION_PLAYLIST`
- `STORE_RECOMMENDATION_PLAYLIST`
- `SYNC_LOCAL_SESSION`
- `UPDATE_PLAYBACK_STATE`
- `PERSIST_CHAT_REPLY`

These steps should be treated as internal plan semantics, not raw HTTP operations.

## Most Important Flow: Recommendation Playlist Planning

This is the most important agent capability in AgentMusic and should be designed separately from generic playback control.

### Goal

The agent must be able to take a user request like:

- "给我来点我常听的"
- "给我来点香港流行，随机模式"
- "帮我生成一个晚上放松的歌单"
- "我最近有点 emo，来点治愈一点的粤语歌"

and convert it into:

1. user-intent understanding
2. candidate generation
3. candidate ranking
4. playlist materialization
5. optional immediate playback
6. history storage

### Why recommendation planning must be independent

Recommendation generation is not the same as search.

It depends on:

- long-term user preferences
- recent conversation context
- historical playlists
- current session context
- optional Spotify metadata

So it should not be treated as just:

```text
search tracks -> take first result
```

That strategy is acceptable for early playback control, but it is not acceptable for recommendation quality.

### Recommendation Planner Inputs

The recommendation planner should use these input sources in priority order.

#### 1. Explicit user constraints

From the current utterance:

- artist
- genre
- language
- mood
- scene
- era
- playback mode

#### 2. Recent chat context

Used to infer continuity:

- user refining a previous recommendation
- user rejecting a prior result
- user asking for "similar but calmer"

#### 3. Long-term user preferences

From `users.preferences`:

- favorite genres
- favorite artists
- excluded genres
- preferred language
- mood preference

#### 4. Historical recommendation playlists

From `playlists` and `playlist_tracks`:

- what the user liked before
- what recent recommendation versions already contain
- avoid duplicate recommendation versions when possible

#### 5. Current session state

From local `sessions`:

- current track
- current mode
- whether user is already listening to a certain style

### Recommendation Planner Output

The recommendation planner should output a structured recommendation plan, not just a text reply.

Recommended output fields:

- `recommendationGoal`
- `hardConstraints`
- `softPreferences`
- `candidateSourceStrategy`
- `targetPlaylistName`
- `targetPlaylistSize`
- `shouldAutoPlay`
- `playbackModeAfterCreation`

### Recommendation Plan Stages

#### Stage 1: Understand recommendation goal

Classify whether the user wants:

- a list only
- a saved playlist
- immediate playback
- a playlist plus playback

#### Stage 2: Build recommendation query

Convert natural language into:

- hard constraints
- soft preferences
- exclusions

#### Stage 3: Generate candidate set

Candidate sources should include:

- locally cached tracks
- previously liked artists or genres
- Spotify search results
- Spotify related artist lookups

This stage should intentionally produce more candidates than needed.

#### Stage 4: Rank candidates

Ranking should consider:

- explicit constraints match
- user preference match
- conversation-context relevance
- diversity balance
- novelty
- duplicate suppression against recent recommendation playlists

#### Stage 5: Materialize playlist

The planner should then:

- choose final ordered track set
- create playlist record
- create playlist track records
- cache recent playlist history

#### Stage 6: Optional playback action

If the user asked to play immediately:

- set playback mode
- start playback using selected first track
- update local session

#### Stage 7: Reply composition

The textual reply should summarize:

- what kind of playlist was generated
- how many tracks were selected
- whether playback started
- whether a new history version was saved

## Recommended Intents for Recommendation Features

Recommendation-related intents should be split clearly.

### `RECOMMEND_PLAYLIST`

Used when recommendation should stop after playlist generation and persistence.

This intent is mainly for:

- explicit "recommend only" requests
- internal planner reuse by `PLAY_RECOMMENDATION`
- historical playlist generation without immediate playback

Examples:

- "帮我生成一个晚间放松歌单，先不要播放"
- "给我推荐一份今晚学习歌单，我先看看"

### `PLAY_RECOMMENDATION`

Used for the default recommendation path in AgentMusic.

This intent must:

1. build a recommendation playlist
2. persist it as a history version
3. select a playback entry
4. start playback

Examples:

- "给我来点香港流行，随机播放"
- "给我推荐点轻松的歌然后直接播"
- "给我来点歌，直接播放"

### `PLAYLIST_HISTORY_ACCESS`

Used when the user wants historical recommendation results.

Examples:

- "打开我上次那个夏天歌单"
- "切到上一版推荐歌单"

## Recommendation-Specific Execution Policy

### Rule 1

Recommendation requests must read local preference data before using Spotify.

### Rule 2

Recommendation requests should prefer local historical playlist context before calling broad Spotify search.

### Rule 3

Recommendation requests should create a playlist object, not only return loose tracks.

### Rule 4

If playback is requested, playlist generation must happen before playback action.

This means `PLAY_RECOMMENDATION` must execute the `RECOMMEND_PLAYLIST` subflow first, then run playback steps.

### Rule 5

Recommendation response should persist a history version unless the user explicitly asked for a temporary suggestion only.

## Proposed Planner Evolution Path

### Phase 1: Baseline deterministic planner

- keyword-assisted intent detection
- simple parameter extraction
- deterministic step generation
- no LLM-dependent re-planning

### Phase 2: Hybrid planner

- LLM-assisted parameter normalization
- candidate ranking improvements
- more robust composite request handling

### Phase 3: Adaptive planner

- rejection-aware refinement
- follow-up edits like "更安静一点"
- user-specific ranking weights

## Immediate Refactor Recommendation

Before further planner optimization, the current code should be reshaped so that:

1. recommendation intents are separated from generic search intents
2. recommendation playlist generation gets its own plan branch
3. `PLAY_RECOMMENDATION` reuses `RECOMMEND_PLAYLIST`
4. composite requests distinguish:
   - search + play
   - artist info + recommend
   - other mixed cases that are not default recommendation playback

The current implementation that uses "search first result then play" is acceptable only for early playback demos, not for recommendation quality.

## Current Design Conclusion

The planner should be optimized around AgentMusic's most important product promise:

- understanding user listening intent
- generating recommendation playlists that reflect user needs
- preserving local historical playlist versions
- optionally starting playback

Therefore, recommendation planning is the center of the planner design, not an extra branch.
