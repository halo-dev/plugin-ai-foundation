## ADDED Requirements

### Requirement: Logical AI calls are recorded at execution time
The system SHALL record one logical call for each subscribed language generation, language streaming, embedding, reranking, or image-generation SDK operation. Constructing an unconsumed reactive result SHALL NOT create a call, while independent subscriptions that execute the model independently SHALL create independent calls.

#### Scenario: Unsubscribed reactive result
- **WHEN** a consumer obtains a reactive model result but never subscribes to it
- **THEN** the system does not increase the logical call count

#### Scenario: Independent subscriptions
- **WHEN** a cold model publisher is subscribed twice and performs two model executions
- **THEN** the system records two logical calls with distinct identities

### Requirement: Calls have an exactly-once terminal lifecycle
The system SHALL create a subscribed call as `IN_PROGRESS` and SHALL transition it at most once to `SUCCEEDED`, `FAILED`, `TIMED_OUT`, `CANCELLED`, or `ABANDONED`. A streaming session exposed through multiple result projections SHALL share one call identity and terminal transition.

#### Scenario: Downstream cancels a stream
- **WHEN** a subscriber cancels an active streaming generation before normal completion
- **THEN** the system records the logical call as `CANCELLED` exactly once

#### Scenario: Stale call after restart
- **WHEN** the plugin starts with an `IN_PROGRESS` record left by a previous runtime instance
- **THEN** the system marks that record `ABANDONED` without counting a new call

### Requirement: Physical model executions are explainable
The system SHALL record actual generation steps, embedding batches, reranking executions, image batches, and provider retry attempts beneath their logical call. It SHALL derive attempt counts from actual provider invocation boundaries rather than configured retry limits, and SHALL prevent duplicate execution identity from adding usage twice.

#### Scenario: Retry succeeds
- **WHEN** an initial provider attempt fails and a retry succeeds
- **THEN** the system records two executions and records the logical call as `SUCCEEDED`

#### Scenario: Multi-step generation
- **WHEN** a language call performs multiple model steps around tool execution
- **THEN** each model step is independently visible beneath one logical call

### Requirement: Token totals use a provider-neutral inclusive contract
The system SHALL treat input tokens as inclusive of cache-read and cache-creation input tokens and output tokens as inclusive of reasoning output tokens. It SHALL calculate accounted total tokens as input plus output when both are known, otherwise use a reported provider total when available, and SHALL NOT add cache or reasoning subsets again.

#### Scenario: Cached reasoning response
- **WHEN** a provider reports input, output, cache-read input, and reasoning output tokens
- **THEN** accounted total equals input plus output and the cache and reasoning values remain informational subsets

#### Scenario: Provider reports only total
- **WHEN** a provider reports a total token count without input and output components
- **THEN** the system uses the reported total and identifies the usage as total-only

### Requirement: Missing and partial usage remain explicit
The system SHALL preserve unavailable usage values as unknown rather than zero. It SHALL classify usage as reported components, reported total, partial, estimated, or missing, and aggregate responses SHALL expose known and missing counts plus usage coverage.

#### Scenario: Provider omits usage
- **WHEN** a successful provider response contains no usable token counts
- **THEN** the call remains `SUCCEEDED`, token values remain unknown, and missing usage reduces the reported coverage

#### Scenario: Later step fails without usage
- **WHEN** completed steps report usage but a later step fails without reporting usage
- **THEN** the system retains the known consumption, marks the call usage partial, and reports the number of missing executions

### Requirement: All reported consumption is counted independently of outcome
The system SHALL include known provider-reported usage from successful, failed, timed-out, cancelled, and retried executions in total consumption. It SHALL expose status as an independent filter so administrators can request success-only views.

#### Scenario: Failed attempt reports usage
- **WHEN** a failed provider attempt reports token consumption
- **THEN** that consumption contributes to the unfiltered total and remains attributable to the failed status

### Requirement: Caller and operation attribution avoid API shape changes
The system SHALL snapshot the caller plugin from the existing classloader-aware stack detection before asynchronous thread changes, SHALL record the audited SDK operation, and SHALL record an optional feature only from the validated `aifoundation.halo.run/feature` key in existing request metadata. It SHALL NOT persist other arbitrary request metadata.

#### Scenario: Caller provides no feature
- **WHEN** the caller plugin is detected but the reserved metadata key is absent
- **THEN** the call is attributed to the plugin and SDK operation with an unknown feature

#### Scenario: Caller cannot be detected
- **WHEN** stack inspection cannot identify a caller plugin
- **THEN** the call is retained in an unknown-caller bucket with its detection source

### Requirement: Historical resource identity is immutable
The system SHALL snapshot the Halo model resource name, provider resource name, provider type, requested provider model ID, and response model ID when available. Historical results SHALL remain understandable after current model or provider resources are changed or deleted.

#### Scenario: Provider resource is deleted
- **WHEN** an administrator views a call after its provider resource has been deleted
- **THEN** the API returns the stored provider and model identity snapshot without requiring the deleted resource

### Requirement: Sensitive model content is excluded
The system SHALL NOT store prompts, completions, embeddings, generated image content or URLs, tool arguments or results, request headers, arbitrary metadata, raw provider payloads, or secret-bearing error messages in the statistics database.

#### Scenario: Call contains sensitive content
- **WHEN** a model request and response contain user content and provider metadata
- **THEN** the persisted statistics contain only approved identity, status, timing, usage, and sanitized error classification fields

### Requirement: Statistics persistence is isolated from model availability
The system SHALL perform SQLite work away from WebFlux event-loop threads through bounded asynchronous infrastructure. Transient persistence failures SHALL be retried within finite limits, and terminal statistics failure or overload SHALL NOT fail the model call but SHALL make data loss visible.

