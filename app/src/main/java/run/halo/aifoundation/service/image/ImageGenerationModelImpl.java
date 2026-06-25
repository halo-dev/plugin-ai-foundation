package run.halo.aifoundation.service.image;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;
import run.halo.aifoundation.chat.GenerationResponseMetadata;
import run.halo.aifoundation.exception.AiGenerationCancelledException;
import run.halo.aifoundation.exception.AiGenerationTimeoutException;
import run.halo.aifoundation.exception.ImageGenerationException;
import run.halo.aifoundation.exception.InvalidMediaContentException;
import run.halo.aifoundation.exception.MediaContentTooLargeException;
import run.halo.aifoundation.exception.UnsupportedModelCapabilityException;
import run.halo.aifoundation.image.GenerateImageRequest;
import run.halo.aifoundation.image.GenerateImageResult;
import run.halo.aifoundation.image.ImageGenerationModel;
import run.halo.aifoundation.image.ImageGenerationWarning;
import run.halo.aifoundation.image.ImageUsage;
import run.halo.aifoundation.media.DataContent;
import run.halo.aifoundation.media.GeneratedFile;
import run.halo.aifoundation.provider.support.ProviderImageGenerationClient;
import run.halo.aifoundation.service.capability.CapabilityMatchIssue;
import run.halo.aifoundation.service.capability.ModelCapabilityMatcher;
import run.halo.aifoundation.service.media.MediaResourcePolicy;

public class ImageGenerationModelImpl implements ImageGenerationModel {

    private static final int DEFAULT_MAX_IMAGES_PER_CALL = 1;
    private static final int DEFAULT_MAX_PARALLEL_CALLS = 2;

    private final ProviderImageGenerationClient client;
    private final ModelCapabilities modelCapabilities;
    private final String modelName;
    private final String providerName;
    private final String providerType;
    private final MediaResourcePolicy mediaResourcePolicy;
    private final ModelCapabilityMatcher capabilityMatcher;

    ImageGenerationModelImpl(ProviderImageGenerationClient client, ModelCapabilities modelCapabilities,
        String modelName, String providerName, String providerType,
        MediaResourcePolicy mediaResourcePolicy, ModelCapabilityMatcher capabilityMatcher) {
        this.client = client;
        this.modelCapabilities = modelCapabilities != null ? modelCapabilities
            : ModelCapabilities.empty();
        this.modelName = hasText(modelName) ? modelName : "unknown";
        this.providerName = hasText(providerName) ? providerName : "unknown";
        this.providerType = providerType;
        this.mediaResourcePolicy = mediaResourcePolicy != null ? mediaResourcePolicy
            : new MediaResourcePolicy();
        this.capabilityMatcher = capabilityMatcher != null ? capabilityMatcher
            : new ModelCapabilityMatcher();
    }

    @Override
    public Mono<GenerateImageResult> generateImage(GenerateImageRequest request) {
        return Mono.defer(() -> {
                validateRequest(request);
                checkCancellation(request);
                var invocation = prepareInvocation(request);
                return executeBatches(invocation)
                    .collectList()
                    .map(results -> aggregate(invocation, results));
            })
            .transform(mono -> withTimeout(mono, request))
            .doOnNext(ignored -> checkCancellation(request));
    }

    @Override
    public ModelCapabilities capabilities() {
        return modelCapabilities;
    }

    private ImageInvocation prepareInvocation(GenerateImageRequest request) {
        validateMedia(request);
        validateCapabilities(request);
        var warnings = new ArrayList<ImageGenerationWarning>();
        var adjusted = adjustedRequest(request, warnings);
        return new ImageInvocation(adjusted, warnings, batches(requestedImageCount(request)),
            concurrency(request));
    }

    private Flux<GenerateImageResult> executeBatches(ImageInvocation invocation) {
        return Flux.fromIterable(invocation.batchSizes())
            .flatMapSequential(batchSize -> invokeBatch(copyForBatch(invocation.request(), batchSize)),
                invocation.concurrency(), 1);
    }

