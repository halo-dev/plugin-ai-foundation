## 1. Implementation

- [x] 1.1 Create `KimiProvider.java` in `app/src/main/java/run/halo/aifoundation/provider/` extending `AbstractAiProviderType` with `@Component`, implementing identity methods (providerType=`kimi`, displayName=`Kimi`, isBuiltIn=true, requiresBaseUrl=false, defaultBaseUrl=`https://api.moonshot.cn`)
- [x] 1.2 Implement `getSupportedEndpointTypes()` returning `List.of("openai-chat")`, `supportsEmbeddings()` returning false, `maxEmbeddingsPerCall()` returning 0, `supportsParallelCalls()` returning false
- [x] 1.3 Implement `buildChatModel()` using Spring AI's `OpenAiApi` with Kimi's base URL, completions path `/v1/chat/completions`, embeddings path `/v1/embeddings`, and `OpenAiChatModel` with the provided model ID

## 2. Verification

- [x] 2.1 Build the project with `./gradlew build` and verify compilation succeeds
- [x] 2.2 Verify the Kimi provider type appears in the provider type list API by starting the dev server and checking the endpoint
