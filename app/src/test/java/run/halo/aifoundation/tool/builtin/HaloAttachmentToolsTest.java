package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolApprovalPolicy;
import run.halo.aifoundation.tool.ToolPermissionDeniedBehavior;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Group;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.service.AttachmentService;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

class HaloAttachmentToolsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final AttachmentService attachments = mock(AttachmentService.class);
    private final PublicHttpClient http = mock(PublicHttpClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final DocumentReaderSettings settings = mock(DocumentReaderSettings.class);
    private final HaloAttachmentTools tools = new HaloAttachmentTools(client, attachments, http, access, settings);

    @BeforeEach
    void setup() {
        when(access.requireResource(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.empty());
        when(settings.importOptions())
                .thenReturn(Mono.just(new DocumentReaderSettings.Options(false, 20, 200_000, 200)));
        when(attachments.getPermalink(any())).thenReturn(Mono.empty());
    }

    @Test
    void registersOneSharedMediaGroupAndRequiresApprovalOnlyForImport() {
        var registrations = tools.tools().collectList().block();
        assertThat(registrations).hasSize(5).allSatisfy(tool -> {
            assertThat(tool.getGroup()).isEqualTo(HaloAttachmentTools.ATTACHMENT_GROUP);
            assertThat(tool.getCategory()).isEqualTo("media");
            assertThat(tool.getDefinition().getApprovalPolicy().getMode())
                    .isEqualTo(
                            "import_attachment".equals(tool.getDefinition().getName())
                                    ? ToolApprovalPolicy.Mode.ALWAYS
                                    : ToolApprovalPolicy.Mode.NEVER);
        });
    }

    @Test
    void declaresPrimaryPermissionsAlongsideEachToolDefinition() {
        var registrations = tools.tools().collectList().block();
        assertThat(registrations)
                .allSatisfy(tool -> assertThat(tool.getPermissionDeniedBehavior())
                        .isEqualTo(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE));
        var byName = registrations.stream()
                .collect(java.util.stream.Collectors.toMap(
                        tool -> tool.getDefinition().getName(), tool -> tool));
        assertThat(byName.get("list_attachments").getRequiredPermissions())
                .containsExactly(resource("storage.halo.run", "attachments", "list"));
        assertThat(byName.get("get_attachment").getRequiredPermissions())
                .containsExactly(named("storage.halo.run", "attachments", "get", "name"));
        assertThat(byName.get("list_attachment_policies").getRequiredPermissions())
                .containsExactly(resource("storage.halo.run", "policies", "list"));
        assertThat(byName.get("list_attachment_groups").getRequiredPermissions())
                .containsExactly(resource("storage.halo.run", "groups", "list"));
        assertThat(byName.get("import_attachment").getRequiredPermissions())
                .containsExactly(
                        resource("console.api.storage.halo.run", "attachments/upload", "create"),
                        named("storage.halo.run", "policies", "get", "policyName"));
    }

    @Test
    void attachmentDownloadsCheckTheExactResourceBeforeReadingMetadataOrRequestingSignedAddresses() {
        when(access.requireResource("storage.halo.run", "attachments", "get", "private-document"))
                .thenReturn(Mono.error(new AccessDeniedException("attachment denied")));
        StepVerifier.create(tools.downloadAttachment("private-document", 1024, false))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(client, http, attachments, settings);
    }

    @Test
    void exposesSafePolicyAndGroupProjectionsWithSearchAndPagination() {
        var first = policy("policy-a", "Local storage");
        first.getSpec().setConfigMapName("secret-storage-config");
        var second = policy("policy-b", "Object storage");
        when(client.listAll(eq(Policy.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(first, second));
        StepVerifier.create(tools.listPolicies(Map.of("query", "storage", "page", 2, "size", 1)))
                .assertNext(value -> {
                    var result = map(value);
                    assertThat(result).containsEntry("total", 2).containsEntry("hasNext", false);
                    assertThat(result.toString()).doesNotContain("secret-storage-config", "configMapName");
                    assertThat((List<?>) result.get("items")).hasSize(1);
                })
                .verifyComplete();
        var group = new Group();
        group.setMetadata(metadata("group"));
        var spec = new Group.GroupSpec();
        spec.setDisplayName("Documents");
        group.setSpec(spec);
        when(client.listAll(eq(Group.class), any(ListOptions.class), any(Sort.class)))
                .thenReturn(Flux.just(group));
        StepVerifier.create(tools.listGroups(Map.of("query", "DOCUMENTS")))
                .assertNext(value -> assertThat(map(value)).containsEntry("total", 1))
                .verifyComplete();
    }

    @Test
    void importsOnlySafelyDownloadedBytesThroughHalosNativeUploadAndReturnsAnImmediatePublicUrl() {
        var uri = URI.create("https://example.com/document.pdf");
        var bytes = "downloaded fixture".getBytes(StandardCharsets.UTF_8);
        var saved = attachment("saved", bytes.length);
        when(client.fetch(Policy.class, "local")).thenReturn(Mono.just(policy("local", "Local")));
        when(http.getResource(uri, 20 * 1_048_576, false))
                .thenReturn(Mono.just(new PublicHttpClient.Response(bytes, MediaType.APPLICATION_PDF, uri)));
        when(attachments.upload(eq("local"), isNull(), eq("document.pdf"), any(), eq(MediaType.APPLICATION_PDF)))
                .thenAnswer(invocation -> {
                    Flux<org.springframework.core.io.buffer.DataBuffer> content = invocation.getArgument(3);
                    return DataBufferUtils.join(content).map(buffer -> {
                        var actual = new byte[buffer.readableByteCount()];
                        buffer.read(actual);
                        DataBufferUtils.release(buffer);
                        assertThat(actual).isEqualTo(bytes);
                        return saved;
                    });
                });
        when(attachments.getPermalink(saved)).thenReturn(Mono.just(URI.create("/upload/document.pdf")));
        StepVerifier.create(tools.importAttachment(Map.of("url", uri.toString(), "policyName", "local")))
                .assertNext(value -> assertThat(map(value))
                        .containsEntry("name", "saved")
                        .containsEntry("url", "/upload/document.pdf")
                        .containsEntry("urlReady", true))
                .verifyComplete();
        verify(attachments).upload(eq("local"), isNull(), eq("document.pdf"), any(), eq(MediaType.APPLICATION_PDF));
    }

    @Test
    void neverExposesPresignedAddressesOrPrivateAnnotationValues() {
        var value = attachment("private", 10);
        value.getMetadata().setAnnotations(Map.of("storage.halo.run/path", "/private/storage/secret.pdf"));
        when(client.fetch(Attachment.class, "private")).thenReturn(Mono.just(value));
        when(attachments.getPermalink(value))
                .thenReturn(Mono.just(URI.create("https://example.com/private.pdf?Signature=secret-token")));
        StepVerifier.create(tools.getAttachment(Map.of("name", "private")))
                .assertNext(result -> {
                    assertThat(map(result)).containsEntry("url", "").containsEntry("urlReady", false);
                    assertThat(result.toString()).doesNotContain("secret-token", "Signature", "/private/storage");
                })
                .verifyComplete();
    }

    @Test
    void downloadsRegisteredLocalAttachmentsThroughTheDedicatedLoopbackPath() {
        var value = attachment("document", 12);
        when(client.fetch(Attachment.class, "document")).thenReturn(Mono.just(value));
        var location = URI.create("http://localhost:8090/upload/document.pdf");
        when(attachments.getSharedURL(value, Duration.ofMinutes(2))).thenReturn(Mono.just(location));
        when(http.getLocalAttachment(location, 1024))
                .thenReturn(Mono.just(new PublicHttpClient.Response(new byte[] {1}, MediaType.APPLICATION_PDF)));
        StepVerifier.create(tools.downloadAttachment("document", 1024, false))
                .expectNextCount(1)
                .verifyComplete();
        verify(http).getLocalAttachment(location, 1024);
    }

    @Test
    void rejectsOversizedAndDeletedAttachmentsAndInvalidStorageBeforeDownloading() {
        var value = attachment("large", 1025);
        when(client.fetch(Attachment.class, "large")).thenReturn(Mono.just(value));
        StepVerifier.create(tools.downloadAttachment("large", 1024, false))
                .expectErrorMessage("Attachment exceeds the download size limit")
                .verify();
        value.getMetadata().setDeletionTimestamp(Instant.now());
        StepVerifier.create(tools.getAttachment(Map.of("name", "large")))
                .expectErrorMessage("Attachment does not exist")
                .verify();
        when(client.fetch(Policy.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(tools.importAttachment(Map.of("url", "https://example.com/file", "policyName", "missing")))
                .expectErrorMessage("Storage policy does not exist")
                .verify();
        verifyNoInteractions(http, attachments);
    }

    @Test
    void rejectsInvalidGroupAndFilenamesWithoutFetchingTheRemoteFile() {
        when(client.fetch(Policy.class, "local")).thenReturn(Mono.just(policy("local", "Local")));
        when(client.fetch(Group.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(tools.importAttachment(
                        Map.of("url", "https://example.com/file", "policyName", "local", "groupName", "missing")))
                .expectErrorMessage("Attachment group does not exist")
                .verify();
        StepVerifier.create(tools.importAttachment(
                        Map.of("url", "https://example.com/file", "policyName", "local", "filename", "../file.pdf")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(http, attachments);
    }

    @Test
    void optionalGroupPermissionIsCheckedBeforeReadingItOrStartingTheDownload() {
        when(client.fetch(Policy.class, "local")).thenReturn(Mono.just(policy("local", "Local")));
        when(access.requireResource("storage.halo.run", "groups", "get", "private-group"))
                .thenReturn(Mono.error(new AccessDeniedException("group denied")));
        StepVerifier.create(tools.importAttachment(Map.of(
                        "url", "https://example.com/file.pdf", "policyName", "local", "groupName", "private-group")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, org.mockito.Mockito.never()).fetch(Group.class, "private-group");
        verifyNoInteractions(http, attachments, settings);
    }

    @Test
    void malformedSignedInputAndPersistedAddressesNeverLeakTheirQuery() {
        var malformed = "https://example.com/doc?token=private token";
        StepVerifier.create(tools.importAttachment(Map.of("url", malformed, "policyName", "local")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Invalid URL")
                        .hasNoCause())
                .verify();
        verifyNoInteractions(client, http, attachments);
        var value = attachment("malformed", 10);
        var status = new Attachment.AttachmentStatus();
        status.setPermalink(malformed);
        value.setStatus(status);
        when(client.fetch(Attachment.class, "malformed")).thenReturn(Mono.just(value));
        when(attachments.getSharedURL(value, Duration.ofMinutes(2))).thenReturn(Mono.empty());
        StepVerifier.create(tools.downloadAttachment("malformed", 1024, false))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Invalid URL")
                        .hasNoCause())
                .verify();
        verifyNoInteractions(http);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    private static Policy policy(String name, String displayName) {
        var value = new Policy();
        value.setMetadata(metadata(name));
        var spec = new Policy.PolicySpec();
        spec.setDisplayName(displayName);
        spec.setTemplateName("local");
        value.setSpec(spec);
        return value;
    }

    private static Attachment attachment(String name, long size) {
        var value = new Attachment();
        value.setMetadata(metadata(name));
        var spec = new Attachment.AttachmentSpec();
        spec.setDisplayName(name + ".pdf");
        spec.setMediaType("application/pdf");
        spec.setSize(size);
        spec.setPolicyName("local");
        value.setSpec(spec);
        return value;
    }
}
