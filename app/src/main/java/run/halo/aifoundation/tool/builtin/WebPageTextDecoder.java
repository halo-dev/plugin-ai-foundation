package run.halo.aifoundation.tool.builtin;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.http.MediaType;

/**
 * Decodes downloaded text while preserving structured formats and rejecting binary content.
 *
 * <p>Byte-order marks take precedence over response charset declarations, followed by XML or HTML declarations and
 * UTF-8. Media detection only examines bounded prefixes; XML entities and document values are never evaluated. HTML's
 * Latin1 and ASCII labels use Windows-1252 as specified by the Encoding Standard; XML and other textual formats retain
 * their declared character encoding.
 *
 * <pre>{@code
 * var decoded = WebPageTextDecoder.decode(bytes, MediaType.APPLICATION_JSON);
 * var originalJson = decoded.text();
 * }</pre>
 */
final class WebPageTextDecoder {
    private static final int PREAMBLE_BYTES = 4_096;
    private static final Pattern XML_START = Pattern.compile("(?is)^\\s*(?:<\\?xml\\b|<(?:rss|feed|svg)\\b)");
    private static final Pattern XML_ENCODING =
            Pattern.compile("(?is)^\\s*<\\?xml\\b[^?]*?\\bencoding\\s*=\\s*['\"]([^'\"]+)['\"]");
    private static final Pattern META_CHARSET = Pattern.compile("(?i)\\bcharset\\s*=\\s*['\"]?([^\\s'\"/>;]+)");
    /**
     * Legacy labels that the Encoding Standard maps to Windows-1252 for HTML documents.
     *
     * @see <a href="https://encoding.spec.whatwg.org/#names-and-labels">Encoding names and labels</a>
     */
    private static final Set<String> HTML_WINDOWS_1252_LABELS = Set.of(
            "ansi_x3.4-1968",
            "ascii",
            "cp1252",
            "cp819",
            "csisolatin1",
            "ibm819",
            "iso-8859-1",
            "iso-ir-100",
            "iso8859-1",
            "iso88591",
            "iso_8859-1",
            "iso_8859-1:1987",
            "l1",
            "latin1",
            "us-ascii",
            "windows-1252",
            "x-cp1252");

    private static final List<int[]> BINARY_PREFIXES = List.of(
            new int[] {0x89, 0x50, 0x4e, 0x47}, // PNG
            new int[] {0xff, 0xd8, 0xff}, // JPEG
            new int[] {0x7f, 0x45, 0x4c, 0x46}, // ELF
            new int[] {0x50, 0x4b, 0x03, 0x04}, // ZIP local header
            new int[] {0x50, 0x4b, 0x05, 0x06}, // ZIP empty archive
            new int[] {0x50, 0x4b, 0x07, 0x08}, // ZIP spanning marker
            new int[] {0x1f, 0x8b}, // GZIP
            new int[] {0x37, 0x7a, 0xbc, 0xaf, 0x27, 0x1c}); // 7-Zip
    private static final List<String> BINARY_TEXT_PREFIXES =
            List.of("%PDF-", "GIF87a", "GIF89a", "Rar!", "SQLite format 3");

    private WebPageTextDecoder() {}

