## Why

The recent maintainability pass made large Java workflows more visible, but the runtime object model is still too linear: major services construct their own collaborators with `new`, hiding responsibilities from Spring, tests, and future extension points. This makes language and embedding execution difficult to compose, decorate, observe, or replace using Spring Boot practices such as injected factories, strategy interfaces, and cross-cutting advice.

## What Changes

- Introduce a Java architecture pass focused on object-oriented boundaries, Spring-managed composition, and explicit domain roles.
- Replace self-assembled collaborator graphs in runtime services with Spring-injected factories, assemblers, strategies, or prototype builders where those collaborators represent behavior rather than pure DTO values.
- Promote repeated provider option construction and provider-support behavior into named domain objects or factories without changing the one-class-per-provider architecture.
- Extract endpoint request validation and console-only test-tool assembly into Spring-managed collaborators where they represent reusable behavior.
- Define rules for acceptable `new` usage so value objects, records, DTOs, exceptions, builders, and framework request objects are not mechanically moved into Spring.
- Keep the change backend-only; no UI behavior or public SDK contract should change.

## Non-Goals

- Do not redesign the provider type system. Concrete provider classes should remain one `@Component` per provider type.
- Do not introduce role/permission configuration or non-super-admin behavior.
- Do not expose Spring AI types through the public `api` module.
- Do not add AOP for its own sake; use AOP only for genuinely cross-cutting runtime behavior such as lifecycle, timing, logging, or policy enforcement.
- Do not refactor DTOs, records, exceptions, and simple value factories only because they use `new`.

## Capabilities

### New Capabilities
- `java-object-model-composition`: Covers object-oriented Java boundaries, Spring-managed collaborator composition, and acceptable direct construction rules for production Java implementation code.

### Modified Capabilities
- `code-maintainability`: Extend maintainability requirements from hotspot visibility to Spring/OOP composition boundaries.
- `language-model-maintainability`: Require language runtime collaborators to be assembled through explicit composition factories instead of being hidden inside `LanguageModelImpl` constructors.

## Impact

- Affected backend packages:
  - `app/src/main/java/run/halo/aifoundation/service/language/**`
  - `app/src/main/java/run/halo/aifoundation/service/embedding/**`
  - `app/src/main/java/run/halo/aifoundation/provider/**`
  - `app/src/main/java/run/halo/aifoundation/endpoint/**`
- Expected implementation changes:
  - new Spring-managed factories/assemblers/strategy interfaces;
  - constructor changes inside app implementation classes;
  - focused tests for factory composition and behavior preservation.
- Public API impact:
  - no intentional public SDK or REST contract changes.
