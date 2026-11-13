package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static run.halo.aifoundation.tool.ToolPermission.named;
import static run.halo.aifoundation.tool.ToolPermission.resource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.ToolPermissionDeniedBehavior;
import run.halo.app.core.extension.content.Comment;
import run.halo.app.core.extension.content.Reply;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Ref;

class HaloCommentToolsTest {
    private final ReactiveExtensionClient client = mock(ReactiveExtensionClient.class);
    private final HaloToolAccess access = mock(HaloToolAccess.class);
    private final HaloCommentTools tools = new HaloCommentTools(client, access);

    @BeforeEach
    void allowCurrentCaller() {
        when(access.actorName()).thenReturn(Mono.just("current-author"));
        when(access.requireResource(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(Mono.empty());
        when(client.create(any(Reply.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.update(any(Comment.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        when(client.update(any(Reply.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
    }

    @Test
    void groupsAllFourToolsAndRequiresApprovalOnlyForWrites() {
        var registrations = tools.tools().collectList().block();
        assertThat(registrations)
                .extracting(tool -> tool.getDefinition().getName())
                .containsExactly("list_comments", "get_comment", "reply_comment", "moderate_comment");
        var group = registrations.getFirst().getGroup();
        assertThat(group.getName()).isEqualTo("comments");
        assertThat(group.getPresets()).extracting(preset -> preset.getName()).containsExactly("read", "maintain");
        assertThat(group.getPresets().getFirst().getToolNames()).containsExactly("list_comments", "get_comment");
        for (var registration : registrations) {
            assertThat(registration.getGroup()).isSameAs(group);
            assertThat(registration.getCategory()).isEqualTo("content");
            assertThat(registration.getDefinition().getDescription()).contains("permissions", "untrusted");
            assertThat(registration
                            .getDefinition()
                            .getApprovalPolicy()
                            .requiresApproval(ToolExecutionContext.builder()
                                    .input(Map.of())
                                    .build()))
                    .isEqualTo(registration.getDefinition().getName().startsWith("reply_")
                            || registration.getDefinition().getName().startsWith("moderate_"));
        }
        assertThat(registrations.get(2).getDefinition().getDescription())
                .contains("moderation permission", "quoted reply");
        assertThat(registrations)
                .allSatisfy(tool -> assertThat(tool.getPermissionDeniedBehavior())
                        .isEqualTo(ToolPermissionDeniedBehavior.FILTER_ON_RESOLVE));
        assertThat(registrations.get(0).getRequiredPermissions())
                .containsExactly(resource("content.halo.run", "comments", "list"));
        assertThat(registrations.get(1).getRequiredPermissions())
                .containsExactly(
                        named("content.halo.run", "comments", "get", "name"),
                        resource("content.halo.run", "replies", "list"));
        assertThat(registrations.get(2).getRequiredPermissions())
                .containsExactly(
                        named("content.halo.run", "comments", "get", "name"),
                        resource("content.halo.run", "replies", "create"));
        assertThat(registrations.get(3).getRequiredPermissions())
                .containsExactly(
                        named("content.halo.run", "comments", "get", "name"),
                        named("content.halo.run", "comments", "update", "name"));
    }

    @Test
    void listUsesRegisteredFiltersAndReturnsNoPrivateOwnerOrRequestDetails() {
        var comment = comment("comment", 7);
        comment.getSpec().setRaw("x".repeat(700));
        comment.getSpec().setHidden(true);
        when(client.listBy(eq(Comment.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(2, 3, 12, List.of(comment))));
        var result = result(tools.listComments(Map.of(
                "page",
                2,
                "size",
                3,
                "query",
                " Halo ",
                "approved",
                false,
                "hidden",
                true,
                "subjectKind",
                "Post",
                "subjectName",
                "article")));
        assertThat(result)
                .containsEntry("page", 2)
                .containsEntry("size", 3)
                .containsEntry("total", 12L)
                .containsEntry("hasNext", true);
        var item = items(result).getFirst();
        assertThat(item)
                .containsEntry("metadataVersion", 7L)
                .containsEntry("hidden", true)
                .containsEntry("truncated", true)
                .containsEntry("ownerKind", "Email");
        assertThat((String) item.get("raw")).hasSize(500);
        assertThat(result.toString())
                .doesNotContain("private@example.com", "192.0.2.1", "secret-agent", "private-token");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(client).listBy(eq(Comment.class), options.capture(), page.capture());
        assertThat(options.getValue().toCondition().toString())
                .contains(
                        "spec.raw",
                        "Halo",
                        "spec.approved",
                        "spec.hidden",
                        "spec.subjectRef",
                        "content.halo.run/Post/article",
                        "metadata.deletionTimestamp");
        assertThat(page.getValue().getPageNumber()).isEqualTo(2);
        assertThat(page.getValue().getPageSize()).isEqualTo(3);
    }

    @Test
    void detailIncludesPagedRepliesWithoutLeakingOtherCommentReplies() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        var own = reply("own", "comment");
        var other = reply("other", "elsewhere");
        var deleting = reply("deleted", "comment");
        deleting.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.listBy(eq(Reply.class), any(), any()))
                .thenReturn(Mono.just(new ListResult<>(2, 2, 5, List.of(own, other, deleting))));
        var result = result(tools.getComment(Map.of("name", "comment", "repliesPage", 2, "repliesSize", 2)));
        var replies = map(result.get("replies"));
        assertThat(replies).containsEntry("page", 2).containsEntry("size", 2).containsEntry("total", 5L);
        assertThat(items(replies))
                .hasSize(1)
                .first()
                .satisfies(item -> assertThat(item).containsEntry("name", "own"));
        assertThat(result.toString()).doesNotContain("private@example.com", "192.0.2.1", "private-token");
        var options = ArgumentCaptor.forClass(ListOptions.class);
        verify(client).listBy(eq(Reply.class), options.capture(), any());
        assertThat(options.getValue().toCondition().toString())
                .contains("spec.commentName", "comment", "metadata.deletionTimestamp");
    }

    @Test
    void createsReplyWithCurrentActorAndNativeDefaultsAfterPrecheckingModeration() {
        var parent = comment("comment", 7);
        var quoted = reply("quoted", "comment");
        quoted.getSpec().setHidden(true);
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(parent));
        when(client.fetch(Reply.class, "quoted")).thenReturn(Mono.just(quoted));
        var result = result(tools.replyComment(
                Map.of("name", "comment", "raw", "<p>Reply</p>", "quoteReply", "quoted", "allowNotification", true)));
        assertThat(result).containsEntry("approved", true).containsEntry("hidden", true);
        var saved = ArgumentCaptor.forClass(Reply.class);
        verify(client).create(saved.capture());
        var spec = saved.getValue().getSpec();
        assertThat(saved.getValue().getMetadata().getName()).startsWith("ai-reply-");
        assertThat(spec.getOwner().getKind()).isEqualTo("User");
        assertThat(spec.getOwner().getName()).isEqualTo("current-author");
        assertThat(spec.getOwner().getAnnotations()).isNullOrEmpty();
        assertThat(spec.getRaw()).isEqualTo("<p>Reply</p>");
        assertThat(spec.getContent()).isEqualTo("<p>Reply</p>");
        assertThat(spec.getQuoteReply()).isEqualTo("quoted");
        assertThat(spec.getAllowNotification()).isTrue();
        assertThat(spec.getPriority()).isZero();
        assertThat(spec.getTop()).isFalse();
        assertThat(spec.getCreationTime()).isNotNull();
        assertThat(spec.getApprovedTime()).isNotNull();
        verify(access).requireResource("content.halo.run", "replies", "get", "quoted");
        verify(access).requireResource("content.halo.run", "comments", "update", "comment");
        verify(access).requireResource("content.halo.run", "replies", "update", "quoted");
        assertThat(parent.getSpec().getApproved()).isFalse();
        assertThat(quoted.getSpec().getApproved()).isNull();
        var parentSaved = ArgumentCaptor.forClass(Comment.class);
        verify(client).update(parentSaved.capture());
        assertThat(parentSaved.getValue().getSpec().getApproved()).isTrue();
        assertThat(parentSaved.getValue().getMetadata().getVersion()).isEqualTo(7);
        var quoteSaved = ArgumentCaptor.forClass(Reply.class);
        verify(client).update(quoteSaved.capture());
        assertThat(quoteSaved.getValue().getSpec().getApproved()).isTrue();
    }

    @Test
    void replyCreateAloneNeverApprovesAnUnapprovedParent() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(access.requireResource("content.halo.run", "comments", "update", "comment"))
                .thenReturn(Mono.error(new AccessDeniedException("moderation denied")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Reply")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void quoteReadIsAuthorizedBeforeFetchingItsContent() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(access.requireResource("content.halo.run", "replies", "get", "quoted"))
                .thenReturn(Mono.error(new AccessDeniedException("quote read denied")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Reply", "quoteReply", "quoted")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, never()).fetch(Reply.class, "quoted");
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void allQuoteModerationPermissionsAreCheckedBeforeAnyMutation() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(client.fetch(Reply.class, "quoted")).thenReturn(Mono.just(reply("quoted", "comment")));
        when(access.requireResource("content.halo.run", "replies", "update", "quoted"))
                .thenReturn(Mono.error(new AccessDeniedException("quote moderation denied")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Reply", "quoteReply", "quoted")))
                .expectError(AccessDeniedException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void createOnlyCallerCanReplyToApprovedContentWithoutApprovingTheirNewReply() {
        var parent = comment("comment", 7);
        parent.getSpec().setApproved(true);
        var quote = reply("quoted", "comment");
        quote.getSpec().setApproved(true);
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(parent));
        when(client.fetch(Reply.class, "quoted")).thenReturn(Mono.just(quote));
        when(access.requireResource(eq("content.halo.run"), eq("replies"), eq("update"), anyString()))
                .thenReturn(Mono.error(new AccessDeniedException("no moderation permission")));
        var result = result(tools.replyComment(Map.of("name", "comment", "raw", "Reply", "quoteReply", "quoted")));
        assertThat(result).containsEntry("approved", false);
        var saved = ArgumentCaptor.forClass(Reply.class);
        verify(client).create(saved.capture());
        assertThat(saved.getValue().getSpec().getApprovedTime()).isNull();
        assertThat(saved.getValue().getSpec().getAllowNotification()).isFalse();
        verify(client, never()).update(any());
    }

    @Test
    void nonPermissionErrorsWhileCheckingReplyApprovalFailClosed() {
        var parent = comment("comment", 7);
        parent.getSpec().setApproved(true);
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(parent));
        when(access.requireResource(eq("content.halo.run"), eq("replies"), eq("update"), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("authorization unavailable")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Reply")))
                .expectErrorMessage("authorization unavailable")
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void replyModerationCasFailureIsPropagatedWithoutCreatingOrRetrying() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(client.update(any(Comment.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("parent changed")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Reply")))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any(Comment.class));
        verify(client, never()).create(any());
    }

    @Test
    void doesNotQuoteAReplyFromADifferentCommentOrADeletedReply() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(client.fetch(Reply.class, "quoted")).thenReturn(Mono.just(reply("quoted", "elsewhere")));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Hello", "quoteReply", "quoted")))
                .expectErrorMessage("Quoted reply does not belong to this comment")
                .verify();
        var deleted = reply("quoted", "comment");
        deleted.getMetadata().setDeletionTimestamp(Instant.now());
        when(client.fetch(Reply.class, "quoted")).thenReturn(Mono.just(deleted));
        StepVerifier.create(tools.replyComment(Map.of("name", "comment", "raw", "Hello", "quoteReply", "quoted")))
                .expectError(IllegalArgumentException.class)
                .verify();
        verify(client, never()).create(any());
        verify(client, never()).update(any());
    }

    @Test
    void rejectsIdentityInjectionAndMalformedOrUnsafeBodyBeforeAnyExtensionRead() {
        for (var input : List.<Map<String, Object>>of(
                Map.of("name", "comment", "raw", "Reply", "owner", Map.of("name", "admin")),
                Map.of("name", "comment", "raw", "Reply", "approved", true),
                Map.of("name", "comment", "raw", " "),
                Map.of("name", "comment", "raw", "x".repeat(20_001)),
                Map.of("name", "../comment", "raw", "Reply"),
                Map.of("name", "comment", "raw", "Reply", "allowNotification", "true"),
                Map.of("name", "comment", "raw", "Reply", "content", "<script>alert(1)</script>"),
                Map.of("name", "comment", "raw", "<img src=x onerror=alert(1)>"))) {
            StepVerifier.create(tools.replyComment(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        verifyNoInteractions(client);
    }

    @Test
    void moderationPreservesOmittedFieldsAndCurrentVersionWithoutMutatingFetchedObject() {
        var current = comment("comment", 7);
        current.getSpec().setHidden(true);
        current.getSpec().setTop(true);
        current.getSpec().setAllowNotification(true);
        current.getSpec().setApprovedTime(Instant.EPOCH);
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(current));
        var saved = new AtomicReference<Comment>();
        when(client.update(any(Comment.class))).thenAnswer(invocation -> {
            Comment update = invocation.getArgument(0);
            saved.set(update);
            return Mono.just(update);
        });
        result(tools.moderateComment(Map.of("name", "comment", "expectedVersion", 7, "approved", false)));
        assertThat(saved.get()).isNotSameAs(current);
        assertThat(saved.get().getMetadata().getVersion()).isEqualTo(7);
        assertThat(saved.get().getSpec().getApproved()).isFalse();
        assertThat(saved.get().getSpec().getApprovedTime()).isNull();
        assertThat(saved.get().getSpec().getHidden()).isTrue();
        assertThat(saved.get().getSpec().getTop()).isTrue();
        assertThat(saved.get().getSpec().getAllowNotification()).isTrue();
        assertThat(saved.get().getSpec().getRaw()).isEqualTo(current.getSpec().getRaw());
        assertThat(saved.get().getSpec().getOwner()).isEqualTo(current.getSpec().getOwner());
        assertThat(current.getSpec().getApprovedTime()).isEqualTo(Instant.EPOCH);
    }

    @Test
    void rejectsAStaleModerationBeforeWritingAndPropagatesConcurrentCasFailureWithoutRetry() {
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 8)));
        StepVerifier.create(tools.moderateComment(Map.of("name", "comment", "expectedVersion", 7, "approved", true)))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, never()).update(any());
        when(client.fetch(Comment.class, "comment")).thenReturn(Mono.just(comment("comment", 7)));
        when(client.update(any(Comment.class)))
                .thenReturn(Mono.error(new OptimisticLockingFailureException("conflict")));
        StepVerifier.create(tools.moderateComment(Map.of("name", "comment", "expectedVersion", 7, "approved", true)))
                .expectError(OptimisticLockingFailureException.class)
                .verify();
        verify(client, times(1)).update(any(Comment.class));
    }

    @Test
    void validatesPagingFiltersAndVersionsBeforeAnyExtensionRead() {
        for (var input : List.<Map<String, Object>>of(
                Map.of("page", 0),
                Map.of("size", 101),
                Map.of("page", 1.5),
                Map.of("query", 1),
                Map.of("approved", "false"),
                Map.of("subjectKind", "Post"),
                Map.of("subjectName", "post"),
                Map.of("subjectKind", "Secret", "subjectName", "secret"))) {
            StepVerifier.create(tools.listComments(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        StepVerifier.create(tools.moderateComment(
                        Map.of("name", "comment", "expectedVersion", Double.NaN, "approved", true)))
                .expectError(IllegalArgumentException.class)
                .verify();
        StepVerifier.create(tools.getComment(Map.of("name", "comment", "repliesSize", 0)))
                .expectError(IllegalArgumentException.class)
                .verify();
        verifyNoInteractions(client);
    }

    private static Comment comment(String name, long version) {
        var comment = new Comment();
        comment.setMetadata(metadata(name, version));
        comment.getMetadata().setAnnotations(Map.of("token", "private-token"));
        var spec = new Comment.CommentSpec();
        spec.setRaw("Hello");
        spec.setContent("<p>Hello</p>");
        spec.setApproved(false);
        spec.setHidden(false);
        spec.setIpAddress("192.0.2.1");
        spec.setUserAgent("secret-agent");
        spec.setOwner(owner());
        var ref = new Ref();
        ref.setGroup("content.halo.run");
        ref.setKind("Post");
        ref.setName("article");
        spec.setSubjectRef(ref);
        comment.setSpec(spec);
        return comment;
    }

    private static Reply reply(String name, String commentName) {
        var reply = new Reply();
        reply.setMetadata(metadata(name, 3));
        var spec = new Reply.ReplySpec();
        spec.setCommentName(commentName);
        spec.setRaw("Reply");
        spec.setContent("<p>Reply</p>");
        spec.setOwner(owner());
        spec.setIpAddress("192.0.2.1");
        reply.setSpec(spec);
        return reply;
    }

    private static Comment.CommentOwner owner() {
        var owner = new Comment.CommentOwner();
        owner.setKind("Email");
        owner.setName("private@example.com");
        owner.setDisplayName("private@example.com");
        owner.setAnnotations(Map.of("token", "private-token"));
        return owner;
    }

    private static Metadata metadata(String name, long version) {
        var metadata = new Metadata();
        metadata.setName(name);
        metadata.setVersion(version);
        metadata.setCreationTimestamp(Instant.EPOCH);
        return metadata;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> result(Mono<Object> result) {
        return map(result.block());
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> items(Map<String, Object> result) {
        return (List<Map<String, Object>>) result.get("items");
    }
}
