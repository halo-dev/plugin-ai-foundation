package run.halo.aifoundation;

import reactor.core.publisher.Mono;

public interface LanguageModel {

    Mono<GenerateTextResult> generateText(String prompt);

    Mono<GenerateTextResult> generateText(GenerateTextRequest request);

    StreamTextResult streamText(GenerateTextRequest request);
}
