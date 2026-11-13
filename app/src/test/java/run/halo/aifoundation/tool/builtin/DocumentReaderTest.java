package run.halo.aifoundation.tool.builtin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import reactor.core.publisher.Mono;
import reactor.netty.http.server.HttpServer;
import reactor.test.StepVerifier;

class DocumentReaderTest {
    private final PublicHttpClient http = mock(PublicHttpClient.class);
    private final HaloAttachmentTools attachments = mock(HaloAttachmentTools.class);
    private final DocumentReaderSettings settings = mock(DocumentReaderSettings.class);
    private final DocumentReader reader = new DocumentReader(http, attachments, settings);
    private final DocumentReaderSettings.Options defaults = options(200_000, 200);

    @BeforeEach
    void setup() {
        when(settings.readOptions()).thenReturn(Mono.just(defaults));
    }

    @Test
    void publicUrlModeDoesNotDeclareAnUnconditionalAttachmentPermission() {
        var registration = reader.tools().single().block();
        assertThat(registration.getRequiredPermissions()).isEmpty();
    }

    @Test
    void extractsAnActualPdfWithPageAndTitleMetadata() throws Exception {
        var value = DocumentReader.extract(fixture("readable.pdf"), defaults);
        assertThat(value)
                .containsEntry("format", "pdf")
                .containsEntry("totalPages", 2)
                .containsEntry("pagesRead", 2)
                .containsEntry("truncated", false)
                .containsEntry("title", "Document Reader Fixture");
        assertThat(value.get("content").toString())
                .contains("Attachment tools document fixture", "Second page: bounded extraction.");
    }

    @Test
    void limitsPdfPagesAndStopsTextOutputAtTheConfiguredLimit() throws Exception {
        var first = DocumentReader.extract(fixture("readable.pdf"), options(200_000, 1));
        assertThat(first).containsEntry("pagesRead", 1).containsEntry("truncated", true);
        assertThat(first.get("content").toString()).contains("Attachment tools").doesNotContain("Second page");
        var bounded = DocumentReader.extract(fixture("readable.pdf"), options(10, 200));
        assertThat(bounded).containsEntry("truncated", true);
        assertThat(bounded.get("content").toString()).hasSize(10).isEqualTo("Attachment");
    }

    @Test
    void extractsChineseDocxParagraphsTablesLineBreaksAndTabs() throws Exception {
        var value = DocumentReader.extract(fixture("readable.docx"), defaults);
        assertThat(value).containsEntry("format", "docx").containsEntry("truncated", false);
        assertThat(value.get("content").toString())
                .contains("文档读取测试", "第一段：中文与 English。", "\n换行内容", "\t制表内容", "表格 A", "表格 B", "最后一段");
        var bounded = DocumentReader.extract(fixture("readable.docx"), options(5, 200));
        assertThat(bounded).containsEntry("content", "文档读取测").containsEntry("truncated", true);
    }

    @Test
    void extractsCommonOpcHeadersFootersFootnotesTablesAndFormatting() throws Exception {
        var value = DocumentReader.extract(fixture("rich.docx"), defaults);
        assertThat(value).containsEntry("format", "docx").containsEntry("truncated", false);
        assertThat(value.get("content").toString())
                .contains("页眉中文", "页脚结束", "正文第一段中文", "脚注说明文字", "\n换行内容", "\t制表内容", "表格 A", "表格 B", "外链标签")
                .doesNotContain("http://127.0.0.1", "external-target");
        var bounded = DocumentReader.extract(fixture("rich.docx"), options(12, 200));
        assertThat(bounded).containsEntry("truncated", true);
        assertThat(bounded.get("content").toString()).startsWith("页眉中文").hasSizeLessThanOrEqualTo(12);
    }

