## ADDED Requirements

### Requirement: Provider-normalized safe usage
The system SHALL normalize inclusive provider usage at protocol boundaries, preserve unknown counters, and SHALL NOT inspect arbitrary raw objects in the statistics consumer.

#### Scenario: Messages cache usage
- **WHEN** Messages reports uncached input 10, cache read 40, cache creation 20, output 5
- **THEN** normalized input is 70 and total is 75; cache counters remain subsets

#### Scenario: No provider usage
- **WHEN** a successful response omits usage
- **THEN** it remains a successful call with unknown usage, never a fabricated zero

### Requirement: Isolated observation
Statistics SHALL NOT change subscription, timeout, middleware, retry, cancellation, tool, or output semantics and SHALL NOT wait for persistence on the model path.

#### Scenario: Statistics failure
- **WHEN** starting statistics or extracting usage fails
- **THEN** the original model result or error remains unchanged and the missing evidence is disclosed

#### Scenario: Inapplicable output projection
- **WHEN** plain text generation output() is subscribed
- **THEN** it completes empty without invoking the provider or creating a logical call

#### Scenario: Cached stream
- **WHEN** middleware returns a cached stream with no provider executions
- **THEN** the subscribed logical call still terminates exactly once

### Requirement: Consistent operational metrics
Summary, trends and history SHALL filter by logical-call start time and logical-call final status. All reported attempt consumption SHALL contribute once to that call.

#### Scenario: Retry succeeds
- **WHEN** a failed attempt reports 10 tokens and its successful retry reports 5
- **THEN** the successful logical call contributes 15 tokens to successful-call statistics

### Requirement: Independent completeness dimensions
The API SHALL distinguish event delivery, provider usage coverage, and query precision.

#### Scenario: Missing attempt usage
- **WHEN** all events arrive but one attempt omits usage
- **THEN** delivery can be complete while usage coverage is partial or missing

#### Scenario: Expired sub-day detail
- **WHEN** a query requests part of an archived UTC day
- **THEN** the API expands to whole UTC days, returns the actual interval and marks preciseRange false

### Requirement: Bounded and recoverable storage
The implementation SHALL use bounded asynchronous writing, finite retries, idempotent events, transactional batches and rollups, epoch-isolated reset, and explicit health degradation.

#### Scenario: Poison event
- **WHEN** one event repeatedly fails inside a batch
- **THEN** it is isolated and disclosed; remaining terminal events can persist as incomplete

#### Scenario: Late completion
- **WHEN** a retained call completes after maintenance
- **THEN** the next query sees its completed status without waiting for another daily maintenance run

#### Scenario: Reset and recovery
- **WHEN** an administrator resets statistics
- **THEN** queued old-epoch events and pre-reset recovery snapshots cannot repopulate the history

### Requirement: Administrator interpretation
Console SHALL explain metric definitions, partial and missing usage, actual query intervals, and storage health; SHALL preserve unknown values and provide bounded pagination and confirmed reset.

#### Scenario: Healthy storage with unknown usage
- **WHEN** storage is healthy but the provider did not report usage
- **THEN** Console shows unknown values and reduced complete usage coverage instead of claiming complete measured consumption
