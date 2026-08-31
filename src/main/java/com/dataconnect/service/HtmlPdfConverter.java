package com.dataconnect.service;

import com.openhtmltopdf.extend.FSUriResolver;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Entities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * HTML → PDF。成绩表等教务页按 Lodop 规则：只渲染内容区、A4 横向、宋体/黑体。
 */
@Service
public class HtmlPdfConverter {

    private static final Logger log = LoggerFactory.getLogger(HtmlPdfConverter.class);
    private static final Pattern ASP_RESOLVE_URL = Pattern.compile(
            "<%\\s*=\\s*ResolveUrl\\(\\s*[\"']([^\"']+)[\"']\\s*\\)\\s*%>", Pattern.CASE_INSENSITIVE);

    private static final String[] FONT_CANDIDATES = {
            "C:\\Windows\\Fonts\\simhei.ttf",
            "C:\\Windows\\Fonts\\simkai.ttf",
            "C:\\Windows\\Fonts\\msyh.ttf",
            "C:\\Windows\\Fonts\\simsun.ttc",
            "C:\\Windows\\Fonts\\msyh.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-microhei.ttc",
            "/usr/share/fonts/truetype/wqy/wqy-zenhei.ttc",
            "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
            "/usr/share/fonts/truetype/arphic/uming.ttc",
            "/usr/share/fonts/truetype/droid/DroidSansFallbackFull.ttf"
    };

    @Value("${app.data-dir:data/}")
    private String dataDir;

    private final ConcurrentHashMap<String, File> embedFontCache = new ConcurrentHashMap<String, File>();
    private volatile boolean fontLogged;

    @PostConstruct
    public void initPdfBoxFontCache() {
        File dir = new File(dataDir, "pdfbox-fontcache");
        if (!dir.exists() && !dir.mkdirs()) {
            log.warn("无法创建 PDFBox 字体缓存目录: {}", dir.getAbsolutePath());
            return;
        }
        System.setProperty("pdfbox.fontcache", dir.getAbsolutePath());
        log.info("PDFBox 字体缓存目录: {}", dir.getAbsolutePath());
    }

    public byte[] convert(byte[] htmlBytes, String baseUri, String fontPath) throws Exception {
        Document doc = Jsoup.parse(new ByteArrayInputStream(htmlBytes), null, baseUri != null ? baseUri : "");
        return convert(doc, baseUri, fontPath);
    }

    public byte[] convert(String html, String baseUri, String fontPath) throws Exception {
        Document doc = Jsoup.parse(html, baseUri != null ? baseUri : "");
        return convert(doc, baseUri, fontPath);
    }

    private byte[] convert(Document source, String baseUri, String fontPath) throws Exception {
        boolean landscape = isLandscapeTranscript(source);
        Document doc = extractPrintContent(source, baseUri);
        sanitizeForPdf(doc);

        File embedFont = resolveEmbedFont(fontPath);

        StringBuilder extraCss = new StringBuilder();
        extraCss.append("@page { margin: 6mm; }");
        extraCss.append("html, body { background: #fff; margin: 0; padding: 0; }");
        extraCss.append("html, body, table, td, th {");
        extraCss.append(" font-family: '宋体', SimSun, '黑体', SimHei, serif; }");
        extraCss.append("table { border-collapse: collapse; }");
        extraCss.append("img { max-width: none; }");
        doc.head().prependElement("style").text(extraCss.toString());

        doc.outputSettings()
                .syntax(Document.OutputSettings.Syntax.xml)
                .escapeMode(Entities.EscapeMode.xhtml)
                .prettyPrint(false);

        String xhtml = doc.html();
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        if (landscape) {
            builder.useDefaultPageSize(297, 210, BaseRendererBuilder.PageSizeUnits.MM);
            log.info("HTML转PDF使用横向 A4（成绩表 Lodop 页面）");
        } else {
            builder.useDefaultPageSize(210, 297, BaseRendererBuilder.PageSizeUnits.MM);
        }
        builder.useFastMode();
        builder.useUriResolver(new SanitizingUriResolver(baseUri));
        registerCjkFonts(builder, embedFont);
        builder.withHtmlContent(xhtml, baseUri != null ? baseUri : "");
        builder.toStream(os);
        builder.run();
        return os.toByteArray();
    }

