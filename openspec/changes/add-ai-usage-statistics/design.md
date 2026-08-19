## Context

AI Foundation wraps all four public model types with audited decorators, but the current recorder only discovers caller plugins in memory at method invocation. The runtime can perform more physical work than the consumer-visible call suggests: language generation can execute multiple model steps, embedding and image requests can split into batches, and provider calls can retry. Streaming also introduces subscription and cancellation semantics that a synchronous method-level recorder cannot represent.

The feature is operational, high-accuracy observability for super administrators, not a billing ledger. It must explain known consumption without double counting, preserve uncertainty, remain safe for sensitive model traffic, and avoid adding blocking JDBC work to reactive model paths. Research and source comparisons are captured in `docs/research/ai-usage-metering-best-practices.md`.

## Goals / Non-Goals

**Goals:**

- Count caller-visible logical SDK invocations and explain their model steps, batches, and retry attempts.
- Provide provider-neutral, coverage-aware token totals across language, embedding, reranking, and image generation.
- Support current summaries, trends, filtered call history, execution details, and storage health from a local SQLite database.
- Retain recent detail and long-lived daily aggregates with explicit temporal resolution.
- Keep model calls available when operational statistics degrade.
- Validate predictable behavior at 1 million logical calls and 5 million executions.

**Non-Goals:**

- Monetary cost, prices, quotas, billing durability, or exact provider invoice reconciliation.
- Prompt/output/tool tracing, arbitrary metadata indexing, end-user or session analytics.
- Multi-instance database sharing, CSV export, full-text search, or configurable retention in v1.

## Decisions

### 1. Separate logical calls from physical executions

Use two immutable-identity levels plus a derived aggregate:

```text
ai_call                         caller-visible SDK subscription
  └─ ai_model_execution         generation step, batch, or rerank unit
       └─ attempt_index         initial provider call or retry

ai_usage_daily                 rebuildable UTC aggregate
```

`ai_calls` drives call counts, history, and a denormalized terminal summary. `ai_model_executions` explains physical work and is the preferred token fact when execution usage exists. `ai_usage_daily` is derived and never overrides retained facts.

Alternative: one flat request-log table, as used by a local proxy. Rejected because one Halo SDK call can contain many provider requests, and a flat table cannot simultaneously give intuitive call history and retry-accurate usage without query-time reconstruction.

### 2. Capture caller synchronously and create identity on subscription

The audited decorator snapshots caller-plugin identity while the external plugin classloader is still on the stack. The reactive wrapper uses `defer`/subscription hooks to create a fresh `call_id`, start time, and statistics epoch for each execution. An unconsumed publisher creates no row.

Stream projections created from one `StreamTextResult` share a session object containing call identity and an atomic terminal guard. Completion, error, timeout, and Reactor cancellation compete to finalize the call once.

Alternative: record at method invocation. Rejected because it counts unconsumed publishers and cannot distinguish repeated cold subscriptions.

### 3. Instrument real provider attempt boundaries

Logical decorators cannot infer retry or batch facts. Add internal observation hooks around the actual language provider call, embedding batch call, reranking call, and image batch call. Each attempt has a stable identity enforced by:

```text
UNIQUE(call_id, unit_kind, unit_index, attempt_index)
```

The hook records only work that reaches the provider invocation boundary. A configured retry limit is never interpreted as an attempt count. Failed attempts without returned usage remain missing; the system does not guess whether the provider consumed tokens.

### 4. Use inclusive normalized token semantics

Stable nullable columns use 64-bit integers:

```text
input_tokens
output_tokens
cache_read_input_tokens
cache_creation_input_tokens
reasoning_output_tokens
provider_total_tokens
accounted_total_tokens
usage_quality
```

Input includes cache subsets and output includes reasoning. When input and output are both known, `accounted_total_tokens = input + output`; otherwise provider total is the fallback. Quality is one of `REPORTED_COMPONENTS`, `REPORTED_TOTAL`, `PARTIAL`, `ESTIMATED`, or `MISSING`. V1 does not perform local estimation, but the quality vocabulary prevents a future estimate from being confused with provider reports.

The parent call summary is denormalized from the selected authoritative child facts. Queries must choose one authoritative level per metric and interval; they never add parent and child token totals together.

### 5. Keep attribution bounded and historical identity immutable

