package run.halo.aifoundation.service.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.exception.ImageGenerationException;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;

class ImageGenerationModelImplTest {

    @Test
    void generateImage_invokesProviderAndReturnsImages() {
        var client = mock(ProviderImageGenerationClient.class);
        when(client.generateImage(any())).thenReturn(Mono.just(result("img-1")));
        var model = imageModel(client, ImageGenerationCapability.builder()
            .textToImage(true)
            .maxImagesPerCall(1)
            .build());

        StepVerifier.create(model.generateImage("A quiet Halo console"))
            .assertNext(result -> {
                assertThat(result.getImage().getBase64()).isEqualTo("img-1");
                assertThat(result.getUsage().getImageCount()).isEqualTo(1);
                assertThat(result.getProviderMetadata())
                    .containsEntry("providerType", "openai");
            })
            .verifyComplete();

        verify(client).generateImage(any());
        assertThat(model.capabilities().getImageGeneration().getTextToImage()).isTrue();
    }

    @Test
    void generateImage_treatsUnknownTextToImageAsUnsupported() {
        var model = imageModel(mock(ProviderImageGenerationClient.class),
            ImageGenerationCapability.unknown());

        StepVerifier.create(model.generateImage("Draw"))
            .expectErrorSatisfies(error -> {
                assertThat(error).isInstanceOf(UnsupportedModelCapabilityException.class);
                var capabilityError = (UnsupportedModelCapabilityException) error;
                assertThat(capabilityError.getCapabilityPath())
                    .isEqualTo("imageGeneration.textToImage");
                assertThat(capabilityError.getActualValue()).isNull();
            })
            .verify();
    }