    /**
     * 教务成绩表用 Lodop 打印 divContent，且 SET_PRINT_PAGESIZE(2) 表示横向 A4。
     */
    private boolean isLandscapeTranscript(Document doc) {
        String html = doc.html();
        if (html.contains("SET_PRINT_PAGESIZE(2")) {
            return true;
        }
        return doc.select("td[width=33.3%], td[width=33.3%]").size() >= 3
                || doc.select("#divContent table td[width=33.3%]").size() >= 2;
    }

    private Document extractPrintContent(Document source, String baseUri) {
        Element content = source.getElementById("divContent");
        if (content == null) {
            return source;
        }
        Document doc = Document.createShell(baseUri != null ? baseUri : "");
        doc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        for (Element link : source.select("link[rel=stylesheet], link[rel=Stylesheet]")) {
            doc.head().appendChild(link.clone());
        }
        doc.body().appendChild(content.clone());
        return doc;
    }

    private void sanitizeForPdf(Document doc) {
        doc.select("script, noscript").remove();
        doc.select("input[type=hidden], input[type=button], button").remove();

        for (Element el : doc.select("[href], [src]")) {
            String attr = el.hasAttr("src") && (!el.hasAttr("href") || "img".equalsIgnoreCase(el.tagName()))
                    ? "src" : "href";
            if (el.hasAttr("src") && "img".equalsIgnoreCase(el.tagName())) {
                attr = "src";
            } else if (el.hasAttr("href")) {
                attr = "href";
            }
            String raw = el.attr(attr);
            String fixed = extractAspResolveUrl(raw);
            if (fixed != null && !fixed.equals(raw)) {
                el.attr(attr, fixed);
            }
            String href = el.attr(attr);
            if (href == null || href.trim().isEmpty() || "null".equalsIgnoreCase(href.trim())
                    || href.contains("<%")) {
                if ("link".equalsIgnoreCase(el.tagName())) {
                    el.remove();
                }
            }
        }

        for (Element img : doc.select("img")) {
            String style = img.attr("style");
            if (style != null && style.toLowerCase().contains("z-index")) {
                img.attr("style", style.replaceAll("(?i)z-index\\s*:\\s*-?\\d+\\s*;?", "z-index: 1;"));
            }
        }
    }

    static String extractAspResolveUrl(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        Matcher m = ASP_RESOLVE_URL.matcher(s);
        if (m.find()) {
            String path = m.group(1).trim();
            if (path.startsWith("~/")) {
                path = path.substring(1);
            }
            return path;
        }
        if (s.contains("<%") && s.contains("%>")) {
            return null;
        }
        return s;
    }

    private static boolean isFontResource(String uri) {
        String u = uri.toLowerCase();
        int q = u.indexOf('?');
        if (q >= 0) {
            u = u.substring(0, q);
        }
        return u.endsWith(".ttf") || u.endsWith(".ttc") || u.endsWith(".otf")
                || u.endsWith(".woff") || u.endsWith(".woff2") || u.endsWith(".eot");
    }

    private File resolveEmbedFont(String fontPath) {
        String key = fontPath == null ? "" : fontPath.trim();
        File cached = embedFontCache.get(key);
        if (cached != null && cached.isFile()) {
            return cached;
        }
        File simhei = findFontFile("simhei.ttf", fontPath);
        File simsun = findFontFile("simsun.ttc", fontPath);
        File fallback = resolveFont(fontPath);
        File embed = pickEmbeddableFont(simhei, simsun, fallback);
        if (embed != null) {
            embedFontCache.put(key, embed);
        }
        return embed;
    }

