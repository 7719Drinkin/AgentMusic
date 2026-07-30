# AgentMusic LLM Planning Harness

## Goal

Before enabling real LLM-driven planning in the main chat path, the model must be constrained to a strict contract that the backend can validate deterministically.

This harness defines:

- the input envelope passed into the model
- the exact JSON output contract
- the allowed plan templates
- the server-side validation rules

## Input Contract

The model receives one serialized JSON payload built from the live request context:

```json
{
  "schemaVersion": "agentmusic.plan.v1",
  "userId": "demo-user",
  "voiceInput": false,
  "latestUserMessage": "来点适合雨天通勤的中文歌",
  "recentMessages": [
    "上一轮生成的是粤语歌单",
    "当前在通勤场景"
  ],
  "allowedIntents": [
    "CHAT_ONLY",
    "TRACK_LOOKUP",
    "ARTIST_LOOKUP",
    "RECOMMEND_PLAYLIST",
    "PLAY_RECOMMENDATION",
    "PLAYLIST_HISTORY_ACCESS",
    "PLAYBACK_CONTROL",
    "COMPOSITE_REQUEST",
    "UNKNOWN"
  ],
  "allowedStepTypes": [
    "READ_USER_PREFERENCES",
    "READ_CHAT_CONTEXT",
    "READ_LOCAL_SESSION",
    "READ_PLAYLIST_HISTORY",
    "LOOKUP_TRACK",
    "SEARCH_TRACKS",
    "LOOKUP_ARTIST",
    "GENERATE_RECOMMENDATION_CANDIDATES",
    "RANK_RECOMMENDATION_CANDIDATES",
    "CREATE_RECOMMENDATION_PLAYLIST",
    "UPDATE_PLAYBACK_STATE",
    "PERSIST_CHAT_REPLY"
  ]
}
```

## Output Contract

The model must return exactly one JSON object:

```json
{
  "schemaVersion": "agentmusic.plan.v1",
  "intent": "PLAY_RECOMMENDATION",
  "summary": "Build a recommendation playlist first, then start playback.",
  "reasoning": "The user asked for songs and expects immediate playback.",
  "confidence": 91,
  "steps": [
    { "type": "READ_CHAT_CONTEXT", "arguments": { "limit": 20 } },
    { "type": "READ_USER_PREFERENCES", "arguments": {} },
    { "type": "READ_PLAYLIST_HISTORY", "arguments": { "limit": 10 } },
    { "type": "GENERATE_RECOMMENDATION_CANDIDATES", "arguments": { "query": "来点适合雨天通勤的中文歌" } },
    { "type": "RANK_RECOMMENDATION_CANDIDATES", "arguments": { "query": "来点适合雨天通勤的中文歌" } },
    { "type": "CREATE_RECOMMENDATION_PLAYLIST", "arguments": { "query": "来点适合雨天通勤的中文歌" } },
    { "type": "UPDATE_PLAYBACK_STATE", "arguments": { "query": "来点适合雨天通勤的中文歌" } },
    { "type": "PERSIST_CHAT_REPLY", "arguments": {} }
  ]
}
```

## Hard Rules

1. `schemaVersion` must match the active harness version.
2. Only listed `intent` values are allowed.
3. Only listed `PlanStepType` values are allowed.
4. `READ_CHAT_CONTEXT` must always be first.
5. `PERSIST_CHAT_REPLY` must always be last.
6. The full step array must match the exact template for the selected intent.
7. Query-driven steps must contain only a non-empty `query`.
8. Read-history / read-context steps must contain only a positive integer `limit`.
9. Session / preference / persist steps must use an empty arguments object.
10. Unknown extra JSON fields are rejected server-side.

## Supported Templates

