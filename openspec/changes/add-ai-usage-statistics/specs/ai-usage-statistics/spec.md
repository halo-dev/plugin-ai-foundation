## Purpose

Provide super administrators with provider-neutral operational statistics that explain AI call outcomes and reported consumption, preserve unknown usage, and support precise historical queries in the user's time zone without persisting request or response content.

## ADDED Requirements

### Requirement: Provider-normalized safe usage
The system SHALL normalize inclusive provider usage at protocol boundaries, preserve unknown counters, and SHALL NOT inspect arbitrary raw objects in the statistics consumer or estimate missing token counts. The normalized counts SHALL also be the counts exposed through the existing SDK usage mapping.

#### Scenario: Messages cache usage
- **WHEN** Messages reports uncached input 10, cache read 40, cache creation 20, and output 5
- **THEN** normalized input is 70 and total is 75; cache counters remain subsets and SHALL NOT be added again to inclusive input

#### Scenario: No provider usage
- **WHEN** a successful response omits usage
- **THEN** it remains a successful call with unknown usage, never a fabricated zero

### Requirement: Isolated asynchronous observation
Observation callback and persistence failures SHALL NOT replace the model result or error. Model calls SHALL NOT wait for statistics persistence, and statistics SHALL NOT create extra provider subscriptions or attempts solely to measure usage. Statistics SHALL preserve the model's timeout scopes, retry policy, tool execution, and cancellation behavior.

#### Scenario: Statistics failure
- **WHEN** beginning observation, extracting observed usage, or writing statistics fails
- **THEN** model execution can proceed with its own result or error; where observation remains possible, missing evidence is represented as unknown, partial, or incomplete statistics

#### Scenario: Queue saturation
- **WHEN** statistics capacity is exhausted
- **THEN** admission drops statistical work and records health degradation instead of waiting for database capacity on the model call path

#### Scenario: Inapplicable output projection
- **WHEN** plain text generation output() is subscribed without structured output
- **THEN** it completes empty without invoking the provider or creating a logical call

#### Scenario: Shared or cached stream
- **WHEN** consumers subscribe to projections of one shared stream, including a stream supplied by cache middleware without provider execution
- **THEN** the projections share one logical-call session and statistics do not subscribe to additional projections to force usage collection

### Requirement: Subscription-scoped non-streaming calls
Non-streaming audited invocations SHALL create their call session and invoke their delegate at subscription time. Repeated subscriptions SHALL invoke the delegate again; synchronous delegate exceptions SHALL become reactive errors. This is an observable change from assembly-time middleware invocation.

#### Scenario: Repeated subscription
- **WHEN** a consumer subscribes twice to the same non-streaming invocation publisher
- **THEN** each subscription creates its own logical call and enters the delegate/middleware chain; provider execution still depends on that chain, including any cache short circuit

### Requirement: Consistent operational metrics
Summary, trends, breakdowns, and call history SHALL filter by logical-call start time and logical-call status. Every reported attempt's consumption SHALL contribute once to its logical call. Trends SHALL attribute a call to the bucket containing its start, even if an execution ends in another bucket.

#### Scenario: Retry succeeds
- **WHEN** a failed attempt reports 10 tokens and its successful retry reports 5
- **THEN** the successful logical call contributes 15 tokens to successful-call statistics

### Requirement: Independent completeness dimensions
The API SHALL distinguish event-delivery completeness from provider usage coverage. Partial usage SHALL preserve known counters but SHALL NOT count as fully covered usage.

#### Scenario: Missing attempt usage
- **WHEN** all events arrive but one attempt omits usage
- **THEN** delivery can be complete while usage coverage is partial or missing

### Requirement: Exact time ranges and local calendar buckets
Statistics SHALL retain UTC instants and filter explicit ranges as inclusive from and exclusive to without expanding the requested range. Trend grouping SHALL accept an IANA time zone, default to UTC when omitted, and reject invalid zones with a client error. The Console SHALL use the browser time zone consistently for calendar input, grouping, and display. Day buckets SHALL follow local calendar boundaries rather than assuming every day lasts 24 hours.

