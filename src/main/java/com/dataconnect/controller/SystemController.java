package com.dataconnect.controller;

import com.dataconnect.DataConnectApplication;
import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.*;
import com.dataconnect.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.*;

import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;

@RestController
public class SystemController {

    private static final Logger log = LoggerFactory.getLogger(SystemController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ResourceLoader resourceLoader;

    @Autowired
    private TemplateCategoryRepository templateCategoryRepository;
    @Autowired
    private TemplateRepository templateRepository;
    @Autowired
    private DsConfigRepository dsConfigRepository;
    @Autowired
    private ColumnConfigRepository columnConfigRepository;
    @Autowired
    private MappingTemplateRepository mappingTemplateRepository;
    @Autowired
    private FlowConfigRepository flowConfigRepository;
    @Autowired
    private TemplateSnippetRepository templateSnippetRepository;

    @PostMapping("/api/restart")
    @ResponseBody
    public ApiResponse<String> restart() {
        DataConnectApplication.restart();
        return ApiResponse.success("应用正在重启，请等待 5 秒后刷新页面...");
    }

    // ==================== data.sql ↔ DB 双向同步 ====================

    @PostMapping("/api/reload-data-sql")
    @ResponseBody
    public ApiResponse<String> reloadDataSql() {
        long startTime = System.currentTimeMillis();
        try {
            Resource resource = resourceLoader.getResource("classpath:data.sql");
            String sql = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

            List<String> statements = new ArrayList<>();
            EncodedResource encoded = new EncodedResource(resource, StandardCharsets.UTF_8);
            ScriptUtils.splitSqlScript(encoded, sql, ScriptUtils.DEFAULT_STATEMENT_SEPARATOR,
                    ScriptUtils.DEFAULT_COMMENT_PREFIX, ScriptUtils.DEFAULT_BLOCK_COMMENT_START_DELIMITER,
                    ScriptUtils.DEFAULT_BLOCK_COMMENT_END_DELIMITER,
                    statements);

            int success = 0;
            int skipped = 0;
            for (int i = 0; i < statements.size(); i++) {
                String stmt = statements.get(i).trim();
                if (stmt.isEmpty()) continue;
                // 跳过纯注释行
                if (stmt.startsWith("--") && !stmt.contains("\n")) {
                    skipped++;
                    continue;
                }
                try {
                    jdbcTemplate.execute(stmt);
                    success++;
                } catch (Exception e) {
                    log.error("Reload statement {} failed: {}", i + 1, e.getMessage());
                    String preview = stmt.length() > 120 ? stmt.substring(0, 120).replace("\n", " ") + "..." : stmt.replace("\n", " ");
                    return ApiResponse.error("第 " + (i + 1) + " 条语句执行失败: " + e.getMessage()
                            + "\n语句预览: " + preview);
                }
            }
            long elapsed = System.currentTimeMillis() - startTime;
            String msg = "Reload完成: " + success + " 条语句执行成功" +
                    (skipped > 0 ? ", " + skipped + " 条注释跳过" : "") +
                    ", 耗时 " + elapsed + "ms";
            log.info(msg);
            return ApiResponse.success(msg);
        } catch (Exception e) {
            log.error("Reload data.sql failed", e);
            return ApiResponse.error("Reload失败: " + e.getMessage());
        }
    }

    @PostMapping("/api/export-data-sql")
    @ResponseBody
    public ApiResponse<Map<String, Object>> exportDataSql() {
        try {
            String sql = generateDataSql();
            int totalRows = countAllRows();
            Resource resource = resourceLoader.getResource("classpath:data.sql");
            try {
                java.io.File file = resource.getFile();
                Files.write(file.toPath(), sql.getBytes(StandardCharsets.UTF_8));
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("mode", "file");
                result.put("message", "导出成功: " + totalRows + " 条记录已写入 data.sql");
                return ApiResponse.success(result);
            } catch (FileNotFoundException e) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("mode", "download");
                result.put("content", sql);
                result.put("message", "JAR模式, 已触发下载 (" + totalRows + " 条记录)");
                return ApiResponse.success(result);
            }
        } catch (Exception e) {
            log.error("Export data.sql failed", e);
            return ApiResponse.error("导出失败: " + e.getMessage());
        }
    }

    // ==================== SQL 生成 ====================

