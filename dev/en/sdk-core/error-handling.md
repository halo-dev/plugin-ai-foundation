# SDK Core: Error handling

[简体中文](../../zh-CN/sdk-core/error-handling.md) | English

Warnings mean the request completed with a recoverable difference. Exceptions mean the requested
contract did not complete.

```java
return model.generateText(request)
    .doOnNext(result -> result.getWarnings()
        .forEach(warning -> log.warn("{}: {}", warning.getCode(), warning.getMessage())))
    .onErrorMap(ProviderApiException.class,
        error -> new UserFacingException("The configured AI provider is unavailable", error));
```

Common exception groups:

| Area                 | Types                                                                                                                      |
| -------------------- | -------------------------------------------------------------------------------------------------------------------------- |
| Model resolution     | `DefaultModelNotConfiguredException`, `ModelNotFoundException`, `ModelDisabledException`, `IncompatibleModelTypeException` |
| Provider             | `ProviderDisabledException`, `ProviderApiException`                                                                        |
| Capability and media | `UnsupportedModelCapabilityException`, `InvalidMediaContentException`, `MediaContentTooLargeException`                     |
| Control              | generation, embedding, and reranking cancellation/timeout exceptions                                                       |
| Output               | `StructuredOutputValidationException`, `ImageGenerationException`                                                          |

Check capabilities before presenting controls when the UI already knows its requirements, but
still handle `UnsupportedModelCapabilityException` because configuration can change.

For streams, a provider failure before events is a normal reactive error. After a UI Message
stream starts, use its terminal `error` or `abort` event and the final reduced message. Never place
API keys, full sensitive prompts, base64 files, or unredacted provider bodies in a user-facing
error.
