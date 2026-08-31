package com.dataconnect.service;

import com.dataconnect.component.DataPacket;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.entity.VisualTemplate;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模拟第三方 OpenAPI：获取 token → 带 access_token 拉数。
 * 结构对齐业务接口（code=10000 / result.access_token / result.data）。
 */
@Service
public class MockOpenApiService {

    private static final Logger log = LoggerFactory.getLogger(MockOpenApiService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    public static final String TABLE_NAME = "mock_openapi_sync";
    public static final int TOKEN_EXPIRES_IN = 7200;

    private final ConcurrentHashMap<String, Long> tokens = new ConcurrentHashMap<>();

    @Value("${server.port:8010}")
    private int serverPort;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private VisualTemplateExecutionService visualTemplateExecutionService;

    public String baseUrl() {
        return "http://127.0.0.1:" + serverPort;
    }

    public String tokenUrl() {
        return baseUrl() + "/mock/openapi/token";
    }

    public String dataUrl() {
        return baseUrl() + "/mock/openapi/data";
    }

    public Map<String, Object> issueToken() {
        purgeExpired();
        String accessToken = UUID.randomUUID().toString().replace("-", "")
                + UUID.randomUUID().toString().replace("-", "").substring(0, 5);
        tokens.put(accessToken, System.currentTimeMillis() + TOKEN_EXPIRES_IN * 1000L);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("access_token", accessToken);
        result.put("expires_in", TOKEN_EXPIRES_IN);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 10000);
        body.put("message", "ok");
        body.put("description", "api请求成功");
        body.put("result", result);
        return body;
    }

