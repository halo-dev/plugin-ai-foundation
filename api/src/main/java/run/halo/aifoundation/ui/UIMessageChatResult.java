package run.halo.aifoundation.ui;

import reactor.core.publisher.Mono;

/**
 * Result returned by the framework-neutral UI message chat handler.
 *
 * @param stream structured UI message chunk stream
 * @param response HTTP-friendly response descriptor for the same stream
 * @param validation validation result produced before model conversion
 * @param conversion conversion result used for the model request
 * @param finish final aggregated response message and updated conversation
 */
public record UIMessageChatResult<M>(UIMessageStream stream,
                                     UIMessageStreamResponse response,
                                     UIMessageValidationResult<M> validation,
                                     UIMessageConversionResult conversion,
                                     Mono<UIMessageStreamFinish<M>> finish) {
}
