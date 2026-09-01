package run.halo.aifoundation.provider.protocol.messages;

import java.util.Objects;
import java.util.Map;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.http.HttpHeaders;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;

/** Common policy for providers that expose the standard Anthropic Messages wire contract. */
public record StandardAnthropicMessagesProfile(
    String providerType,
    String adapterType,
    String endpointPath,
    Authentication authentication
) implements AnthropicMessagesProfile {

    public StandardAnthropicMessagesProfile {
        Objects.requireNonNull(providerType, "providerType must not be null");
        Objects.requireNonNull(adapterType, "adapterType must not be null");
        Objects.requireNonNull(endpointPath, "endpointPath must not be null");
        Objects.requireNonNull(authentication, "authentication must not be null");
    }

    @Override
    public void applyHeaders(HttpHeaders headers, ChatCompletionsOptions options) {
        authentication.apply(headers, options.getApiKey());
        headers.set("anthropic-version", "2023-06-01");
    }

    @Override
    public void customizeRequest(Map<String, Object> body, Prompt prompt,
        ChatCompletionsOptions options, boolean stream) {
        body.putIfAbsent("max_tokens", 4096);
    }

    public enum Authentication {
        X_API_KEY {
            @Override
            void apply(HttpHeaders headers, String apiKey) {
                set(headers, "x-api-key", apiKey);
            }
        },
        API_KEY {
            @Override
            void apply(HttpHeaders headers, String apiKey) {
                set(headers, "api-key", apiKey);
            }
        },
        BEARER {
            @Override
            void apply(HttpHeaders headers, String apiKey) {
                if (hasText(apiKey)) {
                    headers.setBearerAuth(apiKey);
                }
            }
        };

        abstract void apply(HttpHeaders headers, String apiKey);

        static void set(HttpHeaders headers, String name, String value) {
            if (hasText(value)) {
                headers.set(name, value);
            }
        }

        static boolean hasText(String value) {
            if (value == null) {
                return false;
            }
            return !value.isBlank();
        }
    }
}