    public Map<String, Object> queryData(String accessToken, Integer page, Integer perPage, String since) {
        int pageNo = page == null || page < 1 ? 1 : page;
        int size = perPage == null || perPage < 1 ? 3 : perPage;

        if (accessToken == null || accessToken.trim().isEmpty()) {
            return errorBody(10001, "missing_token", "缺少 access_token");
        }
        Long expireAt = tokens.get(accessToken);
        if (expireAt == null || expireAt < System.currentTimeMillis()) {
            return errorBody(10002, "invalid_token", "access_token 无效或已过期");
        }

        List<Map<String, Object>> all = sampleRows();
        if (since != null && !since.trim().isEmpty()) {
            List<Map<String, Object>> filtered = new ArrayList<>();
            for (Map<String, Object> row : all) {
                Object id = row.get("id");
                if (id != null && String.valueOf(id).compareTo(since.trim()) > 0) {
                    filtered.add(row);
                }
            }
            all = filtered;
        }
        int from = Math.min((pageNo - 1) * size, all.size());
        int to = Math.min(from + size, all.size());
        List<Map<String, Object>> pageData = new ArrayList<>(all.subList(from, to));

        Map<String, Object> dataStruct = new LinkedHashMap<>();
        dataStruct.put("id", "string");
        dataStruct.put("title", "string");
        dataStruct.put("author", "string");
        dataStruct.put("year", "string");
        dataStruct.put("source", "string");

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("data", pageData);
        result.put("data_struct", dataStruct);
        result.put("encrypted_field", "");
        result.put("max_page", String.valueOf((all.size() + size - 1) / size));
        result.put("page", pageNo);
        result.put("per_page", size);
        result.put("total", all.size());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 10000);
        body.put("description", "api请求成功");
        body.put("message", "ok");
        body.put("result", result);
        body.put("uuid", UUID.randomUUID().toString().replace("-", ""));
        return body;
    }

    public Map<String, Object> runPipeline(Long mysqlDsId, String tableName) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("path", "获取token → 接口?access_token= → 解析 result.data → 写入 MySQL");

        DsConfig mysql = resolveMysql(mysqlDsId);
        if (mysql == null) {
            report.put("success", false);
            report.put("error", "没有可用的 MySQL 数据源，请先在数据源管理里配置一个 MySQL");
            report.put("dataSources", summarizeDbSources());
            return report;
        }
        String table = (tableName == null || tableName.trim().isEmpty()) ? TABLE_NAME : tableName.trim();

        Map<String, Object> mysqlInfo = new LinkedHashMap<>();
        mysqlInfo.put("id", mysql.getId());
        mysqlInfo.put("name", mysql.getName());
        mysqlInfo.put("host", mysql.getHost());
        mysqlInfo.put("port", mysql.getPort());
        mysqlInfo.put("dbName", mysql.getDbName());
        mysqlInfo.put("table", table);
        report.put("mysql", mysqlInfo);
        report.put("tokenUrl", tokenUrl());
        report.put("dataUrl", dataUrl() + "?access_token=${access_token}");

        try {
            VisualTemplate template = buildPipelineTemplate(mysql.getId(), table);
            DataPacket result = visualTemplateExecutionService.execute(template, DataPacket.empty());
            report.put("success", result != null && result.isSuccess());
            report.put("rowCount", result != null ? result.size() : 0);
            report.put("rows", result != null ? result.getRows() : null);
            if (result != null && !result.isSuccess()) {
                report.put("errorCode", result.getErrorCode());
                report.put("errorMessage", result.getErrorMessage());
            }
            if (result != null && result.getVariables() != null) {
                report.put("executionLog", result.getVariables().get("_executionLog"));
            }
        } catch (Exception e) {
            log.error("模拟管道执行失败", e);
            report.put("success", false);
            report.put("error", e.getMessage());
        }

        Map<String, Object> preview = dataSourceService.previewData(mysql.getId(), table, 20);
        report.put("mysqlPreview", preview);
        return report;
    }

    public VisualTemplate buildPipelineTemplate(Long mysqlDsId, String tableName) throws Exception {
        String table = (tableName == null || tableName.trim().isEmpty()) ? TABLE_NAME : tableName.trim();
        Map<String, Object> config = new LinkedHashMap<>();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("inputType", "MANUAL");
        config.put("input", input);

        Map<String, Object> apiStep = new LinkedHashMap<>();
        apiStep.put("type", "DATA_SOURCE");
        apiStep.put("tokenUrl", tokenUrl());
        apiStep.put("tokenMethod", "POST");
        apiStep.put("tokenExtractPath", "result.access_token");
        apiStep.put("tokenQueryParam", "access_token");
        apiStep.put("apiUrl", dataUrl());
        apiStep.put("apiMethod", "GET");
        apiStep.put("apiListPath", "result.data");
        apiStep.put("apiTimeout", 30);
        apiStep.put("batchSize", 0);

        Map<String, Object> writeStep = new LinkedHashMap<>();
        writeStep.put("type", "OPERATION");
        writeStep.put("sourceType", "DB");
        writeStep.put("operationType", "DB_INSERT");
        writeStep.put("dsId", mysqlDsId);
        writeStep.put("tableName", table);
        writeStep.put("autoCreateTable", true);

        List<Map<String, Object>> steps = new ArrayList<>();
        steps.add(apiStep);
        steps.add(writeStep);
        config.put("steps", steps);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("outputMode", "RETURN");
        config.put("output", output);

        VisualTemplate t = new VisualTemplate();
        t.setName("Token接口同步MySQL模拟");
        t.setEventType("DATA_SOURCE");
        t.setEventConfig(objectMapper.writeValueAsString(config));
        t.setInputParams("[]");
        t.setOutputParams("[]");
        t.setCanvasConfig("{}");
        return t;
    }

    public DsConfig resolveMysql(Long mysqlDsId) {
        if (mysqlDsId != null && mysqlDsId > 0) {
            DsConfig specified = dataSourceService.getById(mysqlDsId).orElse(null);
            if (specified != null && isMysql(specified)) {
                return specified;
            }
        }
        return dataSourceService.findFirstMysql().orElse(null);
    }

    private List<Map<String, Object>> summarizeDbSources() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (DsConfig ds : dataSourceService.listAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", ds.getId());
            item.put("name", ds.getName());
            item.put("sourceType", ds.getSourceType());
            item.put("dbType", ds.getDbType());
            list.add(item);
        }
        return list;
    }

    private static boolean isMysql(DsConfig ds) {
        if (ds == null || !"DB".equalsIgnoreCase(ds.getSourceType())) {
            return false;
        }
        String t = ds.getDbType() != null ? ds.getDbType().toLowerCase() : "";
        return t.contains("mysql") || t.contains("mariadb") || t.contains("tidb") || t.contains("oceanbase");
    }

    private static Map<String, Object> errorBody(int code, String message, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("message", message);
        body.put("description", description);
        body.put("result", new LinkedHashMap<String, Object>());
        return body;
    }

    private List<Map<String, Object>> sampleRows() {
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("T2026001", "模拟学位论文一", "张三", "2026", "mock"));
        rows.add(row("T2026002", "模拟学位论文二", "李四", "2026", "mock"));
        rows.add(row("T2026003", "模拟学位论文三", "王五", "2025", "mock"));
        return rows;
    }

    private static Map<String, Object> row(String id, String title, String author, String year, String source) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("author", author);
        m.put("year", year);
        m.put("source", source);
        return m;
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, Long>> it = tokens.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (e.getValue() < now) {
                it.remove();
            }
        }
    }
}
