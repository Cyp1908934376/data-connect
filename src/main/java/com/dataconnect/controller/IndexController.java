package com.dataconnect.controller;

import com.dataconnect.repository.DsConfigRepository;
import com.dataconnect.repository.FlowConfigRepository;
import com.dataconnect.repository.TaskConfigRepository;
import com.dataconnect.repository.TemplateRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

@Controller
public class IndexController {

    @Autowired
    private DsConfigRepository dsConfigRepository;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private TaskConfigRepository taskConfigRepository;

    @GetMapping("/guide")
    public String guide() {
        return "guide";
    }

    @GetMapping("/docs/sync-strategy")
    public String syncStrategyDoc(Model model) {
        return renderDoc("docs/sync-strategy.md", "同步策略说明", model);
    }

    @GetMapping("/docs/project-guide")
    public String projectGuideDoc(Model model) {
        return renderDoc("docs/project-guide.md", "项目运维手册", model);
    }

    private String renderDoc(String resourcePath, String title, Model model) {
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8));
            String content = reader.lines().collect(Collectors.joining("\n"));
            reader.close();
            model.addAttribute("content", markdownToHtml(content));
        } catch (Exception e) {
            model.addAttribute("content", "<p>文档加载失败: " + e.getMessage() + "</p>");
        }
        model.addAttribute("activeMenu", "");
        model.addAttribute("pageTitle", title);
        return "docs/sync-strategy";
    }

    // 简易 Markdown → HTML
    private String markdownToHtml(String md) {
        StringBuilder html = new StringBuilder();
        boolean inCodeBlock = false;
        boolean inTable = false;
        for (String line : md.split("\n")) {
            // code block
            if (line.startsWith("```")) {
                if (inCodeBlock) { html.append("</code></pre>"); inCodeBlock = false; }
                else { html.append("<pre><code>"); inCodeBlock = true; }
                continue;
            }
            if (inCodeBlock) { html.append(escapeHtml(line)).append("\n"); continue; }
            // table
            if (line.startsWith("|")) {
                if (!inTable) { html.append("<table class=\"table table-bordered table-sm\">"); inTable = true; }
                boolean isHeader = line.contains("---");
                if (isHeader) continue;
                html.append("<tr>");
                for (String cell : line.split("\\|")) {
                    String tag = inTable && html.toString().endsWith("</tr>") && html.toString().contains("<tr>") ? "td" : "th";
                    cell = cell.trim(); if (cell.isEmpty()) continue;
                    html.append("<").append(tag).append(">").append(parseInline(cell)).append("</").append(tag).append(">");
                }
                html.append("</tr>");
                continue;
            } else if (inTable) { html.append("</table>"); inTable = false; }
            // headings
            if (line.startsWith("# ")) { html.append("<h2>").append(parseInline(line.substring(2))).append("</h2>"); continue; }
            if (line.startsWith("## ")) { html.append("<h3>").append(parseInline(line.substring(3))).append("</h3>"); continue; }
            if (line.startsWith("### ")) { html.append("<h4>").append(parseInline(line.substring(4))).append("</h4>"); continue; }
            // hr
            if (line.equals("---")) { html.append("<hr>"); continue; }
            // list
            if (line.startsWith("- ")) { html.append("<li>").append(parseInline(line.substring(2))).append("</li>"); continue; }
            // paragraph
            if (line.trim().isEmpty()) { html.append("<br>"); continue; }
            html.append("<p>").append(parseInline(line)).append("</p>");
        }
        if (inTable) html.append("</table>");
        if (inCodeBlock) html.append("</code></pre>");
        return html.toString();
    }

    private String parseInline(String text) {
        return text
            .replaceAll("`([^`]+)`", "<code>$1</code>")
            .replaceAll("\\*\\*([^*]+)\\*\\*", "<strong>$1</strong>")
            .replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "<a href=\"$2\">$1</a>");
    }

    private String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("dsCount", dsConfigRepository.count());
        model.addAttribute("templateCount", templateRepository.findByIsDeleted(0).size());
        model.addAttribute("flowCount", flowConfigRepository.count());
        model.addAttribute("runningTaskCount", taskConfigRepository.findByStatus("RUNNING").size());
        return "index";
    }
}
