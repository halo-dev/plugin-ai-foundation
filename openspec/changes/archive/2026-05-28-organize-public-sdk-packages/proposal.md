## Why

The public `api` module is still exposed as one large flat Java package, which makes the SDK harder to browse, harder to document coherently, and easier to regress with unused or half-migrated types. The previous change closed major behavior gaps; this change makes the already implemented SDK easier and safer for plugin authors to use through package organization and static quality checks.

## What Changes

- **BREAKING** Move public SDK types from the flat `run.halo.aifoundation` package into cohesive subpackages for chat, messages, parts, schemas, tools, embeddings, lifecycle, options, and exceptions.
- Keep only the top-level service entry points and service locator style entry points in `run.halo.aifoundation`.
- Update all app, tests, docs, and examples to use the new packages without compatibility wrappers or deprecated aliases.
- Add package-level JavaDoc where useful so IDE package browsing explains the purpose of each SDK area.
- Add a lightweight Java quality check that catches half-migrated public SDK packages without introducing formatting or lint tooling.
- Keep generated OpenAPI/client behavior unchanged unless a backend endpoint type actually changes.

## Non-Goals

- This change does not add new AI capabilities or provider integrations.
- This change does not redesign the runtime generation, embedding, or provider behavior already implemented.
- This change does not preserve old imports through compatibility aliases because the plugin is unreleased.
- This change does not reorganize internal `app` provider packages beyond changes required by the public API package move and quality gates.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `sdk-ergonomics`: Require cohesive public SDK package organization, package-level discoverability, and a static quality check that prevents half-migrated API code.

## Impact

- `api/`: public Java package names, imports, package-level docs, and possibly source file locations.
- `app/`: implementation imports and any endpoint/test references to public SDK types.
- `dev/dev.md`: all public SDK examples and import guidance.
- Tests/build: add or configure static quality checks and run full Gradle validation.
- OpenSpec: update `sdk-ergonomics` to make package organization and quality gates an enforceable SDK contract.
