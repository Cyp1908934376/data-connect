package com.dataconnect.service;

import com.dataconnect.entity.DsConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.MessageDigest;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.crypto.Cipher;

/**
 * 论文归档对接服务。
 * 负责：生成元数据.xml、计算PDF/OFD的MD5、打包ZIP、调用档案系统 /open_api/file2Archives 接口。
 */
@Service
public class ThesisArchiveService {

    private static final Logger log = LoggerFactory.getLogger(ThesisArchiveService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build();

    // 上传大文件专用，超时更长
    private final OkHttpClient uploadHttpClient = new OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)
            .writeTimeout(600, TimeUnit.SECONDS)
            .build();

    /**
     * 执行论文归档：根据数据行生成元数据 XML、获取 PDF 文件、打 ZIP 包并推送到档案系统。
     *
     * @param row      已映射的论文数据行（字段名对应 XML 元素名）
     * @param outputDs 输出数据源配置（apiUrl 指向档案系统 /open_api/file2Archives）
     * @return 推送结果 Map（含 success, msg 等）
     */
    public Map<String, Object> execute(Map<String, Object> row, DsConfig outputDs) {
        Map<String, Object> result = new LinkedHashMap<>();
        File tempDir = null;
        File zipFile = null;

        try {
            // 1. 解析配置
            Map<String, String> archiveConfig = parseArchiveConfig(outputDs);
            String ccode = archiveConfig.getOrDefault("ccode", "lwdj");
            String fileIdentifierCode = getFileIdentifierCode(row, archiveConfig);

            if (fileIdentifierCode == null || fileIdentifierCode.isEmpty()) {
                result.put("success", false);
                result.put("error", "fileIdentifierCode 为空，无法推送归档");
                return result;
            }

            // 2. 获取 PDF 文件列表
            List<FileInfo> pdfFiles = resolvePdfFiles(row, archiveConfig);
            if (pdfFiles.isEmpty()) {
                result.put("success", false);
                result.put("error", "未找到可归档的 PDF 文件");
                return result;
            }

            // 3. 计算各 PDF 的 MD5
            for (FileInfo fi : pdfFiles) {
                fi.md5 = computeMd5(fi.data);
                fi.size = fi.data.length;
            }

            // 4. 生成元数据.xml
            String metadataXml = generateMetadataXml(row, pdfFiles, outputDs.getApiBody());

            // 5. 创建临时目录并打包 ZIP
            tempDir = createTempDir();
            // 写入元数据.xml
            writeFile(new File(tempDir, "元数据.xml"), metadataXml.getBytes(StandardCharsets.UTF_8));
            // 写入 PDF 文件
            for (FileInfo fi : pdfFiles) {
                writeFile(new File(tempDir, fi.fileName), fi.data);
            }

            // 6. 打包为 ZIP
            zipFile = File.createTempFile("archive_", ".zip");
            zipDirectory(tempDir, zipFile);

            // 7. 获取 archive token
            String archiveToken = getArchiveToken(outputDs.getApiUrl(), archiveConfig);
            // 8. 推送到档案系统
            result = uploadToArchives(outputDs.getApiUrl(), ccode, fileIdentifierCode, zipFile, archiveToken);

        } catch (Exception e) {
            log.error("论文归档失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", "归档异常: " + e.getMessage());
        } finally {
            // 清理临时文件
            deleteQuietly(zipFile);
            deleteDirQuietly(tempDir);
        }

        return result;
    }

    // ==================== 元数据 XML 生成 ====================