    private void registerCjkFonts(PdfRendererBuilder builder, File embed) {
        if (embed == null) {
            log.warn("未找到中文字体，PDF 中文会显示为 ###。可在下载步骤配置 fontPath");
            return;
        }
        if (!fontLogged) {
            if (embed.getName().toLowerCase().endsWith(".ttc")) {
                log.warn("正在使用 TTC 字体 {}，部分环境下中文会显示为 ###，建议改用 simhei.ttf", embed.getAbsolutePath());
            }
            log.info("HTML转PDF使用字体: {}", embed.getAbsolutePath());
            fontLogged = true;
        }
        boolean subset = !embed.getName().toLowerCase().endsWith(".ttc");
        String[] families = {
                "宋体", "SimSun", "NSimSun", "黑体", "SimHei",
                "serif", "sans-serif", "Times", "Times New Roman", "Arial",
                "Microsoft YaHei", "微软雅黑", "ArchiveCJK"
        };
        for (String family : families) {
            builder.useFont(embed, family, 400, BaseRendererBuilder.FontStyle.NORMAL, subset);
            builder.useFont(embed, family, 700, BaseRendererBuilder.FontStyle.NORMAL, subset);
        }
    }

    /**
     * 优先单文件 TTF（黑体/楷体），避免 simsun.ttc 子集化后中文变成 ###。
     */
    private File pickEmbeddableFont(File simhei, File simsun, File fallback) {
        File[] ordered = {
                simhei,
                findFontFile("simkai.ttf", null),
                findFontFile("msyh.ttf", null),
                fallback,
                simsun
        };
        File ttc = null;
        for (File f : ordered) {
            if (f == null || !f.isFile()) {
                continue;
            }
            if (!f.getName().toLowerCase().endsWith(".ttc")) {
                return f;
            }
            if (ttc == null) {
                ttc = f;
            }
        }
        return ttc;
    }

    private File findFontFile(String fileName, String configured) {
        List<String> paths = new ArrayList<>();
        if (configured != null && !configured.trim().isEmpty()) {
            paths.add(configured.trim());
        }
        paths.add("C:\\Windows\\Fonts\\" + fileName);
        paths.add("/usr/share/fonts/truetype/" + fileName);
        for (String p : paths) {
            File f = new File(p);
            if (f.isFile() && fileName.equalsIgnoreCase(f.getName()) && Files.isReadable(f.toPath())) {
                return f;
            }
        }
        return null;
    }

    private File resolveFont(String configured) {
        List<String> paths = new ArrayList<>();
        if (configured != null && !configured.trim().isEmpty()) {
            paths.add(configured.trim());
        }
        for (String p : FONT_CANDIDATES) {
            paths.add(p);
        }
        for (String p : paths) {
            File f = new File(p);
            if (f.isFile() && Files.isReadable(f.toPath())) {
                return f;
            }
        }
        return null;
    }

    private static class SanitizingUriResolver implements FSUriResolver {
        private final String baseUri;

        SanitizingUriResolver(String baseUri) {
            this.baseUri = baseUri;
        }

        @Override
        public String resolveURI(String baseUri, String uri) {
            String raw = extractAspResolveUrl(uri);
            if (raw == null || raw.isEmpty() || "null".equalsIgnoreCase(raw) || "#".equals(raw)) {
                return "";
            }
            if (isFontResource(raw)) {
                return "";
            }
            String base = (baseUri != null && !baseUri.isEmpty()) ? baseUri : this.baseUri;
            try {
                URI parsed = new URI(raw);
                if (parsed.isAbsolute()) {
                    return parsed.toString();
                }
            } catch (Exception ignored) {
                // relative
            }
            if (base == null || base.isEmpty()) {
                return raw;
            }
            try {
                return new URI(base).resolve(raw).toString();
            } catch (Exception e) {
                log.debug("忽略无效资源 URI: {}", raw);
                return "";
            }
        }
    }
}
