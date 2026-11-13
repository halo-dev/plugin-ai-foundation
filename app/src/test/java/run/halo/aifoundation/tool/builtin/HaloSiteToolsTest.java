package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.infra.SystemInfo;
import run.halo.app.infra.SystemInfoGetter;

/** Verifies the site business exposes public display information without private runtime settings. */
class HaloSiteToolsTest {
    private final SystemInfoGetter systemInfo = mock(SystemInfoGetter.class);
    private final HaloSiteTools tools = new HaloSiteTools(systemInfo, mock(SiteSearcher.class));

    @Test
    void returnsSiteDisplayInformationWithoutDumpingRuntimeAndSystemSettings() throws Exception {
        when(systemInfo.get())
                .thenReturn(Mono.just(new SystemInfo()
                        .setTitle("Halo Site")
                        .setSubtitle("Public subtitle")
                        .setUrl(URI.create("https://example.com/").toURL())
                        .setLogo("/upload/logo.svg")
                        .setFavicon("/upload/favicon.ico")
                        .setLocale(Locale.SIMPLIFIED_CHINESE)
                        .setActivatedThemeName("private-theme-name")));
        assertThat(result(tools.getSiteInfo(Map.of())))
                .containsExactlyInAnyOrderEntriesOf(Map.of(
                        "title",
                        "Halo Site",
                        "subtitle",
                        "Public subtitle",
                        "baseUrl",
                        "https://example.com/",
                        "logo",
                        "/upload/logo.svg",
                        "favicon",
                        "/upload/favicon.ico",
                        "language",
                        "zh-CN"));
    }

    @Test
    void handlesMissingOptionalSiteMetadata() {
        when(systemInfo.get()).thenReturn(Mono.just(new SystemInfo()));
        assertThat(result(tools.getSiteInfo(Map.of())))
                .containsExactlyInAnyOrderEntriesOf(
                        Map.of("title", "", "subtitle", "", "baseUrl", "", "logo", "", "favicon", "", "language", ""));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Mono<Object> result) {
        return (Map<String, Object>) result.block();
    }
}