    /**
     * 根据论文数据行和文件列表生成 电子档案元数据 XML 字符串。
     * xmlFieldConfig 为 ds_config.api_body 中的 JSON 数组，定义 XML 字段映射。
     * 若未配置则回退到内置默认逻辑。
     */
    public String generateMetadataXml(Map<String, Object> row, List<FileInfo> pdfFiles,
            String xmlFieldConfig) throws Exception {
        org.w3c.dom.Document doc = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder().newDocument();
        doc.setXmlStandalone(true);

        org.w3c.dom.Element root = doc.createElement("电子档案元数据");
        doc.appendChild(root);

        // 预计算上下文变量，供字段默认值模板使用
        Map<String, String> ctx = new LinkedHashMap<>();
        ctx.put("year", computeYear(row));
        ctx.put("c2", str(row, "二级目录", "JX16"));
        ctx.put("pages", computePages(row, pdfFiles));
        ctx.put("学位", computeDegreeType(row));
        ctx.put("timeVal", computeTimeVal(row));
        ctx.put("lxDate", computeTimeVal(row));

        // 日期时间变量
        ctx.put("now", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        ctx.put("now:yyyy-MM-dd HH:mm:ss",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // 如果配置了 xmlFields，用配置驱动
        List<Map<String, String>> fieldDefs = null;
        if (xmlFieldConfig != null && !xmlFieldConfig.isEmpty()) {
            try {
                fieldDefs = objectMapper.readValue(xmlFieldConfig,
                        new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                log.warn("XML字段配置解析失败，使用默认逻辑: {}", e.getMessage());
            }
        }

        if (fieldDefs != null && !fieldDefs.isEmpty()) {
            // ---- 配置驱动模式 ----
            for (Map<String, String> def : fieldDefs) {
                String tag = def.get("tag");
                String source = def.getOrDefault("source", "");
                String defVal = def.getOrDefault("default", "");
                if (tag == null || tag.isEmpty()) continue;
                String value = resolveFieldValue(row, ctx, source, defVal);
                addElement(doc, root, tag, value);
            }
        } else {
            // ---- 内置默认逻辑（向后兼容） ----
            renderDefaultFields(doc, root, row, pdfFiles, ctx);
        }

        // 环境信息
        org.w3c.dom.Element env = doc.createElement("环境信息");
        addElement(doc, env, "软件环境", str(row, "软件环境", ""));
        addElement(doc, env, "硬件环境", str(row, "硬件环境", ""));
        root.appendChild(env);

        // 数字对象列表
        for (int i = 0; i < pdfFiles.size(); i++) {
            FileInfo fi = pdfFiles.get(i);
            org.w3c.dom.Element dobj = doc.createElement("数字对象");
            addElement(doc, dobj, "数字对象标识", "文档" + (i + 1));
            addElement(doc, dobj, "格式信息", fi.format != null ? fi.format : "pdf");
            addElement(doc, dobj, "计算机文件名", fi.fileName);
            addElement(doc, dobj, "计算机文件大小", String.valueOf(fi.size));
            addElement(doc, dobj, "数字摘要", fi.md5);
            root.appendChild(dobj);
        }

        // 输出为格式化的 XML 字符串
        TransformerFactory tf = TransformerFactory.newInstance();
        javax.xml.transform.Transformer t = tf.newTransformer();
        t.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        t.setOutputProperty(OutputKeys.INDENT, "yes");
        t.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");

        StringWriter sw = new StringWriter();
        t.transform(new DOMSource(doc), new StreamResult(sw));
        return sw.toString();
    }

    // ---- 配置驱动模式：解析字段值 ----
    private String resolveFieldValue(Map<String, Object> row, Map<String, String> ctx,
            String source, String defVal) {
        // 优先取 source 字段的值
        String val = null;
        if (source != null && !source.isEmpty()) {
            val = str(row, source, null);
        }
        if (val != null && !val.isEmpty()) return val;

        // 回退到 default，进行模板替换 {key}
        if (defVal == null) return "";
        String result = defVal;
        // 替换 {key} 占位符
        for (Map.Entry<String, String> e : ctx.entrySet()) {
            String placeholder = "{" + e.getKey() + "}";
            if (result.contains(placeholder)) {
                result = result.replace(placeholder, e.getValue() != null ? e.getValue() : "");
            }
        }
        // 替换行数据字段
        for (Map.Entry<String, Object> e : row.entrySet()) {
            String placeholder = "{" + e.getKey() + "}";
            if (result.contains(placeholder)) {
                Object rv = e.getValue();
                if (rv instanceof String) {
                    result = result.replace(placeholder, (String) rv);
                } else if (rv != null) {
                    result = result.replace(placeholder, rv.toString());
                }
            }
        }
        return result;
    }

    // ---- 内置默认逻辑（向后兼容，不可更改的模板） ----
    private void renderDefaultFields(org.w3c.dom.Document doc, org.w3c.dom.Element root,
            Map<String, Object> row, List<FileInfo> pdfFiles, Map<String, String> ctx) {
        String year = ctx.get("year");
        String c2Val = ctx.get("c2");
        String pages = ctx.get("pages");
        String degreeType = ctx.get("学位");
        String degreeZh = str(row, "培养层次", str(row, "degreeZh", ""));
        String timeVal = ctx.get("timeVal");

        String ztm = str(row, "正题名", "");
        if (ztm.isEmpty() && !degreeType.isEmpty()) {
            String name = str(row, "姓名", str(row, "author", ""));
            String studentId = str(row, "学号", str(row, "authorPersonId", ""));
            String title = str(row, "title", "");
            ztm = "宁波诺丁汉大学" + name + "(" + studentId + ")" + degreeType + "及评审材料 (" + title + ")";
        }

        addElement(doc, root, "一级目录", str(row, "一级目录", year));
        addElement(doc, root, "二级目录", c2Val);
        addElement(doc, root, "三级目录", str(row, "三级目录", ""));
        addElement(doc, root, "全宗号", str(row, "全宗号", "0289"));
        addElement(doc, root, "实体分类号", str(row, "实体分类号", year + "-" + c2Val));
        addElement(doc, root, "案卷号", str(row, "案卷号", ""));
        addElement(doc, root, "文件号", str(row, "文件号", ""));
        addElement(doc, root, "密级", str(row, "密级", "内部"));
        addElement(doc, root, "正题名", ztm);
        addElement(doc, root, "信息分类号", str(row, "信息分类号", str(row, "authorPersonId", "")));
        addElement(doc, root, "合作者", str(row, "合作者", str(row, "supervisor", "")));
        addElement(doc, root, "档案馆室代号", "");
        addElement(doc, root, "主题词", str(row, "主题词", str(row, "orgUuid", "")));
        addElement(doc, root, "页数", pages);
        addElement(doc, root, "时间", timeVal);
        addElement(doc, root, "第一责任者", str(row, "第一责任者", str(row, "姓名", str(row, "author", ""))));
        addElement(doc, root, "责任者", "宁波诺丁汉大学");
        addElement(doc, root, "单位", str(row, "单位", str(row, "归档单位", str(row, "学院", str(row, "orgUuid", "")))));
        addElement(doc, root, "归档份数", str(row, "归档份数", "1"));
        addElement(doc, root, "保管期限", str(row, "保管期限", "长期"));
        addElement(doc, root, "载体", str(row, "载体", "电子文件"));
        addElement(doc, root, "文本", str(row, "文本", "正本"));
        addElement(doc, root, "获奖项", str(row, "获奖项", "中国"));
        addElement(doc, root, "获奖等级", str(row, "获奖等级", str(row, "gender", "")));
        addElement(doc, root, "获奖时间", str(row, "获奖时间", str(row, "enrollDate", "")));
        addElement(doc, root, "归档时间", LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        addElement(doc, root, "出版项", str(row, "出版项", "普通全日制"));
        addElement(doc, root, "存址", str(row, "存址", degreeZh));
        addElement(doc, root, "学籍变更", str(row, "学籍变更", ""));
        addElement(doc, root, "输入员", str(row, "输入员", "论文系统"));
        addElement(doc, root, "标识", "论文系统");
    }

    // ---- 上下文变量计算 ----
    private String computeYear(Map<String, Object> row) {
        String tv = computeTimeVal(row);
        return tv.length() >= 4 ? tv.substring(0, 4)
                : String.valueOf(LocalDate.now().getYear());
    }

    private String computeTimeVal(Map<String, Object> row) {
        String tv = str(row, "时间", "");
        if (tv.isEmpty()) tv = str(row, "submissionDate", "").replaceAll("-", "");
        return tv;
    }

    private String computePages(Map<String, Object> row, List<FileInfo> pdfFiles) {
        String pages = str(row, "页数", "");
        if (pages.isEmpty()) {
            Object pc = row.get("pdf_count");
            pages = pc != null ? String.valueOf(pc) : String.valueOf(pdfFiles.size());
        }
        return pages;
    }

    private String computeDegreeType(Map<String, Object> row) {
        String degreeZh = str(row, "培养层次", str(row, "degreeZh", ""));
        String degreeEn = str(row, "degreeEn", "");
        if (degreeEn.contains("EdD")) return "教育学专业博士论文";
        if (degreeEn.contains("MRes")) return "研究型硕士论文";
        if (degreeZh.contains("博士")) return "博士论文";
        if (degreeZh.contains("硕士") || degreeZh.contains("研究生")) return "硕士论文";
        return "";
    }

    // ==================== MD5 计算 ====================

    public String computeMd5(byte[] data) {
        if (data == null || data.length == 0) return "";
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("MD5 计算失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 从输入流计算 MD5。
     */
    public String computeMd5(InputStream is) throws Exception {
        MessageDigest md = MessageDigest.getInstance("MD5");
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    // ==================== ZIP 打包 ====================

    public void zipDirectory(File sourceDir, File destZip) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(destZip);
             ZipOutputStream zos = new ZipOutputStream(fos, StandardCharsets.UTF_8)) {
            File[] files = sourceDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    addToZip(file, "", zos);
                }
            }
        }
    }

    private void addToZip(File file, String parentPath, ZipOutputStream zos) throws Exception {
        String entryName = parentPath.isEmpty() ? file.getName() : parentPath + "/" + file.getName();
        if (file.isDirectory()) {
            zos.putNextEntry(new ZipEntry(entryName + "/"));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(child, entryName, zos);
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(entryName));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) {
                    zos.write(buf, 0, n);
                }
            }
            zos.closeEntry();
        }
    }

