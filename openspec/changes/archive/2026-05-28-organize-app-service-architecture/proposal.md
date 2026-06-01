## Why

The `app` implementation currently keeps many orchestration, mapping, validation, streaming, and tool execution classes in a flat `service` package. This makes the Spring Boot application layer harder to navigate, blurs responsibility boundaries, and leaves service abstractions implicit even after the large implementation classes were partially split.

## What Changes

- Reorganize `app/src/main/java/run/halo/aifoundation/` implementation packages around Spring application responsibilities instead of keeping all service collaborators in one flat package.
- Keep Spring-facing service implementations in a concise service layer, and move language generation, embedding execution, stream protocol, tool execution, request mapping, response mapping, and validation helpers into cohesive subpackages.
- Introduce internal service/port interfaces where a component has a meaningful Spring service boundary, such as model resolution, language model operations, or embedding operations.
- Keep helper classes package-private where possible so implementation detail does not become accidental architecture surface.
- Update extension definitions, component scanning assumptions, tests, and documentation references to use the new package names.
- Add architecture checks or focused tests that prevent new unrelated implementation helpers from being added directly to the flat `service` package.

## Non-Goals

- This is backend-only and does not change the console UI.
- This does not change the public Java SDK contracts in the `api` module.
- This does not add checkstyle, formatting plugins, or Gradle formatting tools.
- This does not redesign provider type discovery or split provider metadata from provider behavior.

## Capabilities

### New Capabilities
- `app-service-architecture`: Defines the internal backend package organization and service boundary expectations for the AI Foundation app implementation.

### Modified Capabilities
- None.

## Impact

- Affected code: `app/src/main/java/run/halo/aifoundation/service/`, related implementation imports, extension metadata, tests, and backend documentation references.
- Public API impact: none expected for consumer plugins.
- Runtime impact: Spring component discovery and Halo extension definitions must continue to resolve the same beans after package movement.
- Validation impact: backend compilation and tests must prove that moved components are wired correctly and that existing language and embedding behavior remains unchanged.
