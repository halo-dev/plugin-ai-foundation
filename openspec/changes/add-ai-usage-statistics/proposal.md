## Why

AI Foundation currently knows which plugin resolves and invokes a model, but it does not retain per-call outcomes or provider-reported usage. Super administrators therefore cannot see total token consumption, identify callers or models responsible for usage, or investigate failed, retried, batched, and multi-step executions.

## What Changes

- Separate provider protocol normalization, runtime observations, and asynchronous statistics storage. Observation callback and storage failures must not replace model results or make calls wait for persistence.
- Record one logical call per subscribed language, embedding, reranking, or image-generation invocation, with underlying steps, batches, and provider retry attempts counted without duplication.
- Preserve unknown and partial usage rather than fabricating zero values; distinguish provider usage coverage from event-delivery completeness. Do not estimate missing token counts.
- Retain per-call statistical facts in a plugin-owned SQLite database for precise historical queries. Remove UTC daily rollups and automatic expansion of requested time ranges.
- Store UTC instants; use the browser's IANA time zone for Console date selection, trend grouping, heatmap days, and display. APIs default to UTC when no time zone is supplied.
- Keep public call history and details within the last 90 days; clean up execution details older than the 30-day UTC-date cutoff, except those belonging to ongoing calls. The 90-day access window does not delete call statistics.
- Add super-administrator APIs and a Chinese Console view for summaries, trends, breakdowns, cursor-paginated history, details, health, and confirmed reset. Do not add a historical-range warning banner.
- Keep database ownership simple: initialize or validate, use bounded asynchronous writes, clean up execution details, and close resources. Corruption disables statistics for manual handling; no automatic backup, restore, file replacement, or reconstruction.
- **BREAKING (behavioral, not API signatures):** normalized usage is also returned through existing SDK responses. Messages input includes cache reads and creation; some previously zero-filled missing counters become unknown. Non-streaming delegate/middleware assembly runs on subscription, so repeated subscriptions can invoke middleware again.

## Capabilities

### New Capabilities

- `ai-usage-statistics`: Provider-neutral operational metering, precise time-zone-aware historical queries, retained call facts, administrative health/reset operations, and Console presentation.

### Modified Capabilities

None. The compatibility effects on existing SDK consumers are documented under Impact and in the design; this change introduces only the statistics capability spec.

## Non-goals

- Calculating prices, enforcing quotas, or providing a billing-grade, lossless ledger or exact provider-invoice reconciliation.
- Storing prompts, outputs, tool arguments, headers, arbitrary metadata, raw provider payloads, or error messages; tracking end users or sessions.
- Sharing one SQLite database across multiple Halo instances.
- Automatic backup/restore, corruption repair, daily rollup storage, or a historical-range warning banner.
- CSV export, full-text search, administrator-configurable retention, or a hard bound on the lifetime size of the database in the first release.

## Impact

- **Backend and Console UI:** this is a full-stack feature. Existing model selection, provider configuration, Secret references, and cross-plugin service discovery remain in place.
- **Java/npm SDK:** public types and method/request shapes remain unchanged and provider-neutral. An optional validated feature tag is read from existing metadata. However, consumers using the pre-change usage semantics or middleware assembly timing can observe the behavioral changes above; signature compatibility is not a claim of complete behavioral compatibility.
- **Compatibility and release handling:** existing consumers of the released Java SDK (the project has shipped since v1.0.0) need review if they manually add Messages cache counters, treat absent usage as zero, or rely on middleware assembly occurring once before subscription. The release first containing this change must document these changes and the consumer adjustments; its release number has not yet been assigned here. No persisted Halo Extension migration is required.
- **app:** provider usage normalization, audited decorators, execution observation, SQLite storage/lifecycle, execution retention, and Console endpoints. Only new statistics paths and schemas are added; existing endpoint contracts remain unchanged.
- **Generated contracts:** regenerate OpenAPI and the TypeScript client after endpoint changes; never edit generated files manually.
- **Build/runtime:** adds SQLite JDBC and plugin-owned persistent files under the Halo work directory. Statistics schema v1 is new and unreleased; development databases are not migrated. Future released schema changes require explicit transactional migrations.
- **Operations and performance:** bounded queues protect model calls from waiting on database writes, but retained call facts and indexes grow over time. Startup integrity checks and analytical queries have size-dependent costs. Reset clears logical data without promising immediate file shrinkage. The opt-in million-call benchmark has not met its existing 500 ms aggregate P95 target; passing normal CI does not imply that performance target passed.
