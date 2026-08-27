package run.halo.aifoundation.provider.protocol.responses;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.provider.contract.ProviderContractSource;

@ProviderContractSource(
    provider = "openai-responses-wire",
    officialDocumentation = "https://developers.openai.com/api/reference/resources/responses",
    retrievedAt = "2026-08-24"
)
class ResponsesWireCodecTest {

    private final ResponsesWireCodec codec = new ResponsesWireCodec(
        new StandardResponsesProfile("openai", "openai-responses"));

    @Test
    @SuppressWarnings("unchecked")
    void normalizesTextReasoningToolsSourcesFilesAndUsage() {
        var result = codec.decodeResponse("""
            {
              "id":"resp_1","model":"gpt-test","status":"completed",
              "output":[
                {"type":"reasoning","summary":[{"type":"summary_text","text":"Think."}]},
                {"type":"message","content":[
                  {"type":"output_text","text":"Answer.","annotations":[
                    {"type":"url_citation","url":"https://example.com","title":"Source"}
                  ]},
                  {"type":"output_file","file_id":"file_1","filename":"answer.txt"}
                ]},
                {"type":"function_call","id":"item_1","call_id":"call_1",
                 "name":"weather","arguments":"{}"},
                {"type":"file_search_call","results":[
                  {"file_id":"file_2","filename":"reference.md","score":0.9}
                ]},
                {"type":"web_search_call","action":{"sources":[
                  {"url":"https://search.example","title":"Search result"}
                ]}}
              ],
              "usage":{"input_tokens":10,"output_tokens":4,"total_tokens":14,
                "input_tokens_details":{"cached_tokens":3}}
            }
            """);

        assertThat(result.id()).isEqualTo("resp_1");
        assertThat(result.text()).isEqualTo("Answer.");
        assertThat(result.reasoning()).isEqualTo("Think.");
        assertThat(result.toolCalls()).containsExactly(new ResponsesResult.ToolCall(
            "item_1", "call_1", "weather", "{}"));
        assertThat(result.sources()).hasSize(2);
        assertThat(result.files()).hasSize(2);
        assertThat((List<Map<String, Object>>) result.providerMetadata()
            .get("providerOutputItems"))
            .extracting(item -> item.get("type"))
            .containsExactly("reasoning", "web_search_call");
        assertThat(result.usage().inputTokens()).isEqualTo(10);
        assertThat(result.usage().details()).containsKey("input_tokens_details");
    }

    @Test
    void normalizesStreamingItemLifecycleAndTerminalResponse() {
        var stream = codec.newStreamDecoder();

        assertThat(stream.accept("""
            {"type":"response.output_item.added","output_index":1,
             "item":{"type":"function_call","id":"item_1","call_id":"call_1",
             "name":"weather","arguments":""}}
            """)).containsExactly(new ResponsesStreamPart.ToolInputStart(
                1, "item_1", "call_1", "weather"));
        assertThat(stream.accept("""
            {"type":"response.function_call_arguments.delta","output_index":1,
             "item_id":"item_1","delta":"{"}
            """)).containsExactly(new ResponsesStreamPart.ToolInputDelta(
                1, "item_1", "call_1", "{"));
        assertThat(stream.accept("""
            {"type":"response.function_call_arguments.delta","output_index":1,
             "item_id":"item_1","delta":"}"}
            """)).containsExactly(new ResponsesStreamPart.ToolInputDelta(
                1, "item_1", "call_1", "}"));
        assertThat(stream.accept("""
            {"type":"response.output_item.done","output_index":1,
             "item":{"type":"function_call","id":"item_1","call_id":"call_1",
             "name":"weather","arguments":"{}"}}
            """)).containsExactly(new ResponsesStreamPart.ToolInputEnd(
                1, "item_1", "call_1", "weather", "{}"));
        assertThat(stream.accept("""
            {"type":"response.output_text.delta","item_id":"message_1","delta":"Hello"}
            """)).containsExactly(new ResponsesStreamPart.TextDelta("message_1", "Hello"));
        assertThat(stream.accept("""
            {"type":"response.reasoning_summary_text.delta","item_id":"reasoning_1",
             "delta":"Think"}
            """)).containsExactly(new ResponsesStreamPart.ReasoningDelta(
                "reasoning_1", "Think"));
        assertThat(stream.accept("""
            {"type":"response.reasoning_text.delta","item_id":"reasoning_2",
             "delta":" again"}
            """)).containsExactly(new ResponsesStreamPart.ReasoningDelta(
                "reasoning_2", " again"));

        var completed = stream.accept("""
            {"type":"response.completed","response":{"id":"resp_1","model":"gpt-test",
             "status":"completed","output":[],"usage":{"input_tokens":1,
             "output_tokens":2,"total_tokens":3}}}
            """);
        assertThat(completed).singleElement().isInstanceOf(ResponsesStreamPart.Completed.class);
    }

    @Test
    void ignoresNullOptionalMetadataInTerminalResponse() {
        var completed = codec.newStreamDecoder().accept("""
            {"type":"response.completed","response":{"id":"resp_1","model":"gpt-test",
             "status":"completed","metadata":null,"error":null,"incomplete_details":null,
             "output":[],"usage":{"input_tokens":1,"output_tokens":2,"total_tokens":3}}}
            """);

        assertThat(completed).singleElement().satisfies(part -> {
            assertThat(part).isInstanceOf(ResponsesStreamPart.Completed.class);
            var result = ((ResponsesStreamPart.Completed) part).result();
            assertThat(result.providerMetadata())
                .doesNotContainKeys("metadata", "error", "incomplete_details");
            assertThat(result.usage().totalTokens()).isEqualTo(3);
        });
    }

    @Test
    void preservesUnknownEventsAsSanitizedProviderMetadata() {
        var event = codec.newStreamDecoder().accept("""
            {"type":"response.provider.future","api_key":"secret-value","value":1}
            """);

        assertThat(event).containsExactly(new ResponsesStreamPart.Unknown(
            "response.provider.future",
            Map.of("type", "response.provider.future", "api_key", "[REDACTED]", "value", 1)));
    }

    @Test
    void terminalFailureIsTypedAndRedacted() {
        assertThatThrownBy(() -> codec.newStreamDecoder().accept("""
            {"type":"response.failed","response":{"error":{
              "message":"bad request api_key: secret-value"}}}
            """))
            .isInstanceOf(ResponsesProtocolException.class)
            .hasMessageContaining("response.failed")
            .hasMessageNotContaining("secret-value");
    }
}