    private Mono<GenerateImageResult> invokeBatch(GenerateImageRequest request) {
        var call = client.generateImage(request)
            .doOnSubscribe(ignored -> checkCancellation(request))
            .doOnNext(ignored -> checkCancellation(request));
        var maxRetries = maxRetries(request);
        if (maxRetries <= 0) {
            return call;
        }
        return call.retryWhen(Retry.max(maxRetries).filter(this::isRetryable));
    }

    private GenerateImageResult aggregate(ImageInvocation invocation,
        List<GenerateImageResult> results) {
        var images = new ArrayList<GeneratedFile>();
        var warnings = new ArrayList<ImageGenerationWarning>(invocation.warnings());
        var responses = new ArrayList<GenerationResponseMetadata>();
        var providerMetadata = new ArrayList<Map<String, Object>>();
        ImageUsage usage = null;
        for (var result : results) {
            if (result == null) {
                continue;
            }
            if (result.getImages() != null) {
                images.addAll(result.getImages());
            }
            if (result.getWarnings() != null) {
                warnings.addAll(result.getWarnings());
            }
            if (result.getResponses() != null) {
                responses.addAll(result.getResponses());
            }
            if (result.getProviderMetadata() != null && !result.getProviderMetadata().isEmpty()) {
                providerMetadata.add(result.getProviderMetadata());
            }
            usage = addUsage(usage, result.getUsage());
        }
        if (images.isEmpty()) {
            throw new ImageGenerationException("Image generation returned no images", modelName,
                providerName, providerType);
        }
        return GenerateImageResult.builder()
            .images(List.copyOf(images))
            .usage(usage != null ? usage : ImageUsage.builder().imageCount(images.size()).build())
            .warnings(List.copyOf(warnings))
            .responses(List.copyOf(responses))
            .providerMetadata(providerMetadata(providerMetadata))
            .build();
    }