Persist the detected caller plugin and detection source, audited operation, and an optional feature read from the existing `aifoundation.halo.run/feature` request metadata key. Validate feature as at most 64 lowercase ASCII letters, digits, `.`, `-`, or `_`; invalid values are ignored and reflected in diagnostics. Do not derive feature from class or method names because refactors and shared services make that dimension unstable.

Snapshot `model_name`, `provider_name`, `provider_type`, `request_model_id`, and the provider-reported `response_model_id`. Resource names remain identifiers, while provider type remains a separate descriptive dimension. Historical queries do not require current Extension resources.

### 6. Persist only approved operational metadata

The database excludes prompts, responses, embedding vectors, images and URLs, tools, headers, request context, arbitrary metadata, raw usage objects, and raw error bodies. Errors are reduced to a bounded category and sanitized code. This is a statistics database, not a content-tracing system.

### 7. Use SQLite WAL behind bounded blocking infrastructure

Store the database in a fixed plugin-specific directory derived from the Halo work directory. Resolve and validate the exact path during implementation against `PluginsRootGetter`; do not expose a configurable filesystem path.

Runtime shape:

```text
terminal/attempt events -> bounded queue -> one serialized writer
Console requests        -> small read pool -> short read transactions
                                      SQLite WAL
```

All JDBC runs on dedicated bounded threads, never Reactor event loops. Connections use a finite busy timeout. Transactions contain only SQL and precomputed values. Reader requests use one short transaction for a consistent response snapshot.

Statistics enqueue and writes are best effort with bounded retries. Overload or permanent failure does not fail the AI operation, but updates in-memory and durable health where possible. An affected call is incomplete rather than silently complete. Queue capacity and timeouts are selected from benchmarks rather than embedded in the product contract.

Alternative: one connection behind a global application lock. Rejected because server-side Console reads should not serialize behind every write and long shared critical sections increase model-path risk.

### 8. Model persistence as idempotent start and terminal commands

At subscription, enqueue a call-start command with all identity snapshots. At terminal signal, enqueue a command that conditionally transitions `IN_PROGRESS` once. The serialized writer normally preserves order; terminal persistence is still an upsert so a retried or delayed start cannot cause terminal loss.

Every command carries `statistics_epoch`. Reset atomically clears facts and aggregates and advances the epoch. Commands from an earlier epoch are discarded. Plugin startup marks prior-runtime `IN_PROGRESS` rows `ABANDONED`.

On shutdown, stop admission, drain for a finite timeout, record remaining loss, checkpoint as appropriate, and close the pool, writer, scheduler, and database. Never wait without a bound.

### 9. Use three storage groups with schema versioning

Conceptual `ai_calls` fields include identity/epoch, caller and model snapshots, operation/model type, start/completion/duration, streaming flag, terminal status, sanitized error, counts, normalized usage summary, missing-execution count, and completeness.

Conceptual `ai_model_executions` fields include call identity, unit kind/index, attempt index, timestamps, status, request/response model, normalized usage, quality, and sanitized error.

`ai_usage_daily` is keyed by UTC date plus caller, feature, provider resource, provider type, model, model type, operation, status, and usage quality. It stores call/status counts, token sums, usage-quality counts, duration aggregates, and completeness indicators.

Additional metadata tables store schema version, statistics epoch, rollup watermark, and health/recovery information. Concrete DDL remains internal and is managed through ordered forward migrations.

### 10. Retain detail with non-overlapping rollups

- Logical calls: 90 days.
- Execution detail: 30 days.
- Daily rollups: indefinite in v1.

A rollup job recomputes complete UTC days after a safety delay. Aggregate upsert and eligible source deletion commit in the same transaction. A failed transaction is safe to retry.

Summary planning selects non-overlapping sources. Recent sub-day token trends use execution facts; call counts use call facts. Closed historical days use rollups. While call detail remains but execution detail has expired, call history remains available but token trends outside execution retention disclose day resolution. After call detail expires, list/detail endpoints do not synthesize records.

Every date range is UTC and half-open: `[from, to)`. Console local dates are converted at the API boundary.

### 11. Provide purpose-specific administrator APIs

Expose separate Console operations for summary, trends, cursor-paginated calls, call detail, health, and reset. They accept only documented filters and do not expose SQL or a generic grouping DSL. An opaque list cursor binds the filter fingerprint and last `(started_at, id)` tuple. Default range is 30 days; the server enforces a bounded range appropriate to the selected resolution.

