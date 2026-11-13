package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import run.halo.aifoundation.tool.ToolExecutionContext;
import run.halo.aifoundation.tool.builtin.WebPageReaderSettings.ReadOptions;

class WebPageReaderTest {
    private static final URI URL = URI.create("https://example.com/input");
    private static final ReadOptions DEFAULT_OPTIONS = new ReadOptions(
            false,
            WebPageReaderSettings.DEFAULT_MAX_RESPONSE_SIZE_MB,
            WebPageReaderSettings.DEFAULT_MAX_CONTENT_CHARACTERS);

    @Test
    void extractsHtmlTextAndRemovesExecutableAndEmbeddedContent() {
        var html = """
            <!doctype html><html><head><title>Halo 文档</title><style>hidden-css</style></head>
            <body><h1>公开内容</h1><script>secret-script</script><noscript>tracking</noscript>
            <iframe>embedded-secret</iframe><template>template-secret</template><p>A &amp; B</p>
            </body></html>
            """;
        var result = extract(html, MediaType.TEXT_HTML);
        assertThat(result)
                .containsEntry("title", "Halo 文档")
                .containsEntry("content", "公开内容 A & B")
                .containsEntry("truncated", false)
                .containsEntry("url", URL.toString())
                .containsEntry("finalUrl", URL.toString())
                .containsEntry("contentType", "text/html");
    }

    @Test
    void parsesXhtmlAsHtmlAndReturnsActualResponseMetadata() {
        var finalUrl = URI.create("https://example.com/final/document");
        var type = MediaType.parseMediaType("application/xhtml+xml;charset=UTF-8");
        var html = "<html xmlns='http://www.w3.org/1999/xhtml'><head><title>Final</title>"
                + "</head><body><p>Hello</p><script>hidden</script></body></html>";
        var result = WebPageReader.extract(
                URL, new PublicHttpClient.Response(html.getBytes(StandardCharsets.UTF_8), type, finalUrl));
        assertThat(result)
                .containsEntry("url", URL.toString())
                .containsEntry("finalUrl", finalUrl.toString())
                .containsEntry("contentType", type.toString())
                .containsEntry("title", "Final")
                .containsEntry("content", "Hello");
    }

    @ParameterizedTest
    @MethodSource("structuredTextFormats")
    void preservesTextFormatsWithoutParsingTheirContents(String mimeType, String text) {
        var type = MediaType.parseMediaType(mimeType);
        assertThat(extract(text, type))
                .containsEntry("content", text)
                .containsEntry("title", "")
                .containsEntry("contentType", type.toString())
                .containsEntry("truncated", false);
    }

    static Stream<Arguments> structuredTextFormats() {
        return Stream.of(
                Arguments.of("application/json", "{\n  \"html\": \"<b>A &amp; B</b>\", \"count\": 2\n}\n"),
                Arguments.of("application/problem+json", "{\"detail\":\"<script>kept as data</script>\"}"),
                Arguments.of("application/x-ndjson", "{\"item\":1}\n{\"item\":2}\n"),
                Arguments.of("application/json-seq", "\u001e{\"item\":1}\n\u001e{\"item\":2}\n"),
                Arguments.of("application/xml", "<?xml version=\"1.0\"?><root>&amp; &external;</root>"),
                Arguments.of("application/rss+xml", "<rss>\n  <channel><title>News</title></channel>\n</rss>"),
                Arguments.of("application/atom+xml", "<feed xmlns=\"http://www.w3.org/2005/Atom\">\n</feed>"),
                Arguments.of("text/markdown", "# Halo\n\n- **Bold**\n- <b>Literal HTML</b>\n"),
                Arguments.of("text/csv", "name,value\n\"Halo, CMS\",2\n"),
                Arguments.of("application/yaml", "service:\n  name: Halo\n  enabled: true\n"),
                Arguments.of("application/toml", "[service]\nname = \"Halo\"\n"),
                Arguments.of("application/javascript", "const html = '<h1>Halo</h1>';\nconsole.log(html);\n"),
                Arguments.of("text/x-python", "def greet():\n    return '<p>Halo</p>'\n"),
                Arguments.of("text/x-unusual-format", "<html>\n  preserve as declared text\n</html>\n"));
    }

