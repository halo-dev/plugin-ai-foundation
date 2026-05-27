package run.halo.aifoundation.service;

import java.util.ArrayList;
import java.util.List;
import reactor.core.publisher.Flux;
import run.halo.aifoundation.PartType;
import run.halo.aifoundation.TextStreamPart;

final class StreamProtocolNormalizer {

    private StreamProtocolNormalizer() {
    }

    static Flux<TextStreamPart> normalize(Flux<TextStreamPart> source) {
        return Flux.defer(() -> {
            var state = new State();
            return source.concatMap(part -> Flux.fromIterable(state.accept(part)))
                .concatWith(Flux.defer(() -> Flux.fromIterable(state.closeOpenBlocks())));
        });
    }

    private static final class State {
        private String textId;
        private String reasoningId;
        private String toolInputId;

        List<TextStreamPart> accept(TextStreamPart part) {
            if (part == null || part.getType() == null) {
                return List.of();
            }
            var parts = new ArrayList<TextStreamPart>();
            switch (part.getType()) {
                case PartType.TEXT_START -> {
                    closeReasoning(parts);
                    closeToolInput();
                    textId = part.getId();
                    parts.add(part);
                }
                case PartType.TEXT_DELTA -> {
                    closeReasoning(parts);
                    closeToolInput();
                    if (textId == null) {
                        textId = part.getId();
                        parts.add(TextStreamPart.textStart(textId));
                    }
                    parts.add(part);
                }
                case PartType.TEXT_END -> {
                    if (textId != null) {
                        parts.add(part);
                        textId = null;
                    }
                }
                case PartType.REASONING_START -> {
                    closeText(parts);
                    closeToolInput();
                    reasoningId = part.getId();
                    parts.add(part);
                }
                case PartType.REASONING_DELTA -> {
                    closeText(parts);
                    closeToolInput();
                    if (reasoningId == null) {
                        reasoningId = part.getId();
                        parts.add(TextStreamPart.reasoningStart(reasoningId));
                    }
                    parts.add(part);
                }
                case PartType.REASONING_END -> {
                    if (reasoningId != null) {
                        parts.add(part);
                        reasoningId = null;
                    }
                }
                case PartType.TOOL_INPUT_START -> {
                    closeText(parts);
                    closeReasoning(parts);
                    toolInputId = part.getId();
                    parts.add(part);
                }
                case PartType.TOOL_INPUT_DELTA -> {
                    closeText(parts);
                    closeReasoning(parts);
                    if (toolInputId == null) {
                        toolInputId = part.getId();
                        parts.add(TextStreamPart.toolInputStart(toolInputId, part.getToolCallId(),
                            part.getToolName()));
                    }
                    parts.add(part);
                }
                case PartType.TOOL_CALL -> {
                    closeText(parts);
                    closeReasoning(parts);
                    closeToolInput();
                    parts.add(part);
                }
                case PartType.FINISH_STEP, PartType.FINISH, PartType.ERROR, PartType.ABORT -> {
                    closeText(parts);
                    closeReasoning(parts);
                    closeToolInput();
                    parts.add(part);
                }
                default -> parts.add(part);
            }
            return parts;
        }

        List<TextStreamPart> closeOpenBlocks() {
            var parts = new ArrayList<TextStreamPart>();
            closeText(parts);
            closeReasoning(parts);
            closeToolInput();
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

        private void closeToolInput() {
            toolInputId = null;
        }
    }
}