#### Scenario: Historical local day
- **WHEN** a user in Asia/Shanghai requests an already historical local day from 00:00 to the next 00:00
- **THEN** summary, trends, and breakdowns include only calls within the corresponding UTC interval; execution-detail expiry does not expand the range to UTC days or change its totals

#### Scenario: Fractional offset and daylight saving
- **WHEN** trends are requested in Asia/Kathmandu, or across a daylight-saving transition in America/New_York or Australia/Lord_Howe
- **THEN** local boundaries respect the zone's offset, local days can have non-24-hour durations, and repeated hours remain distinguishable by their UTC bucket starts and displayed offsets

#### Scenario: Omitted query range
- **WHEN** an API caller omits to and from
- **THEN** to defaults to the next UTC midnight and from to 30 days earlier, giving unchanged default boundaries for pagination within that UTC day; these defaults do not promise a user-local today range

### Requirement: Retained facts and bounded detail access
Call statistics SHALL remain available for historical aggregates until an explicit reset. Public call history and individual call details SHALL be restricted to calls starting within the last 90 days. Execution details older than the UTC start of today minus 30 calendar days SHALL be eligible for cleanup once the parent call has ended. Cleanup SHALL preserve aggregate call usage and SHALL NOT require daily rollups.

#### Scenario: Expired call details
- **WHEN** a call is older than the public 90-day detail window
- **THEN** it is absent from public call history and detail lookup, but remains included in matching historical aggregates

#### Scenario: Ongoing execution
- **WHEN** execution details cross the cleanup cutoff while their parent call remains ongoing
- **THEN** the execution details are retained for eventual completion or interrupted-call reconciliation

### Requirement: Explicit storage degradation and restart reconciliation
Persistence SHALL use bounded asynchronous work, finite retries, idempotent facts, and atomic batches. A missing database SHALL be initialized; a recognized corrupt database SHALL disable statistics without automatic replacement or restore. Startup reconciliation SHALL mark persisted unfinished calls abandoned, retain known usage, and avoid counting previously reported incompleteness again. The feature SHALL NOT claim lossless or billing-grade durability.

#### Scenario: Poison event
- **WHEN** one event repeatedly fails inside a batch
- **THEN** it is isolated and disclosed so unrelated terminal events can still persist

#### Scenario: Corrupt database
- **WHEN** database validation identifies corruption
- **THEN** statistics become unavailable, model calls can continue, and the database files are left for manual handling rather than recreated

#### Scenario: Restart after incomplete delivery
- **WHEN** an unfinished call was already marked incomplete before restart
- **THEN** restart reconciliation abandons the call without incrementing its incomplete-call health count again

### Requirement: Confirmed reset
Statistics reset SHALL require explicit administrator confirmation and invalidate queued events and sessions from the old statistics epoch. Reset SHALL clear logical statistics without promising immediate disk-space reclamation.

#### Scenario: Late event after reset
- **WHEN** an old invocation submits an event after a reset has committed
- **THEN** it cannot repopulate the cleared statistics

### Requirement: Safe administrator presentation
The Chinese Console SHALL provide summaries, trends, breakdowns, health, bounded call pagination, and confirmed reset for super administrators. It SHALL preserve unknown values, distinguish them from zero, and use the same query time zone for chart labels and tooltips. It SHALL NOT add a historical-range expansion warning banner because queries do not expand their requested ranges. Stored statistical records SHALL exclude prompts, outputs, tool arguments, headers, arbitrary metadata, raw usage payloads, and error messages.

#### Scenario: Healthy storage with unknown usage
- **WHEN** storage is healthy but the provider did not report usage
- **THEN** the Console shows unknown values and reduced complete usage coverage instead of claiming measured zero consumption

#### Scenario: Current detail state
- **WHEN** a call has progressed since its list row was fetched
- **THEN** the detail view uses the current detail response for both its call summary and execution records