    @ParameterizedTest
    @ValueSource(strings = {"content/main.xml", "content/main.XML", "content/main"})
    void readsTheOpcMainDocumentRelationshipInsteadOfAssumingAFixedZipEntry(String target) throws Exception {
        var entries = unzip(fixture("readable.docx"));
        var relocated = relocateMain(entries, target);
        assertThat(DocumentReader.extract(zip(relocated), defaults)
                        .get("content")
                        .toString())
                .contains("文档读取测试", "最后一段");
    }

    @Test
    void externalHyperlinksAreTextOnlyAndCannotMakeAnHttpRequest() throws Exception {
        var requests = new AtomicInteger();
        var server = HttpServer.create()
                .host("127.0.0.1")
                .port(0)
                .handle((request, response) -> {
                    requests.incrementAndGet();
                    return response.sendString(Mono.just("must-not-read"));
                })
                .bindNow();
        try {
            var entries = unzip(fixture("rich.docx"));
            entries.compute(
                    "word/_rels/document.xml.rels",
                    (name, value) -> new String(value, StandardCharsets.UTF_8)
                            .replace("127.0.0.1:1/", "127.0.0.1:" + server.port() + "/")
                            .getBytes(StandardCharsets.UTF_8));
            var content = DocumentReader.extract(zip(entries), defaults)
                    .get("content")
                    .toString();
            assertThat(content).contains("外链标签").doesNotContain("must-not-read", "external-target", "http://");
            assertThat(requests).hasValue(0);
        } finally {
            server.disposeNow();
        }
    }

    @Test
    void extractsStrictWordTextWithoutIncludingDeletedTextOrFieldInstructions() throws Exception {
        var xml = "<w:document xmlns:w='http://purl.oclc.org/ooxml/wordprocessingml/main'><w:body><w:p>"
                + "<w:r><w:t>Visible 中文</w:t></w:r><w:r><w:instrText>INCLUDETEXT private-source</w:instrText></w:r>"
                + "<w:del><w:r><w:t>deleted-private-text</w:t></w:r></w:del>"
                + "<w:moveFrom><w:r><w:t>moved-private-text</w:t></w:r></w:moveFrom>"
                + "</w:p></w:body></w:document>";
        var content = DocumentReader.extract(docx(xml), defaults).get("content").toString();
        assertThat(content).isEqualTo("Visible 中文");
    }

    @Test
    void readsTheDrawingMlTextBoxOnceInsteadOfRepeatingItsVmlFallback() throws Exception {
        var xml = wordPart(
                "document",
                "<w:body>" + textBox("Text box content", "Text box content") + paragraph("Body after text box")
                        + "</w:body>");

        var result = DocumentReader.extract(docx(xml), defaults);

        assertThat(result).containsEntry("truncated", false);
        assertThat(result.get("content").toString())
                .containsOnlyOnce("Text box content")
                .contains("Body after text box");
    }

    @Test
    void selectsTheFirstChoiceOnlyWhenAllRequiredNamespacesAreUnderstood() throws Exception {
        var alternatives = "<mc:AlternateContent><mc:Choice Requires='wps future'>"
                + paragraph("Unsupported first choice") + "</mc:Choice><mc:Choice Requires='wps w'>"
                + paragraph("First supported choice") + "</mc:Choice><mc:Choice Requires='w'>"
                + paragraph("Later supported choice") + "</mc:Choice><mc:Fallback>"
                + paragraph("Unused fallback") + "</mc:Fallback></mc:AlternateContent>";

        var result =
                DocumentReader.extract(docx(wordPart("document", "<w:body>" + alternatives + "</w:body>")), defaults);

        assertThat(result.get("content")).isEqualTo("First supported choice");
    }

