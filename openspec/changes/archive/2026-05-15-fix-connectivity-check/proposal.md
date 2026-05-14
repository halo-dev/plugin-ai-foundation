## Why

The provider connectivity check endpoint (`POST providers/{name}/connectivity`) currently only instantiates a `ChatModel` object without sending any actual network request. This gives users a false "OK" status even when the API key is wrong, the network is unreachable, or the base URL is invalid. This is a critical reliability bug that undermines user trust in the connectivity feature.

## What Changes

- Fix `ProviderConsoleEndpoint.performConnectivityCheck()` to actually verify remote service reachability by calling the provider's model discovery API (which sends a real HTTP request to the provider's `/v1/models` or equivalent endpoint).
- If the discovery call succeeds, connectivity is confirmed as OK.
- If the discovery call fails (network error, authentication error, invalid base URL), the actual error message is captured and returned.
- Update the `provider-debug-api` spec to clarify that connectivity validation must involve an actual remote API call, not just local object construction.

## Capabilities

### New Capabilities
<!-- None — this is a bug fix to existing behavior. -->

### Modified Capabilities
- `provider-debug-api`: Clarify the "Test provider connectivity" requirement to specify that validation must perform an actual remote API call and propagate real errors, not just instantiate a client object.

## Impact

- Backend: `ProviderConsoleEndpoint.java` — `performConnectivityCheck()` method changes.
- Spec: `openspec/specs/provider-debug-api/spec.md` — connectivity requirement is tightened.
- No API contract changes; endpoint URL, request/response shape remain the same.
- No frontend changes required.
