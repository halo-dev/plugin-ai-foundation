## Why

`AiProvider.spec.proxyHost` and `proxyPort` are already exposed by the backend resource and Console form, but provider HTTP clients do not read them. This misleads administrators and prevents providers from working in environments where upstream AI services must be reached through an HTTP proxy.

## What Changes

- Implement runtime support for provider-level HTTP proxy settings instead of removing the existing fields.
- Apply the proxy configuration to all upstream HTTP clients created by provider types, including WebClient-based model discovery/connectivity checks and RestClient-based chat/embedding clients.
- Keep current behavior unchanged when no proxy host or port is configured.
- Validate incomplete or invalid proxy configuration server-side so a saved provider cannot silently ignore a broken proxy setup.
- Add focused tests around provider client construction and proxy/no-proxy behavior.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `ai-provider-config`: Provider connection configuration will require configured proxy fields to affect upstream provider calls.

## Impact

- Backend provider infrastructure in `app/src/main/java/run/halo/aifoundation/provider/`, especially shared client builder code in `AbstractAiProviderType`.
- `AiProvider` validation and Console provider save/test paths that already carry `proxyHost` and `proxyPort`.
- Provider discovery, connectivity checks, chat, and embedding calls that create WebClient or RestClient instances.
- No public SDK API changes and no breaking change to existing provider resources without proxy settings.