    @Test
    void selectsFallbackForUnknownNamespacesAndSkipsAnUnsupportedChoiceWithoutFallback() throws Exception {
        var alternatives = "<mc:AlternateContent><mc:Choice Requires='undeclared'>"
                + paragraph("Unresolved namespace choice") + "</mc:Choice><mc:Fallback>"
                + paragraph("Fallback content") + "</mc:Fallback></mc:AlternateContent>"
                + "<mc:AlternateContent><mc:Choice Requires='future'>"
                + paragraph("Unsupported choice without fallback") + "</mc:Choice></mc:AlternateContent>"
                + paragraph("Visible after alternatives");

        var result =
                DocumentReader.extract(docx(wordPart("document", "<w:body>" + alternatives + "</w:body>")), defaults);

        assertThat(result.get("content").toString())
                .contains("Fallback content", "Visible after alternatives")
                .doesNotContain("Unresolved", "Unsupported");
    }

    @Test
    void keepsNestedAlternativeSelectionsIndependentAndSkipsDeletedRevisionSubtrees() throws Exception {
        var inner = "<mc:AlternateContent><mc:Choice Requires='future'>"
                + paragraph("Unsupported nested choice") + "</mc:Choice><mc:Fallback>"
                + paragraph("Nested fallback") + "</mc:Fallback></mc:AlternateContent>";
        var deleted = "<w:del><mc:AlternateContent><mc:Choice Requires='w'><w:moveFrom>"
                + paragraph("Deleted nested choice") + "</w:moveFrom></mc:Choice><mc:Fallback>"
                + paragraph("Deleted nested fallback") + "</mc:Fallback></mc:AlternateContent></w:del>";
        var outer = "<mc:AlternateContent><mc:Choice Requires='wps'>" + paragraph("Outer choice") + inner
                + deleted + "<w:moveFrom>" + paragraph("Moved text") + "</w:moveFrom></mc:Choice>"
                + "<mc:Fallback>" + textBox("Unused nested text box", "Unused nested fallback")
                + "</mc:Fallback></mc:AlternateContent>" + paragraph("Visible after nested alternatives");

        var result = DocumentReader.extract(docx(wordPart("document", "<w:body>" + outer + "</w:body>")), defaults);

        assertThat(result.get("content").toString())
                .contains("Outer choice", "Nested fallback", "Visible after nested alternatives")
                .doesNotContain("Unsupported", "Deleted", "Moved", "Unused");
    }

    @Test
    void readsSupportedChoiceWithoutFallbackAndKeepsDistinctTextBoxesWithTheSameText() throws Exception {
        var choiceOnly = "<mc:AlternateContent><mc:Choice Requires='wps'>" + paragraph("Choice without fallback")
                + "</mc:Choice></mc:AlternateContent>";
        var xml = wordPart(
                "document",
                "<w:body>" + choiceOnly + textBox("Repeated label", "Repeated label")
                        + textBox("Repeated label", "Repeated label") + "</w:body>");

        var result = DocumentReader.extract(docx(xml), defaults);

        assertThat(result.get("content").toString()).contains("Choice without fallback");
        assertThat(result.get("content").toString().split("Repeated label", -1)).hasSize(3);
    }

    @Test
    void readsTextBoxAlternativesOnceInHeadersAndFooters() throws Exception {
        var entries = unzip(fixture("rich.docx"));
        entries.put(
                "word/header1.xml",
                wordPart("hdr", textBox("Header text box", "Header text box")).getBytes(StandardCharsets.UTF_8));
        entries.put(
                "word/footer1.xml",
                wordPart("ftr", textBox("Footer text box", "Footer text box")).getBytes(StandardCharsets.UTF_8));

        var result = DocumentReader.extract(zip(entries), defaults);

        assertThat(result.get("content").toString())
                .containsOnlyOnce("Header text box")
                .containsOnlyOnce("Footer text box")
                .contains("正文第一段中文");
    }

    @Test
    void refusesAnExternalMainDocumentRelationshipInsteadOfFetchingOrGuessingABody() throws Exception {
        var entries = docxEntries(wordDocument("Must not guess this body"));
        entries.put(
                "_rels/.rels",
                ("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                                + "<Relationship Id='document' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' "
                                + "TargetMode='External' Target='http://127.0.0.1:1/private-document'/></Relationships>")
                        .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DocumentReader.extract(zip(entries), defaults))
                .hasMessageContaining("not a DOCX")
                .hasMessageNotContaining("private-document");
    }

