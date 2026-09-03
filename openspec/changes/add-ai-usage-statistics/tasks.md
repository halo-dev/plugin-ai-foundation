## 1. SQLite Foundation and Schema

- [x] 1.1 Add the repository-pinned SQLite JDBC dependency and verify the driver is packaged and loadable from the built plugin JAR.
- [x] 1.2 Resolve and test a fixed plugin-owned database directory beneath the Halo work directory without exposing a path setting.
- [x] 1.3 Define versioned DDL for statistics metadata, logical calls, model executions, daily usage, health state, and the initial evidence-backed indexes.
- [x] 1.4 Implement transactional forward migrations with pre-migration consistent backup and tests for fresh creation, upgrade, rollback-on-error, and preserved source files.
- [x] 1.5 Configure WAL, foreign keys, finite busy timeouts, integrity checks, and lifecycle-managed writer/read connections.

## 2. Usage Domain and Privacy Contract

- [x] 2.1 Define internal call, execution, status, usage-quality, normalized-token, completeness, and statistics-epoch domain types using nullable 64-bit usage fields.
- [x] 2.2 Implement and test inclusive token normalization, provider-total fallback, partial/missing classification, coverage, and overflow-safe aggregation.
- [x] 2.3 Implement bounded sanitized error classification and tests proving prompts, outputs, tools, headers, arbitrary metadata, raw usage objects, and provider bodies are never persisted.
- [x] 2.4 Read and validate the optional `aifoundation.halo.run/feature` value from existing request metadata while keeping absent or invalid features unknown.
- [x] 2.5 Snapshot caller, detection source, model resource, provider resource, provider type, requested model ID, and response model ID without depending on live Extensions during history reads.

## 3. Asynchronous Persistence and Health

- [x] 3.1 Implement a dedicated bounded statistics scheduler, queue, serialized writer, and small read pool with no JDBC work on WebFlux event-loop threads.
- [x] 3.2 Implement idempotent start, execution-attempt, and terminal upsert commands with epoch and unique-identity enforcement.
- [x] 3.3 Implement finite enqueue/write retries and overload handling that preserves model results while tracking dropped events, incomplete calls, write failures, queue depth, and affected intervals.
- [x] 3.4 Implement plugin startup reconciliation for stale `IN_PROGRESS` calls and finite stop/reload draining, checkpointing, and complete resource shutdown.
- [x] 3.5 Add concurrency and fault-injection tests for duplicate commands, reordered start/terminal writes, SQLite busy responses, queue saturation, writer failure, and repeated plugin lifecycle cycles.

## 4. Logical Call Instrumentation

- [x] 4.1 Refactor audited model wrappers to snapshot caller identity synchronously but create one logical call per actual reactive subscription.
- [x] 4.2 Finalize non-streaming language, embedding, reranking, and image calls exactly once for success, failure, timeout, and cancellation.
- [x] 4.3 Introduce a shared streaming call session so every projection of one underlying stream shares identity, usage, and an atomic terminal guard.
- [x] 4.4 Add tests for unsubscribed publishers, repeated cold subscriptions, concurrent terminal signals, stream cancellation, unknown callers, and calls completing after a statistics reset.

## 5. Physical Execution Instrumentation

- [x] 5.1 Instrument actual language provider attempts and multi-step generation boundaries without deriving attempts from retry configuration.
- [x] 5.2 Instrument embedding batches and each actual retry attempt, including concurrent batches and partial failure.
- [x] 5.3 Instrument reranking provider execution and any retry path with the same execution identity and usage contract.
- [x] 5.4 Instrument image-generation batches and each actual retry attempt, including split requests and partial failure.
- [x] 5.5 Aggregate authoritative execution usage into logical-call summaries without parent/child double counting, preserving partial and missing attempts.
- [x] 5.6 Add focused tests for first-attempt success, retry success, exhausted retries, failed attempts with and without usage, multi-step language calls, and concurrent batch ordering.

## 6. Retention, Rollups, and Query Repositories

- [x] 6.1 Implement UTC half-open time-range primitives and non-overlapping source planning for raw execution, logical-call, and daily-rollup intervals.
- [x] 6.2 Implement idempotent complete-day rollup with a safety delay and atomic aggregate replacement plus eligible-detail deletion.
- [x] 6.3 Enforce fixed v1 retention of 30-day execution detail, 90-day logical-call detail, and indefinite daily rollups.
- [x] 6.4 Implement summary and trend queries with status counts, inclusive token subsets, known/missing counts, coverage, resolution, authoritative intervals, and completeness.
- [x] 6.5 Implement stable keyset-paginated logical-call history and call detail with execution children and confirmed filters.
- [x] 6.6 Implement atomic statistics reset with confirmation validation and epoch advancement.
- [x] 6.7 Add boundary tests for UTC midnight, daylight-saving Console inputs, partial-day ranges, retention cutoffs, rollup retry, reset races, and prevention of raw/rollup double counting.