    private String generateDataSql() {
        StringBuilder sb = new StringBuilder();
        sb.append("-- ============================================\n");
        sb.append("-- Data Connect - 数据对接服务初始化数据\n");
        sb.append("-- Auto-generated: ").append(LocalDateTime.now()).append("\n");
        sb.append("-- ============================================\n\n");

        exportTemplateCategory(sb);
        exportTemplate(sb);
        exportDsConfig(sb);
        exportColumnConfig(sb);
        exportMappingTemplate(sb);
        exportFlowConfig(sb);
        exportTemplateSnippet(sb);

        return sb.toString();
    }

    private int countAllRows() {
        return templateCategoryRepository.findAll().size()
                + templateRepository.findByIsDeleted(0).size()
                + dsConfigRepository.findAll().size()
                + columnConfigRepository.findAll().size()
                + mappingTemplateRepository.findAll().size()
                + flowConfigRepository.findAll().size()
                + templateSnippetRepository.findAll().size();
    }

    // -------------------- template_category --------------------

    private void exportTemplateCategory(StringBuilder sb) {
        List<TemplateCategory> rows = templateCategoryRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- template_category has no data\n\n");
            return;
        }
        sb.append("-- 模板分类\n");
        sb.append("MERGE INTO template_category (id, name, parent_id, sort_order) KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            TemplateCategory r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(r.getParentId()).append(", ")
              .append(r.getSortOrder() != null ? r.getSortOrder() : 0)
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // -------------------- template --------------------

    private void exportTemplate(StringBuilder sb) {
        List<TemplateEntity> rows = templateRepository.findByIsDeleted(0);
        if (rows.isEmpty()) {
            sb.append("-- template has no data\n\n");
            return;
        }
        // 按 category_id 分组，每组内按 id 排序
        rows.sort(Comparator.comparing(TemplateEntity::getCategoryId)
                .thenComparing(TemplateEntity::getId));

        Long currentCategory = null;
        for (TemplateEntity r : rows) {
            if (currentCategory == null || !currentCategory.equals(r.getCategoryId())) {
                currentCategory = r.getCategoryId();
                sb.append("-- template (category_id=").append(currentCategory).append(")\n");
                sb.append("MERGE INTO template (id, name, category_id, content, type, tags, is_deleted, version) KEY(id) VALUES\n");
            }
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(r.getCategoryId()).append(", ")
              .append(esc(r.getContent())).append(", ")
              .append(esc(r.getType())).append(", ")
              .append(esc(r.getTags())).append(", ")
              .append(r.getIsDeleted() != null ? r.getIsDeleted() : 0).append(", ")
              .append(r.getVersion() != null ? r.getVersion() : 1)
              .append(");\n\n");
        }
    }

    // -------------------- ds_config --------------------

    private void exportDsConfig(StringBuilder sb) {
        List<DsConfig> rows = dsConfigRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- ds_config has no data\n\n");
            return;
        }
        sb.append("-- 数据源配置\n");
        String cols = "id, name, description, source_type, db_type, host, port, db_name, " +
                "table_name, username, password, charset, jdbc_params, max_pool_size, min_idle, " +
                "conn_timeout, init_sql, test_query, ssl_enabled, ssl_cert_path, " +
                "api_type, api_method, api_url, api_timeout, api_retry_times, api_retry_interval, " +
                "api_headers, api_body, api_auth_type, api_auth_config, " +
                "api_mode, template_id, api_chain_config, enabled";
        sb.append("MERGE INTO ds_config (").append(cols).append(") KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            DsConfig r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(esc(r.getDescription())).append(", ")
              .append(esc(r.getSourceType())).append(", ")
              .append(esc(r.getDbType())).append(", ")
              .append(esc(r.getHost())).append(", ")
              .append(r.getPort()).append(", ")
              .append(esc(r.getDbName())).append(", ")
              .append(esc(r.getTableName())).append(", ")
              .append(esc(r.getUsername())).append(", ")
              .append(esc(r.getPassword())).append(", ")
              .append(esc(r.getCharset())).append(", ")
              .append(esc(r.getJdbcParams())).append(", ")
              .append(r.getMaxPoolSize()).append(", ")
              .append(r.getMinIdle()).append(", ")
              .append(r.getConnTimeout()).append(", ")
              .append(esc(r.getInitSql())).append(", ")
              .append(esc(r.getTestQuery())).append(", ")
              .append(r.getSslEnabled() != null ? r.getSslEnabled() : 0).append(", ")
              .append(esc(r.getSslCertPath())).append(", ")
              .append(esc(r.getApiType())).append(", ")
              .append(esc(r.getApiMethod())).append(", ")
              .append(esc(r.getApiUrl())).append(", ")
              .append(r.getApiTimeout()).append(", ")
              .append(r.getApiRetryTimes()).append(", ")
              .append(r.getApiRetryInterval()).append(", ")
              .append(esc(r.getApiHeaders())).append(", ")
              .append(esc(r.getApiBody())).append(", ")
              .append(esc(r.getApiAuthType())).append(", ")
              .append(esc(r.getApiAuthConfig())).append(", ")
              .append(esc(r.getApiMode())).append(", ")
              .append(r.getTemplateId()).append(", ")
              .append(esc(r.getApiChainConfig())).append(", ")
              .append(r.getEnabled() != null ? r.getEnabled() : 1)
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // -------------------- column_config --------------------

    private void exportColumnConfig(StringBuilder sb) {
        List<ColumnConfig> rows = columnConfigRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- column_config has no data\n\n");
            return;
        }
        sb.append("-- 列配置\n");
        sb.append("MERGE INTO column_config (id, name, description, column_type, columns_json) KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            ColumnConfig r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(esc(r.getDescription())).append(", ")
              .append(esc(r.getColumnType())).append(", ")
              .append(esc(r.getColumnsJson()))
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // -------------------- mapping_template --------------------

    private void exportMappingTemplate(StringBuilder sb) {
        List<MappingTemplate> rows = mappingTemplateRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- mapping_template has no data\n\n");
            return;
        }
        sb.append("-- 字段映射模板\n");
        sb.append("MERGE INTO mapping_template (id, name, description, ds_config_id, column_config_id, ")
          .append("push_column_config_id, mappings, postman_json) KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            MappingTemplate r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(esc(r.getDescription())).append(", ")
              .append(r.getDsConfigId()).append(", ")
              .append(r.getColumnConfigId()).append(", ")
              .append(r.getPushColumnConfigId()).append(", ")
              .append(esc(r.getMappings())).append(", ")
              .append(esc(r.getPostmanJson()))
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // -------------------- flow_config --------------------

    private void exportFlowConfig(StringBuilder sb) {
        List<FlowConfig> rows = flowConfigRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- flow_config has no data\n\n");
            return;
        }
        sb.append("-- 流程配置\n");
        sb.append("MERGE INTO flow_config (id, name, description, input_ds_id, output_ds_id, ")
          .append("pre_template_id, mapping_template_id, post_template_id, template_params, ")
          .append("pipeline_config, sync_strategy, incremental_column, incremental_column_type) KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            FlowConfig r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(esc(r.getDescription())).append(", ")
              .append(r.getInputDsId()).append(", ")
              .append(r.getOutputDsId()).append(", ")
              .append(r.getPreTemplateId()).append(", ")
              .append(r.getMappingTemplateId()).append(", ")
              .append(r.getPostTemplateId()).append(", ")
              .append(esc(r.getTemplateParams())).append(", ")
              .append(esc(r.getPipelineConfig())).append(", ")
              .append(esc(r.getSyncStrategy())).append(", ")
              .append(esc(r.getIncrementalColumn())).append(", ")
              .append(esc(r.getIncrementalColumnType()))
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // -------------------- template_snippet --------------------

    private void exportTemplateSnippet(StringBuilder sb) {
        List<TemplateSnippet> rows = templateSnippetRepository.findAll();
        if (rows.isEmpty()) {
            sb.append("-- template_snippet has no data\n\n");
            return;
        }
        sb.append("-- 代码片段\n");
        sb.append("MERGE INTO template_snippet (id, name, group_name, description, code, sort_order) KEY(id) VALUES\n");
        for (int i = 0; i < rows.size(); i++) {
            TemplateSnippet r = rows.get(i);
            sb.append("(")
              .append(r.getId()).append(", ")
              .append(esc(r.getName())).append(", ")
              .append(esc(r.getGroupName())).append(", ")
              .append(esc(r.getDescription())).append(", ")
              .append(esc(r.getCode())).append(", ")
              .append(r.getSortOrder() != null ? r.getSortOrder() : 0)
              .append(i < rows.size() - 1 ? "),\n" : ");\n\n");
        }
    }

    // ==================== 工具方法 ====================

    /**
     * 格式化 SQL 值：字符串加引号并转义，数字/NULL 直接输出
     */
    private static String esc(String val) {
        if (val == null) return "NULL";
        return "'" + val.replace("'", "''") + "'";
    }
}