- `CHAT_ONLY` -> `READ_CHAT_CONTEXT -> PERSIST_CHAT_REPLY`
- `TRACK_LOOKUP` -> `READ_CHAT_CONTEXT -> LOOKUP_TRACK -> PERSIST_CHAT_REPLY`
- `ARTIST_LOOKUP` -> `READ_CHAT_CONTEXT -> LOOKUP_ARTIST -> PERSIST_CHAT_REPLY`
- `RECOMMEND_PLAYLIST` -> `READ_CHAT_CONTEXT -> READ_USER_PREFERENCES -> READ_PLAYLIST_HISTORY -> GENERATE_RECOMMENDATION_CANDIDATES -> RANK_RECOMMENDATION_CANDIDATES -> CREATE_RECOMMENDATION_PLAYLIST -> PERSIST_CHAT_REPLY`
- `PLAY_RECOMMENDATION` -> `READ_CHAT_CONTEXT -> READ_USER_PREFERENCES -> READ_PLAYLIST_HISTORY -> GENERATE_RECOMMENDATION_CANDIDATES -> RANK_RECOMMENDATION_CANDIDATES -> CREATE_RECOMMENDATION_PLAYLIST -> UPDATE_PLAYBACK_STATE -> PERSIST_CHAT_REPLY`
- `PLAYLIST_HISTORY_ACCESS` -> `READ_CHAT_CONTEXT -> READ_PLAYLIST_HISTORY -> PERSIST_CHAT_REPLY`
- `PLAYBACK_CONTROL` -> `READ_CHAT_CONTEXT -> READ_LOCAL_SESSION -> UPDATE_PLAYBACK_STATE -> PERSIST_CHAT_REPLY`
- `COMPOSITE_REQUEST` -> `READ_CHAT_CONTEXT -> SEARCH_TRACKS -> UPDATE_PLAYBACK_STATE -> PERSIST_CHAT_REPLY`
- `UNKNOWN` -> `READ_CHAT_CONTEXT -> PERSIST_CHAT_REPLY`

## Current Status

This harness is implemented and covered by unit tests.

OpenAI-compatible base URL is configurable through `openai.base-url`, so Kimi-compatible endpoints can be wired without changing code.

It is now wired into the main planner path through `LlmBackedTaskPlanner`:

- validated LLM output becomes the primary planning result
- `SimpleTaskPlanner` remains the fallback path
- planner source and fallback status are surfaced through reply metadata

## Validation Workflow

Use the following sequence to validate whether a real LLM output is acceptable:

1. Call the OpenAI-compatible chat-completions endpoint in shadow mode.
2. Extract only `choices[0].message.content`.
3. Pass the raw content into `AgentLlmPlanningHarness.parseAndValidate(...)`.
4. Treat the result as accepted only if:
   - JSON parsing succeeds
   - schema version matches
   - intent is in the whitelist
   - step sequence matches the exact template
   - argument objects pass field-level validation
   - a valid `AgentPlan` is returned
5. Treat any parsing failure or validation failure as a rejected output and fall back to `SimpleTaskPlanner`.

For local manual verification, use:

```powershell
cd E:\Course_Design\AgentMusic\agentmusic-backend
mvn -q "-Dmaven.resources.skip=true" test-compile
java -cp "<test-and-dependency-classpath>" com.agentmusic.agentmusic_backend.LiveLlmPlanningSmokeRunner
```

The runner uses the local `application-local.properties` and validates a real recommendation-plus-playback request against the active harness.

## Acceptance Criteria

When evaluating a real provider such as Kimi, the quality gate is not "the answer looks reasonable". The quality gate is:

- raw JSON parse success rate
- harness validation pass rate
- `AgentPlan` conversion success rate
- downstream planner execution success rate

Only outputs that pass all four checks should be considered eligible for main-path rollout.

## Latest Manual Smoke Result

Latest manual live smoke against the configured Kimi-compatible endpoint now shows:

- the provider returns structured JSON that matches the requested schema shape
- the recommendation-plus-playback path can pass the strict harness end to end
- repeated shadow runs for the same prompt produced:
  - `intent=PLAY_RECOMMENDATION`
  - the exact `PLAY_RECOMMENDATION` step template
  - valid `limit` and `query` arguments for every required step

This means the harness is now doing the right job for the main recommendation path:

- invalid output is still rejected
- compliant output is accepted and converted into `AgentPlan`

The next implementation step is no longer prompt contract design. The next step is validating the real running chat endpoint under provider rate limits and confirming that `planningSource=llm-harness` is stable in normal traffic.