## 7. Backup and Recovery

- [ ] 7.1 Publish validated SQLite-consistent snapshots inside the plugin work directory so Halo full-site backup includes recoverable plugin state; document that Halo 2.25 has no backend plugin backup hook.
- [x] 7.2 Validate restored schema version and integrity before enabling statistics access.
- [x] 7.3 Preserve or quarantine a damaged database and disable only statistics when migration or integrity validation fails; never silently create an empty healthy replacement.
- [x] 7.4 Add end-to-end backup, restore, migration-failure, corrupt-database, WAL-state, and recovery-health tests.

## 8. Administrator APIs and Generated Client

- [x] 8.1 Define server-side validated request and response models for summary, trends, call list, call detail, health, and reset, including bounded date ranges and opaque cursor/filter binding.
- [x] 8.2 Implement purpose-specific Console endpoints with the super-administrator permission boundary and no generic SQL or grouping DSL.
- [x] 8.3 Add endpoint tests for every filter, unknown/partial usage, disclosed resolution, incomplete data, cursor stability, invalid feature/date/cursor input, reset confirmation, and unauthorized access.
- [x] 8.4 Regenerate the TypeScript API client with `./gradlew generateApiClient` and verify no generated files were hand-edited.

## 9. Console Statistics Experience

- [x] 9.1 Add the Chinese-language super-administrator statistics route and navigation entry using the generated client.
- [x] 9.2 Implement total calls and token summaries, usage coverage/completeness, status breakdown, and UTC-aware trend controls.
- [x] 9.3 Implement caller, feature, provider, model, model type, operation, status, usage-quality, and bounded date filters.
- [x] 9.4 Implement cursor-paginated logical-call history with expandable execution details and explicit unknown, partial, cancelled, timed-out, and abandoned states.
- [x] 9.5 Implement persistent storage-health and data-loss warnings plus a confirmed statistics-reset flow.
- [x] 9.6 Add frontend type checks, lint, focused component/composable tests, and Console interaction coverage for loading, empty, error, degraded, pagination, filter, detail, and reset states.

## 10. Scale and Release Verification

- [x] 10.1 Build a deterministic representative benchmark dataset with at least 1,000,000 logical calls and 5,000,000 executions across the confirmed dimensions and usage qualities.
- [ ] 10.2 Measure enqueue latency, common filtered first-page queries, 90-day summaries/trends, deep cursor traversal, rollup/retention, database and WAL size, and backup/restore on a documented reference environment.
- [ ] 10.3 Use `EXPLAIN QUERY PLAN` and measured results to add or revise only necessary compound, covering, or partial indexes and rerun the complete benchmark.
- [ ] 10.4 Verify enqueue p95 at or below 1 ms, common list p95 at or below 200 ms, and 90-day aggregate p95 at or below 500 ms, or record and resolve the failed acceptance criterion.
- [x] 10.5 Run backend tests, frontend lint/type checks/tests, packaged-JAR SQLite verification, repeated Halo reload, abrupt-stop recovery, and the full Gradle build.
- [ ] 10.6 Update administrator and consumer documentation for metric definitions, coverage, privacy, retention, feature metadata, historical resolution, backup, reset, health, and single-instance limitations.

## 11. Review Remediation

- [x] 11.1 Preserve execution usage for provider attempts that cross UTC day boundaries and add a retention regression test.
- [x] 11.2 Execute aggregate reads against one SQLite snapshot and add concurrent-write consistency coverage.
- [x] 11.3 Start execution telemetry only at the actual provider invocation boundary and cover pre-invocation cancellation.
- [x] 11.4 Persist the full affected interval and compute aggregate completeness for the requested time range.
- [ ] 11.5 Verify and document the Halo backup/restore integration boundary instead of claiming an unverified extension point.
- [ ] 11.6 Check in the required 1M-call/5M-execution benchmark harness, report, query plans, and operator documentation.
- [x] 11.7 Remove unused APIs and return values, centralize operation/feature domains, and split SQLite query and maintenance responsibilities.
- [x] 11.8 Regenerate clients, run backend and frontend verification, and perform a final diff review.
