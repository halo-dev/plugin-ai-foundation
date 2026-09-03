## Why

AI Foundation currently knows which plugin resolves and invokes a model, but it does not retain per-call outcomes or provider-reported usage. Super administrators therefore cannot see total token consumption, identify callers or models responsible for usage, or investigate failed, retried, batched, and multi-step executions.

## What Changes

- Record one logical call for each subscribed language, embedding, reranking, or image-generation SDK invocation.
- Record underlying model executions for generation steps, batches, and provider retry attempts so token usage is explainable without double counting.
- Normalize provider-reported token usage while preserving unknown, partial, and estimated quality instead of treating missing values as zero.
- Persist recent call and execution facts in a plugin-owned SQLite database and maintain rebuildable UTC daily rollups for long-term statistics.
- Add super-administrator APIs and Console UI for summaries, trends, filtered cursor-paginated call history, call details, storage health, and explicit statistics reset.
- Infer the caller plugin from the existing classloader-aware stack inspection, record the audited SDK operation, and optionally accept a validated feature tag from the existing request metadata without changing public request shapes.
- Bound detail retention and asynchronous persistence so statistics failures remain visible but do not fail model calls.

## Capabilities

### New Capabilities

- `ai-usage-statistics`: Provider-neutral call metering, token accounting, retained history, aggregate queries, administrative health/reset operations, and Console presentation.

### Modified Capabilities

None.

## Non-goals

- Calculating prices or monetary cost.
- Storing prompts, outputs, tool arguments, request headers, arbitrary metadata, or raw provider payloads.
- Tracking end users or sessions.
- Enforcing quotas, billing-grade durability, or exact reconciliation with provider invoices.
- Sharing one SQLite database across multiple Halo instances.
- CSV export, full-text search, or administrator-configurable retention in the first release.

## Impact

- **Backend and UI:** this is a full-stack Console feature, not a backend-only or UI-only change.
- `api`: no public method or request shape changes; one reserved metadata key may be read when present.
- `app`: audited model decorators, internal provider execution boundaries, SQLite lifecycle/storage, retention and rollup jobs, Console endpoints, OpenAPI generation, health reporting, and super-administrator authorization.
- `ui`: a new Chinese-language statistics view using the generated API client.
- Build/runtime: adds an embedded SQLite JDBC dependency and plugin-owned persistent files under the Halo work directory.
- Operations: Halo backup/restore must use a SQLite-consistent snapshot; plugin stop/reload must drain bounded work and close all database resources.
