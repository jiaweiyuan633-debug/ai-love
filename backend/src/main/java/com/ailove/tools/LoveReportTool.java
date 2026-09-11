package com.ailove.tools;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.BaseFont;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

/**
 * 恋爱报告生成工具：把模型的分析结论排版成一份中文 PDF 报告。
 * 使用 OpenPDF + STSong-Light 内置 CJK 字体，无需携带字体文件。
 */
@Component
public class LoveReportTool {

    private static final Logger log = LoggerFactory.getLogger(LoveReportTool.class);

    private static final Path REPORT_DIR = Path.of("data", "reports");

    @Tool(description = "生成一份恋爱分析 PDF 报告并返回文件路径，当用户明确要求生成报告时调用")
    public String generateLoveReport(
            @ToolParam(description = "报告标题，例如：小明与小红的恋爱攻略") String title,
            @ToolParam(description = "报告正文内容，markdown 风格的纯文本，可分点") String content) {
        try {
            Files.createDirectories(REPORT_DIR);
            String fileName = "love-report-" + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".pdf";
            Path target = REPORT_DIR.resolve(fileName);

            BaseFont cjk = BaseFont.createFont("STSong-Light", "UniGB-UCS2-H", BaseFont.NOT_EMBEDDED);
            Font titleFont = new Font(cjk, 20, Font.BOLD);
            Font bodyFont = new Font(cjk, 12, Font.NORMAL);

            Document document = new Document(PageSize.A4, 50, 50, 60, 60);
            com.lowagie.text.pdf.PdfWriter.getInstance(document, Files.newOutputStream(target));
            document.open();
            Paragraph titlePara = new Paragraph(title, titleFont);
            titlePara.setAlignment(Element.ALIGN_CENTER);
            titlePara.setSpacingAfter(20);
            document.add(titlePara);
            for (String line : content.split("\n")) {
                document.add(new Paragraph(line, bodyFont));
            }
            Paragraph footer = new Paragraph("AI 恋爱大师 生成于 " + LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")), bodyFont);
            footer.setSpacingBefore(24);
            document.add(footer);
            document.close();

            String absolutePath = target.toAbsolutePath().toString();
            log.info("恋爱报告已生成: {}", absolutePath);
            return "报告已生成：" + absolutePath;
        } catch (IOException e) {
            log.warn("恋爱报告生成失败", e);
            return "报告生成失败：" + e.getMessage();
        }
    }
}
