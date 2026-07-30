# Recommendation Search Chain

Version: R1.4
Updated: 2026-05-21

## 1. Purpose

This document records the current recommendation execution chain from user request to final playlist tracks.

It exists for three reasons:

1. Clarify which parts are LLM-driven and which parts remain deterministic.
2. Record the actual Spotify retrieval paths now used in production code.
3. Provide a stable reference while theme-aware retrieval and semantic normalization evolve.

## 2. Current Architecture

The recommendation chain now uses a hybrid model:

- `LLM` is responsible for:
  - recommendation semantic normalization into `RecommendationSpec`
  - request mode classification
  - candidate reranking
- `deterministic code` is responsible for:
  - Spotify candidate retrieval
  - hard artist / album scoping
  - explicit-track priority insertion
  - playability filtering, deduplication, and final truncation

## 3. Core Files

- Planner harness:
  - `src/main/java/com/agentmusic/agentmusic_backend/planner/llm/AgentLlmPlanningHarness.java`
- Recommendation semantic normalization and rerank entrypoint:
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/LlmBackedRecommendationSelectionService.java`
- Local query extraction and structured entity hints:
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/SearchQueryRefiner.java`
- Spotify metadata retrieval and local result scoring:
  - `src/main/java/com/agentmusic/agentmusic_backend/service/impl/DefaultMusicMetadataService.java`
- Spotify Web API client:
  - `src/main/java/com/agentmusic/agentmusic_backend/integration/spotify/SpotifyWebApiCatalogClient.java`

## 4. Request Modes

`RecommendationSpec.requestMode` is now the main semantic normalization output.

Current modes:

- `ARTIST_ONLY`
- `ENTITY_CONSTRAINED`
- `ALBUM_ONLY`
- `THEME_AWARE`
- `GENERAL`

### 4.1 Theme Semantic Fields

`RecommendationSpec` now also carries theme fields for `THEME_AWARE` requests:

- `language`
- `era`
- `genre`
- `mood`
- `scene`
- `seedArtists`

Current normalization contract:

- `language`: `cantonese | mandarin | english | japanese | korean`
- `era`: `1980s | 1990s | 2000s | 2010s | 2020s`
- `genre`: `cantopop | mandopop | pop | rock | folk | ballad`
- `mood`: `rainy | calm | energetic`
- `scene`: `commute | late-night | rainy`
- `seedArtists`: 4 to 8 artists strongly associated with the requested language, era, genre, mood, or scene

The spec is now LLM-led. Deterministic code only performs repair and guardrail enforcement.

### 4.2 Semantic Normalization Rules

Current runtime behavior:

1. The latest user message is treated as the source of truth.
2. Older conversation and recent recommendation summaries are ignored for standalone requests.
3. Older context is only included for clearly referential requests such as:
   - `继续`
   - `再来`
   - `更多`
   - `类似`
   - `again`
   - `more`
   - `similar`
   - `continue`
4. Code-side repair remains intentionally narrow:
   - fill missing artist / track / album from deterministic hints if obvious
   - clear `track` when it duplicates `album`
   - prefer latest-message quoted track / album entities over conflicting LLM fields
   - coerce clearly misclassified album-only requests back to `ALBUM_ONLY`
   - coerce explicit theme-only requests back to `THEME_AWARE` when LLM returns `GENERAL` or hallucinated pseudo-entities
   - coerce explicit track requests away from `ARTIST_ONLY` so the quoted target track is not cleared
   - preserve strict `ARTIST_ONLY` and `ALBUM_ONLY` hard scopes

## 5. Entity Recommendation Flows

### 5.1 Artist-only

Examples:

- `推荐20首张雨生的歌`
- `来点谭咏麟的歌`

Current flow:

1. LLM or fallback resolves:
   - `requestMode = ARTIST_ONLY`
   - `artist`
   - `desiredTrackCount`
2. Code resolves primary `artistId` using `searchArtists(...)`.
3. Code expands that artist catalog using:
   - artist albums
   - album tracks
4. Only tracks belonging to that `artistId` remain in the candidate pool.
5. LLM reranks the candidate pool.
6. Code applies strict artist filtering and truncates to the final count.

Current verified behavior:

- `推荐20首张雨生的歌`
  - returns `20` tracks
  - all tracks belong to the same `artistId`
  - candidates span multiple albums instead of one shallow search page

### 5.2 Artist + Track (+ Album)

Examples:

- `推荐张雨生《发晕》以及他的其他歌曲`
- `推荐张雨生专辑《两伊战争红色热情》里的《我最深爱的人伤我最深》以及张雨生的其他歌曲`

Current flow:

1. LLM resolves:
   - `requestMode = ENTITY_CONSTRAINED`
   - `artist`
   - `track`
   - optional `album`
2. Code resolves primary `artistId`.
3. Code tries structured Spotify queries first:
   - `track:<title> artist:<artist> album:<album>`
   - `track:<title> artist:<artist>`
   - `artist:<artist> album:<album>`
4. Code keeps only candidates that still belong to the resolved `artistId`.
5. Code expands additional same-artist catalog candidates when needed.
6. LLM reranks the candidates.
7. Code enforces final ordering:
   - explicit track first when found
   - same album next
   - same artist additional tracks after that
   - for very short CJK titles, substring expansions such as `淡水河` for `河` are deferred behind normal same-artist catalog tracks

