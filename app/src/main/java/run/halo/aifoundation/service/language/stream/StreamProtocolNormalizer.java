package run.halo.aifoundation.service.language.stream;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;

public final class StreamProtocolNormalizer {

    private StreamProtocolNormalizer() {
    }

    public static Flux<TextStreamPart> normalize(Flux<TextStreamPart> source) {
        return Flux.defer(() -> {
            var state = new State();
            return source.concatMap(part -> Flux.fromIterable(state.accept(part)))
                .concatWith(Flux.defer(() -> Flux.fromIterable(state.closeOpenBlocks())));
        });
    }

    private static final class State {
        private String textId;
        private String reasoningId;
        private final LinkedHashMap<String, TextStreamPart> toolInputs = new LinkedHashMap<>();

        List<TextStreamPart> accept(TextStreamPart part) {
            if (part == null || part.getType() == null) {
                return List.of();
            }
            var parts = new ArrayList<TextStreamPart>();
            switch (part.getType()) {
                case PartType.TEXT_START -> acceptTextStart(part, parts);
                case PartType.TEXT_DELTA -> acceptTextDelta(part, parts);
                case PartType.TEXT_END -> acceptTextEnd(part, parts);
                case PartType.REASONING_START -> acceptReasoningStart(part, parts);
                case PartType.REASONING_DELTA -> acceptReasoningDelta(part, parts);
                case PartType.REASONING_END -> acceptReasoningEnd(part, parts);
                case PartType.TOOL_INPUT_START -> acceptToolInputStart(part, parts);
                case PartType.TOOL_INPUT_DELTA -> acceptToolInputDelta(part, parts);
                case PartType.TOOL_INPUT_END -> acceptToolInputEnd(part, parts);
                case PartType.TOOL_CALL, PartType.TOOL_INPUT_ERROR,
                    PartType.TOOL_APPROVAL_REQUEST -> {
                    closeText(parts);
                    closeReasoning(parts);
                    closeToolInputs(parts);
                    parts.add(part);
                }
                case PartType.FINISH_STEP, PartType.FINISH, PartType.ERROR, PartType.ABORT -> {
                    closeText(parts);
                    closeReasoning(parts);
                    closeToolInputs(parts);
                    parts.add(part);
                }
                default -> parts.add(part);
            }
            return parts;
        }

        private void acceptTextStart(TextStreamPart part, List<TextStreamPart> parts) {
            closeReasoning(parts);
            closeToolInputs(parts);
            textId = part.getId();
            parts.add(part);
        }

        private void acceptTextDelta(TextStreamPart part, List<TextStreamPart> parts) {
            closeReasoning(parts);
            closeToolInputs(parts);
            if (textId == null) {
                textId = part.getId();
                parts.add(TextStreamPart.textStart(textId));
            }
            parts.add(part);
        }

        private void acceptTextEnd(TextStreamPart part, List<TextStreamPart> parts) {
            if (textId != null) {
                parts.add(part);
                textId = null;
            }
        }

        private void acceptReasoningStart(TextStreamPart part, List<TextStreamPart> parts) {
            closeText(parts);
            closeToolInputs(parts);
            reasoningId = part.getId();
            parts.add(part);
        }

        private void acceptReasoningDelta(TextStreamPart part, List<TextStreamPart> parts) {
            closeText(parts);
            closeToolInputs(parts);
            if (reasoningId == null) {
                reasoningId = part.getId();
                parts.add(TextStreamPart.reasoningStart(reasoningId));
            }
            parts.add(part);
        }

        private void acceptReasoningEnd(TextStreamPart part, List<TextStreamPart> parts) {
            if (reasoningId != null) {
                parts.add(part);
                reasoningId = null;
            }
        }

        private void acceptToolInputStart(TextStreamPart part, List<TextStreamPart> parts) {
            closeText(parts);
            closeReasoning(parts);
            toolInputs.put(part.getId(), part);
            parts.add(part);
        }

        private void acceptToolInputDelta(TextStreamPart part, List<TextStreamPart> parts) {
            closeText(parts);
            closeReasoning(parts);
            if (!toolInputs.containsKey(part.getId())) {
                var start = TextStreamPart.toolInputStart(part.getId(), part.getToolCallId(),
                    part.getToolName());
                toolInputs.put(part.getId(), start);
                parts.add(start);
            }
            parts.add(part);
        }

        private void acceptToolInputEnd(TextStreamPart part, List<TextStreamPart> parts) {
            if (toolInputs.remove(part.getId()) != null) {
                parts.add(part);
            }
        }

        List<TextStreamPart> closeOpenBlocks() {
            var parts = new ArrayList<TextStreamPart>();
            closeText(parts);
            closeReasoning(parts);
            closeToolInputs(parts);
            return parts;
        }

        private void closeText(List<TextStreamPart> parts) {
            if (textId != null) {
                parts.add(TextStreamPart.textEnd(textId));
                textId = null;
            }
        }

        private void closeReasoning(List<TextStreamPart> parts) {
            if (reasoningId != null) {
                parts.add(TextStreamPart.reasoningEnd(reasoningId));
                reasoningId = null;
            }
        }

        private void closeToolInputs(List<TextStreamPart> parts) {
            toolInputs.values().forEach(start -> parts.add(TextStreamPart.toolInputEnd(
                start.getId(), start.getToolCallId(), start.getToolName())));
            toolInputs.clear();
        }
    }
}