    @Test
    void neverExpandsXmlEntitiesOrFetchesTheirTargets() {
        var xml = """
            <?xml version="1.0"?>
            <!DOCTYPE root [<!ENTITY external SYSTEM "file:///etc/passwd">]>
            <root>&external;</root>
            """;
        assertThat(extract(xml, MediaType.APPLICATION_XML)).containsEntry("content", xml);
    }

    @Test
    void respectsExplicitCharacterEncoding() {
        var text = "中文 café\n第二行";
        var type = MediaType.parseMediaType("text/plain;charset=GB18030");
        var result = WebPageReader.extract(
                URL, new PublicHttpClient.Response(text.getBytes(Charset.forName("GB18030")), type));
        assertThat(result).containsEntry("content", text).containsEntry("contentType", type.toString());
    }

    @ParameterizedTest
    @MethodSource("unicodeByteOrderMarks")
    void decodesUnicodeByteOrderMarksBeforeAConflictingDeclaredCharset(String charset, byte[] bom) {
        var text = "中文 😀\nNext";
        var encoded = text.getBytes(Charset.forName(charset));
        var body = new byte[bom.length + encoded.length];
        System.arraycopy(bom, 0, body, 0, bom.length);
        System.arraycopy(encoded, 0, body, bom.length, encoded.length);
        var type = MediaType.parseMediaType("text/plain;charset=ISO-8859-1");
        assertThat(WebPageReader.extract(URL, new PublicHttpClient.Response(body, type)))
                .containsEntry("content", text)
                .containsEntry("contentType", type.toString());
    }