#### Scenario: Statistics queue is full
- **WHEN** a statistics event cannot be admitted within the configured finite enqueue policy
- **THEN** the model call continues and statistics health reports the affected incomplete call or dropped event

#### Scenario: SQLite is unavailable
- **WHEN** the statistics database cannot accept writes
- **THEN** model APIs remain available and the statistics health endpoint reports the failure and affected time

### Requirement: SQLite lifecycle is reload-safe and local
The system SHALL use a plugin-owned SQLite database on a local Halo work-directory path, use WAL with serialized short writes and bounded readers, and close its queue, schedulers, connections, and WAL resources during plugin stop or reload within a finite drain timeout.

#### Scenario: Plugin reload
- **WHEN** Halo reloads the plugin while statistics work is queued
- **THEN** the plugin stops accepting new statistics work, drains for a finite interval, records any loss, and releases all database and thread resources

### Requirement: Detail retention and daily rollups do not overlap
The system SHALL retain logical call detail for 90 days, execution detail for 30 days, and UTC daily aggregates indefinitely in the first release. Daily aggregation SHALL be idempotent, SHALL cover only complete UTC days after a safety delay, and SHALL commit aggregate replacement and eligible detail deletion atomically.

#### Scenario: Rollup transaction fails
- **WHEN** daily rollup or eligible detail deletion fails before commit
- **THEN** neither partial rollup replacement nor partial deletion is visible and the day can be retried safely

#### Scenario: Query spans rolled-up and raw time
- **WHEN** a summary spans historical rollups and retained raw data
- **THEN** the system combines non-overlapping intervals so no call or token usage is counted twice

### Requirement: Historical precision is disclosed
The system SHALL use UTC half-open time intervals and SHALL disclose the resolution and detail availability of every aggregate response. It SHALL NOT fabricate sub-day detail after the authoritative execution or call facts for that interval have expired.

#### Scenario: Sub-day query exceeds detail retention
- **WHEN** an administrator requests hourly token statistics for an interval with only daily rollups
- **THEN** the API reports day resolution or rejects the unavailable precision instead of synthesizing hourly values

### Requirement: Administrative statistics APIs are purpose-specific
The system SHALL expose super-administrator endpoints for summary, trend, cursor-paginated call list, call detail, statistics health, and explicit reset. List and aggregate endpoints SHALL support bounded date filtering plus caller, feature, provider, model, model type, operation, status, and usage-quality filters where applicable.

#### Scenario: Filtered call history
- **WHEN** a super administrator requests calls for a bounded date range, caller plugin, model, and status
- **THEN** the API returns a stable page ordered by start time and identity with an opaque next cursor

#### Scenario: Non-administrator requests statistics
- **WHEN** a caller without the super-administrator boundary requests a statistics endpoint
- **THEN** access is denied

### Requirement: Reset creates a new statistics epoch
The system SHALL require explicit administrator confirmation to reset statistics, SHALL atomically remove calls, executions, and daily rollups, and SHALL create a new statistics epoch so late terminal events from the prior epoch cannot repopulate cleared history.

#### Scenario: Old call finishes after reset
- **WHEN** a model call created in the previous statistics epoch finishes after an administrator reset
- **THEN** its terminal event does not create data in the new epoch

### Requirement: Statistics health exposes completeness
The system SHALL expose queue depth, dropped events, incomplete calls, write failures, last write error time, migration or integrity failures, and the affected interval. Summary responses SHALL indicate when their totals may be incomplete.

#### Scenario: A persistence event is lost
- **WHEN** any statistics event is dropped or permanently fails to persist
- **THEN** the Console displays a persistent warning and affected aggregate responses do not claim complete data

### Requirement: Backup, migration, and corruption do not silently discard history
The system SHALL create and validate SQLite-consistent backup snapshots, retain the newest two, version its schema, and preserve the original database when migration fails. It MAY restore the newest validated snapshot when the live database is missing or unrecognized only after preserving invalid live database evidence; it SHALL NOT create an empty healthy replacement when storage evidence exists. Statistics failure SHALL be isolated from model service startup.

#### Scenario: Migration fails during plugin upgrade
- **WHEN** a forward statistics schema migration cannot complete
- **THEN** the model service remains available, statistics enter an unhealthy state, and the original database remains available for recovery

#### Scenario: Database integrity check fails
- **WHEN** the statistics database fails integrity validation
- **THEN** the system isolates the damaged database, attempts only an explicitly supported recovery, and never reports an empty replacement as valid historical statistics

### Requirement: The Console presents usage and history in Chinese
The system SHALL provide a Chinese-language super-administrator view containing total calls and tokens, usage coverage and completeness, time trends, filters, cursor-paginated call history, expandable execution detail, and storage health.

#### Scenario: Administrator opens statistics view
- **WHEN** a super administrator opens the AI usage statistics route
- **THEN** the Console loads summary, trend, and recent-call data through the generated API client and visibly distinguishes unknown, partial, and incomplete usage

### Requirement: Target-scale performance is verified
The implementation SHALL be benchmarked with at least 1,000,000 logical calls and 5,000,000 execution records. On the documented reference environment, event enqueue p95 SHALL be no greater than 1 ms without synchronous JDBC, common first-page filtered lists p95 SHALL be no greater than 200 ms, and 90-day summary and trend queries p95 SHALL be no greater than 500 ms.

#### Scenario: Target-scale benchmark
- **WHEN** the documented benchmark suite runs against the target-scale representative dataset
- **THEN** it reports latency percentiles, database and WAL sizes, query plans, queue behavior, and whether every stated threshold passed