    // ==================== 推送到档案系统 ====================

    /**
     * 获取档案系统 JWT token。
     * 流程: getPublicKey → RSA加密密码 → getToken
     */
    private String getArchiveToken(String apiUrl, Map<String, String> config) {
        String appkey = config.getOrDefault("appkey", "dba");
        String password = config.getOrDefault("password", "Aa@12345");
        try {
            // 从 apiUrl 提取 base URL (scheme + host + port)
            URL urlObj = new URL(apiUrl);
            String base = urlObj.getProtocol() + "://" + urlObj.getHost()
                    + (urlObj.getPort() > 0 ? ":" + urlObj.getPort() : "");

            // Step 1: 获取 RSA 公钥
            String pubkeyUrl = base + "/open_api/getPublicKey";
            Request pubkeyReq = new Request.Builder().url(pubkeyUrl)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .build();
            String pubkeyStr;
            try (Response resp = httpClient.newCall(pubkeyReq).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("getPublicKey failed: HTTP {}", resp.code());
                    return null;
                }
                String body = resp.body() != null ? resp.body().string() : "";
                @SuppressWarnings("unchecked")
                Map<String, Object> pubkeyResp = objectMapper.readValue(body, Map.class);
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) pubkeyResp.get("data");
                pubkeyStr = data != null ? (String) data.get("PublicKey") : null;
                if (pubkeyStr == null) pubkeyStr = (String) pubkeyResp.get("PublicKey");
                if (pubkeyStr == null) {
                    log.warn("PublicKey not found in response");
                    return null;
                }
            }

