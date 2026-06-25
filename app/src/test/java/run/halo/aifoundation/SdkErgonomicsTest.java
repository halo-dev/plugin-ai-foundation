package run.halo.aifoundation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import run.halo.aifoundation.chat.GenerateTextResult;
import run.halo.aifoundation.chat.GenerationStep;
import run.halo.aifoundation.chat.GenerateTextRequest;
import run.halo.aifoundation.chat.ReasoningOptions;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageResponseFormat;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.message.ModelMessage;
import run.halo.aifoundation.message.ModelMessagePart;
import run.halo.aifoundation.options.ProviderOptions;
import run.halo.aifoundation.part.GenerationContentPart;
import run.halo.aifoundation.part.PartType;
import run.halo.aifoundation.part.TextStreamPart;
import run.halo.aifoundation.schema.JsonSchema;
import run.halo.aifoundation.schema.OutputSpec;
import run.halo.aifoundation.schema.OutputType;
import run.halo.aifoundation.tool.ToolDefinition;
import run.halo.aifoundation.tool.ToolApprovalPolicy;
import run.halo.aifoundation.tool.ToolApprovalRequest;
import run.halo.aifoundation.tool.ToolApprovalResponse;
import run.halo.aifoundation.tool.ToolCall;
import run.halo.aifoundation.tool.ToolCallRepairContext;
import run.halo.aifoundation.tool.ToolCallRepairResult;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.ui.FilePart;
import run.halo.aifoundation.ui.UIMessageStreamReader;
import run.halo.aifoundation.ui.UIMessageStreams;
import reactor.core.publisher.Mono;
import tools.jackson.databind.json.JsonMapper;

class SdkErgonomicsTest {

    @Test
    void jsonSchema_buildsDiscoverableObjectSchema() {
        var schema = JsonSchema.object()
            .description("Weather lookup input")
            .property("location", JsonSchema.string().description("City name"))
            .property("unit", JsonSchema.enumeration("celsius", "fahrenheit"))
            .property("days", JsonSchema.integer())
            .required("location")
            .build();

        assertThat(schema.toMap()).containsEntry("type", "object");
        assertThat(schema.toMap()).containsEntry("required", List.of("location"));
        var properties = (Map<?, ?>) schema.toMap().get("properties");
        assertThat(properties.containsKey("location")).isTrue();
        assertThat(properties.containsKey("unit")).isTrue();
        assertThat(properties.containsKey("days")).isTrue();
    }

    @Test
    void toolDefinition_acceptsTypedInputAndOutputSchemas() {
        var tool = ToolDefinition.builder()
            .name("weather")
            .description("Get weather")
            .inputSchema(JsonSchema.object()
                .property("location", JsonSchema.string())
                .required("location")
                .build())
            .outputSchema(JsonSchema.object()
                .property("temperature", JsonSchema.number())
                .required("temperature")
                .build())
            .build();

        assertThat(tool.getInputSchema()).containsEntry("type", "object");
        assertThat(tool.getOutputSchema()).containsEntry("type", "object");
    }

    @Test
    void toolDefinition_acceptsApprovalPolicies() {
        var always = ToolDefinition.builder()
            .name("dangerous")
            .needsApproval(true)
            .build();
        var dynamic = ToolDefinition.builder()
            .name("payment")
            .needsApproval(context -> ((Number) context.getInput().get("amount")).intValue() > 1000)
            .build();

        assertThat(always.getApprovalPolicy().getMode())
            .isEqualTo(ToolApprovalPolicy.Mode.ALWAYS);
        assertThat(dynamic.getApprovalPolicy().requiresApproval(ToolExecutionContext.builder()
            .input(Map.of("amount", 1200))
            .build())).isTrue();
    }

    @Test
    void generationResultsExposeProviderNeutralResponseMessages() {
        var responseMessage = ModelMessage.assistant("hello");
        var step = GenerationStep.builder()
            .responseMessages(List.of(responseMessage))
            .build();
        var result = GenerateTextResult.builder()
            .responseMessages(List.of(responseMessage))
            .steps(List.of(step))
            .build();

        assertThat(result.getResponseMessages())
            .singleElement()
            .satisfies(message -> {
                assertThat(message.getRole().name()).isEqualTo("ASSISTANT");
                assertThat(message.getContent().getFirst().getText()).isEqualTo("hello");
            });
        assertThat(result.getSteps().getFirst().getResponseMessages())
            .containsExactly(responseMessage);
    }

    @Test
    void outputSpec_acceptsTypedSchemas() {
        var objectOutput = OutputSpec.object(JsonSchema.object()
            .property("name", JsonSchema.string())
            .required("name")
            .build());
        var arrayOutput = OutputSpec.array(JsonSchema.string().build());

        assertThat(objectOutput.getType()).isEqualTo(OutputType.OBJECT);
        assertThat(objectOutput.getSchema()).containsEntry("type", "object");
        assertThat(arrayOutput.getType()).isEqualTo(OutputType.ARRAY);
        assertThat(arrayOutput.getElementSchema()).containsEntry("type", "string");
    }