    private void validateRequest(GenerateImageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Generate image request is required");
        }
        if (!hasText(request.getPrompt())) {
            throw new IllegalArgumentException("Image generation prompt is required");
        }
        if (request.getN() != null && request.getN() <= 0) {
            throw new IllegalArgumentException("Image generation n must be positive");
        }
        if (request.getMaxRetries() != null && request.getMaxRetries() < 0) {
            throw new IllegalArgumentException("Image generation maxRetries must not be negative");
        }
        if (request.getMaxParallelCalls() != null && request.getMaxParallelCalls() <= 0) {
            throw new IllegalArgumentException(
                "Image generation maxParallelCalls must be positive");
        }
    }

    private void validateMedia(GenerateImageRequest request) {
        var media = new ArrayList<DataContent>();
        if (request.getImages() != null) {
            for (var image : request.getImages()) {
                validateImageMedia(image, "image");
                media.add(image);
            }
        }
        if (request.getMask() != null) {
            validateImageMedia(request.getMask(), "mask");
            media.add(request.getMask());
        }
        try {
            mediaResourcePolicy.validate(media);
        } catch (InvalidMediaContentException | MediaContentTooLargeException e) {
            throw e;
        }
    }

    private void validateImageMedia(DataContent media, String name) {
        if (media == null) {
            throw new InvalidMediaContentException(name + " media content must not be null");
        }
        if (media.isUrl() == media.isData()) {
            throw new InvalidMediaContentException(
                name + " media content must set exactly one of url or data",
                media.getMediaType(), media.getFilename(), null, null);
        }
        if (!hasText(media.getMediaType())) {
            throw new InvalidMediaContentException(
                name + " mediaType is required for image generation media",
                media.getMediaType(), media.getFilename(), null, null);
        }
        if (!isImageMediaType(media.getMediaType())) {
            throw new InvalidMediaContentException(
                name + " mediaType must match image/*",
                media.getMediaType(), media.getFilename(), null, null);
        }
    }

    private void validateCapabilities(GenerateImageRequest request) {
        var expected = ImageGenerationCapability.builder()
            .textToImage(hasInputImages(request) ? null : Boolean.TRUE)
            .imageToImage(hasInputImages(request) ? Boolean.TRUE : null)
            .maskInput(request.getMask() != null ? Boolean.TRUE : null)
            .build();
        var result = capabilityMatcher.match(modelCapabilities,
            ModelCapabilityRequirement.builder().imageGeneration(expected).build());
        if (result.matched()) {
            return;
        }
        var issue = result.issues().isEmpty()
            ? new CapabilityMatchIssue("imageGeneration", expected, modelCapabilities)
            : result.issues().getFirst();
        throw new UnsupportedModelCapabilityException(modelName, providerName, providerType,
            issue.path(), issue.expected(), issue.actual());
    }

    private GenerateImageRequest adjustedRequest(GenerateImageRequest request,
        List<ImageGenerationWarning> warnings) {
        return GenerateImageRequest.builder()
            .prompt(request.getPrompt())
            .images(request.getImages())
            .mask(request.getMask())
            .n(request.getN())
            .size(supportedSize(request, warnings))
            .aspectRatio(supportedAspectRatio(request, warnings))
            .seed(request.getSeed())
            .responseFormat(request.getResponseFormat())
            .providerOptions(request.getProviderOptions())
            .headers(request.getHeaders())
            .maxRetries(request.getMaxRetries())
            .maxParallelCalls(request.getMaxParallelCalls())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .cancellationToken(request.getCancellationToken())
            .timeouts(request.getTimeouts())
            .build();
    }

    private String supportedSize(GenerateImageRequest request,
        List<ImageGenerationWarning> warnings) {
        var size = request.getSize();
        var supported = imageGenerationCapabilities().getSizes();
        if (!hasText(size) || supported == null || supported.isEmpty()
            || supported.contains(size)) {
            return size;
        }
        warnings.add(warning("size-unsupported",
            "Requested image size is not declared by the resolved model and was ignored.",
            Map.of("requested", size, "supported", supported)));
        return null;
    }

    private String supportedAspectRatio(GenerateImageRequest request,
        List<ImageGenerationWarning> warnings) {
        var aspectRatio = request.getAspectRatio();
        var supported = imageGenerationCapabilities().getAspectRatios();
        if (!hasText(aspectRatio) || supported == null || supported.isEmpty()
            || supported.contains(aspectRatio)) {
            return aspectRatio;
        }
        warnings.add(warning("aspect-ratio-unsupported",
            "Requested image aspect ratio is not declared by the resolved model and was ignored.",
            Map.of("requested", aspectRatio, "supported", supported)));
        return null;
    }

    private List<Integer> batches(int count) {
        var maxImagesPerCall = maxImagesPerCall();
        var batches = new ArrayList<Integer>();
        var remaining = count;
        while (remaining > 0) {
            var batch = Math.min(remaining, maxImagesPerCall);
            batches.add(batch);
            remaining -= batch;
        }
        return batches;
    }

    private GenerateImageRequest copyForBatch(GenerateImageRequest request, int n) {
        return GenerateImageRequest.builder()
            .prompt(request.getPrompt())
            .images(request.getImages())
            .mask(request.getMask())
            .n(n)
            .size(request.getSize())
            .aspectRatio(request.getAspectRatio())
            .seed(request.getSeed())
            .responseFormat(request.getResponseFormat())
            .providerOptions(request.getProviderOptions())
            .headers(request.getHeaders())
            .maxRetries(request.getMaxRetries())
            .maxParallelCalls(request.getMaxParallelCalls())
            .metadata(request.getMetadata())
            .context(request.getContext())
            .cancellationToken(request.getCancellationToken())
            .timeouts(request.getTimeouts())
            .build();
    }

    private ImageUsage addUsage(ImageUsage left, ImageUsage right) {
        if (right == null) {
            return left;
        }
        if (left == null) {
            return ImageUsage.builder()
                .inputTokens(right.getInputTokens())
                .outputTokens(right.getOutputTokens())
                .totalTokens(right.getTotalTokens())
                .imageCount(right.getImageCount())
                .raw(right.getRaw())
                .build();
        }
        return ImageUsage.builder()
            .inputTokens(add(left.getInputTokens(), right.getInputTokens()))
            .outputTokens(add(left.getOutputTokens(), right.getOutputTokens()))
            .totalTokens(add(left.getTotalTokens(), right.getTotalTokens()))
            .imageCount(add(left.getImageCount(), right.getImageCount()))
            .raw(rawUsage(left.getRaw(), right.getRaw()))
            .build();
    }

    private List<Object> rawUsage(Object left, Object right) {
        var values = new ArrayList<>();
        if (left != null) {
            values.add(left);
        }
        if (right != null) {
            values.add(right);
        }
        return List.copyOf(values);
    }

    private Integer add(Integer left, Integer right) {
        if (left == null) {
            return right;
        }
        if (right == null) {
            return left;
        }
        return left + right;
    }

    private Map<String, Object> providerMetadata(List<Map<String, Object>> batchMetadata) {
        var metadata = new LinkedHashMap<String, Object>();
        metadata.put("providerType", providerType);
        metadata.put("providerName", providerName);
        metadata.put("modelName", modelName);
        if (!batchMetadata.isEmpty()) {
            metadata.put("batches", List.copyOf(batchMetadata));
        }
        return metadata;
    }

    private ImageGenerationWarning warning(String code, String message,
        Map<String, Object> metadata) {
        return ImageGenerationWarning.builder()
            .code(code)
            .message(message)
            .providerMetadata(metadata)
            .build();
    }

    private int requestedImageCount(GenerateImageRequest request) {
        return request.getN() != null ? request.getN() : 1;
    }

    private int maxImagesPerCall() {
        var value = imageGenerationCapabilities().getMaxImagesPerCall();
        return value != null && value > 0 ? value : DEFAULT_MAX_IMAGES_PER_CALL;
    }

    private int concurrency(GenerateImageRequest request) {
        var requested = request.getMaxParallelCalls() != null
            ? request.getMaxParallelCalls()
            : DEFAULT_MAX_PARALLEL_CALLS;
        return Math.max(1, Math.min(requested, batches(requestedImageCount(request)).size()));
    }

    private int maxRetries(GenerateImageRequest request) {
        return request.getMaxRetries() != null ? request.getMaxRetries() : 0;
    }

    private ImageGenerationCapability imageGenerationCapabilities() {
        return modelCapabilities.getImageGeneration() != null
            ? modelCapabilities.getImageGeneration()
            : ImageGenerationCapability.unknown();
    }

    private boolean hasInputImages(GenerateImageRequest request) {
        return request.getImages() != null && !request.getImages().isEmpty();
    }

    private void checkCancellation(GenerateImageRequest request) {
        if (request != null && request.getCancellationToken() != null
            && request.getCancellationToken().isCancellationRequested()) {
            throw new AiGenerationCancelledException("Image generation was cancelled");
        }
    }

    private <T> Mono<T> withTimeout(Mono<T> mono, GenerateImageRequest request) {
        var timeout = timeout(request);
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            return mono;
        }
        return mono.timeout(timeout)
            .onErrorMap(TimeoutException.class,
                error -> new AiGenerationTimeoutException("image", timeout, error));
    }

    private Duration timeout(GenerateImageRequest request) {
        return request != null && request.getTimeouts() != null
            ? request.getTimeouts().getTotalTimeout()
            : null;
    }

    private boolean isRetryable(Throwable error) {
        return !(error instanceof IllegalArgumentException)
            && !(error instanceof UnsupportedModelCapabilityException)
            && !(error instanceof InvalidMediaContentException)
            && !(error instanceof MediaContentTooLargeException)
            && !(error instanceof AiGenerationCancelledException)
            && !(error instanceof ImageGenerationException);
    }

    private boolean isImageMediaType(String mediaType) {
        return mediaType != null && mediaType.toLowerCase().startsWith("image/");
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record ImageInvocation(GenerateImageRequest request,
                                   List<ImageGenerationWarning> warnings,
                                   List<Integer> batchSizes,
                                   int concurrency) {
    }
}
