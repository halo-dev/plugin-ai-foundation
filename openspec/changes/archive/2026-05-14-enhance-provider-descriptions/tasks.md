## 1. Metadata — Chinese Providers

- [x] 1.1 Add description, iconUrl, websiteUrl, and documentationUrl to `DeepSeekProvider`
- [x] 1.2 Add description, iconUrl, websiteUrl, and documentationUrl to `KimiProvider`
- [x] 1.3 Add description, iconUrl, websiteUrl, and documentationUrl to `ZhiPuProvider`
- [x] 1.4 Add description, iconUrl, websiteUrl, and documentationUrl to `ErnieProvider`
- [x] 1.5 Add description, iconUrl, websiteUrl, and documentationUrl to `DouBaoProvider`

## 2. Metadata — International Providers

- [x] 2.1 Add description, iconUrl, websiteUrl, and documentationUrl to `OpenAiProvider`
- [x] 2.2 Add description, iconUrl, websiteUrl, and documentationUrl to `OllamaProvider`
- [x] 2.3 Add description, iconUrl, websiteUrl, and documentationUrl to `SiliconFlowProvider`
- [x] 2.4 Add description, iconUrl, websiteUrl, and documentationUrl to `AiHubMixProvider`

## 3. Verification

- [x] 3.1 Confirm every referenced icon PNG exists under `app/src/main/resources/static/brands/`
- [x] 3.2 Run `./gradlew compileJava` to ensure all provider classes compile
- [x] 3.3 Start the dev server and verify the provider-types API returns non-null metadata for all providers