            // Step 2: RSA 加密密码
            byte[] keyBytes = Base64.getDecoder().decode(pubkeyStr);
            X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
            KeyFactory keyFactory = KeyFactory.getInstance("RSA");
            PublicKey publicKey = keyFactory.generatePublic(keySpec);
            Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            String appsecret = Base64.getEncoder().encodeToString(encrypted);

            // Step 3: 获取 JWT token
            String tokenParams = "appkey=" + URLEncoder.encode(appkey, "UTF-8")
                    + "&appsecret=" + URLEncoder.encode(appsecret, "UTF-8");
            Request tokenReq = new Request.Builder().url(base + "/open_api/gettoken")
                    .post(RequestBody.create(tokenParams,
                            MediaType.parse("application/x-www-form-urlencoded")))
                    .build();
            try (Response resp = httpClient.newCall(tokenReq).execute()) {
                if (!resp.isSuccessful()) {
                    log.warn("getToken failed: HTTP {}", resp.code());
                    return null;
                }
                String body = resp.body() != null ? resp.body().string() : "";
                log.info("getToken response: {}", body.length() > 500 ? body.substring(0, 500) + "..." : body);
                @SuppressWarnings("unchecked")
                Map<String, Object> tokenResp = objectMapper.readValue(body, Map.class);
                // Check code field first
                Object codeObj = tokenResp.get("code");
                if (codeObj != null && !((Number) codeObj).equals(0)) {
                    log.warn("getToken failed: code={}, msg={}", codeObj, tokenResp.get("msg"));
                    return null;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) tokenResp.get("data");
                String token = data != null ? (String) data.get("token") : null;
                log.info("getToken success, token prefix: {}",
                        token != null ? token.substring(0, Math.min(20, token.length())) + "..." : "null");
                return token;
            }
        } catch (Exception e) {
            log.warn("获取 archive token 失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通过 multipart/form-data POST 上传 ZIP 到档案系统。
     */
    public Map<String, Object> uploadToArchives(String apiUrl, String ccode,
            String fileIdentifierCode, File zipFile, String archiveToken) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();

        if (apiUrl == null || apiUrl.isEmpty()) {
            result.put("success", false);
            result.put("error", "档案系统 URL 未配置");
            return result;
        }

        RequestBody fileBody = RequestBody.create(zipFile, MediaType.parse("application/zip"));
        MultipartBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("fileData", zipFile.getName(), fileBody)
                .addFormDataPart("ccode", ccode)
                .addFormDataPart("fileIdentifierCode", fileIdentifierCode)
                .build();

        Request.Builder reqBuilder = new Request.Builder()
                .url(apiUrl)
                .post(requestBody);

        if (archiveToken != null && !archiveToken.isEmpty()) {
            reqBuilder.addHeader("token", archiveToken);
        }

        Request request = reqBuilder.build();

        log.info("推送归档: fileIdentifierCode={}, ccode={}, fileSize={}",
                fileIdentifierCode, ccode, zipFile.length());

        try (Response response = uploadHttpClient.newCall(request).execute()) {
            String respBody = response.body() != null ? response.body().string() : "";
            log.info("归档推送响应: HTTP {} - {}", response.code(),
                    respBody.length() > 500 ? respBody.substring(0, 500) + "..." : respBody);

            result.put("httpStatus", response.code());
            result.put("success", response.isSuccessful());

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> respJson = objectMapper.readValue(respBody, Map.class);
                result.putAll(respJson);
            } catch (Exception e) {
                result.put("responseBody", respBody);
            }

            if (!response.isSuccessful()) {
                result.put("error", "档案系统返回 HTTP " + response.code() + ": " + respBody);
            }
        }

        return result;
    }

    // ==================== PDF 文件解析 ====================

    /**
     * 从数据行中解析 PDF 文件列表。
     * 支持多种来源（按优先级）：
     * 1. row.pdfFiles — JSON 数组 [{path/url: "...", name: "..."}, ...]
     * 2. row.documents — JSON 数组（诺丁汉原始格式），过滤 mimeType=application/pdf
     * 3. row.pdf_url / row.pdf_localPath — 单个 PDF 的 URL 或本地路径（诺丁汉数据源输出）
     * 4. row.<configKey> — 配置指定的字段名，值为文件路径（逗号分隔）
     */
    private List<FileInfo> resolvePdfFiles(Map<String, Object> row, Map<String, String> config) {
        String nottApiKey = config.getOrDefault("nottApiKey", "e3c5f52d-a905-43ac-a10c-4ea5255e368d");
        // 新API网关使用Cookie认证，token由模板写入row
        String downloadToken = asString(row.get("_downloadToken"));
        if (!downloadToken.isEmpty()) {
            nottApiKey = downloadToken;
        }
        List<FileInfo> files = new ArrayList<>();

        // 方式1：pdfFiles JSON 数组
        Object pdfFilesObj = row.get("pdfFiles");
        if (pdfFilesObj instanceof String && !((String) pdfFilesObj).isEmpty()) {
            try {
                List<Map<String, String>> list = objectMapper.readValue(
                        (String) pdfFilesObj,
                        new TypeReference<List<Map<String, String>>>() {});
                for (Map<String, String> item : list) {
                    FileInfo fi = resolveFileInfo(item);
                    if (fi != null) files.add(fi);
                }
            } catch (Exception e) {
                log.debug("pdfFiles 字段 JSON 解析失败: {}", e.getMessage());
            }
        } else if (pdfFilesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) pdfFilesObj;
            for (Object item : list) {
                if (item instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, String> map = (Map<String, String>) item;
                    FileInfo fi = resolveFileInfo(map);
                    if (fi != null) files.add(fi);
                }
            }
        }

        if (!files.isEmpty()) return files;

        // 方式2：诺丁汉 documents 数组（JSON字符串或List），过滤 PDF
        Object documentsObj = row.get("documents");
        if (documentsObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> docs = (List<Map<String, Object>>) documentsObj;
            for (Map<String, Object> doc : docs) {
                String mime = String.valueOf(doc.getOrDefault("mimeType", ""));
                String downloadUrl = String.valueOf(doc.getOrDefault("downloadUrl", ""));
                String fileName = doc.get("fileName") != null ? String.valueOf(doc.get("fileName")) : null;
                if (!downloadUrl.isEmpty() && !"null".equals(downloadUrl)
                        && (fileName == null || !fileName.contains("changehistory"))) {
                    FileInfo fi = loadFileFromUrl(downloadUrl, fileName, nottApiKey);
                    if (fi != null) files.add(fi);
                }
            }
        } else if (documentsObj instanceof String && !((String) documentsObj).isEmpty()) {
            try {
                List<Map<String, Object>> docs = objectMapper.readValue(
                        (String) documentsObj,
                        new TypeReference<List<Map<String, Object>>>() {});
                for (Map<String, Object> doc : docs) {
                    String mime = doc.get("mimeType") != null ? doc.get("mimeType").toString() : "";
                    String downloadUrl = doc.get("downloadUrl") != null ? doc.get("downloadUrl").toString() : "";
                    String fileName = doc.get("fileName") != null ? doc.get("fileName").toString() : null;
                    if ("application/pdf".equals(mime) && !downloadUrl.isEmpty()) {
                        FileInfo fi = loadFileFromUrl(downloadUrl, fileName, nottApiKey);
                        if (fi != null) files.add(fi);
                    }
                }
            } catch (Exception e) {
                log.debug("documents 字段解析失败: {}", e.getMessage());
            }
        }

        if (!files.isEmpty()) return files;

        // 方式3：诺丁汉数据源输出的 pdf_url / pdf_localPath
        String pdfUrl = asString(row.get("pdf_url"));
        String pdfFileName = asString(row.get("pdf_fileName"));
        String pdfLocalPath = asString(row.get("pdf_localPath"));

        if (!pdfLocalPath.isEmpty()) {
            FileInfo fi = loadFileFromPath(pdfLocalPath, pdfFileName.isEmpty() ? null : pdfFileName);
            if (fi != null) files.add(fi);
        } else if (!pdfUrl.isEmpty()) {
            FileInfo fi = loadFileFromUrl(pdfUrl, pdfFileName.isEmpty() ? null : pdfFileName);
            if (fi != null) files.add(fi);
        }

        if (!files.isEmpty()) return files;

        // 方式4：从配置指定的字段中读取
        String fileField = config.get("pdfFileField");
        if (fileField != null) {
            Object val = row.get(fileField);
            if (val instanceof String && !((String) val).isEmpty()) {
                String[] paths = ((String) val).split(",");
                for (int i = 0; i < paths.length; i++) {
                    String p = paths[i].trim();
                    if (!p.isEmpty()) {
                        FileInfo fi = loadFileFromPath(p);
                        if (fi != null) files.add(fi);
                    }
                }
            }
        }

        return files;
    }

    private String asString(Object obj) {
        return obj != null ? obj.toString() : "";
    }

    private FileInfo resolveFileInfo(Map<String, String> item) {
        String path = item.get("path");
        String name = item.get("name");
        String url = item.get("url");
        String content = item.get("content");  // base64

        if (content != null && !content.isEmpty()) {
            byte[] data = Base64.getDecoder().decode(content);
            String fileName = name != null ? name : "thesis.pdf";
            FileInfo fi = new FileInfo();
            fi.fileName = fileName;
            fi.data = data;
            fi.format = item.getOrDefault("format", "pdf");
            return fi;
        }

        if (url != null && !url.isEmpty()) {
            return loadFileFromUrl(url, name);
        }

        if (path != null && !path.isEmpty()) {
            return loadFileFromPath(path, name);
        }

        return null;
    }

    private FileInfo loadFileFromPath(String path) {
        return loadFileFromPath(path, new File(path).getName());
    }

    private FileInfo loadFileFromPath(String path, String fileName) {
        try {
            File f = new File(path);
            if (!f.exists() || !f.isFile()) {
                log.warn("PDF 文件不存在: {}", path);
                return null;
            }
            byte[] data = readAllBytes(new FileInputStream(f));
            FileInfo fi = new FileInfo();
            fi.fileName = fileName != null ? fileName : f.getName();
            fi.data = data;
            fi.format = detectFormat(fi.fileName);
            return fi;
        } catch (Exception e) {
            log.warn("读取 PDF 文件失败: {} - {}", path, e.getMessage());
            return null;
        }
    }

    private FileInfo loadFileFromUrl(String urlStr, String name) {
        return loadFileFromUrl(urlStr, name, "e3c5f52d-a905-43ac-a10c-4ea5255e368d");
    }

    private FileInfo loadFileFromUrl(String urlStr, String name, String apiKey) {
        // 对于新 API 网关，每次下载前重新登录获取新鲜 token，防止执行时间过长导致 token 过期
        if (urlStr.contains("api.nottingham.edu.cn")) {
            String freshToken = freshNottinghamToken();
            if (freshToken != null && !freshToken.isEmpty()) {
                apiKey = freshToken;
            }
        }
        try {
            URL url = new URL(urlStr);
            URLConnection conn = url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(600000);
            if (urlStr.contains("api.nottingham.edu.cn") && apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Cookie", "identitytoken=" + apiKey);
            } else if (urlStr.contains("nottingham") && apiKey != null && !apiKey.isEmpty()) {
                conn.setRequestProperty("Api-Key", apiKey);
            }
            byte[] data = readAllBytes(conn.getInputStream());
            String fileName = name != null ? name : extractFileNameFromUrl(urlStr);
            FileInfo fi = new FileInfo();
            fi.fileName = fileName;
            fi.data = data;
            fi.format = detectFormat(fileName);
            return fi;
        } catch (Exception e) {
            log.warn("下载 PDF 文件失败: {} - {}", urlStr, e.getMessage());
            return null;
        }
    }

    private String detectFormat(String fileName) {
        if (fileName == null) return "pdf";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".ofd")) return "ofd";
        if (lower.endsWith(".doc")) return "doc";
        if (lower.endsWith(".docx")) return "docx";
        // 默认视为 pdf
        return "pdf";
    }

    // 缓存新鲜的 Nottingham token，避免每次下载 PDF 都重新登录
    private volatile String cachedNottinghamToken;
    private volatile long cachedNottinghamTokenTime;

    /**
     * 重新登录 api.nottingham.edu.cn 获取新 token，带缓存（5 分钟内复用）。
     */
    private String freshNottinghamToken() {
        long now = System.currentTimeMillis();
        if (cachedNottinghamToken != null && (now - cachedNottinghamTokenTime) < 300_000) {
            return cachedNottinghamToken;
        }
        try {
            String loginUrl = "https://api.nottingham.edu.cn/unnc/rest/core/auth/login"
                    + "?userName=efile&password=ynYZVCeB74LQJ9@k";
            Request req = new Request.Builder().url(loginUrl)
                    .post(RequestBody.create("", MediaType.parse("application/json")))
                    .build();
            try (Response resp = httpClient.newCall(req).execute()) {
                if (resp.isSuccessful() && resp.body() != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> body = objectMapper.readValue(resp.body().string(), Map.class);
                    String token = (String) body.get("identitytoken");
                    if (token != null && !token.isEmpty()) {
                        log.info("Nottingham token refreshed");
                        cachedNottinghamToken = token;
                        cachedNottinghamTokenTime = now;
                        return token;
                    }
                }
            }
        } catch (Exception e) {
            log.warn("刷新 Nottingham token 失败: {}", e.getMessage());
        }
        return null;
    }

    private String extractFileNameFromUrl(String url) {
        String name = url.substring(url.lastIndexOf('/') + 1);
        int q = name.indexOf('?');
        if (q > 0) name = name.substring(0, q);
        return name.isEmpty() ? "thesis.pdf" : name;
    }

    // ==================== 配置解析 ====================

    private Map<String, String> parseArchiveConfig(DsConfig outputDs) {
        Map<String, String> config = new LinkedHashMap<>();
        config.put("ccode", "lwdj"); // 默认推送源

        String authConfig = outputDs.getApiAuthConfig();
        if (authConfig != null && !authConfig.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = objectMapper.readValue(authConfig, Map.class);
                config.putAll(parsed);
            } catch (Exception e) {
                log.debug("解析归档配置失败: {}", e.getMessage());
            }
        }

        // 也支持从 apiHeaders 读取（JSON key-value）
        String headers = outputDs.getApiHeaders();
        if (headers != null && !headers.isEmpty()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> parsed = objectMapper.readValue(headers, Map.class);
                for (Map.Entry<String, String> e : parsed.entrySet()) {
                    if (e.getKey().startsWith("archive_")) {
                        config.put(e.getKey().substring(8), e.getValue());
                    }
                }
            } catch (Exception ignored) {}
        }

        return config;
    }

    private String getFileIdentifierCode(Map<String, Object> row, Map<String, String> config) {
        // 优先使用配置指定的字段
        String idField = config.get("fileIdentifierField");
        if (idField != null) {
            Object val = row.get(idField);
            if (val != null && !val.toString().isEmpty()) return val.toString();
        }
        // 其次使用"标识"字段
        Object val = row.get("标识");
        if (val != null && !val.toString().isEmpty()) return val.toString();
        // 最后使用 UUID
        return UUID.randomUUID().toString().replace("-", "");
    }

    // ==================== 工具方法 ====================

    private void addElement(org.w3c.dom.Document doc, org.w3c.dom.Element parent,
            String name, String value) {
        org.w3c.dom.Element el = doc.createElement(name);
        el.setTextContent(value != null ? convertHtmlSubSup(value) : "");
        parent.appendChild(el);
    }

    /** HTML &lt;sub&gt;/&lt;sup&gt; → Unicode 上下标，其余 HTML 标签删除 */
    private String convertHtmlSubSup(String text) {
        if (text == null) return "";
        text = replaceSubSup(text, "sub", true);
        text = replaceSubSup(text, "sup", false);
        text = text.replaceAll("<[^>]+>", "");
        return text;
    }

    private String replaceSubSup(String text, String tag, boolean isSub) {
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "<" + tag + ">([^<]*)</" + tag + ">");
        java.util.regex.Matcher m = p.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String content = m.group(1);
            StringBuilder conv = new StringBuilder();
            for (char ch : content.toCharArray()) {
                if (isSub) {
                    if (ch >= '0' && ch <= '9') conv.append((char) ('\u2080' + (ch - '0')));
                    else conv.append(ch);
                } else {
                    switch (ch) {
                        case '0': conv.append('\u2070'); break;
                        case '1': conv.append('\u00B9'); break;
                        case '2': conv.append('\u00B2'); break;
                        case '3': conv.append('\u00B3'); break;
                        case '4': conv.append('\u2074'); break;
                        case '5': conv.append('\u2075'); break;
                        case '6': conv.append('\u2076'); break;
                        case '7': conv.append('\u2077'); break;
                        case '8': conv.append('\u2078'); break;
                        case '9': conv.append('\u2079'); break;
                        case '-': case '\u2013': conv.append('\u207B'); break;
                        case '+': conv.append('\u207A'); break;
                        default: conv.append(ch);
                    }
                }
            }
            m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(conv.toString()));
        }
        m.appendTail(sb);
        return sb.toString();
    }
    private String str(Map<String, Object> row, String key, String defaultValue) {
        Object val = row.get(key);
        return val != null ? val.toString() : defaultValue;
    }

    private byte[] readAllBytes(InputStream is) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) {
            buffer.write(buf, 0, n);
        }
        is.close();
        return buffer.toByteArray();
    }

    // ==================== 临时文件 ====================

    private File createTempDir() throws Exception {
        File dir = File.createTempFile("archive_", "_dir");
        dir.delete();
        dir.mkdirs();
        return dir;
    }

    private void writeFile(File file, byte[] data) throws Exception {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(data);
        }
    }

    private void deleteQuietly(File f) {
        if (f != null && f.exists()) f.delete();
    }

    private void deleteDirQuietly(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File child : children) {
                if (child.isDirectory()) deleteDirQuietly(child);
                else child.delete();
            }
        }
        dir.delete();
    }

    // ==================== 内部类 ====================

    public static class FileInfo {
        public String fileName;
        public byte[] data;
        public long size;
        public String md5;
        public String format;
    }
}
