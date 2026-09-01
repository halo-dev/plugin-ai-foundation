package run.halo.aifoundation.provider.ernie;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import run.halo.aifoundation.provider.protocol.chatcompletions.ChatCompletionsOptions;
import run.halo.aifoundation.provider.protocol.responses.ResponsesProfile;

final class ErnieResponsesProfile implements ResponsesProfile {

    @Override
    public String providerType() {
        return "ernie";
    }

    @Override
    public String adapterType() {
        return "ernie-responses";
    }

    @Override
    public void customizeRequestBody(Map<String, Object> body,
        ChatCompletionsOptions options, boolean stream) {
        // Qianfan stores Responses for three days by default. The SDK replays canonical messages
        // statelessly, so storage must be opt-in rather than an invisible server-side side effect.
        body.putIfAbsent("store", false);

        var builtins = body.remove("builtinTools");
        if (builtins instanceof List<?> builtinTools && !builtinTools.isEmpty()) {
            var tools = new ArrayList<Object>();
            if (body.get("tools") instanceof List<?> existing) {
                tools.addAll(existing);
            }
            tools.addAll(builtinTools);
            body.put("tools", List.copyOf(tools));
        }
    }
}
