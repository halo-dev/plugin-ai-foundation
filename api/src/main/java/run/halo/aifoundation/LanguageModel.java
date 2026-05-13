package run.halo.aifoundation;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LanguageModel {

    Mono<String> chat(String prompt);

    Flux<ChatChunk> streamChat(ChatRequest request);
}