Current verified behavior:

- explicit target tracks are now stably inserted at position 1
- same-title tracks by other artists are no longer allowed to outrank the target
- short-title false positives no longer dominate the immediate follow-up positions after the exact hit

### 5.3 Album-only

Examples:

- `推荐谭咏麟《世外桃源》专辑里的歌曲`
- `推荐张雨生专辑《两伊战争白色才情》里的歌`
- `推荐张雨生《两伊战争白色才情》专辑里的歌曲`

Current flow:

1. LLM resolves or code repairs to:
   - `requestMode = ALBUM_ONLY`
   - `album`
   - optional `artist`
2. Candidate retrieval is constrained to the target album.
3. If artist is also known, candidates are narrowed to the dominant artist within that album result set.
4. If the user did not explicitly ask for additional tracks, expansion outside the album is disabled.

Current verified behavior:

- equivalent album-only phrasings now converge to the same result
- album-only requests no longer spill into other albums after the album tracks are exhausted

## 6. Theme-aware Retrieval

Current status:

- `THEME_AWARE` now exists as a request mode in the semantic normalization contract.
- A first retrieval pass is now implemented to support requests such as:
  - `给我来点90年代的粤语歌`
  - `来点适合雨天通勤的中文歌`

Current architecture for this first pass:

1. LLM extracts theme semantics rather than explicit entities.
2. LLM may provide `seedArtists` for broad requests where direct Spotify theme search is likely to be noisy.
   - Runtime code merges LLM seed artists with deterministic defaults for known regions / eras instead of replacing the defaults.
3. Code derives a `ThemeAwareProfile` and generates multiple theme-oriented search queries from:
   - the latest user message
   - semantic theme fields
   - language / era / scene / mood / genre hints
   - deterministic latest-message theme clues override invalid or overly generic LLM theme fields
4. Code resolves each seed artist to an `artistId` and expands a small catalog slice for that artist.
5. Spotify theme search supplements the seed-artist pool with broader candidates.
6. Candidate retrieval hit counts are retained for rerank and deterministic scoring.
7. LLM reranks candidates against the latest user message.
8. Code applies a theme-quality bucket before accepting the LLM order:
   - seed-artist theme search hits are preferred over generic artist catalog spillover
   - duplicate titles are deferred when alternatives exist
   - low-confidence theme candidates are not used to fill the playlist when higher-confidence candidates already exist
   - CJK-language theme candidates now require theme surface evidence, seed-artist evidence, or other strong local evidence
   - live/concert variants and generic non-song segments such as `Intro` or bare `90s` are downranked or excluded
9. LLM rerank receives explicit candidate evidence:
   - explicit title match
   - artist match
   - album match
   - theme quality bucket
   - low-confidence theme flag

This flow is less mature than the three entity-oriented flows above, but now has stricter guardrails against obvious query-spill candidates.

Known limitation:

- Without release-year and language metadata in the local `TrackDto`, theme matching still depends heavily on:
  - Spotify query quality
  - seed artist quality
  - candidate surface strings
  - LLM rerank quality
- As a result, requests such as `90s Cantonese songs` now route through a dedicated flow with better filtering, but result quality is still not yet at the same level as entity-constrained requests.

## 7. Spotify Retrieval Paths

The current chain uses these Spotify Web API surfaces:

- `GET /v1/search`
- `GET /v1/artists/{id}`
- `GET /v1/artists/{id}/albums`
- `GET /v1/albums/{id}`

### 7.1 Structured Search

Current supported structured fields:

- `track:<title>`
- `artist:<name>`
- `album:<name>`

Example:

- `track:发晕 artist:张雨生 album:两伊战争白色才情`

### 7.2 Artist Catalog Expansion

For `ARTIST_ONLY`, parts of `ENTITY_CONSTRAINED`, and theme seed artists, the chain no longer depends only on a shallow plain-text track search page.

Current approach:

1. resolve `artistId`
2. fetch artist album ids
3. expand album tracks
4. assemble a broader artist catalog candidate pool

For `THEME_AWARE`, this expansion is intentionally smaller per seed artist so the final pool can keep artist diversity.

Note:

- `GET /artists/{id}/top-tracks` was observed to return `403 Forbidden` under the current token path.
- For that reason, artist catalog expansion currently relies on:
  - artist albums
  - album tracks

## 8. Remaining Gaps

### Priority 1

1. Add richer theme-aware candidate evidence:
   - release year
   - language / locale clues
   - stronger theme filtering beyond title / artist / album surface evidence
   - artist locale / genre hints from Spotify artist metadata when available
2. Improve observability for:
   - recommendation spec extraction source
   - rerank source
   - repair / guardrail activation
3. Standardize playlist naming using deterministic structured naming rules.

### Priority 2

1. Expand alias and variant handling for album / title matching.
   - Current code already avoids substring false positives for short explicit titles such as `河`.
   - Current code accepts shorter album aliases when the shorter album name is still a strong substring of the requested album.
2. Add more metadata to recommendation debugging and acceptance tooling.
3. Normalize document encoding debt across root docs and backend docs.
