## Context

The custom OpenAI-compatible provider currently behaves like a fixed OpenAI adapter with a configurable Base URL. Runtime paths for chat, embeddings, and image generation are effectively hardcoded by the generic OpenAI-compatible client builders, and rerank is not enabled for `openailike`.

Administrators often use OpenAI-compatible services that share request shapes but publish capability-specific paths such as `/chat/completions`, `/embeddings`, `/rerank`, or `/images/generations`. The console already previews the effective chat completions URL from Base URL plus provider metadata, so endpoint overrides should fit that same mental model.

## Goals / Non-Goals

**Goals:**
- Let `openailike` providers configure chat, embedding, rerank, and image endpoint paths.
- Provide default endpoint paths and let explicit provider settings override them.
- Enable `openailike` rerank using the configured rerank endpoint and the standard rerank request shape.
- Render endpoint fields above proxy host and proxy port in the provider create/edit form.
- Show effective endpoint URL previews in each field help text.
- Keep endpoint settings as provider instance configuration, not hardcoded frontend provider lists.

**Non-Goals:**
- Do not add endpoint overrides to built-in providers.
- Do not verify arbitrary OpenAI-compatible rerank support at save time.
- Do not add provider-specific authentication or non-standard request mapping in this change.
- Do not preserve compatibility with older unreleased provider specs beyond normal optional field handling.

## Decisions

1. Store endpoint paths on `AiProvider.spec`.

   Add optional string fields for chat, embedding, rerank, and image endpoint paths. These fields belong to the provider instance because two OpenAI-compatible providers may point at different services with different paths. Alternative considered: add these paths only to provider type metadata. That would not let users override them per provider and would not solve custom services.

2. Scope UI exposure to `openailike`.

   The new fields should appear when the selected provider type is `openailike`. Built-in providers already encode their official endpoint shapes in their provider classes. Alternative considered: show fields for every provider. That would invite unsupported configuration on providers whose implementation needs custom request or response handling.

3. Normalize endpoint paths as relative paths.

   Endpoint values should be trimmed and normalized to a leading slash before runtime use. Empty values mean "use default". Absolute URLs should be rejected because Base URL remains the single authority for host/proxy/cache identity. Alternative considered: allow absolute endpoint URLs. That creates ambiguous behavior when combined with Base URL previews and proxy configuration.

4. Add runtime helpers for OpenAI-compatible endpoint selection.

   `AbstractAiProviderType` should gain helpers that resolve default paths and provider overrides before constructing OpenAI-compatible chat, embedding, image, and rerank clients. `OpenAiLikeProvider` will use these helpers and add `AdapterType.RERANK`. Alternative considered: special-case all logic in `OpenAiLikeProvider`. Shared helpers keep the path resolution close to existing OpenAI-compatible client construction.

5. Use the standard rerank client for `openailike`.

   The custom OpenAI-compatible provider should call the configured rerank endpoint with the existing standard body shape: `model`, `query`, `documents`, optional `top_n`, and `return_documents`. Provider options remain namespaced by provider type. Alternative considered: introduce a new OpenAI-compatible rerank client class. The existing standard client already matches the common `/rerank` API shape.

## Risks / Trade-offs

- [Risk] Some OpenAI-compatible rerank services use a non-standard request or response shape. -> Mitigation: document this change as standard-shape support and rely on built-in providers for custom mappings.
- [Risk] Endpoint fields could appear confusing for built-in providers. -> Mitigation: only render them for `openailike`.
- [Risk] Invalid endpoint input could produce malformed URLs. -> Mitigation: validate relative paths server-side and normalize leading slashes for previews and runtime use.
- [Risk] Provider client cache may retain old endpoint settings after edit. -> Mitigation: existing provider update invalidation covers spec changes, and endpoint fields are part of the provider spec.

## Migration Plan

1. Add optional spec fields and validation.
2. Regenerate API docs and TypeScript client.
3. Add form fields and previews for `openailike`.
4. Enable `openailike` rerank using the configured/default rerank endpoint.
5. Run focused backend and frontend checks.

Rollback is to remove the optional fields and restore `OpenAiLikeProvider` to its previous adapter list; no persisted data migration is required for this unreleased plugin.

## Open Questions

- Should the default image endpoint be `/images/generations` only, or should the UI copy mention that some services may require a provider-specific built-in adapter for image variants?
