package run.halo.aifoundation.provider.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UriReferencePolicyTest {

    @Test
    void acceptsOnlyConfiguredLeadingProtocolMarkers() {
        var policy = UriReferencePolicy.allowing("http://", "https://", "data:");

        assertThat(policy.allows("https://example.com/image.png")).isTrue();
        assertThat(policy.allows("DATA:image/png;base64,AQID")).isTrue();
        assertThat(policy.allows("ftp://example.com/image.png")).isFalse();
        assertThat(policy.allows("prefix-https://example.com/image.png")).isFalse();
        assertThat(policy.allows(null)).isFalse();
    }

    @Test
    void keepsOpaqueProviderReferencesExplicit() {
        var policy = UriReferencePolicy.allowing("data:", "ms://");

        assertThat(policy.allows("ms://file-video-1")).isTrue();
        assertThat(policy.allows("https://example.com/video.mp4")).isFalse();
    }

    @Test
    void rejectsEmptyPoliciesAndPrefixes() {
        assertThatThrownBy(UriReferencePolicy::allowing)
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("At least one");
        assertThatThrownBy(() -> UriReferencePolicy.allowing("data:", " "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("must not be blank");
    }
}
