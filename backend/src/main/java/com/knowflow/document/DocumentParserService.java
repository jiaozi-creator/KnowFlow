package com.knowflow.document;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class DocumentParserService {
    public ParsedDocument parse(byte[] bytes, String filename) throws Exception {
        String extension = extension(filename);
        return switch (extension) {
            case "pdf" -> parsePdf(bytes);
            case "docx" -> parseDocx(bytes);
            case "md", "markdown" -> parseMarkdown(bytes);
            case "txt" -> new ParsedDocument(List.of(new ParsedDocument.Section(null, null,
                    decodeText(bytes))));
            default -> throw new IllegalArgumentException("不支持的文档格式: " + extension);
        };
    }

    private ParsedDocument parsePdf(byte[] bytes) throws Exception {
        List<ParsedDocument.Section> sections = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document);
                if (text != null && !text.isBlank()) sections.add(new ParsedDocument.Section(page, null, text));
            }
        }
        return new ParsedDocument(sections);
    }

    private ParsedDocument parseDocx(byte[] bytes) throws Exception {
        List<ParsedDocument.Section> sections = new ArrayList<>();
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            String currentHeading = null;
            StringBuilder block = new StringBuilder();
            for (var paragraph : document.getParagraphs()) {
                String text = paragraph.getText();
                if (text == null || text.isBlank()) continue;
                String style = paragraph.getStyle();
                boolean heading = style != null && style.toLowerCase(Locale.ROOT).startsWith("heading");
                if (heading) {
                    if (!block.isEmpty()) {
                        sections.add(new ParsedDocument.Section(null, currentHeading, block.toString()));
                        block.setLength(0);
                    }
                    currentHeading = text.trim();
                } else {
                    block.append(text).append('\n');
                }
            }
            if (!block.isEmpty() || currentHeading != null) {
                sections.add(new ParsedDocument.Section(null, currentHeading, block.toString()));
            }
            document.getTables().forEach(table -> {
                StringBuilder tableText = new StringBuilder();
                table.getRows().forEach(row -> {
                    row.getTableCells().forEach(cell -> tableText.append(cell.getText()).append(" | "));
                    tableText.append('\n');
                });
                if (!tableText.isEmpty()) sections.add(new ParsedDocument.Section(null, "表格", tableText.toString()));
            });
        }
        return new ParsedDocument(sections);
    }

    private ParsedDocument parseMarkdown(byte[] bytes) {
        String markdown = decodeText(bytes);
        String text = markdown
                .replaceAll("(?m)^#{1,6}\\s*", "")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", "")
                .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                .replace("**", "").replace("__", "").replace("`", "");
        return new ParsedDocument(List.of(new ParsedDocument.Section(null, null, text)));
    }

    private String extension(String filename) {
        int index = filename.lastIndexOf('.');
        return index < 0 ? "" : filename.substring(index + 1).toLowerCase(Locale.ROOT);
    }

    /**
     * 自动识别 Markdown / TXT 常见文本编码。
     *
     * 支持：
     *
     * 1. UTF-8 BOM
     * 2. UTF-16LE BOM
     * 3. UTF-16BE BOM
     * 4. 无 BOM 的合法 UTF-8
     * 5. GB18030 / GBK / 中文 ANSI fallback
     */
    private String decodeText(byte[] bytes) {

        if (bytes == null || bytes.length == 0) {
            return "";
        }

        /*
         * UTF-8 BOM
         *
         * EF BB BF
         */
        if (
                bytes.length >= 3
                        && (bytes[0] & 0xFF) == 0xEF
                        && (bytes[1] & 0xFF) == 0xBB
                        && (bytes[2] & 0xFF) == 0xBF
        ) {

            return new String(
                    bytes,
                    3,
                    bytes.length - 3,
                    StandardCharsets.UTF_8
            );
        }

        /*
         * UTF-16 Little Endian BOM
         *
         * FF FE
         *
         * 当前 samples/差旅管理制度.md
         * 就属于这种编码。
         */
        if (
                bytes.length >= 2
                        && (bytes[0] & 0xFF) == 0xFF
                        && (bytes[1] & 0xFF) == 0xFE
        ) {

            return new String(
                    bytes,
                    2,
                    bytes.length - 2,
                    StandardCharsets.UTF_16LE
            );
        }

        /*
         * UTF-16 Big Endian BOM
         *
         * FE FF
         */
        if (
                bytes.length >= 2
                        && (bytes[0] & 0xFF) == 0xFE
                        && (bytes[1] & 0xFF) == 0xFF
        ) {

            return new String(
                    bytes,
                    2,
                    bytes.length - 2,
                    StandardCharsets.UTF_16BE
            );
        }

        /*
         * 没有 BOM 时：
         *
         * 先进行严格 UTF-8 解码。
         *
         * 如果字节不是合法 UTF-8，
         * decodeStrictUtf8 会直接抛出异常，
         * 而不会静默产生 � 字符。
         */
        try {

            return decodeStrictUtf8(bytes);

        } catch (CharacterCodingException ignored) {

            /*
             * Windows 中文环境常见：
             *
             * GBK
             * GB2312
             * ANSI
             *
             * 使用 GB18030 作为兼容 fallback。
             */
            return new String(
                    bytes,
                    Charset.forName("GB18030")
            );
        }
    }

    /**
     * 严格 UTF-8 解码。
     *
     * 非法 UTF-8 输入直接抛出 CharacterCodingException。
     */
    private String decodeStrictUtf8(byte[] bytes)
            throws CharacterCodingException {

        var decoder =
                StandardCharsets.UTF_8
                        .newDecoder()
                        .onMalformedInput(
                                CodingErrorAction.REPORT
                        )
                        .onUnmappableCharacter(
                                CodingErrorAction.REPORT
                        );

        return decoder
                .decode(
                        ByteBuffer.wrap(bytes)
                )
                .toString();
    }
}
