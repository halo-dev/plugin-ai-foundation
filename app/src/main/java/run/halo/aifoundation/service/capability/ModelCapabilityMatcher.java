package run.halo.aifoundation.service.capability;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import run.halo.aifoundation.capability.ImageGenerationCapability;
import run.halo.aifoundation.capability.InputSource;
import run.halo.aifoundation.capability.LanguageCapability;
import run.halo.aifoundation.capability.ModelCapabilities;
import run.halo.aifoundation.capability.ModelCapabilityRequirement;

@Component
public class ModelCapabilityMatcher {

    public CapabilityMatchResult match(ModelCapabilities capabilities,
        ModelCapabilityRequirement requirement) {
        if (requirement == null) {
            return CapabilityMatchResult.success();
        }
        var issues = new ArrayList<CapabilityMatchIssue>();
        matchLanguage(capabilities == null ? null : capabilities.getLanguage(),
            requirement.getLanguage(), issues);
        matchImageGeneration(capabilities == null ? null : capabilities.getImageGeneration(),
            requirement.getImageGeneration(), issues);
        return issues.isEmpty()
            ? CapabilityMatchResult.success()
            : CapabilityMatchResult.unmatched(issues);
    }

    private void matchLanguage(LanguageCapability actual, LanguageCapability expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null) {
            return;
        }
        matchBoolean("language.imageInput", actual == null ? null : actual.getImageInput(),
            expected.getImageInput(), issues);
        matchBoolean("language.fileInput", actual == null ? null : actual.getFileInput(),
            expected.getFileInput(), issues);
        matchBoolean("language.reasoningHistory",
            actual == null ? null : actual.getReasoningHistory(),
            expected.getReasoningHistory(), issues);
        matchMediaTypes("language.inputMediaTypes",
            actual == null ? null : actual.getInputMediaTypes(),
            expected.getInputMediaTypes(), issues);
        matchInputSources("language.inputSources",
            actual == null ? null : actual.getInputSources(),
            expected.getInputSources(), issues);
    }

    private void matchImageGeneration(ImageGenerationCapability actual,
        ImageGenerationCapability expected, List<CapabilityMatchIssue> issues) {
        if (expected == null) {
            return;
        }
        matchBoolean("imageGeneration.textToImage",
            actual == null ? null : actual.getTextToImage(), expected.getTextToImage(), issues);
        matchBoolean("imageGeneration.imageToImage",
            actual == null ? null : actual.getImageToImage(), expected.getImageToImage(), issues);
        matchBoolean("imageGeneration.maskInput",
            actual == null ? null : actual.getMaskInput(), expected.getMaskInput(), issues);
        matchMinInteger("imageGeneration.maxImagesPerCall",
            actual == null ? null : actual.getMaxImagesPerCall(),
            expected.getMaxImagesPerCall(), issues);
        matchExactList("imageGeneration.sizes",
            actual == null ? null : actual.getSizes(), expected.getSizes(), issues);
        matchExactList("imageGeneration.aspectRatios",
            actual == null ? null : actual.getAspectRatios(), expected.getAspectRatios(), issues);
        matchMediaTypes("imageGeneration.outputMediaTypes",
            actual == null ? null : actual.getOutputMediaTypes(),
            expected.getOutputMediaTypes(), issues);
    }

    private void matchBoolean(String path, Boolean actual, Boolean expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null) {
            return;
        }
        if (!expected.equals(actual)) {
            issues.add(new CapabilityMatchIssue(path, expected, actual));
        }
    }

    private void matchMinInteger(String path, Integer actual, Integer expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null) {
            return;
        }
        if (actual == null || actual < expected) {
            issues.add(new CapabilityMatchIssue(path, expected, actual));
        }
    }

    private void matchInputSources(String path, List<InputSource> actual, List<InputSource> expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null || expected.isEmpty()) {
            return;
        }
        if (actual == null || !actual.containsAll(expected)) {
            issues.add(new CapabilityMatchIssue(path, expected, actual));
        }
    }

    private void matchExactList(String path, List<String> actual, List<String> expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null || expected.isEmpty()) {
            return;
        }
        if (actual == null || !actual.containsAll(expected)) {
            issues.add(new CapabilityMatchIssue(path, expected, actual));
        }
    }

    private void matchMediaTypes(String path, List<String> actual, List<String> expected,
        List<CapabilityMatchIssue> issues) {
        if (expected == null || expected.isEmpty()) {
            return;
        }
        if (actual == null || actual.isEmpty()) {
            issues.add(new CapabilityMatchIssue(path, expected, actual));
            return;
        }
        for (var required : expected) {
            if (actual.stream().noneMatch(supported -> coversMediaType(supported, required))) {
                issues.add(new CapabilityMatchIssue(path, required, actual));
            }
        }
    }

    boolean coversMediaType(String supported, String required) {
        if (!hasText(supported) || !hasText(required)) {
            return false;
        }
        var supportedParts = supported.toLowerCase().split("/", 2);
        var requiredParts = required.toLowerCase().split("/", 2);
        if (supportedParts.length != 2 || requiredParts.length != 2) {
            return supported.equalsIgnoreCase(required);
        }
        return coversPart(supportedParts[0], requiredParts[0])
            && coversPart(supportedParts[1], requiredParts[1]);
    }

    private boolean coversPart(String supported, String required) {
        return "*".equals(supported) || supported.equals(required);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