    static Stream<Arguments> unicodeByteOrderMarks() {
        return Stream.of(
                Arguments.of("UTF-8", new byte[] {(byte) 0xef, (byte) 0xbb, (byte) 0xbf}),
                Arguments.of("UTF-16LE", new byte[] {(byte) 0xff, (byte) 0xfe}),
                Arguments.of("UTF-16BE", new byte[] {(byte) 0xfe, (byte) 0xff}),
                Arguments.of("UTF-32LE", new byte[] {(byte) 0xff, (byte) 0xfe, 0, 0}),
                Arguments.of("UTF-32BE", new byte[] {0, 0, (byte) 0xfe, (byte) 0xff}));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<meta charset='GB18030'>",
                "<meta http-equiv='Content-Type' content='text/html; charset=GB18030'>"
            })
    void respectsHtmlMetaEncoding(String meta) {
        var html = "<html><head>" + meta + "<title>文档</title></head><body>中文内容</body></html>";
        var body = html.getBytes(Charset.forName("GB18030"));
        assertThat(WebPageReader.extract(URL, new PublicHttpClient.Response(body, MediaType.TEXT_HTML)))
                .containsEntry("title", "文档")
                .containsEntry("content", "中文内容");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<!-- <meta charset='US-ASCII'> -->",
                "<script>const template = \"<meta charset='US-ASCII'>\";</script>",
                "<meta name='description' content='charset=US-ASCII'>",
                "<meta content='charset=US-ASCII'>",
                "<meta http-equiv='refresh' content='charset=US-ASCII'>"
            })
    void ignoresFakeHtmlEncodingDeclarations(String misleadingMarkup) {
        var html = "<html><head>" + misleadingMarkup + "<meta charset='UTF-8'></head><body>中文内容</body></html>";
        assertThat(extract(html, MediaType.TEXT_HTML)).containsEntry("content", "中文内容");
        assertThat(extract(html, null)).containsEntry("content", "中文内容");
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "<meta charset='unsupported-encoding'>",
                "<meta http-equiv='Content-Type' content='text/html; charset=unsupported-encoding'>"
            })
    void skipsInvalidHtmlEncodingDeclarationsAndTriesSubsequentMeta(String invalidMeta) {
        var html = "<html><head>" + invalidMeta + "<meta charset='GB18030'></head><body>中文内容</body></html>";
        assertThat(WebPageReader.extract(
                        URL,
                        new PublicHttpClient.Response(html.getBytes(Charset.forName("GB18030")), MediaType.TEXT_HTML)))
                .containsEntry("content", "中文内容");
    }

    @Test
    void fallsBackToUtf8WhenHtmlOnlyContainsInvalidEncodingDeclarations() {
        assertThat(extract(
                        "<html><head><meta charset='unsupported-encoding'>" + "</head><body>中文内容</body></html>",
                        MediaType.TEXT_HTML))
                .containsEntry("content", "中文内容");
    }

    @Test
    void respectsXmlDeclarationEncodingWithoutParsingTheDocument() {
        var text = "<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><root>café &amp;</root>";
        assertThat(WebPageReader.extract(
                        URL,
                        new PublicHttpClient.Response(
                                text.getBytes(StandardCharsets.ISO_8859_1), MediaType.APPLICATION_XML)))
                .containsEntry("content", text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE"})
    void recognizesXmlUnicodeDeclarationSignaturesWithoutBom(String charset) {
        var text = "<?xml version=\"1.0\" encoding=\"" + charset + "\"?><root>中文</root>";
        assertThat(WebPageReader.extract(
                        URL,
                        new PublicHttpClient.Response(
                                text.getBytes(Charset.forName(charset)), MediaType.APPLICATION_XML)))
                .containsEntry("content", text);
    }

    @Test
    void declaredCharsetTakesPriorityOverHtmlMeta() {
        var text = "<html><head><meta charset='UTF-8'></head><body>café</body></html>";
        var type = MediaType.parseMediaType("text/html;charset=ISO-8859-1");
        assertThat(WebPageReader.extract(
                        URL, new PublicHttpClient.Response(text.getBytes(StandardCharsets.ISO_8859_1), type)))
                .containsEntry("content", "café");
    }

    @ParameterizedTest
    @ValueSource(strings = {"ISO-8859-1", "latin1", "ascii", "US-ASCII", "cp819", "ANSI_X3.4-1968"})
    void decodesLegacyHtmlHttpCharsetLabelsAsWindows1252(String label) {
        var text = "“Halo” costs €5 — it’s ready…";
        var html = "<html><head><meta charset='UTF-8'><title>“Halo”</title></head><body>" + text + "</body></html>";
        var type = MediaType.parseMediaType("text/html;charset=" + label);
        assertThat(WebPageReader.extract(
                        URL, new PublicHttpClient.Response(html.getBytes(Charset.forName("windows-1252")), type)))
                .containsEntry("title", "“Halo”")
                .containsEntry("content", text)
                .containsEntry("contentType", type.toString());
    }

    @ParameterizedTest
    @MethodSource("legacyHtmlMetaDeclarations")
    void decodesLegacyHtmlMetaLabelsAsWindows1252WithOrWithoutADeclaredMediaType(String meta) {
        var text = "“Halo” costs €5 — it’s ready…";
        var html = "<html><head>" + meta + "<title>“Halo”</title></head><body>" + text + "</body></html>";
        var body = html.getBytes(Charset.forName("windows-1252"));
        for (var type : new MediaType[] {MediaType.TEXT_HTML, null, MediaType.APPLICATION_OCTET_STREAM}) {
            assertThat(WebPageReader.extract(URL, new PublicHttpClient.Response(body, type)))
                    .containsEntry("title", "“Halo”")
                    .containsEntry("content", text);
        }
    }

    static Stream<String> legacyHtmlMetaDeclarations() {
        return Stream.of("ISO-8859-1", "  LaTiN1  ", "ascii", "ISO_8859-1:1987", "iso-ir-100", "cp819", "x-cp1252")
                .flatMap(label -> Stream.of(
                        "<meta charset='" + label + "'>",
                        "<meta http-equiv='Content-Type' content='text/html; charset=\"" + label.trim() + "\"'>"));
    }

    @ParameterizedTest
    @MethodSource("unicodeByteOrderMarks")
    void preservesBomPrecedenceOverLegacyHtmlHttpAndMetaLabels(String charset, byte[] bom) {
        var text = "中文 “Halo” 😀";
        var html = "<html><head><meta charset='US-ASCII'></head><body>" + text + "</body></html>";
        var encoded = html.getBytes(Charset.forName(charset));
        var body = new byte[bom.length + encoded.length];
        System.arraycopy(bom, 0, body, 0, bom.length);
        System.arraycopy(encoded, 0, body, bom.length, encoded.length);
        var type = MediaType.parseMediaType("text/html;charset=ISO-8859-1");
        assertThat(WebPageReader.extract(URL, new PublicHttpClient.Response(body, type)))
                .containsEntry("content", text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/xml", "text/plain", "application/xhtml+xml"})
    void preservesIso88591ControlsForNonHtmlMediaTypesInsteadOfApplyingWindows1252(String mediaType) {
        var text = "A\u0085B";
        var type = MediaType.parseMediaType(mediaType + ";charset=ISO-8859-1");
        assertThat(WebPageTextDecoder.decode(text.getBytes(StandardCharsets.ISO_8859_1), type)
                        .text())
                .isEqualTo(text);
    }

    @ParameterizedTest
    @ValueSource(strings = {"application/json", "application/xml", "text/plain", "application/xhtml+xml"})
    void keepsAsciiStrictForNonHtmlMediaTypes(String mediaType) {
        var type = MediaType.parseMediaType(mediaType + ";charset=US-ASCII");
        assertThatThrownBy(() -> WebPageReader.extract(
                        URL, new PublicHttpClient.Response("“Halo”".getBytes(Charset.forName("windows-1252")), type)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("The response is not valid text in its character encoding");
    }

    @Test
    void preservesIso88591XmlDeclarationWithoutAnHttpCharset() {
        var text = "<?xml version='1.0' encoding='ISO-8859-1'?><root>A\u0085B</root>";
        assertThat(WebPageReader.extract(
                        URL,
                        new PublicHttpClient.Response(
                                text.getBytes(StandardCharsets.ISO_8859_1), MediaType.APPLICATION_XML)))
                .containsEntry("content", text);
    }

    @ParameterizedTest
    @MethodSource("untypedText")
    void detectsUnknownAndOctetStreamTextFromItsBody(String text, String detectedType, String expectedContent) {
        for (var type : new MediaType[] {null, MediaType.APPLICATION_OCTET_STREAM}) {
            assertThat(extract(text, type))
                    .containsEntry("contentType", detectedType)
                    .containsEntry("content", expectedContent);
        }
    }

    static Stream<Arguments> untypedText() {
        return Stream.of(
                Arguments.of("<!doctype html><html><body>Hello <b>Halo</b></body></html>", "text/html", "Hello Halo"),
                Arguments.of(
                        "<?xml version='1.0'?><root>Halo</root>",
                        "application/xml",
                        "<?xml version='1.0'?><root>Halo</root>"),
                Arguments.of("{\n\"name\": \"Halo\"\n}", "application/json", "{\n\"name\": \"Halo\"\n}"),
                Arguments.of("\u001e{\"name\":\"Halo\"}\n", "application/json-seq", "\u001e{\"name\":\"Halo\"}\n"),
                Arguments.of(
                        "# Markdown\n\n  Original formatting\n",
                        "text/plain",
                        "# Markdown\n\n  Original formatting\n"));
    }

    @Test
    void acceptsEmptyResponsesIncludingMissingContentType() {
        for (var type : new MediaType[] {
            null, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_HTML, MediaType.APPLICATION_JSON
        }) {
            assertThat(extract("", type))
                    .containsEntry("content", "")
                    .containsEntry("title", "")
                    .containsEntry("truncated", false);
        }
    }

    @Test
    void preservesLegitimateUnicodeReplacementCharacters() {
        var text = "Document contains a literal replacement character: \ufffd";
        assertThat(extract(text, null)).containsEntry("content", text);
    }

    @ParameterizedTest
    @MethodSource("binaryBodies")
    void rejectsBinaryBodiesEvenWhenDeclaredAsText(byte[] body) {
        for (var type :
                new MediaType[] {null, MediaType.APPLICATION_OCTET_STREAM, MediaType.TEXT_PLAIN, MediaType.TEXT_HTML}) {
            assertThatThrownBy(() -> WebPageReader.extract(URL, new PublicHttpClient.Response(body, type)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    static Stream<Arguments> binaryBodies() {
        return Stream.of(
                Arguments.of((Object) "%PDF-1.7\nnot a readable document".getBytes(StandardCharsets.UTF_8)),
                Arguments.of((Object) new byte[] {(byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a}),
                Arguments.of((Object) new byte[] {0x50, 0x4b, 0x03, 0x04, 0x41, 0x42}),
                Arguments.of((Object) "GIF89a".getBytes(StandardCharsets.UTF_8)),
                Arguments.of((Object) new byte[] {'a', 0, 'b'}),
                Arguments.of((Object) new byte[] {(byte) 0xc3, 0x28}),
                Arguments.of((Object) new byte[] {'a', 0x01, 'b'}));
    }

    @Test
    void doesNotInferEncodingOrTrustTextFileExtensions() {
        var latin1 = "café".getBytes(StandardCharsets.ISO_8859_1);
        assertThatThrownBy(() -> WebPageReader.extract(
                        URI.create("https://example.com/readme.md"), new PublicHttpClient.Response(latin1, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> WebPageReader.extract(
                        URI.create("https://example.com/file.txt"),
                        new PublicHttpClient.Response(new byte[] {0, 1, 2}, MediaType.APPLICATION_OCTET_STREAM)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsUnsupportedDeclaredEncodingWithoutLeakingResponseContents() {
        var text = "<?xml version='1.0' encoding='made-up-encoding'?><secret>private</secret>";
        assertThatThrownBy(() -> extract(text, MediaType.APPLICATION_XML))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Unsupported response character encoding");
    }

    @Test
    void truncatesLongTextWithoutBreakingSurrogatePairs() {
        var limit = DEFAULT_OPTIONS.maxContentCharacters();
        var text = "a".repeat(limit - 1) + "😀tail";
        var result = extract(text, MediaType.TEXT_PLAIN);
        assertThat((String) result.get("content")).isEqualTo("a".repeat(limit - 1));
        assertThat(result).containsEntry("truncated", true);
        var exact = "a".repeat(limit - 2) + "😀";
        assertThat(extract(exact, MediaType.TEXT_PLAIN))
                .containsEntry("content", exact)
                .containsEntry("truncated", false);
    }

    @Test
    void truncatesJsonAsTextAndSignalsThatItMayBeIncomplete() {
        var text = "{\"value\":\"" + "a".repeat(DEFAULT_OPTIONS.maxContentCharacters()) + "\"}";
        var result = extract(text, MediaType.APPLICATION_JSON);
        assertThat((String) result.get("content")).isEqualTo(text.substring(0, DEFAULT_OPTIONS.maxContentCharacters()));
        assertThat(result).containsEntry("truncated", true);
    }

    @Test
    void boundsHtmlTitlesWithoutBreakingSurrogatePairs() {
        var result = extract(
                "<html><head><title>" + "a".repeat(299) + "😀tail</title></head><body>Text</body></html>",
                MediaType.TEXT_HTML);
        assertThat(result).containsEntry("title", "a".repeat(299));
    }

    @Test
    void defensivelyRejectsResponsesAboveTheByteBudget() {
        var body = new byte[DEFAULT_OPTIONS.maxResponseBytes() + 1];
        assertThatThrownBy(() -> WebPageReader.extract(URL, new PublicHttpClient.Response(body, MediaType.TEXT_PLAIN)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("External response exceeds the size limit");
    }

    @Test
    void usesBoundedTextTransportWithNoCallerHeaderInput() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        when(settings.readOptions()).thenReturn(Mono.just(DEFAULT_OPTIONS));
        when(http.getText(any(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(
                        new PublicHttpClient.Response("Hello".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN)));
        var registration = new WebPageReader(http, settings).tools().blockFirst();
        StepVerifier.create(registration
                        .getDefinition()
                        .getExecutor()
                        .execute(ToolExecutionContext.builder()
                                .input(Map.of("url", URL.toString()))
                                .build()))
                .assertNext(result ->
                        assertThat(((Map<?, ?>) result).get("content")).isEqualTo("Hello"))
                .verifyComplete();
        verify(http).getText(URL, DEFAULT_OPTIONS.maxResponseBytes(), false);
        verify(settings).readOptions();
    }

    @Test
    void readsOneFreshOptionsSnapshotPerSubscriptionAndReusesItForExtraction() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        var first = new ReadOptions(true, 1, 3);
        var second = new ReadOptions(false, 2, 8);
        var current = new AtomicReference<>(first);
        when(settings.readOptions()).thenAnswer(ignored -> Mono.just(current.get()));
        when(http.getText(any(), anyInt(), anyBoolean())).thenReturn(Mono.fromSupplier(() -> {
            // Simulate an administrator changing settings while the response is in flight.
            current.set(second);
            return new PublicHttpClient.Response("ab😀tail".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN);
        }));
        var reader = new WebPageReader(http, settings);
        var request = reader.read(Map.of("url", URL.toString()));
        StepVerifier.create(request)
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("content")).isEqualTo("ab");
                    assertThat(values.get("truncated")).isEqualTo(true);
                })
                .verifyComplete();
        StepVerifier.create(request)
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat(values.get("content")).isEqualTo("ab😀tail");
                    assertThat(values.get("truncated")).isEqualTo(false);
                })
                .verifyComplete();
        verify(settings, times(2)).readOptions();
        verify(http).getText(URL, first.maxResponseBytes(), true);
        verify(http).getText(URL, second.maxResponseBytes(), false);
    }

    @Test
    void largerDefaultsReadBeyondThePreviousDownloadAndOutputLimits() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        when(settings.readOptions()).thenReturn(Mono.just(DEFAULT_OPTIONS));
        var text = "a".repeat(1_048_577);
        when(http.getText(any(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(
                        new PublicHttpClient.Response(text.getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN)));
        StepVerifier.create(new WebPageReader(http, settings).read(Map.of("url", URL.toString())))
                .assertNext(result -> {
                    var values = (Map<?, ?>) result;
                    assertThat((String) values.get("content")).hasSize(100_000);
                    assertThat(values.get("truncated")).isEqualTo(true);
                })
                .verifyComplete();
        verify(http).getText(URL, 10 * 1_048_576, false);
        var formerlyTruncated = "b".repeat(12_001);
        assertThat(extract(formerlyTruncated, MediaType.TEXT_PLAIN))
                .containsEntry("content", formerlyTruncated)
                .containsEntry("truncated", false);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "text/html"})
    void customOutputLimitPreservesSurrogatePairsAndAccuratelyReportsTruncation(String mime) {
        var options = new ReadOptions(false, 1, 3);
        var type = MediaType.parseMediaType(mime);
        var text = "ab😀tail";
        var body = "text/html".equals(mime) ? "<html><body>" + text + "</body></html>" : text;
        assertThat(WebPageReader.extract(
                        URL, new PublicHttpClient.Response(body.getBytes(StandardCharsets.UTF_8), type), options))
                .containsEntry("content", "ab")
                .containsEntry("truncated", true);
        var exact = "a😀";
        assertThat(WebPageReader.extract(
                        URL, new PublicHttpClient.Response(exact.getBytes(StandardCharsets.UTF_8), type), options))
                .containsEntry("content", exact)
                .containsEntry("truncated", false);
    }

    @Test
    void appliesTheConfiguredByteLimitToBothTransportAndDefensiveExtraction() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        var options = new ReadOptions(false, 1, 4);
        when(settings.readOptions()).thenReturn(Mono.just(options));
        when(http.getText(any(), anyInt(), anyBoolean()))
                .thenReturn(Mono.just(
                        new PublicHttpClient.Response(new byte[options.maxResponseBytes() + 1], MediaType.TEXT_PLAIN)));
        StepVerifier.create(new WebPageReader(http, settings).read(Map.of("url", URL.toString())))
                .expectErrorMessage("External response exceeds the size limit")
                .verify();
        verify(http).getText(URL, 1_048_576, false);
        verify(settings).readOptions();
        var exact = "a".repeat(options.maxResponseBytes()).getBytes(StandardCharsets.UTF_8);
        assertThat(WebPageReader.extract(URL, new PublicHttpClient.Response(exact, MediaType.TEXT_PLAIN), options))
                .containsEntry("content", "aaaa")
                .containsEntry("truncated", true);
    }

    @Test
    void doesNotRequestPagesWhenConfigurationCannotBeLoaded() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        when(settings.readOptions()).thenReturn(Mono.error(new IllegalStateException("Unable to load settings")));
        StepVerifier.create(new WebPageReader(http, settings).read(Map.of("url", URL.toString())))
                .expectErrorMessage("Unable to load settings")
                .verify();
        verifyNoInteractions(http);
    }

    @Test
    void rejectsMalformedSignedUrlsWithoutEchoingTheInputOrItsCause() {
        var http = mock(PublicHttpClient.class);
        var settings = mock(WebPageReaderSettings.class);
        StepVerifier.create(new WebPageReader(http, settings)
                        .read(Map.of("url", "https://example.com/doc?token=fake-sensitive token")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Invalid URL")
                        .hasNoCause())
                .verify();
        verifyNoInteractions(http, settings);
    }

    private static Map<String, Object> extract(String text, MediaType type) {
        return WebPageReader.extract(URL, new PublicHttpClient.Response(text.getBytes(StandardCharsets.UTF_8), type));
    }
}