    @Test
    void providerOptions_buildsNamespacedOptions() {
        var options = ProviderOptions.of(
            ProviderOptions.namespace("openai")
                .option("seed", 42)
                .option("dimensions", 512)
                .option("encodingFormat", "float")
                .build()
        );

        assertThat(options).containsOnlyKeys("openai");
        assertThat(options.get("openai"))
            .containsEntry("seed", 42)
            .containsEntry("dimensions", 512)
            .containsEntry("encodingFormat", "float");
    }

    @Test
    void reasoningOptions_buildTypedSettings() {
        assertThat(ReasoningOptions.providerDefault().isExplicit()).isFalse();
        assertThat(ReasoningOptions.enabled().getMode())
            .isEqualTo(ReasoningOptions.Mode.ENABLED);
        assertThat(ReasoningOptions.disabled().getMode())
            .isEqualTo(ReasoningOptions.Mode.DISABLED);
        assertThat(ReasoningOptions.effort(ReasoningOptions.Effort.LOW).getEffort())
            .isEqualTo(ReasoningOptions.Effort.LOW);
        assertThat(GenerateTextRequest.builder()
            .prompt("fast")
            .reasoning(ReasoningOptions.disabled())
            .build()
            .getReasoning()
            .isExplicit()).isTrue();
        assertThat(GenerateTextRequest.builder().prompt("default").build().getReasoning())
            .isNull();
    }

    @Test
    void reasoningOptions_areJsonSerializable() throws Exception {
        var mapper = JsonMapper.builder().build();
        var request = GenerateTextRequest.builder()
            .prompt("fast")
            .reasoning(ReasoningOptions.effort(ReasoningOptions.Effort.HIGH))
            .build();

        var json = mapper.writeValueAsString(request);
        var restored = mapper.readValue(json, GenerateTextRequest.class);

        assertThat(restored.getReasoning().getMode()).isEqualTo(ReasoningOptions.Mode.ENABLED);
        assertThat(restored.getReasoning().getEffort()).isEqualTo(ReasoningOptions.Effort.HIGH);
    }

