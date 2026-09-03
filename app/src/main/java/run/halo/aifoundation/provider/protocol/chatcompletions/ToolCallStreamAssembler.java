package run.halo.aifoundation.provider.protocol.chatcompletions;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.ai.chat.messages.AssistantMessage;
import run.halo.aifoundation.service.language.stream.ProviderStreamPart;

/** Owns the lifecycle and incremental argument state of streamed tool calls. */
final class ToolCallStreamAssembler {

    private static final String TOOL_CALL_ID_PREFIX = "call_";

    private final String providerType;
    private final StreamDialect streamDialect;
    private final Map<Integer, MutableToolCall> toolCalls = new LinkedHashMap<>();

    ToolCallStreamAssembler(String providerType, StreamDialect streamDialect) {
        this.providerType = providerType;
        this.streamDialect = streamDialect;
    }

    List<ProviderStreamPart> update(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        if (node.isEmpty()) {
            return List.of();
        }
        var parts = new ArrayList<ProviderStreamPart>();
        var batchSize = node.size();
        var ordinal = 0;
        for (var item : node) {
            var index = toolCallIndex(item, ordinal, batchSize);
            var current = toolCalls.computeIfAbsent(index,
                ignored -> new MutableToolCall(fallbackId(index)));
            parts.addAll(current.append(index, item));
            ordinal++;
        }
        return parts;
    }

    List<AssistantMessage.ToolCall> currentToolCalls() {
        return toolCalls.values().stream()
            .map(MutableToolCall::toToolCall)
            .toList();
    }

    List<ProviderStreamPart> finishParts() {
        var parts = new ArrayList<ProviderStreamPart>();
        for (var entry : toolCalls.entrySet()) {
            parts.addAll(entry.getValue().finish(entry.getKey()));
        }
        return parts;
    }

    private int toolCallIndex(JsonNode item, int ordinal, int batchSize) {
        if (item.path("index").isNumber()) {
            return item.path("index").asInt();
        }
        if (batchSize > 1) {
            return ordinal;
        }
        if (toolCalls.size() == 1) {
            return toolCalls.keySet().iterator().next();
        }
        return toolCalls.isEmpty() ? 0 : toolCalls.size();
    }

    private String fallbackId(int index) {
        return TOOL_CALL_ID_PREFIX + index;
    }

    private final class MutableToolCall {

        private String id;
        private String type = "function";
        private String name = "";
        private final StringBuilder arguments = new StringBuilder();
        private final List<String> bufferedDeltas = new ArrayList<>();
        private StreamingStatus streamingStatus = StreamingStatus.UNKNOWN;
        private boolean idFrozen;
        private boolean lifecycleStarted;
        private boolean lifecycleEnded;
        private boolean deltasReliable = true;

        private MutableToolCall(String fallbackId) {
            this.id = fallbackId;
        }

        private List<ProviderStreamPart> append(int index, JsonNode node) {
            var parts = new ArrayList<ProviderStreamPart>();
            updateIdentity(node);

            var function = node.path("function");
            updateName(function);
            appendArguments(index, function, parts);
            flushBufferedDeltasWhenNamed(index, parts);
            return parts;
        }

        private void updateIdentity(JsonNode node) {
            var nextId = text(node.path("id"));
            if (hasText(nextId) && !idFrozen) {
                id = nextId;
            }
            var nextType = text(node.path("type"));
            if (hasText(nextType)) {
                type = nextType;
            }
        }

        private void updateName(JsonNode function) {
            var nextName = text(function.path("name"));
            if (hasText(nextName)) {
                name = nextName;
            }
        }

        private void appendArguments(int index, JsonNode function,
            List<ProviderStreamPart> parts) {
            var argumentNode = function.path("arguments");
            if (!argumentNode.isTextual()) {
                return;
            }
            var providerArguments = argumentNode.asText();
            if (providerArguments.isEmpty()) {
                return;
            }
            idFrozen = true;

            var normalized = streamDialect.normalizeArguments(index, arguments.toString(),
                providerArguments);
            if (!normalized.reliable()) {
                markDeltasUnreliable(providerArguments);
                return;
            }

            var delta = normalized.delta() == null ? "" : normalized.delta();
            arguments.append(delta);
            if (!deltasReliable) {
                return;
            }
            if (delta.isEmpty()) {
                return;
            }

            streamingStatus = StreamingStatus.DELTA_OBSERVED;
            if (!hasText(name)) {
                bufferedDeltas.add(delta);
                return;
            }

            startAndFlush(index, parts);
            parts.add(new ProviderStreamPart.ToolInputDeltaPart(index, delta));
        }

        private void markDeltasUnreliable(String providerArguments) {
            deltasReliable = false;
            bufferedDeltas.clear();
            arguments.setLength(0);
            arguments.append(providerArguments);
        }

        private void flushBufferedDeltasWhenNamed(int index,
            List<ProviderStreamPart> parts) {
            if (!deltasReliable) {
                return;
            }
            if (!hasText(name)) {
                return;
            }
            if (bufferedDeltas.isEmpty()) {
                return;
            }
            startAndFlush(index, parts);
        }

        private void startAndFlush(int index, List<ProviderStreamPart> parts) {
            if (!lifecycleStarted) {
                lifecycleStarted = true;
                parts.add(new ProviderStreamPart.ToolInputStartPart(index, id, name));
            }
            for (var delta : bufferedDeltas) {
                parts.add(new ProviderStreamPart.ToolInputDeltaPart(index, delta));
            }
            bufferedDeltas.clear();
        }

        private List<ProviderStreamPart> finish(int index) {
            if (!hasText(name)) {
                throw new IllegalStateException(providerType
                    + " Chat Completions tool call at index " + index
                    + " did not provide a name");
            }
            if (streamingStatus == StreamingStatus.UNKNOWN) {
                streamingStatus = StreamingStatus.FINAL_ONLY;
            }
            if (!lifecycleStarted) {
                return List.of();
            }
            if (lifecycleEnded) {
                return List.of();
            }
            lifecycleEnded = true;
            return List.of(new ProviderStreamPart.ToolInputEndPart(index));
        }

        private AssistantMessage.ToolCall toToolCall() {
            return new AssistantMessage.ToolCall(id, type, name, arguments.toString());
        }
    }

    private String text(JsonNode node) {
        return node.isTextual() ? node.asText() : null;
    }

    private boolean hasText(String value) {
        if (value == null) {
            return false;
        }
        return !value.isBlank();
    }

    private enum StreamingStatus {
        UNKNOWN,
        DELTA_OBSERVED,
        FINAL_ONLY
    }
}
