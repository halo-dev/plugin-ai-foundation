package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;

/** Verifies the attachment business owns safe, bounded attachment-list queries. */
class HaloAttachmentListTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloAttachmentTools tools = new HaloAttachmentTools(
            client,
            mock(run.halo.app.core.extension.service.AttachmentService.class),
            mock(PublicHttpClient.class),
            mock(HaloToolAccess.class),
            mock(DocumentReaderSettings.class));

    @Test
    void returnsOnlyAttachmentDisplayMetadataAndThePersistedPublicPermalink() {
        var attachment = attachment("image", "https://cdn.example.com/image.png");
        attachment
                .getMetadata()
                .setAnnotations(Map.of("storage-path", "/private/files/image.png", "access-key", "private-key"));
        attachment.getSpec().setOwnerName("private-user");
        attachment.getSpec().setPolicyName("private-policy");
        attachment.getSpec().setGroupName("private-group");
        attachment.getStatus().setThumbnails(Map.of("large", "https://cdn.example.com/private?token=x"));
        when(client.listBy(eq(Attachment.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(attachment))));
        var output = result(tools.listAttachments(Map.of("query", "image")));
        assertThat(items(output).getFirst())
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "name",
                        "image",
                        "displayName",
                        "image.png",
                        "mediaType",
                        "image/png",
                        "size",
                        128L,
                        "url",
                        "https://cdn.example.com/image.png"));
        assertThat(output.toString()).doesNotContain("private", "token");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(Attachment.class), options.capture(), any());
        assertThat(options.getValue().toCondition().toString()).contains("spec.displayName", "metadata.name", "image");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "https://cdn.example.com/image.png?X-Amz-Signature=secret",
                "https://user:secret@cdn.example.com/image.png",
                "file:///private/files/image.png",
                "javascript:alert(1)",
                "/upload/image.png?token=secret",
                "https://cdn.example.com/a#secret",
                "/private/file name.png",
                "C:\\private\\image.png"
            })
    void omitsSensitiveAndInvalidAttachmentUrls(String url) {
        when(client.listBy(eq(Attachment.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(attachment("image", url)))));
        assertThat(items(result(tools.listAttachments(Map.of()))).getFirst()).containsEntry("url", "");
    }

    @Test
    void rejectsInvalidPaginationAndQueriesBeforeTouchingTheExtensionClient() {
        var values = List.<Map<String, Object>>of(
                Map.of("page", 0),
                Map.of("page", 10_001),
                Map.of("page", 1.5),
                Map.of("size", 0),
                Map.of("size", 101),
                Map.of("size", "10"),
                Map.of("size", Double.NaN),
                Map.of("size", Long.MAX_VALUE),
                Map.of("query", 1),
                Map.of("query", "x".repeat(201)));
        for (var input : values) {
            StepVerifier.create(tools.listAttachments(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        var withNull = new HashMap<String, Object>();
        withNull.put("size", null);
        StepVerifier.create(tools.listAttachments(withNull))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    @Test
    void acceptsMaximumPaginationAndReturnsEmptyLists() {
        when(client.listBy(eq(Attachment.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(10_000, 100, 0, List.<Attachment>of())));
        var output = result(tools.listAttachments(Map.of("page", 10_000, "size", 100, "query", "  ")));
        assertThat(output)
                .containsEntry("page", 10_000)
                .containsEntry("size", 100)
                .containsEntry("total", 0L)
                .containsEntry("hasNext", false);
        assertThat(items(output)).isEmpty();
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Attachment.class), any(), page.capture());
        assertThat(page.getValue().getPageNumber()).isEqualTo(10_000);
        assertThat(page.getValue().getPageSize()).isEqualTo(100);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Mono<Object> result) {
        return (Map<String, Object>) result.block();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }

    private static Metadata metadata(String name) {
        var metadata = new Metadata();
        metadata.setName(name);
        return metadata;
    }

    private static Attachment attachment(String name, String url) {
        var attachment = new Attachment();
        attachment.setMetadata(metadata(name));
        var spec = new Attachment.AttachmentSpec();
        spec.setDisplayName(name + ".png");
        spec.setMediaType("image/png");
        spec.setSize(128L);
        attachment.setSpec(spec);
        var status = new Attachment.AttachmentStatus();
        status.setPermalink(url);
        attachment.setStatus(status);
        return attachment;
    }
}