    @Test
    void messagePartBuilderRejectsInvalidShape() {
        assertThatThrownBy(() -> ModelMessagePart.builder()
            .type(PartType.TEXT)
            .text("hello")
            .toolName("weather")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("text message part has invalid fields");
    }

    @Test
    void dataContent_normalizesDataUrlsAndKeepsUrlSourceDistinct() {
        var content = DataContent.dataUrl("data:image/png;base64,aGVsbG8=", "image.png");
        var urlContent = DataContent.url("https://example.com/image.png");

        assertThat(content.getData()).isEqualTo("aGVsbG8=");
        assertThat(content.getMediaType()).isEqualTo("image/png");
        assertThat(content.source()).isEqualTo(InputSource.DATA);
        assertThat(content.getUrl()).isNull();
        assertThat(urlContent.source()).isEqualTo(InputSource.URL);
        assertThat(urlContent.getMediaType()).isNull();
    }

    @Test
    void dataContent_rejectsInvalidBase64AndConflictingSources() {
        assertThatThrownBy(() -> DataContent.data("not base64", "image/png"))
            .isInstanceOf(InvalidMediaContentException.class);
        assertThatThrownBy(() -> DataContent.builder()
            .url("https://example.com/image.png")
            .data("aGVsbG8=")
            .mediaType("image/png")
            .build())
            .isInstanceOf(InvalidMediaContentException.class)
            .hasMessage("media content must set exactly one of url or data");
    }

    @Test
    void modelMessagePart_acceptsImageAndFileMedia() {
        var image = ModelMessagePart.image(DataContent.data("aGVsbG8=", "image/png"));
        var file = ModelMessagePart.file(DataContent.url("https://example.com/manual.pdf",
            "application/pdf"));

        assertThat(image.getType()).isEqualTo(PartType.IMAGE);
        assertThat(image.getMedia().getMediaType()).isEqualTo("image/png");
        assertThat(file.getType()).isEqualTo(PartType.FILE);
        assertThat(file.getMedia().source()).isEqualTo(InputSource.URL);
    }

    @Test
    void generatedFile_reusesFileContentAndImageResultConvenience() {
        var file = GeneratedFile.url("https://example.com/generated.png", "image/png",
            "generated.png");
        var content = GenerationContentPart.file(file);
        var result = GenerateImageResult.builder()
            .images(List.of(file))
            .build();

        assertThat(content.getType()).isEqualTo(PartType.FILE);
        assertThat(content.getUrl()).isEqualTo(file.getUrl());
        assertThat(result.getImage()).isSameAs(file);
    }

    @Test
    void generateImageRequest_acceptsProviderOptionsAndCapabilityRequirements() {
        var request = GenerateImageRequest.builder()
            .prompt("Halo mascot")
            .n(2)
            .size(1024)
            .responseFormat(ImageResponseFormat.URL)
            .providerOptions(ProviderOptions.namespace("openai")
                .option("quality", "high")
                .build())
            .build();
        var wideRequest = GenerateImageRequest.builder()
            .prompt("Halo banner")
            .size(1024, 768)
            .build();
        var requirement = ModelCapabilityRequirement.languageImageInput("image/*",
            InputSource.DATA);

        assertThat(request.getSize()).isEqualTo("1024x1024");
        assertThat(wideRequest.getSize()).isEqualTo("1024x768");
        assertThat(request.getProviderOptions()).containsOnlyKeys("openai");
        assertThat(request.getProviderOptions().get("openai")).containsEntry("quality", "high");
        assertThat(requirement.getLanguage().getImageInput()).isTrue();
        assertThat(requirement.getLanguage().getInputMediaTypes()).containsExactly("image/*");
        assertThatThrownBy(() -> GenerateImageRequest.builder().size(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("edge must be positive");
        assertThatThrownBy(() -> GenerateImageRequest.builder().size(1024, -1))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("height must be positive");
    }

    @Test
    void uiMessageStreamWriter_acceptsGeneratedFiles() {
        var file = GeneratedFile.base64("abc123", "image/png", "generated.png");
        var stream = UIMessageStreams.create("assistant-1", writer ->
            writer.writeFile("image-1", file));
        var message = UIMessageStreamReader.read(options -> options.stream(stream))
            .responseMessage()
            .block();

        assertThat(message.parts())
            .singleElement()
            .isInstanceOfSatisfying(FilePart.class, part -> {
                assertThat(part.fileId()).isEqualTo("image-1");
                assertThat(part.mediaType()).isEqualTo("image/png");
                assertThat(part.data()).isEqualTo("abc123");
                assertThat(part.title()).isEqualTo("generated.png");
            });
    }

    @Test
    void approvalPartsHaveTypedFactories() {
        var request = ToolApprovalRequest.from(ToolCall.builder()
            .toolCallId("call_1")
            .toolName("run")
            .input(Map.of("command", "rm file"))
            .build(), "approval_call_1", 0, Map.of());
        var response = ToolApprovalResponse.builder()
            .approvalId("approval_call_1")
            .toolCallId("call_1")
            .toolName("run")
            .approved(false)
            .reason("Destructive command")
            .build();

        assertThat(ModelMessagePart.toolApprovalRequest(request).getType())
            .isEqualTo(PartType.TOOL_APPROVAL_REQUEST);
        assertThat(ModelMessagePart.toolApprovalResponse(response).getApproved())
            .isFalse();
        assertThat(GenerationContentPart.toolApprovalRequest(request).getApprovalId())
            .isEqualTo("approval_call_1");
        assertThat(TextStreamPart.toolApprovalRequest(request).getType())
            .isEqualTo(PartType.TOOL_APPROVAL_REQUEST);
    }

    @Test
    void generateTextRequest_acceptsTransientToolCallRepairCallback() throws Exception {
        var mapper = JsonMapper.builder().build();
        var request = GenerateTextRequest.builder()
            .prompt("repair tool")
            .toolCallRepair(context -> Mono.just(ToolCallRepairResult.repaired(ToolCall.builder()
                .toolCallId(context.getToolCall().getToolCallId())
                .toolName(context.getToolCall().getToolName())
                .input(Map.of("location", "SF"))
                .build())))
            .build();

        var repaired = request.getToolCallRepair().repair(ToolCallRepairContext.builder()
            .toolCall(ToolCall.builder()
                .toolCallId("call_1")
                .toolName("weather")
                .input(Map.of("city", "SF"))
                .build())
            .build()).block();

        assertThat(repaired.getToolCall().getInput()).containsEntry("location", "SF");
        var json = mapper.writeValueAsString(request);
        assertThat(json).doesNotContain("toolCallRepair");
        var restored = mapper.readValue(json, GenerateTextRequest.class);
        assertThat(restored.getToolCallRepair()).isNull();
    }

    @Test
    void generationContentBuilderRejectsInvalidShape() {
        assertThatThrownBy(() -> GenerationContentPart.builder()
            .type(PartType.TOOL_RESULT)
            .toolCallId("call_1")
            .toolName("weather")
            .text("not valid here")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("tool-result generation content part has invalid fields");
    }

    @Test
    void streamPartBuilderRejectsMissingRequiredFields() {
        assertThatThrownBy(() -> TextStreamPart.builder()
            .type(PartType.TEXT_DELTA)
            .delta("hello")
            .build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("text-delta stream part id must not be blank");
    }
}
