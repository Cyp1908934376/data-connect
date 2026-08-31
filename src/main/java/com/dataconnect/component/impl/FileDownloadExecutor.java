package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.service.HtmlPdfConverter;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 可配置附件下载步骤：按 URL 模板拉取文件，HTML 自动转 PDF，写入 row.pdfFiles 供归档使用。
 */
@Component
public class FileDownloadExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(FileDownloadExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build();

    @Autowired
    private HtmlPdfConverter htmlPdfConverter;

    @Override
    public String getType() {
        return "FILE_DOWNLOAD";
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String urlTemplate = str(config.get("urlTemplate"));
        if (urlTemplate.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "附件下载未配置 URL 模板");
        }

        String fileNameTemplate = str(config.get("fileNameTemplate"));
        if (fileNameTemplate.isEmpty()) {
            fileNameTemplate = "${学号}_附件.pdf";
        }
        String convertMode = str(config.get("convertMode"));
        if (convertMode.isEmpty()) {
            convertMode = "AUTO";
        }
        String fontPath = str(config.get("fontPath"));
        Map<String, String> extraHeaders = parseHeaders(config.get("headers"));
        Map<String, Object> templateInput = firstRowFromContext(context);

        List<Map<String, Object>> rows = input.getRows();
        if (rows == null || rows.isEmpty()) {
            context.warn("附件下载: 无数据行，跳过");
            return input;
        }

        List<Map<String, Object>> resultRows = new ArrayList<>();
        int rowNum = 0;
        for (Map<String, Object> row : rows) {
            Map<String, Object> resultRow = new LinkedHashMap<>(row);
            rowNum++;
            try {
                String url = resolveTemplate(urlTemplate, resultRow, templateInput);
                if (url.isEmpty() || url.contains("${")) {
                    resultRow.put("_downloadSuccess", false);
                    resultRow.put("_downloadMessage", "URL 模板未能解析完整: " + url);
                    context.warn("[" + rowNum + "] URL未解析完整: " + url);
                    resultRows.add(resultRow);
                    continue;
                }

                String fileName = resolveTemplate(fileNameTemplate, resultRow, templateInput);
                if (fileName.isEmpty() || fileName.contains("${")) {
                    fileName = "attachment.pdf";
                }

                context.info("[" + rowNum + "/" + rows.size() + "] 下载附件: " + url);
                DownloadResult downloaded = download(url, extraHeaders);
                byte[] fileBytes = downloaded.bytes;
                boolean html = isHtml(downloaded.contentType, fileBytes);
                boolean pdf = isPdf(downloaded.contentType, fileBytes);
                context.info("[" + rowNum + "] 下载响应: type=" + downloaded.contentType
                        + ", size=" + (fileBytes != null ? fileBytes.length : 0)
                        + ", html=" + html + ", pdf=" + pdf);

                if ("HTML_TO_PDF".equalsIgnoreCase(convertMode) || ("AUTO".equalsIgnoreCase(convertMode) && html && !pdf)) {
                    fileBytes = htmlPdfConverter.convert(fileBytes, url, fontPath);
                    if (!fileName.toLowerCase().endsWith(".pdf")) {
                        fileName = stripExt(fileName) + ".pdf";
                    }
                    context.info("[" + rowNum + "] HTML 已转为 PDF, file=" + fileName + ", size=" + fileBytes.length);
                } else if (pdf && !fileName.toLowerCase().endsWith(".pdf")) {
                    fileName = stripExt(fileName) + ".pdf";
                }

                appendPdfFile(resultRow, fileName, fileBytes);
                resultRow.put("_downloadSuccess", true);
                resultRow.put("_downloadMessage", "OK");
                resultRow.put("_downloadUrl", url);
                context.info("[" + rowNum + "] 附件就绪: " + fileName + ", size=" + (fileBytes != null ? fileBytes.length : 0));
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                log.warn("附件下载失败: row={}, msg={}", rowNum, msg, e);
                context.error("[" + rowNum + "] 附件下载失败: " + msg);
                resultRow.put("_downloadSuccess", false);
                resultRow.put("_downloadMessage", msg);
            }
            resultRows.add(resultRow);
        }
        int okCount = 0;
        int failCount = 0;
        for (Map<String, Object> r : resultRows) {
            if (Boolean.TRUE.equals(r.get("_downloadSuccess"))) {
                okCount++;
            } else {
                failCount++;
            }
        }
        context.info("附件下载结束: 成功 " + okCount + " / 失败 " + failCount + " / 共 " + resultRows.size());
        return DataPacket.ofList(resultRows);
    }

    private void appendPdfFile(Map<String, Object> row, String fileName, byte[] data) {
        Object existing = row.get("pdfFiles");
        List<Map<String, Object>> list = new ArrayList<>();
        if (existing instanceof List) {
            for (Object item : (List<?>) existing) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> m = (Map<String, Object>) item;
                    list.add(m);
                }
            }
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("name", fileName);
        file.put("format", "pdf");
        file.put("data", data);
        list.add(file);
        row.put("pdfFiles", list);
        if (row.get("pdf_fileName") == null || row.get("pdf_fileName").toString().isEmpty()) {
            row.put("pdf_fileName", fileName);
        }
    }

    private DownloadResult download(String url, Map<String, String> extraHeaders) throws Exception {
        Request.Builder builder = new Request.Builder().url(url).get();
        if (extraHeaders != null) {
            for (Map.Entry<String, String> e : extraHeaders.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    builder.addHeader(e.getKey(), e.getValue());
                }
            }
        }
        try (Response resp = httpClient.newCall(builder.build()).execute()) {
            if (!resp.isSuccessful()) {
                throw new RuntimeException("下载失败 HTTP " + resp.code() + " " + url);
            }
            ResponseBody body = resp.body();
            if (body == null) {
                throw new RuntimeException("下载响应为空: " + url);
            }
            DownloadResult result = new DownloadResult();
            result.bytes = body.bytes();
            result.contentType = resp.header("Content-Type", "");
            return result;
        }
    }

    private boolean isPdf(String contentType, byte[] data) {
        if (contentType != null && contentType.toLowerCase().contains("application/pdf")) {
            return true;
        }
        return data != null && data.length >= 4
                && data[0] == '%' && data[1] == 'P' && data[2] == 'D' && data[3] == 'F';
    }

    private boolean isHtml(String contentType, byte[] data) {
        if (contentType != null) {
            String ct = contentType.toLowerCase();
            if (ct.contains("text/html") || ct.contains("application/xhtml")) {
                return true;
            }
        }
        if (data == null || data.length < 16) {
            return false;
        }
        int len = Math.min(data.length, 256);
        String head = new String(data, 0, len, java.nio.charset.StandardCharsets.ISO_8859_1).toLowerCase();
        return head.contains("<html") || head.contains("<!doctype html") || head.contains("<table");
    }

    private String resolveTemplate(String template, Map<String, Object> row, Map<String, Object> inputParams) {
        if (template == null) {
            return "";
        }
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1).trim();
            Object val = row != null ? row.get(key) : null;
            if (val == null && inputParams != null) {
                val = inputParams.get(key);
            }
            String replacement = val != null ? val.toString() : "";
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString().trim();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstRowFromContext(ExecutionContext context) {
        Object v = context.getGlobalVariable("_templateInput");
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        return new HashMap<>();
    }

    private Map<String, String> parseHeaders(Object raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        String s = raw.toString().trim();
        if (s.isEmpty()) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(s, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.debug("解析下载请求头失败: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    private String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private String str(Object v) {
        return v != null ? v.toString().trim() : "";
    }

    private static class DownloadResult {
        byte[] bytes;
        String contentType;
    }
}