    @Test
    void reportsPasswordProtectedScannedAndExtractionRestrictedPdfs() throws Exception {
        try (var document = Loader.loadPDF(fixture("readable.pdf"))) {
            var protection = new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission());
            document.protect(protection);
            var bytes = new ByteArrayOutputStream();
            document.save(bytes);
            assertThatThrownBy(() -> DocumentReader.extract(bytes.toByteArray(), defaults))
                    .hasMessageContaining("password protected");
        }
        try (var document = new PDDocument()) {
            document.addPage(new PDPage());
            var bytes = new ByteArrayOutputStream();
            document.save(bytes);
            assertThatThrownBy(() -> DocumentReader.extract(bytes.toByteArray(), defaults))
                    .hasMessageContaining("scanned or image-only");
        }
        try (var document = Loader.loadPDF(fixture("readable.pdf"))) {
            var permission = new AccessPermission();
            permission.setCanExtractContent(false);
            document.protect(new StandardProtectionPolicy("owner-password", "", permission));
            var bytes = new ByteArrayOutputStream();
            document.save(bytes);
            assertThatThrownBy(() -> DocumentReader.extract(bytes.toByteArray(), defaults))
                    .hasMessageContaining("does not permit text extraction");
        }
    }

    @Test
    void rejectsDamagedUnsupportedAndIncompleteDocuments() throws Exception {
        assertThatThrownBy(() -> DocumentReader.extract("%PDF-1.4\nbroken".getBytes(), defaults))
                .hasMessageContaining("damaged");
        var docx = fixture("readable.docx");
        assertThatThrownBy(() -> DocumentReader.extract(Arrays.copyOf(docx, docx.length - 15), defaults))
                .hasMessageContaining("damaged or incomplete");
        assertThatThrownBy(() ->
                        DocumentReader.extract(new byte[] {(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0}, defaults))
                .hasMessageContaining("Encrypted or legacy Office");
        assertThatThrownBy(() -> DocumentReader.extract("ordinary text".getBytes(), defaults))
                .hasMessageContaining("read_web_page");
        assertThatThrownBy(() -> DocumentReader.extract(zip(Map.of("other", new byte[0])), defaults))
                .hasMessageContaining("not a DOCX");
    }

    @Test
    void rejectsXmlEntitiesAndBoundsExpandedZipBytesAndEntryCounts() throws Exception {
        var xml = "<!DOCTYPE x [<!ENTITY external SYSTEM 'http://127.0.0.1:1/private'>]>"
                + "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"
                + "<w:body><w:p><w:r><w:t>&external;</w:t></w:r></w:p></w:body></w:document>";
        assertThatThrownBy(() -> DocumentReader.extract(docx(xml), defaults))
                .isInstanceOf(IllegalArgumentException.class);
        var expandedParts = docxEntries(wordDocument("Fixture"));
        expandedParts.put("unused.bin", new byte[11 * 1_048_576]);
        var expanded = zip(expandedParts);
        assertThatThrownBy(() ->
                        DocumentReader.extract(expanded, new DocumentReaderSettings.Options(false, 1, 200_000, 200)))
                .hasMessageContaining("expanded archive size limit");
        var many = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(many)) {
            for (int i = 0; i < 2_001; i++) {
                zip.putNextEntry(new ZipEntry("entry-" + i));
                zip.closeEntry();
            }
        }
        assertThatThrownBy(() -> DocumentReader.extract(many.toByteArray(), defaults))
                .hasMessageContaining("too many archive entries");
    }

    @Test
    void rejectsDuplicateOpcPartsXmlBombsAndEntitiesInPackageRelationships() throws Exception {
        var entries = docxEntries(wordDocument("Fixture"));
        entries.put("word/document.xmX", entries.get("word/document.xml"));
        var duplicate = zip(entries);
        var from = "word/document.xmX".getBytes(StandardCharsets.US_ASCII);
        var to = "word/document.xml".getBytes(StandardCharsets.US_ASCII);
        for (int i = 0; i <= duplicate.length - from.length; i++) {
            if (Arrays.equals(Arrays.copyOfRange(duplicate, i, i + from.length), from)) {
                System.arraycopy(to, 0, duplicate, i, to.length);
            }
        }
        assertThatThrownBy(() -> DocumentReader.extract(duplicate, defaults))
                .hasMessageContaining("duplicate archive entries");
        var xmlBomb = docxEntries(wordDocument("Fixture"));
        xmlBomb.put("unused.xml", new byte[33 * 1_048_576]);
        assertThatThrownBy(() -> DocumentReader.extract(zip(xmlBomb), defaults)).hasMessageContaining("XML size limit");
        var malicious = docxEntries(wordDocument("Fixture"));
        malicious.put(
                "_rels/.rels",
                ("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///private-document'>]>"
                                + "<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>&external;</Relationships>")
                        .getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> DocumentReader.extract(zip(malicious), defaults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("private-document");
    }

    @Test
    void extensionlessXmlMainPartsStillReceiveEntityAndSizePrechecks() throws Exception {
        var malicious = relocateMain(
                docxEntries("<!DOCTYPE x [<!ENTITY external SYSTEM 'file:///private-document'>]>"
                        + wordDocument("&external;")),
                "content/main");
        assertThatThrownBy(() -> DocumentReader.extract(zip(malicious), defaults))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("entities")
                .hasMessageNotContaining("private-document");
        var oversized = relocateMain(docxEntries(wordDocument("Fixture")), "content/main");
        oversized.put("content/main", new byte[33 * 1_048_576]);
        assertThatThrownBy(() -> DocumentReader.extract(zip(oversized), defaults))
                .hasMessageContaining("XML size limit");
    }

    @Test
    void directoryEntriesCannotHideExpandedBytesFromTheArchiveBudget() throws Exception {
        var entries = docxEntries(wordDocument("Fixture"));
        entries.put("unused-directory/", new byte[11 * 1_048_576]);
        assertThatThrownBy(() -> DocumentReader.extract(
                        zip(entries), new DocumentReaderSettings.Options(false, 1, 200_000, 200)))
                .hasMessageContaining("expanded archive size limit");
    }

    @Test
    void readsTheFinalPublicUrlAndRoutesAttachmentsWithoutExposingInternalSharedUrls() throws Exception {
        var requested = URI.create("https://example.com/file.pdf");
        var finalUri = URI.create("https://cdn.example.com/document.pdf");
        var bytes = fixture("readable.pdf");
        when(http.getResource(requested, defaults.maxResponseBytes(), false))
                .thenReturn(Mono.just(new PublicHttpClient.Response(bytes, MediaType.APPLICATION_PDF, finalUri)));
        StepVerifier.create(reader.read(Map.of("url", requested.toString())))
                .assertNext(value -> assertThat(map(value))
                        .containsEntry("url", requested.toString())
                        .containsEntry("finalUrl", finalUri.toString())
                        .containsEntry("format", "pdf"))
                .verifyComplete();
        when(attachments.downloadAttachment("registered", defaults.maxResponseBytes(), false))
                .thenReturn(Mono.just(new PublicHttpClient.Response(
                        bytes,
                        MediaType.APPLICATION_PDF,
                        URI.create("https://private.example/file?signature=secret"))));
        StepVerifier.create(reader.read(Map.of("attachmentName", "registered")))
                .assertNext(value -> {
                    assertThat(map(value))
                            .containsEntry("attachmentName", "registered")
                            .doesNotContainKeys("url", "finalUrl");
                    assertThat(value.toString()).doesNotContain("signature", "private.example", "secret");
                })
                .verifyComplete();
        verify(attachments).downloadAttachment("registered", defaults.maxResponseBytes(), false);
    }

    @Test
    void propagatesAttachmentPermissionDenialFromTheAuthorizedDownloader() {
        when(attachments.downloadAttachment("registered", defaults.maxResponseBytes(), false))
                .thenReturn(Mono.error(new AccessDeniedException("denied")));
        StepVerifier.create(reader.read(Map.of("attachmentName", "registered")))
                .expectError(AccessDeniedException.class)
                .verify();
        verifyNoInteractions(http);
        verify(attachments).downloadAttachment("registered", defaults.maxResponseBytes(), false);
    }

    @Test
    void rejectsAmbiguousSourcesBeforeDownloadingOrReadingSettings() {
        for (var input : java.util.List.of(
                Map.<String, Object>of(),
                Map.<String, Object>of("url", "https://example.com/file", "attachmentName", "file"),
                Map.<String, Object>of("attachmentName", "../file"),
                Map.<String, Object>of("url", "https://example.com/file", "unknown", true))) {
            StepVerifier.create(reader.read(input))
                    .expectError(IllegalArgumentException.class)
                    .verify();
        }
        verifyNoInteractions(http, attachments, settings);
    }

    @Test
    void propagatesConfigurationAndDownloadFailuresAndEnforcesTheByteBudgetAgain() {
        when(settings.readOptions()).thenReturn(Mono.error(new IllegalArgumentException("invalid saved config")));
        StepVerifier.create(reader.read(Map.of("url", "https://example.com/file.pdf")))
                .expectErrorMessage("invalid saved config")
                .verify();
        verifyNoInteractions(http, attachments);
        assertThatThrownBy(() -> DocumentReader.extract(
                        new byte[1_048_577], new DocumentReaderSettings.Options(false, 1, 200_000, 200)))
                .hasMessageContaining("download size limit");
    }

    @Test
    void malformedSignedInputNeverAppearsInAnErrorOrItsCause() {
        StepVerifier.create(reader.read(Map.of("url", "https://example.com/doc?token=private token")))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(IllegalArgumentException.class)
                        .hasMessage("Invalid URL")
                        .hasNoCause())
                .verify();
        verifyNoInteractions(http, attachments, settings);
    }

    private static DocumentReaderSettings.Options options(int characters, int pages) {
        return new DocumentReaderSettings.Options(false, 20, characters, pages);
    }

    private static byte[] fixture(String name) throws IOException {
        try (var stream = DocumentReaderTest.class.getResourceAsStream("/documents/" + name)) {
            if (stream == null) throw new IOException("Missing document fixture: " + name);
            return stream.readAllBytes();
        }
    }

    private static byte[] docx(String document) throws IOException {
        return zip(docxEntries(document));
    }

    private static LinkedHashMap<String, byte[]> docxEntries(String document) {
        var entries = new LinkedHashMap<String, byte[]>();
        entries.put(
                "[Content_Types].xml",
                ("<Types xmlns='http://schemas.openxmlformats.org/package/2006/content-types'>"
                                + "<Default Extension='rels' ContentType='application/vnd.openxmlformats-package.relationships+xml'/>"
                                + "<Default Extension='xml' ContentType='application/xml'/>"
                                + "<Override PartName='/word/document.xml' ContentType='application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml'/></Types>")
                        .getBytes(StandardCharsets.UTF_8));
        entries.put(
                "_rels/.rels",
                ("<Relationships xmlns='http://schemas.openxmlformats.org/package/2006/relationships'>"
                                + "<Relationship Id='document' Type='http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument' Target='word/document.xml'/></Relationships>")
                        .getBytes(StandardCharsets.UTF_8));
        entries.put("word/document.xml", document.getBytes(StandardCharsets.UTF_8));
        return entries;
    }

    private static String wordDocument(String text) {
        return "<w:document xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'>"
                + "<w:body><w:p><w:r><w:t>" + text + "</w:t></w:r></w:p></w:body></w:document>";
    }

    /**
     * Builds a Word text part with the namespaces used by modern and fallback text-box representations.
     *
     * @param root the native Word root, such as document, hdr or ftr
     * @param markup text-part children containing ordinary or alternate WordprocessingML
     * @return a complete text-part XML document
     */
    private static String wordPart(String root, String markup) {
        return "<w:" + root + " xmlns:w='http://schemas.openxmlformats.org/wordprocessingml/2006/main'"
                + " xmlns:mc='http://schemas.openxmlformats.org/markup-compatibility/2006'"
                + " xmlns:wps='http://schemas.microsoft.com/office/word/2010/wordprocessingShape'"
                + " xmlns:wp='http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing'"
                + " xmlns:a='http://schemas.openxmlformats.org/drawingml/2006/main'"
                + " xmlns:v='urn:schemas-microsoft-com:vml' xmlns:future='urn:unsupported-word-feature'>"
                + markup + "</w:" + root + ">";
    }

    private static String paragraph(String text) {
        return "<w:p><w:r><w:t>" + text + "</w:t></w:r></w:p>";
    }

    /**
     * Models Word's DrawingML text box and its VML fallback as two alternatives of one logical shape.
     *
     * <p>Both representations contain WordprocessingML text. For example, identical choice and fallback labels must
     * yield one occurrence without deduplicating separate shapes that happen to share a label.
     *
     * @param choiceText visible text in the modern Word shape
     * @param fallbackText visible text in its legacy representation
     * @return a Word paragraph containing one alternate-content text box
     */
    private static String textBox(String choiceText, String fallbackText) {
        return "<w:p><w:r><mc:AlternateContent><mc:Choice Requires='wps'><w:drawing>"
                + "<wp:inline><wp:extent cx='1270000' cy='254000'/><wp:docPr id='1' name='Text Box'/>"
                + "<a:graphic><a:graphicData uri='http://schemas.microsoft.com/office/word/2010/wordprocessingShape'>"
                + "<wps:wsp><wps:cNvSpPr txBox='1'/><wps:spPr/><wps:txbx><w:txbxContent>"
                + paragraph(choiceText) + "</w:txbxContent></wps:txbx><wps:bodyPr/></wps:wsp>"
                + "</a:graphicData></a:graphic></wp:inline></w:drawing></mc:Choice>"
                + "<mc:Fallback><w:pict><v:shape id='TextBoxFallback' style='width:100pt;height:20pt'>"
                + "<v:textbox><w:txbxContent>" + paragraph(fallbackText)
                + "</w:txbxContent></v:textbox></v:shape></w:pict></mc:Fallback></mc:AlternateContent></w:r></w:p>";
    }

    private static LinkedHashMap<String, byte[]> relocateMain(Map<String, byte[]> entries, String target) {
        var relocated = new LinkedHashMap<String, byte[]>();
        for (var entry : entries.entrySet()) {
            var name = entry.getKey();
            var value = entry.getValue();
            if (name.equals("[Content_Types].xml") || name.equals("_rels/.rels")) {
                value = new String(value, StandardCharsets.UTF_8)
                        .replace("word/document.xml", target)
                        .getBytes(StandardCharsets.UTF_8);
            }
            relocated.put(name.equals("word/document.xml") ? target : name, value);
        }
        return relocated;
    }

    private static LinkedHashMap<String, byte[]> unzip(byte[] bytes) throws IOException {
        var entries = new LinkedHashMap<String, byte[]>();
        try (var zip = new ZipInputStream(new java.io.ByteArrayInputStream(bytes), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
                zip.closeEntry();
            }
        }
        return entries;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws IOException {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
