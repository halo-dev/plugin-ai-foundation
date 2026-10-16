package run.halo.aifoundation.tool.builtin;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.aifoundation.service.tool.ToolSettingsStore;
import run.halo.aifoundation.tool.ToolConfiguration;

/**
 * Stores independent limits for document reading and remote attachment import.
 *
 * <p>Document reads accept download, extracted-text, and PDF-page limits. Attachment imports accept only the download
 * limit. Each tool also owns its VPN compatibility switch. For example, saving {@code {"maxPages":400}} on the document
 * tool does not change attachment-import settings.
 *
 * <p>Forms use the standard FormKit schemas in {@code tools/configuration/document-reader.yaml} and
 * {@code tools/configuration/attachment-import.yaml}. Both saved values and execution-time values are validated on the
 * backend.
 */
@Component
@RequiredArgsConstructor
public class DocumentReaderSettings {
    /** Identifier of the tool that extracts text from a document. */
    public static final String DOCUMENT_ID = "ai-foundation/read_document";

    /** Identifier of the tool that imports a remote file into Halo attachments. */
    public static final String IMPORT_ID = "ai-foundation/import_attachment";

    private static final String SIZE = "maxResponseSizeMb";
    private static final String CONTENT = "maxContentCharacters";
    private static final String PAGES = "maxPages";
    private static final String VPN = "vpnCompatibilityEnabled";
    private final ToolSettingsStore settings;

    /**
     * Creates the configuration form for document reading.
     *
     * @return document form and callbacks backed by {@link #DOCUMENT_ID}
     */
    public ToolConfiguration configuration() {
        return configuration(DOCUMENT_ID, true);
    }

    /**
     * Creates the configuration form for attachment import.
     *
     * @return import form and callbacks backed by {@link #IMPORT_ID}
     */
    public ToolConfiguration importConfiguration() {
        return configuration(IMPORT_ID, false);
    }

    /**
     * Reads and validates document-reading options for each subscription.
     *
     * @return saved options, with defaults for omitted fields
     */
    public Mono<Options> readOptions() {
        return read(DOCUMENT_ID, true);
    }

    /**
     * Reads and validates attachment-import options for each subscription.
     *
     * @return import options whose content and page limits retain the document defaults
     */
    public Mono<Options> importOptions() {
        return read(IMPORT_ID, false);
    }

    private ToolConfiguration configuration(String id, boolean document) {
        var schemaName = document ? "document-reader" : "attachment-import";
        return ToolConfiguration.builder()
                .schema(ToolConfigurationSchemas.load(schemaName))
                .values(() -> read(id, document).map(options -> values(options, document)))
                .save(input ->
                        Mono.defer(() -> settings.saveConfiguration(id, values(parse(input, document), document))))
                .build();
    }

    private Mono<Options> read(String id, boolean document) {
        return Mono.defer(() -> settings.configuration(id))
                .defaultIfEmpty(Map.of())
                .map(values -> parse(values, document));
    }

    private static Options parse(Map<String, Object> values, boolean document) {
        var allowed = document ? Set.of(SIZE, CONTENT, PAGES, VPN) : Set.of(SIZE, VPN);
        if (values == null) {
            throw new IllegalArgumentException("包含不支持的文档或附件导入配置项");
        }
        if (values.keySet().stream().anyMatch(key -> key == null || !allowed.contains(key))) {
            throw new IllegalArgumentException("包含不支持的文档或附件导入配置项");
        }
        var vpn = values.get(VPN);
        if (values.containsKey(VPN) && !(vpn instanceof Boolean)) {
            throw new IllegalArgumentException("VPN 兼容模式必须为布尔值");
        }
        return new Options(
                Boolean.TRUE.equals(vpn),
                integer(values, SIZE, 20, 100),
                document ? integer(values, CONTENT, 200_000, 2_000_000) : 200_000,
                document ? integer(values, PAGES, 200, 2_000) : 200);
    }

    private static Map<String, Object> values(Options options, boolean document) {
        if (!document) {
            return Map.of(SIZE, options.maxResponseSizeMb(), VPN, options.vpnCompatibilityEnabled());
        }
        return Map.of(
                SIZE,
                options.maxResponseSizeMb(),
                CONTENT,
                options.maxContentCharacters(),
                PAGES,
                options.maxPages(),
                VPN,
                options.vpnCompatibilityEnabled());
    }

    private static int integer(Map<String, Object> values, String field, int fallback, int maximum) {
        if (!values.containsKey(field)) {
            return fallback;
        }
        if (!(values.get(field) instanceof Number number)) {
            throw invalidInteger(field, maximum);
        }
        int value;
        try {
            value = new BigDecimal(number.toString()).intValueExact();
        } catch (ArithmeticException | NumberFormatException error) {
            throw invalidInteger(field, maximum);
        }
        if (value < 1 || value > maximum) {
            throw invalidInteger(field, maximum);
        }
        return value;
    }

    private static IllegalArgumentException invalidInteger(String field, int maximum) {
        return new IllegalArgumentException(field + " 必须为 1 到 " + maximum + " 之间的整数");
    }

    /**
     * Validated options for one document read or attachment import.
     *
     * @param vpnCompatibilityEnabled whether trusted VPN Fake-IP addresses are allowed
     * @param maxResponseSizeMb maximum decompressed download size in mebibytes
     * @param maxContentCharacters maximum number of extracted text characters
     * @param maxPages maximum number of PDF pages to extract
     */
    public record Options(
            boolean vpnCompatibilityEnabled, int maxResponseSizeMb, int maxContentCharacters, int maxPages) {
        /**
         * Converts the configured mebibyte limit into bytes for the HTTP client.
         *
         * @return maximum decompressed download size in bytes
         */
        public int maxResponseBytes() {
            return Math.multiplyExact(maxResponseSizeMb, 1_048_576);
        }
    }
}
