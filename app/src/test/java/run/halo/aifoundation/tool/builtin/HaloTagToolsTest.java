package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.app.core.extension.content.Tag;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;

/**
 * Verifies tag lookup and mutation contracts using the native extension client boundary.
 *
 * <p>Examples cover lookup projections, unique slugs, omitted and cleared fields, unsupported input and concurrent
 * metadata versions. These cases exercise the business provider directly; registry authorization is verified by the
 * shared tool service tests.
 */
class HaloTagToolsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloTagTools tools = new HaloTagTools(client);

    @BeforeEach
    void setUp() {
        when(client.countBy(eq(Tag.class), any())).thenReturn(Mono.just(0L));
        when(client.create(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.getMetadata().setVersion(1L);
            return Mono.just(tag);
        });
        when(client.update(any(Tag.class))).thenAnswer(invocation -> {
            Tag tag = invocation.getArgument(0);
            tag.getMetadata().setVersion(tag.getMetadata().getVersion() + 1);
            return Mono.just(tag);
        });
    }

    @Test
    void createsTagsWithOptionalColorAndReturnsTheVersionNeededByTheNextEdit() {
        var result = map(tools.createTag(Map.of(
                        "displayName",
                        "Halo",
                        "slug",
                        "halo",
                        "color",
                        "#aBc",
                        "description",
                        "Halo tag",
                        "cover",
                        "https://example.com/a.png"))
                .block());
        assertThat(result)
                .containsEntry("color", "#aBc")
                .containsEntry("metadataVersion", 1L)
                .containsEntry("description", "Halo tag")
                .containsEntry("cover", "https://example.com/a.png");
    }

    @Test
    void checksSlugUsingRegisteredIndexesAndExcludesTheCurrentResourceOnUpdate() {
        when(client.fetch(Tag.class, "existing")).thenReturn(Mono.just(tag("existing", 7)));
        tools.updateTag(Map.of("name", "existing", "expectedVersion", 7, "displayName", "Renamed"))
                .block();
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).countBy(eq(Tag.class), options.capture());
        assertThat(options.getValue().toCondition().toString())
                .contains("metadata.deletionTimestamp", "spec.slug", "existing", "metadata.name")
                .doesNotContain("spec.displayName");
    }

    @Test
    void rejectsStaleAndConcurrentVersionsWithoutRetrying() {
        when(client.fetch(Tag.class, "existing")).thenReturn(Mono.just(tag("existing", 8)));
        StepVerifier.create(tools.updateTag(Map.of("name", "existing", "expectedVersion", 7, "color", "#fff")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any());
        when(client.fetch(Tag.class, "existing")).thenReturn(Mono.just(tag("existing", 7)));
        when(client.update(any(Tag.class))).thenReturn(Mono.error(new OptimisticLockingFailureException("changed")));
        StepVerifier.create(tools.updateTag(Map.of("name", "existing", "expectedVersion", 7, "color", "#fff")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any(Tag.class));
    }

    @Test
    void refusesDuplicateTagSlugsBeforeAnyWrite() {
        when(client.countBy(eq(Tag.class), any())).thenReturn(Mono.just(1L));
        StepVerifier.create(tools.createTag(Map.of("displayName", "Halo", "slug", "halo")))
                .expectError(DuplicateKeyException.class)
                .verify();
        when(client.fetch(Tag.class, "existing")).thenReturn(Mono.just(tag("existing", 7)));
        StepVerifier.create(tools.updateTag(Map.of("name", "existing", "expectedVersion", 7, "slug", "halo")))
                .expectError(DuplicateKeyException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void supportsExplicitOptionalFieldClearing() {
        var tag = tag("existing", 7);
        tag.getSpec().setColor("#fff");
        tag.getSpec().setCover("/keep.png");
        when(client.fetch(Tag.class, "existing")).thenReturn(Mono.just(tag));
        assertThat(map(tools.updateTag(Map.of("name", "existing", "expectedVersion", 7, "color", "", "description", ""))
                        .block()))
                .containsEntry("color", "")
                .containsEntry("description", "")
                .containsEntry("cover", "/keep.png");
    }

    @Test
    void rejectsMissingAndDeletedResourcesAndParents() {
        when(client.fetch(Tag.class, "missing")).thenReturn(Mono.empty());
        StepVerifier.create(tools.updateTag(Map.of("name", "missing", "expectedVersion", 1, "color", "#fff")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"owner", "metadata", "children", "template", "url", "callerName"})
    void rejectsUnsupportedFieldsBeforeReadingOrWriting(String field) {
        var input = new HashMap<String, Object>(Map.of("displayName", "Name", "slug", "slug"));
        input.put(field, "unapproved");
        StepVerifier.create(tools.createTag(input))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
    }

    @Test
    void validatesColorsNullTextAndVersionsAuthoritatively() {
        for (var value : List.of("red", "#ff", "#ffff", "#ggg")) {
            StepVerifier.create(tools.createTag(Map.of("displayName", "Name", "slug", "slug", "color", value)))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        var withNull = new HashMap<String, Object>(Map.of("displayName", "Name", "slug", "slug"));
        withNull.put("description", null);
        StepVerifier.create(tools.createTag(withNull))
                .expectError(IllegalArgumentException.class)
                .verify();
        for (var version : List.of(
                0,
                -1,
                1.5,
                "7",
                Double.NaN,
                Double.POSITIVE_INFINITY,
                new java.math.BigDecimal("9223372036854775808"))) {
            StepVerifier.create(
                            tools.updateTag(Map.of("name", "existing", "expectedVersion", version, "color", "#fff")))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.updateTag(Map.of("name", "existing", "expectedVersion", 7)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void searchesTagsByDisplayNameAndSlugUsingDefaultPaging() {
        var tag = tag("halo", "Halo", "halo");
        tag.getSpec().setColor("#ffffff");
        tag.getMetadata().setVersion(9L);
        tag.getStatus().setVisiblePostCount(7);
        tag.getStatus().setPostCount(999);
        when(client.listBy(eq(Tag.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(tag))));
        var output = result(tools.listTags(Map.of("query", "Halo")));
        assertThat(output)
                .containsEntry("page", 1)
                .containsEntry("size", 20)
                .containsEntry("total", 1L)
                .containsEntry("hasNext", false);
        assertThat(items(output).getFirst())
                .containsEntry("name", "halo")
                .containsEntry("displayName", "Halo")
                .containsEntry("slug", "halo")
                .containsEntry("color", "#ffffff")
                .containsEntry("visiblePostCount", 7)
                .containsEntry("metadataVersion", 9L)
                .doesNotContainKeys("postCount", "metadata");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(Tag.class), options.capture(), any());
        assertThat(options.getValue().toCondition().toString())
                .contains("spec.displayName", "spec.slug", "metadata.name", "Halo");
    }

    @Test
    void defensivelyDropsDeletingAndMalformedResources() {
        var deleting = tag("deleting", "Deleted", "deleted");
        deleting.getMetadata().setDeletionTimestamp(Instant.now());
        var malformed = new Tag();
        malformed.setMetadata(metadata("malformed", 1));
        when(client.listBy(eq(Tag.class), any(), any()))
                .thenReturn(Mono.just(
                        new ListResult<>(1, 20, 3, List.of(tag("ok", "Public", "public"), deleting, malformed))));
        assertThat(items(result(tools.listTags(Map.of()))))
                .hasSize(1)
                .first()
                .satisfies(item -> assertThat(item).containsEntry("name", "ok"));
    }

    @Test
    void handlesMissingOptionalTagStatus() {
        var tag = tag("empty", "Empty", "empty");
        tag.setStatus(null);
        when(client.listBy(eq(Tag.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(1, 20, 1, List.of(tag))));
        assertThat(items(result(tools.listTags(Map.of()))).getFirst())
                .containsEntry("url", "")
                .containsEntry("visiblePostCount", 0)
                .containsEntry("color", "");
    }

    private static Tag tag(String name, long version) {
        var tag = new Tag();
        tag.setMetadata(metadata(name, version));
        var spec = new Tag.TagSpec();
        spec.setDisplayName(name);
        spec.setSlug(name);
        tag.setSpec(spec);
        return tag;
    }

    private static Tag tag(String name, String displayName, String slug) {
        var tag = new Tag();
        tag.setMetadata(metadata(name, 1));
        var spec = new Tag.TagSpec();
        spec.setDisplayName(displayName);
        spec.setSlug(slug);
        tag.setSpec(spec);
        var status = new Tag.TagStatus();
        status.setPermalink("/tags/" + slug);
        tag.setStatus(status);
        return tag;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> result(Mono<Object> value) {
        return map(value.block());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }
}
