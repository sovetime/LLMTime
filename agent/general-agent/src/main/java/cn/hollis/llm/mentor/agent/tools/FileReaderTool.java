package cn.hollis.llm.mentor.agent.tools;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.sl.usermodel.TextShape;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * 通用文件读取工具，供 ReactAgent 调用。
 * 支持格式：PDF、Word（docx/doc）、Excel（xlsx/xls）、PowerPoint（pptx）、
 * 纯文本（txt/md/csv/log/xml/json/yaml/yml/properties）
 */
public class FileReaderTool {

    private static final Logger log = LoggerFactory.getLogger(FileReaderTool.class);

    /**
     * 单次返回文本的最大字符数，防止超出上下文窗口
     */
    private static final int MAX_CHARS = 8000;

    @Tool(description = """
            读取本地文件并返回其文本内容。
            支持格式：
              - PDF (.pdf)
              - Word (.docx / .doc)
              - Excel (.xlsx / .xls)  —— 返回所有 Sheet 的表格文本
              - PowerPoint (.pptx)    —— 返回所有幻灯片的文字
              - 纯文本 (.txt / .md / .csv / .log / .xml / .json / .yaml / .yml / .properties)
            参数 filePath 为文件的绝对路径。
            对于 PDF 文件，可选参数 startPage / endPage 指定读取页码范围（从 1 开始）；其他格式忽略此参数。
            若文本过长会自动截断并在末尾注明剩余字符数。
            """)
    public String read_file(
            @ToolParam(description = "文件的绝对路径，支持 pdf/docx/doc/xlsx/xls/pptx/txt/md/csv 等格式") String filePath,
            @ToolParam(required = false, description = "【仅 PDF 有效】起始页码（从 1 开始，默认第 1 页）") Integer startPage,
            @ToolParam(required = false, description = "【仅 PDF 有效】结束页码（含，默认最后一页）") Integer endPage) {

        if (filePath == null || filePath.isBlank()) {
            return "Error: filePath 不能为空";
        }

        File file = new File(filePath);
        if (!file.exists()) {
            return "Error: 文件不存在 -> " + filePath;
        }
        if (!file.canRead()) {
            return "Error: 文件无读取权限 -> " + filePath;
        }

        String ext = getExtension(file.getName()).toLowerCase();

        try {
            return switch (ext) {
                case "pdf" -> readPdf(file, startPage, endPage);
                case "docx" -> readDocx(file);
                case "doc" -> readDoc(file);
                case "xlsx" -> readExcel(file, false);
                case "xls" -> readExcel(file, true);
                case "pptx" -> readPptx(file);
                default -> readText(file);
            };
        } catch (Exception e) {
            log.error("读取文件失败: {}", filePath, e);
            return "Error: 读取文件失败 -> " + e.getMessage();
        }
    }

    // -------------------------------------------------------------------------
    // PDF
    // -------------------------------------------------------------------------
    private String readPdf(File file, Integer startPage, Integer endPage) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file)) {
            int total = doc.getNumberOfPages();
            int from = (startPage != null && startPage >= 1) ? startPage : 1;
            int to = (endPage != null && endPage >= 1) ? Math.min(endPage, total) : total;

            if (from > total) {
                return String.format("Error: startPage(%d) 超过文件总页数(%d)", from, total);
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(from);
            stripper.setEndPage(to);
            stripper.setSortByPosition(true);

            String text = stripper.getText(doc);
            String header = String.format("[PDF  共 %d 页，本次读取第 %d-%d 页]\n\n", total, from, to);
            return header + truncate(text);
        }
    }

    // -------------------------------------------------------------------------
    // Word docx
    // -------------------------------------------------------------------------
    private String readDocx(File file) throws IOException {
        try (XWPFDocument doc = new XWPFDocument(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();
            for (XWPFParagraph para : doc.getParagraphs()) {
                String text = para.getText();
                if (text != null && !text.isBlank()) {
                    sb.append(text).append("\n");
                }
            }
            return "[Word (.docx)]\n\n" + truncate(sb.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Word doc (旧格式)
    // -------------------------------------------------------------------------
    private String readDoc(File file) throws IOException {
        try (HWPFDocument doc = new HWPFDocument(new FileInputStream(file));
             WordExtractor extractor = new WordExtractor(doc)) {
            String text = String.join("\n", extractor.getParagraphText());
            return "[Word (.doc)]\n\n" + truncate(text);
        }
    }

    // -------------------------------------------------------------------------
    // Excel xlsx / xls
    // -------------------------------------------------------------------------
    private String readExcel(File file, boolean isOld) throws IOException {
        try (InputStream is = new FileInputStream(file);
             Workbook wb = isOld ? new HSSFWorkbook(is) : new XSSFWorkbook(is)) {

            StringBuilder sb = new StringBuilder();
            DataFormatter formatter = new DataFormatter();

            for (int si = 0; si < wb.getNumberOfSheets(); si++) {
                Sheet sheet = wb.getSheetAt(si);
                sb.append("=== Sheet: ").append(sheet.getSheetName()).append(" ===\n");
                for (Row row : sheet) {
                    StringBuilder rowSb = new StringBuilder();
                    for (Cell cell : row) {
                        if (rowSb.length() > 0) rowSb.append("\t");
                        rowSb.append(formatter.formatCellValue(cell));
                    }
                    String rowStr = rowSb.toString().trim();
                    if (!rowStr.isEmpty()) {
                        sb.append(rowStr).append("\n");
                    }
                }
                sb.append("\n");
            }
            String ext = isOld ? ".xls" : ".xlsx";
            return "[Excel (" + ext + ") 共 " + wb.getNumberOfSheets() + " 个 Sheet]\n\n" + truncate(sb.toString());
        }
    }

    // -------------------------------------------------------------------------
    // PowerPoint pptx
    // -------------------------------------------------------------------------
    private String readPptx(File file) throws IOException {
        try (XMLSlideShow ppt = new XMLSlideShow(new FileInputStream(file))) {
            StringBuilder sb = new StringBuilder();
            int slideNum = 1;
            for (var slide : ppt.getSlides()) {
                sb.append("--- 第 ").append(slideNum++).append(" 页 ---\n");
                for (var shape : slide.getShapes()) {
                    if (shape instanceof TextShape<?, ?> ts) {
                        String text = ts.getText();
                        if (text != null && !text.isBlank()) {
                            sb.append(text).append("\n");
                        }
                    }
                }
                sb.append("\n");
            }
            return "[PowerPoint (.pptx) 共 " + ppt.getSlides().size() + " 页]\n\n" + truncate(sb.toString());
        }
    }

    // -------------------------------------------------------------------------
    // 纯文本（txt/md/csv/log/xml/json/yaml/yml/properties 等）
    // -------------------------------------------------------------------------
    private String readText(File file) throws IOException {
        String content = Files.readString(file.toPath(), StandardCharsets.UTF_8);
        String ext = getExtension(file.getName()).toUpperCase();
        return "[文本文件 (." + ext.toLowerCase() + ")]\n\n" + truncate(content);
    }

    // -------------------------------------------------------------------------
    // 工具方法
    // -------------------------------------------------------------------------
    private String truncate(String text) {
        if (text == null) return "";
        if (text.length() <= MAX_CHARS) return text;
        int remaining = text.length() - MAX_CHARS;
        return text.substring(0, MAX_CHARS)
                + String.format("\n\n...[内容过长，已截断，剩余约 %d 字符未显示，请指定更小的范围继续读取]", remaining);
    }

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot >= 0 && dot < filename.length() - 1) ? filename.substring(dot + 1) : "";
    }
}