Summary responses include status counts, normalized token totals and subsets, known/missing usage counts, coverage, resolution, authoritative interval, and completeness. Only super administrators can read, inspect health, or reset data.

The Vue Console uses the generated OpenAPI client and Chinese copy. The primary history row is a logical call; its execution children are expandable.

### 12. Treat backup and migration failures as visible degradation

WAL databases are backed up through SQLite Online Backup or an equivalent consistent snapshot, not by copying only the main file. Validated timestamped snapshots live beside the plugin database under `backups/`, retain the newest two, and request a passive checkpoint after publication. Startup may restore the newest valid snapshot only after preserving an invalid live database and its sidecars under `backups/corrupted/`.

Halo 2.25 has no backend extension point that lets a plugin participate atomically in a full-site backup; `backup:tabs:create` extends only the Console UI. The core backup copies the Halo work directory, including the plugin directory, so the plugin-owned validated snapshots are included as ordinary files. Recovery therefore treats those snapshots, rather than an uncoordinated copy of the live WAL database alone, as the portable restore source.

Schema migration first obtains a recoverable snapshot. If migration or integrity validation fails without a validated recovery candidate, preserve or quarantine the original database, disable statistics, and expose health details while model services continue. Never silently replace failed history with an empty healthy database.

### 13. Prove indexes and limits at target scale

Begin with indexes supporting chronological keyset pagination and the confirmed caller/model filters. Add compound or partial indexes only after representative `EXPLAIN QUERY PLAN` and benchmark evidence. Validate at 1 million calls and 5 million executions, reporting database/WAL size, write queue behavior, retention and rollup duration, backup/restore duration, and p95 query latency against the spec thresholds.

## Risks / Trade-offs

- [SQLite is single-host and single-writer] -> Declare single-instance scope, keep writes short, use WAL locally, and revisit the storage engine before supporting HA.
- [Provider failures may consume unreported tokens] -> Preserve missing/partial quality and coverage; never describe operational totals as billing-exact.
- [Async telemetry can be lost] -> Bound retries, expose loss intervals and counts, and mark affected summaries incomplete.
- [Streaming can emit competing terminal signals] -> Share one call session and guard terminal state atomically and in SQL.
- [Long readers can delay WAL checkpoints] -> Use bounded ranges, cursor pagination, short read transactions, and monitor WAL/checkpoint health.
- [Daily rollup can double-count raw data] -> Use explicit source intervals, transactional rollup/deletion, and boundary-focused tests.
- [Feature metadata can become high-cardinality] -> Restrict it to one reserved validated value and omit invalid or absent features.
- [Large deletes do not immediately shrink the file] -> Measure incremental vacuum/checkpoint policy under realistic churn before enabling maintenance.
- [SQLite JDBC packaging and plugin reload can leak native/classloader resources] -> Add packaged-JAR and repeated reload tests that assert all threads and connections terminate.

## Migration Plan

1. Add the SQLite dependency, database-path resolver, schema versioning, initial DDL, health state, and lifecycle-managed connection infrastructure behind no Console route.
2. Add call-session instrumentation and real provider-attempt hooks with focused lifecycle, retry, batch, stream-cancel, and privacy tests.
3. Add repositories, retention, rollups, reset epoch, consistent backup integration, and recovery tests.
4. Add administrator endpoints and regenerate the TypeScript API client.
5. Add the Chinese Console statistics view and API/component tests.
6. Run target-scale benchmarks, tune measured indexes and bounds, and record the reference environment and results.
7. Exercise plugin reload, abrupt-stop recovery, migration failure, backup/restore, corruption handling, and data-loss health reporting before enabling the route by default.

Rollback disables statistics endpoints and event admission before reverting application code. Existing database files are retained rather than downgraded or deleted automatically.

## Open Questions

There are no unresolved product decisions. Implementation must validate these technical facts before declaring the change complete:

- Whether a future Halo release adds a backend backup hook that can request a fresh plugin snapshot before packaging the work directory.
- The stream-result ownership mechanism needed to share one call session across every projection.
- The complete set of provider-attempt interception points and which failed paths can return usage.
- Queue capacity, busy timeout, read-pool size, checkpoint cadence, and any incremental-vacuum policy from benchmark evidence.