    static Decoded decode(byte[] body, MediaType contentType) {
        rejectBinarySignature(body);
        var encoding = encoding(body, contentType);
        String text;
        try {
            text = encoding.charset()
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body, encoding.offset(), body.length - encoding.offset()))
                    .toString();
        } catch (CharacterCodingException error) {
            throw new IllegalArgumentException("The response is not valid text in its character encoding");
        }
        var detectedType = hasDeclaredType(contentType) ? contentType.toString() : detectType(text);
        var jsonSequence = detectedType.toLowerCase(Locale.ROOT).startsWith("application/json-seq");
        rejectBinaryCharacters(text, jsonSequence);
        var html = hasDeclaredType(contentType) ? isHtml(contentType) : looksLikeHtml(text);
        return new Decoded(text, detectedType, html);
    }

    private static Encoding encoding(byte[] body, MediaType contentType) {
        if (startsWith(body, 0xff, 0xfe, 0x00, 0x00)) {
            return new Encoding(charset("UTF-32LE"), 4);
        }
        if (startsWith(body, 0x00, 0x00, 0xfe, 0xff)) {
            return new Encoding(charset("UTF-32BE"), 4);
        }
        if (startsWith(body, 0xef, 0xbb, 0xbf)) {
            return new Encoding(StandardCharsets.UTF_8, 3);
        }
        if (startsWith(body, 0xff, 0xfe)) {
            return new Encoding(StandardCharsets.UTF_16LE, 2);
        }
        if (startsWith(body, 0xfe, 0xff)) {
            return new Encoding(StandardCharsets.UTF_16BE, 2);
        }
        var preamble = new String(body, 0, Math.min(body.length, PREAMBLE_BYTES), StandardCharsets.ISO_8859_1);
        var htmlCharsetRules = usesHtmlCharsetRules(contentType, preamble);
        if (contentType != null && contentType.getCharset() != null) {
            var declared = contentType.getCharset();
            return new Encoding(htmlCharsetRules ? htmlCharset(declared.name()) : declared, 0);
        }
        // XML defines these signatures for declarations encoded without a BOM.
        if (startsWith(body, 0x00, 0x00, 0x00, 0x3c)) {
            return new Encoding(charset("UTF-32BE"), 0);
        }
        if (startsWith(body, 0x3c, 0x00, 0x00, 0x00)) {
            return new Encoding(charset("UTF-32LE"), 0);
        }
        if (startsWith(body, 0x00, 0x3c, 0x00, 0x3f)) {
            return new Encoding(StandardCharsets.UTF_16BE, 0);
        }
        if (startsWith(body, 0x3c, 0x00, 0x3f, 0x00)) {
            return new Encoding(StandardCharsets.UTF_16LE, 0);
        }
        var xmlEncoding = XML_ENCODING.matcher(preamble);
        if (xmlEncoding.find()) {
            var label = xmlEncoding.group(1);
            return new Encoding(htmlCharsetRules ? htmlCharset(label) : charset(label), 0);
        }
        if (isHtml(contentType) || (!hasDeclaredType(contentType) && looksLikeHtml(preamble))) {
            return htmlEncoding(preamble, htmlCharsetRules);
        }
        return new Encoding(StandardCharsets.UTF_8, 0);
    }

    private static Encoding htmlEncoding(String preamble, boolean htmlCharsetRules) {
        for (var meta : Jsoup.parse(preamble).select("meta")) {
            var name = metaCharset(meta);
            if (name.isBlank()) {
                continue;
            }
            try {
                return new Encoding(htmlCharsetRules ? htmlCharset(name) : Charset.forName(name.trim()), 0);
            } catch (IllegalArgumentException ignored) {
                // Ignore invalid HTML declarations and try subsequent real meta elements.
            }
        }
        return new Encoding(StandardCharsets.UTF_8, 0);
    }

    /**
     * Resolves an HTML charset label using the web's Windows-1252 aliases before falling back to the JDK registry.
     *
     * <p>For example, HTML declared as {@code ISO-8859-1} must decode byte {@code 0x93} as a left double quotation
     * mark, rather than a C1 control character. This mapping does not apply to XML or plain-text declarations.
     *
     * @param label HTTP or meta charset label, compared case-insensitively after trimming
     * @return the HTML character encoding
     * @throws IllegalArgumentException if the label is unsupported by the JDK and is not a known HTML alias
     */
    private static Charset htmlCharset(String label) {
        var normalized = label.trim().toLowerCase(Locale.ROOT);
        if (HTML_WINDOWS_1252_LABELS.contains(normalized)) {
            return Charset.forName("windows-1252");
        }
        return charset(normalized);
    }

    private static boolean usesHtmlCharsetRules(MediaType contentType, String preamble) {
        if (hasDeclaredType(contentType)) {
            return MediaType.TEXT_HTML.isCompatibleWith(contentType);
        }
        return looksLikeHtml(preamble);
    }

    private static String metaCharset(org.jsoup.nodes.Element meta) {
        var charset = meta.attr("charset");
        if (!charset.isBlank()) {
            return charset;
        }
        if (!"content-type".equalsIgnoreCase(meta.attr("http-equiv").trim())) {
            return "";
        }
        var matcher = META_CHARSET.matcher(meta.attr("content"));
        return matcher.find() ? matcher.group(1) : "";
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name.trim());
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Unsupported response character encoding");
        }
    }

    private static boolean hasDeclaredType(MediaType contentType) {
        return contentType != null && !MediaType.APPLICATION_OCTET_STREAM.isCompatibleWith(contentType);
    }

    private static boolean isHtml(MediaType contentType) {
        return contentType != null
                && (MediaType.TEXT_HTML.isCompatibleWith(contentType)
                        || MediaType.APPLICATION_XHTML_XML.isCompatibleWith(contentType));
    }

    private static String detectType(String text) {
        if (looksLikeHtml(text)) {
            return MediaType.TEXT_HTML_VALUE;
        }
        if (XML_START
                .matcher(text.substring(0, Math.min(text.length(), PREAMBLE_BYTES)))
                .find()) {
            return MediaType.APPLICATION_XML_VALUE;
        }
        if (text.startsWith("\u001e{") || text.startsWith("\u001e[")) {
            return "application/json-seq";
        }
        var trimmed = text.strip();
        if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
            return MediaType.APPLICATION_JSON_VALUE;
        }
        return MediaType.TEXT_PLAIN_VALUE;
    }

    private static boolean looksLikeHtml(String text) {
        var prefix = text.substring(0, Math.min(text.length(), PREAMBLE_BYTES)).toLowerCase(Locale.ROOT);
        int offset = skipWhitespace(prefix, 0);
        while (offset < prefix.length()) {
            int end;
            if (prefix.startsWith("<!--", offset)) {
                end = prefix.indexOf("-->", offset + 4);
                if (end < 0) {
                    return false;
                }
                offset = skipWhitespace(prefix, end + 3);
            } else if (prefix.startsWith("<?xml", offset)) {
                end = prefix.indexOf("?>", offset + 5);
                if (end < 0) {
                    return false;
                }
                offset = skipWhitespace(prefix, end + 2);
            } else {
                break;
            }
        }
        if (prefix.startsWith("<!doctype", offset)) {
            offset = skipWhitespace(prefix, offset + 9);
            return startsWithName(prefix, offset, "html");
        }
        return startsWithName(prefix, offset, "<html")
                || startsWithName(prefix, offset, "<head")
                || startsWithName(prefix, offset, "<body")
                || startsWithName(prefix, offset, "<title");
    }

    private static int skipWhitespace(String text, int offset) {
        while (offset < text.length() && Character.isWhitespace(text.charAt(offset))) {
            offset++;
        }
        return offset;
    }

    private static boolean startsWithName(String text, int offset, String name) {
        if (!text.startsWith(name, offset)) {
            return false;
        }
        int end = offset + name.length();
        return end == text.length()
                || Character.isWhitespace(text.charAt(end))
                || text.charAt(end) == '>'
                || text.charAt(end) == '/';
    }

    private static void rejectBinaryCharacters(String text, boolean jsonSequence) {
        for (int i = 0; i < text.length(); i++) {
            char value = text.charAt(i);
            if (Character.isISOControl(value) && !allowedTextControl(value, jsonSequence)) {
                throw new IllegalArgumentException("The response contains binary or invalid text data");
            }
        }
    }

    private static boolean allowedTextControl(char value, boolean jsonSequence) {
        return switch (value) {
            case '\t', '\n', '\r', '\f', '\u0085' -> true;
            case '\u001e' -> jsonSequence;
            default -> false;
        };
    }

    private static void rejectBinarySignature(byte[] body) {
        if (BINARY_PREFIXES.stream().anyMatch(prefix -> startsWith(body, prefix))) {
            throw new IllegalArgumentException("The response contains binary data");
        }
        if (BINARY_TEXT_PREFIXES.stream().anyMatch(prefix -> asciiStartsWith(body, prefix))) {
            throw new IllegalArgumentException("The response contains binary data");
        }
        if (asciiStartsWith(body, "RIFF")
                && body.length >= 12
                && new String(body, 8, 4, StandardCharsets.US_ASCII).equals("WEBP")) {
            throw new IllegalArgumentException("The response contains binary data");
        }
    }

    private static boolean asciiStartsWith(byte[] body, String prefix) {
        return body.length >= prefix.length()
                && new String(body, 0, prefix.length(), StandardCharsets.US_ASCII).equals(prefix);
    }

    private static boolean startsWith(byte[] body, int... bytes) {
        if (body.length < bytes.length) {
            return false;
        }
        for (int i = 0; i < bytes.length; i++) {
            if ((body[i] & 0xff) != bytes[i]) {
                return false;
            }
        }
        return true;
    }

    /**
     * Decoded resource and the media classification used by the reader.
     *
     * @param text original decoded text with any byte-order mark removed
     * @param contentType declared or detected response media type
     * @param html whether HTML body extraction should be applied
     */
    record Decoded(String text, String contentType, boolean html) {}

    /**
     * Character decoder and the byte offset after an optional byte-order mark.
     *
     * @param charset encoding selected from BOM, headers, document declarations or UTF-8
     * @param offset number of prefix bytes omitted before decoding
     */
    private record Encoding(Charset charset, int offset) {}
}