    @Test
    void generateImage_validatesImageToImageMaskAndMediaBeforeProviderCall() {
        var client = mock(ProviderImageGenerationClient.class);
        when(client.generateImage(any())).thenReturn(Mono.just(result("img-1")));
        var model = imageModel(client, ImageGenerationCapability.builder()
            .imageToImage(true)
            .maskInput(true)
            .maxImagesPerCall(1)
            .build());

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Edit")
                .images(List.of(DataContent.data(new byte[] {1}, "image/png")))
                .mask(DataContent.data(new byte[] {2}, "image/png"))
                .build()))
            .expectNextCount(1)
            .verifyComplete();

        verify(client).generateImage(any());

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Edit")
                .images(List.of(DataContent.data(new byte[] {1}, "application/pdf")))
                .build()))
            .expectErrorMessage("image mediaType must match image/*")
            .verify();
    }

    @Test
    void generateImage_rejectsMaskWithoutInputImage() {
        var client = mock(ProviderImageGenerationClient.class);
        var model = imageModel(client, ImageGenerationCapability.builder()
            .textToImage(true)
            .maskInput(true)
            .maxImagesPerCall(1)
            .build());

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Edit")
                .mask(DataContent.data(new byte[] {2}, "image/png"))
                .build()))
            .expectErrorMessage("Image generation mask requires at least one input image")
            .verify();
    }

    @Test
    void generateImage_rejectsNullInputImage() {
        var client = mock(ProviderImageGenerationClient.class);
        var model = imageModel(client, ImageGenerationCapability.builder()
            .imageToImage(true)
            .maxImagesPerCall(1)
            .build());
        var images = new ArrayList<DataContent>();
        images.add(null);

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Edit")
                .images(images)
                .build()))
            .expectError(InvalidMediaContentException.class)
            .verify();
    }

    @Test
    void generateImage_splitsByMaxImagesPerCallAndAggregatesInOrder() {
        var requestedCounts = new ArrayList<Integer>();
        var negativePrompts = new ArrayList<String>();
        ProviderImageGenerationClient client = request -> {
            requestedCounts.add(request.getN());
            negativePrompts.add(request.getNegativePrompt());
            var images = new ArrayList<GeneratedFile>();
            for (int index = 0; index < request.getN(); index++) {
                images.add(GeneratedFile.base64("batch-" + requestedCounts.size() + "-" + index,
                    "image/png"));
            }
            return Mono.just(GenerateImageResult.builder()
                .images(images)
                .usage(ImageUsage.builder().imageCount(request.getN()).build())
                .providerMetadata(Map.of("n", request.getN()))
                .build());
        };
        var model = imageModel(client, ImageGenerationCapability.builder()
            .textToImage(true)
            .maxImagesPerCall(2)
            .build());

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Draw five")
                .negativePrompt("blurry")
                .n(5)
                .maxParallelCalls(2)
                .build()))
            .assertNext(result -> {
                assertThat(requestedCounts).containsExactly(2, 2, 1);
                assertThat(negativePrompts).containsExactly("blurry", "blurry", "blurry");
                assertThat(result.getImages()).extracting(GeneratedFile::getBase64)
                    .containsExactly("batch-1-0", "batch-1-1", "batch-2-0", "batch-2-1",
                        "batch-3-0");
                assertThat(result.getUsage().getImageCount()).isEqualTo(5);
                assertThat((List<?>) result.getProviderMetadata().get("batches")).hasSize(3);
            })
            .verifyComplete();
    }

    @Test
    void generateImage_warnsAndDropsUnsupportedOptionalSize() {
        var seenRequests = new ArrayList<GenerateImageRequest>();
        ProviderImageGenerationClient client = request -> {
            seenRequests.add(request);
            return Mono.just(result("img-1"));
        };
        var model = imageModel(client, ImageGenerationCapability.builder()
            .textToImage(true)
            .maxImagesPerCall(1)
            .sizes(List.of("1024x1024"))
            .build());

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Draw")
                .size("512x512")
                .build()))
            .assertNext(result -> {
                assertThat(seenRequests.getFirst().getSize()).isNull();
                assertThat(result.getWarnings()).extracting("code")
                    .contains("size-unsupported");
            })
            .verifyComplete();
    }

    @Test
    void generateImage_omitsAdministratorMappedUnsupportedFieldsAcrossBatches() {
        var seenRequests = new ArrayList<GenerateImageRequest>();
        ProviderImageGenerationClient client = request -> {
            seenRequests.add(request);
            return Mono.just(result("img-" + seenRequests.size()));
        };
        var mappings = new run.halo.aifoundation.provider.mapping.EffectiveParameterMappings(Map.of(
            run.halo.aifoundation.provider.mapping.ModelParameter.IMAGE_COUNT,
            effective(run.halo.aifoundation.extension.ModelParameterMappings.Mode.TEMPLATE,
                "image.n"),
            run.halo.aifoundation.provider.mapping.ModelParameter.NEGATIVE_PROMPT,
            effective(run.halo.aifoundation.extension.ModelParameterMappings.Mode.UNSUPPORTED,
                null)));
        var model = new ImageGenerationModelImpl(client,
            ModelCapabilities.imageGeneration(ImageGenerationCapability.builder()
                .textToImage(true).maxImagesPerCall(2).build()),
            "image-model", "provider-a", "openai", new MediaResourcePolicy(),
            new ModelCapabilityMatcher(), mappings);

        StepVerifier.create(model.generateImage(GenerateImageRequest.builder()
                .prompt("Draw")
                .n(3)
                .negativePrompt("blurry")
                .build()))
            .assertNext(response -> {
                assertThat(seenRequests).extracting(GenerateImageRequest::getN)
                    .containsExactly(2, 1);
                assertThat(seenRequests).extracting(GenerateImageRequest::getNegativePrompt)
                    .containsOnlyNulls();
                assertThat(response.getWarnings()).singleElement()
                    .satisfies(warning -> assertThat(warning.getProviderMetadata())
                        .containsEntry("parameter", "NEGATIVE_PROMPT")
                        .containsEntry("modelName", "image-model")
                        .containsEntry("providerName", "provider-a"));
            })
            .verifyComplete();
    }

    @Test
    void generateImage_failsWhenProviderReturnsNoImages() {
        var client = mock(ProviderImageGenerationClient.class);
        when(client.generateImage(any())).thenReturn(Mono.just(GenerateImageResult.builder()
            .images(List.of())
            .build()));
        var model = imageModel(client, ImageGenerationCapability.builder()
            .textToImage(true)
            .maxImagesPerCall(1)
            .build());

        StepVerifier.create(model.generateImage("Draw"))
            .expectError(ImageGenerationException.class)
            .verify();
    }

    private ImageGenerationModelImpl imageModel(ProviderImageGenerationClient client,
        ImageGenerationCapability capability) {
        return new ImageGenerationModelImpl(client, ModelCapabilities.imageGeneration(capability),
            "image-model", "openai-provider", "openai", new MediaResourcePolicy(),
            new ModelCapabilityMatcher());
    }

    private GenerateImageResult result(String base64) {
        return GenerateImageResult.builder()
            .images(List.of(GeneratedFile.base64(base64, "image/png")))
            .usage(ImageUsage.builder().imageCount(1).build())
            .providerMetadata(Map.of("requestId", base64))
            .build();
    }

    private run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.EffectiveMapping effective(
        run.halo.aifoundation.extension.ModelParameterMappings.Mode mode, String template) {
        return new run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.EffectiveMapping(
            mode, template, null,
            run.halo.aifoundation.provider.mapping.EffectiveParameterMappings.Source.MODEL);
    }
}
