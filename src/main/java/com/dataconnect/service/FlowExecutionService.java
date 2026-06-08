package com.dataconnect.service;

import com.dataconnect.entity.ColumnConfig;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.entity.FlowConfig;
import com.dataconnect.entity.MappingTemplate;
import com.dataconnect.entity.TemplateEntity;
import com.dataconnect.pipeline.PipelineStage;
import com.dataconnect.pipeline.PipelineStep;
import com.dataconnect.repository.FlowConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class FlowExecutionService {

    private static final Logger log = LoggerFactory.getLogger(FlowExecutionService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private TemplateService templateService;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    @Autowired
    private ApiClientService apiClientService;

    @Autowired
    private MappingTemplateService mappingTemplateService;

    @Autowired
    private ColumnConfigService columnConfigService;

    @Autowired
    private ExecutionLogFileService executionLogFileService;

    private final List<String> executionLogs = new CopyOnWriteArrayList<>();

    private enum SyncStrategy {
        FULL, INCREMENTAL_TIME, INCREMENTAL_ID;

        static SyncStrategy from(String s) {
            try { return valueOf(s); }
            catch (Exception e) { return FULL; }
        }
    }

    public Map<String, Object> execute(Long flowConfigId) {
        executionLogs.clear();
        List<Map<String, Object>> stepLogs = new ArrayList<>();
        Map<String, Object> result = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        FlowConfig flowConfig = flowConfigRepository.findById(flowConfigId).orElse(null);
        if (flowConfig == null) {
            log.warn("执行流程失败: 流程配置不存在, flowConfigId={}", flowConfigId);
            result.put("success", false);
            result.put("error", "流程配置不存在");
            return result;
        }

        // Determine sync strategy (default FULL for backward compat)
        SyncStrategy strategy = SyncStrategy.from(flowConfig.getSyncStrategy());

        log.info("开始执行对接流程, id={}, name={}, strategy={}", flowConfig.getId(), flowConfig.getName(), strategy);
        try {
            addLog("INFO", "开始执行对接流程: " + flowConfig.getName() + " [策略: " + strategy + "]");
            addStepLog(stepLogs, "INFO", "开始执行对接流程: " + flowConfig.getName());

            // Load watermark for incremental strategies
            Map<String, Object> watermarkBefore = null;
            if (strategy != SyncStrategy.FULL) {
                watermarkBefore = executionLogFileService.loadWatermark(flowConfigId);
                if (watermarkBefore != null) {
                    addLog("INFO", "加载水位线: " + flowConfig.getIncrementalColumn()
                            + " > " + watermarkBefore.get("lastValue"));
                    addStepLog(stepLogs, "INFO", "加载水位线: " + watermarkBefore.get("lastValue"));
                } else {
                    addLog("INFO", "首次执行，无历史水位线，将按全量处理");
                    addStepLog(stepLogs, "INFO", "首次执行，无历史水位线");
                }
            }

            // Step 1: 读取输入数据 (with watermark filter for incremental)
            addLog("INFO", "步骤1: 从输入数据源读取数据...");
            addStepLog(stepLogs, "INFO", "步骤1: 从输入数据源读取数据");
            List<Map<String, Object>> inputData = readInputData(flowConfig, strategy, watermarkBefore);
            if (inputData == null) {
                result.put("success", false);
                result.put("error", "读取输入数据失败");
                writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                        stepLogs, 0, 0, 0, watermarkBefore, null, "FAILED", "读取输入数据失败");
                return result;
            }
            addLog("INFO", "读取到 " + inputData.size() + " 条数据");
            addStepLog(stepLogs, "INFO", "读取到 " + inputData.size() + " 条数据");
            if (inputData.size() >= 1000 && strategy != SyncStrategy.FULL) {
                addLog("WARN", "增量读取达到1000条上限，可能存在未同步数据");
                addStepLog(stepLogs, "WARN", "增量读取达到1000条上限，可能存在未同步数据");
            }

            List<Map<String, Object>> processedData = inputData;
            int failCount = 0;

            // 构建管道配置（优先读取pipelineConfig，为空时从旧的3字段合成）
            List<PipelineStage> pipeline = buildPipeline(flowConfig);

            // 按阶段顺序执行管道
            for (PipelineStage stage : pipeline) {
                addLog("INFO", "执行阶段: " + stage.getName() + " [" + stage.getPosition() + "]");
                addStepLog(stepLogs, "INFO", "执行阶段: " + stage.getName() + " [" + stage.getPosition() + "]");

                if ("AFTER_READ".equals(stage.getPosition())) {
                    for (PipelineStep step : stage.getSteps()) {
                        processedData = executeStep(step, processedData, flowConfig.getTemplateParams());
                    }
                } else if ("BEFORE_WRITE".equals(stage.getPosition())) {
                    for (PipelineStep step : stage.getSteps()) {
                        processedData = executeStep(step, processedData, flowConfig.getTemplateParams());
                    }
                }
            }

            // Step 5: 写入输出数据源 (with upsert for incremental)
            addLog("INFO", "步骤5: 写入输出数据源...");
            addStepLog(stepLogs, "INFO", "步骤5: 写入输出数据源");
            int writeCount = writeOutputData(flowConfig, processedData, strategy);
            addLog("INFO", "成功写入 " + writeCount + " 条数据");
            addStepLog(stepLogs, "INFO", "成功写入 " + writeCount + " 条数据");

            // Save new watermark from processed data
            Map<String, Object> watermarkAfter = null;
            if (strategy != SyncStrategy.FULL && !processedData.isEmpty()) {
                Object newHighWater = computeHighWaterMark(processedData, flowConfig);
                if (newHighWater != null) {
                    watermarkAfter = new LinkedHashMap<>();
                    watermarkAfter.put("flowConfigId", flowConfigId);
                    watermarkAfter.put("flowName", flowConfig.getName());
                    watermarkAfter.put("strategy", strategy.name());
                    watermarkAfter.put("incrementalColumn", flowConfig.getIncrementalColumn());
                    watermarkAfter.put("lastValue", String.valueOf(newHighWater));
                    watermarkAfter.put("lastExecTime", LocalDateTime.now().toString());
                    watermarkAfter.put("lastExecStatus", "SUCCESS");
                    watermarkAfter.put("totalSynced", writeCount);
                    executionLogFileService.saveWatermark(flowConfigId, watermarkAfter);
                    addLog("INFO", "水位线已更新: " + newHighWater);
                    addStepLog(stepLogs, "INFO", "水位线已更新: " + flowConfig.getIncrementalColumn()
                            + " = " + newHighWater);
                } else {
                    addLog("WARN", "增量列 [" + flowConfig.getIncrementalColumn() + "] 在结果中未找到，水位线未更新");
                    addStepLog(stepLogs, "WARN", "增量列未在数据中找到，水位线未更新");
                }
            }

            // AFTER_WRITE 阶段：写入后执行（通知、级联同步等）
            for (PipelineStage stage : pipeline) {
                if ("AFTER_WRITE".equals(stage.getPosition())) {
                    addLog("INFO", "执行后置阶段: " + stage.getName());
                    addStepLog(stepLogs, "INFO", "执行后置阶段: " + stage.getName());
                    for (PipelineStep step : stage.getSteps()) {
                        executeStep(step, processedData, flowConfig.getTemplateParams());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            addLog("INFO", "执行完成, 总耗时: " + duration + "ms");
            addStepLog(stepLogs, "INFO", "执行完成, 总耗时: " + duration + "ms");

            log.info("流程执行完成, id={}, name={}, readCount={}, writeCount={}, duration={}ms",
                    flowConfig.getId(), flowConfig.getName(), inputData.size(), writeCount, duration);

            // Write execution log to file
            writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                    stepLogs, inputData.size(), processedData.size(), writeCount,
                    watermarkBefore, watermarkAfter, "SUCCESS", null);

            result.put("success", true);
            result.put("totalCount", inputData.size());
            result.put("successCount", processedData.size());
            result.put("failCount", failCount);
            result.put("writeCount", writeCount);
            result.put("duration", duration);
            result.put("logs", new ArrayList<>(executionLogs));
        } catch (Exception e) {
            log.error("Flow execution failed", e);
            writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                    stepLogs, 0, 0, 0, null, null, "FAILED", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("logs", new ArrayList<>(executionLogs));
        }

        return result;
    }

    private void addStepLog(List<Map<String, Object>> stepLogs, String level, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("level", level);
        entry.put("message", message);
        stepLogs.add(entry);
    }

    private void writeExecutionLogToFile(Long flowConfigId, FlowConfig flowConfig,
            SyncStrategy strategy, long startTime, List<Map<String, Object>> stepLogs,
            int readCount, int successCount, int writeCount,
            Map<String, Object> watermarkBefore, Map<String, Object> watermarkAfter,
            String status, String errorMessage) {
        try {
            Map<String, Object> execData = new LinkedHashMap<>();
            execData.put("flowConfigId", flowConfigId);
            execData.put("flowName", flowConfig.getName());
            execData.put("strategy", strategy.name());
            execData.put("startTime", LocalDateTime.now().minusNanos(
                    (System.currentTimeMillis() - startTime) * 1_000_000).toString());
            execData.put("endTime", LocalDateTime.now().toString());
            execData.put("durationMs", System.currentTimeMillis() - startTime);
            execData.put("status", status);
            execData.put("readCount", readCount);
            execData.put("successCount", successCount);
            execData.put("writeCount", writeCount);
            execData.put("failCount", 0);
            if (watermarkBefore != null) execData.put("watermarkBefore", watermarkBefore);
            if (watermarkAfter != null) execData.put("watermarkAfter", watermarkAfter);
            if (errorMessage != null) execData.put("errorMessage", errorMessage);
            execData.put("stepLogs", stepLogs);
            executionLogFileService.writeExecutionLog(flowConfigId, execData);
        } catch (Exception e) {
            log.error("写入执行日志文件失败, flowConfigId={}", flowConfigId, e);
        }
    }

    private List<Map<String, Object>> readInputData(FlowConfig flowConfig,
            SyncStrategy strategy, Map<String, Object> watermark) {
        DsConfig inputDs = dataSourceService.getById(flowConfig.getInputDsId()).orElse(null);
        if (inputDs == null) return null;

        if ("DB".equals(inputDs.getSourceType())) {
            return readFromDatabase(inputDs, flowConfig, strategy, watermark);
        } else {
            return readFromApi(inputDs, strategy, watermark);
        }
    }

    private List<Map<String, Object>> readFromDatabase(DsConfig dsConfig, FlowConfig flowConfig,
            SyncStrategy strategy, Map<String, Object> watermark) {
        log.info("从数据库读取数据, dsId={}, name={}, strategy={}", dsConfig.getId(), dsConfig.getName(), strategy);
        List<Map<String, Object>> rows = new ArrayList<>();
        DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
        if (ds == null) {
            log.warn("无法获取数据库连接, dsId={}", dsConfig.getId());
            return rows;
        }

        // 优先执行初始化SQL
        try (Connection conn = ds.getConnection()) {
            if (dsConfig.getInitSql() != null && !dsConfig.getInitSql().isEmpty()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(dsConfig.getInitSql());
                }
            }

            // 优先使用配置中指定的表名，未指定则取第一个表
            String targetTable = (dsConfig.getTableName() != null && !dsConfig.getTableName().isEmpty())
                    ? dsConfig.getTableName() : null;
            if (targetTable == null) {
                DatabaseMetaData meta = conn.getMetaData();
                List<String> tableNames = new ArrayList<>();
                try (ResultSet rs = meta.getTables(dsConfig.getDbName(), null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) {
                        tableNames.add(rs.getString("TABLE_NAME"));
                    }
                }
                if (!tableNames.isEmpty()) {
                    targetTable = tableNames.get(0);
                    log.info("未指定数据表, 使用默认表: {}", targetTable);
                }
            }
            if (targetTable == null) {
                log.warn("未找到可用数据表, dsId={}", dsConfig.getId());
                return rows;
            }

            // Build incremental SQL
            String incCol = flowConfig.getIncrementalColumn();
            String sql = "SELECT * FROM " + targetTable;
            if (strategy != SyncStrategy.FULL && watermark != null && incCol != null && !incCol.isEmpty()) {
                // Validate incremental column name to prevent SQL injection
                if (!incCol.matches("^[a-zA-Z_][a-zA-Z0-9_]*$")) {
                    addLog("ERROR", "无效的增量列名: " + incCol);
                    log.error("无效的增量列名: {}", incCol);
                    return rows;
                }
                Object lastVal = watermark.get("lastValue");
                if (lastVal != null) {
                    if (strategy == SyncStrategy.INCREMENTAL_ID) {
                        sql += " WHERE " + incCol + " > " + lastVal + " ORDER BY " + incCol;
                    } else {
                        // Use >= for time-based to avoid losing rows with same timestamp
                        // Upsert on write side handles any duplicates from re-read
                        sql += " WHERE " + incCol + " >= '" + lastVal + "' ORDER BY " + incCol;
                    }
                }
            }
            sql += " LIMIT 1000";

            addLog("DEBUG", "读取表: " + targetTable + " SQL: " + sql);
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                    ResultSetMetaData rsmd = rs.getMetaData();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= rsmd.getColumnCount(); i++) {
                            row.put(rsmd.getColumnName(i), rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
        } catch (Exception e) {
            addLog("ERROR", "读取数据库失败: " + e.getMessage());
        }
        return rows;
    }

    private List<Map<String, Object>> readFromApi(DsConfig dsConfig,
            SyncStrategy strategy, Map<String, Object> watermark) {
        log.info("从API读取数据, dsId={}, name={}, mode={}, strategy={}", dsConfig.getId(), dsConfig.getName(), dsConfig.getApiMode(), strategy);
        try {
            // Build watermark params for API request
            Map<String, String> extraParams = null;
            if (strategy != SyncStrategy.FULL && watermark != null && watermark.get("lastValue") != null) {
                extraParams = new HashMap<>();
                extraParams.put("watermark_lastValue", String.valueOf(watermark.get("lastValue")));
                extraParams.put("watermark_column", String.valueOf(watermark.get("incrementalColumn")));
                extraParams.put("strategy", strategy.name());
            }
            String response = apiClientService.executeRequest(dsConfig, extraParams);
            @SuppressWarnings("unchecked")
            Map<String, Object> jsonResponse = objectMapper.readValue(response, Map.class);

            String mode = dsConfig.getApiMode() != null ? dsConfig.getApiMode() : "SINGLE";

            if ("CHAIN".equals(mode)) {
                // Chain result: {success, steps, variables, lastResponse}
                // lastResponse is the JSON string body of the last step
                String lastResponse = (String) jsonResponse.get("lastResponse");
                if (lastResponse != null && !lastResponse.isEmpty()) {
                    try {
                        Object parsed = objectMapper.readValue(lastResponse, Object.class);
                        return extractListFromObject(parsed);
                    } catch (Exception e) {
                        // Not JSON, return as single row
                        List<Map<String, Object>> result = new ArrayList<>();
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("raw", lastResponse);
                        result.add(row);
                        return result;
                    }
                }
                // Fallback: return variables as a single row
                @SuppressWarnings("unchecked")
                Map<String, Object> vars = (Map<String, Object>) jsonResponse.get("variables");
                if (vars != null && !vars.isEmpty()) {
                    List<Map<String, Object>> result = new ArrayList<>();
                    result.add(new LinkedHashMap<>(vars));
                    return result;
                }
                return new ArrayList<>();
            }

            if ("SCRIPT".equals(mode)) {
                // Script result may have data at top level or in "data" key
                return extractListFromObject(jsonResponse);
            }

            // SINGLE mode — original behavior
            return extractListFromObject(jsonResponse);
        } catch (Exception e) {
            addLog("ERROR", "读取接口数据失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractListFromObject(Object obj) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (obj instanceof List) {
            // Direct list
            for (Object item : (List<?>) obj) {
                if (item instanceof Map) {
                    result.add(new LinkedHashMap<>((Map<String, Object>) item));
                }
            }
            if (!result.isEmpty()) return result;
        }
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            // Try "data" key first
            Object data = map.get("data");
            if (data instanceof List) {
                for (Object item : (List<?>) data) {
                    if (item instanceof Map) {
                        result.add(new LinkedHashMap<>((Map<String, Object>) item));
                    }
                }
                if (!result.isEmpty()) return result;
            }
            // Try nested lists in data values
            for (Object value : map.values()) {
                if (value instanceof List) {
                    for (Object item : (List<?>) value) {
                        if (item instanceof Map) {
                            result.add(new LinkedHashMap<>((Map<String, Object>) item));
                        }
                    }
                    if (!result.isEmpty()) return result;
                }
            }
            // Return the whole map as a single row
            result.add(new LinkedHashMap<>(map));
            return result;
        }
        return result;
    }

    /**
     * Compute the new high-water mark from processed data.
     * Returns the max value of the incremental column across all rows.
     */
    private Object computeHighWaterMark(List<Map<String, Object>> data, FlowConfig flowConfig) {
        if (data == null || data.isEmpty()) return null;
        String col = flowConfig.getIncrementalColumn();
        if (col == null || col.isEmpty()) return null;
        String colType = flowConfig.getIncrementalColumnType();
        if ("NUMERIC".equals(colType)) {
            long maxVal = Long.MIN_VALUE;
            for (Map<String, Object> row : data) {
                Object val = row.get(col);
                if (val instanceof Number) {
                    maxVal = Math.max(maxVal, ((Number) val).longValue());
                }
            }
            return maxVal == Long.MIN_VALUE ? null : maxVal;
        } else {
            // DATETIME or string: find max via lexicographic compare (works for ISO format)
            String maxVal = null;
            for (Map<String, Object> row : data) {
                Object val = row.get(col);
                if (val != null) {
                    String s = val.toString();
                    if (maxVal == null || s.compareTo(maxVal) > 0) {
                        maxVal = s;
                    }
                }
            }
            return maxVal;
        }
    }

    /**
     * 应用数据对接模板的键值对映射。
     * 根据 MappingTemplate.mappings 中的 receiveKey -> pushKey 关系重新映射数据。
     * 如果列配置中该 receiveKey 关联了处理模板，则先将值通过模板处理再映射。
     * 未在映射中指定的字段保留原样。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyMapping(MappingTemplate mappingTemplate, Map<String, Object> row) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            List<Map<String, String>> mappings = objectMapper.readValue(
                    mappingTemplate.getMappings(),
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});

            // Build receiveKey -> templateId map from column config
            Map<String, Long> columnTemplateMap = new LinkedHashMap<>();
            if (mappingTemplate.getColumnConfigId() != null) {
                ColumnConfig columnConfig = columnConfigService.getById(mappingTemplate.getColumnConfigId()).orElse(null);
                if (columnConfig != null && columnConfig.getColumnsJson() != null) {
                    List<Map<String, Object>> columns = objectMapper.readValue(
                            columnConfig.getColumnsJson(),
                            new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                    for (Map<String, Object> col : columns) {
                        String key = (String) col.get("key");
                        Object tid = col.get("templateId");
                        if (key != null && tid != null) {
                            columnTemplateMap.put(key, Long.valueOf(String.valueOf(tid)));
                        }
                    }
                }
            }

            // 按映射关系构建输出: receiveKey 的值 -> pushKey
            for (Map<String, String> mapping : mappings) {
                String receiveKey = mapping.get("receiveKey");
                String pushKey = mapping.get("pushKey");
                if (receiveKey != null && pushKey != null) {
                    Object value = row.getOrDefault(receiveKey, null);

                    // If this receive column has a linked template, process value through it
                    Long colTemplateId = columnTemplateMap.get(receiveKey);
                    if (colTemplateId != null && value != null) {
                        TemplateEntity colTemplate = templateService.getById(colTemplateId).orElse(null);
                        if (colTemplate != null) {
                            // Wrap the single value in a map for template processing
                            Map<String, Object> tempRow = new LinkedHashMap<>();
                            tempRow.put(receiveKey, value);
                            Map<String, Object> processed = applyTemplate(colTemplate, tempRow, null);
                            // Extract the processed value (may be the same key or transformed)
                            if (processed.containsKey(receiveKey)) {
                                value = processed.get(receiveKey);
                            } else if (!processed.isEmpty()) {
                                // Template may have transformed to a different structure
                                value = processed;
                            }
                        }
                    }

                    result.put(pushKey, value);
                }
            }
            // 保留未在映射中的字段
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                result.putIfAbsent(entry.getKey(), entry.getValue());
            }
        } catch (Exception e) {
            addLog("ERROR", "解析映射关系失败: " + e.getMessage());
            return new LinkedHashMap<>(row);
        }
        return result;
    }

    /**
     * 应用模板脚本转换数据。
     * 支持两种模式：
     * 1. 简单变量替换: 脚本中的 ${变量名} 会被替换为数据行中的实际值
     * 2. Groovy 脚本执行: 完整 Groovy/Java 代码，可访问 input(输入行)、params(模板参数)、out(输出Map)
     *
     * Groovy 模板示例:
     * <pre>
     * // 字段映射和转换
     * out['target_id'] = input['source_id']
     * out['full_name'] = input['first_name'] + ' ' + input['last_name']
     * out['age'] = (input['age'] as int) + 1
     *
     * // 或直接 return
     * def result = [:]
     * result.status = input['status'] == '1' ? 'active' : 'inactive'
     * return result
     * </pre>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> applyTemplate(TemplateEntity template, Map<String, Object> row, String templateParams) {
        if (template == null || template.getContent() == null || template.getContent().isEmpty()) {
            return new LinkedHashMap<>(row);
        }

        String script = template.getContent();

        // Parse template params
        Map<String, Object> params = new LinkedHashMap<>();
        if (templateParams != null && !templateParams.isEmpty()) {
            try {
                Map<String, Object> parsed = objectMapper.readValue(templateParams, Map.class);
                params.putAll(parsed);
            } catch (Exception e) {
                log.debug("模板参数解析失败, 将忽略: {}", e.getMessage());
            }
        }

        // Phase 1: Simple ${var} variable substitution (for simple template scenarios)
        // Substitute row values
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            script = script.replace(placeholder, value);
        }
        // Substitute template param values
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            String value = entry.getValue() != null ? String.valueOf(entry.getValue()) : "";
            script = script.replace(placeholder, value);
        }

        // Phase 2: Execute as Groovy/Java script
        try {
            groovy.lang.Binding binding = new groovy.lang.Binding();
            binding.setVariable("input", new LinkedHashMap<>(row));
            binding.setVariable("params", params);
            binding.setVariable("out", new LinkedHashMap<>());

            groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(binding);
            Object result = shell.evaluate(script);

            // Priority 1: If script populated 'out' variable, use it
            Object outVar = binding.getVariable("out");
            if (outVar instanceof Map && !((Map<?, ?>) outVar).isEmpty()) {
                return new LinkedHashMap<>((Map<String, Object>) outVar);
            }

            // Priority 2: If script returned a Map, use it
            if (result instanceof Map) {
                return new LinkedHashMap<>((Map<String, Object>) result);
            }

            // Priority 3: If script returned a JSON string, parse it
            if (result instanceof String) {
                try {
                    Map<String, Object> parsed = objectMapper.readValue((String) result, Map.class);
                    return new LinkedHashMap<>(parsed);
                } catch (Exception e) {
                    // Not valid JSON, fall through
                }
            }

            // Priority 4: Script executed but didn't return usable data — use input row
            addLog("WARN", "模板脚本未返回有效数据(Map/JSON)，使用原始数据");
        } catch (Exception e) {
            addLog("WARN", "模板脚本执行失败: " + e.getMessage() + "，使用原始数据");
        }

        return new LinkedHashMap<>(row);
    }

    /**
     * 构建管道配置。
     * 优先从 pipelineConfig JSON 解析；为空时从旧的 pre/mapping/post 三个字段合成。
     */
    private List<PipelineStage> buildPipeline(FlowConfig flowConfig) {
        if (flowConfig.getPipelineConfig() != null && !flowConfig.getPipelineConfig().isEmpty()) {
            try {
                return objectMapper.readValue(flowConfig.getPipelineConfig(),
                        new TypeReference<List<PipelineStage>>() {});
            } catch (Exception e) {
                addLog("WARN", "管道配置解析失败，回退到旧字段模式: " + e.getMessage());
            }
        }

        // 向后兼容：从旧 3 字段合成管道
        List<PipelineStage> stages = new ArrayList<>();

        // AFTER_READ 阶段：前置模板
        if (flowConfig.getPreTemplateId() != null && flowConfig.getPreTemplateId() > 0) {
            PipelineStage stage = new PipelineStage();
            stage.setPosition("AFTER_READ");
            stage.setName("前置处理");
            PipelineStep step = new PipelineStep();
            step.setType("TEMPLATE");
            step.setTemplateId(flowConfig.getPreTemplateId());
            stage.setSteps(Collections.singletonList(step));
            stages.add(stage);
        }

        // AFTER_READ 阶段：数据对接模板（追加到已有 AFTER_READ 阶段或新建）
        if (flowConfig.getMappingTemplateId() != null && flowConfig.getMappingTemplateId() > 0) {
            PipelineStage afterRead = null;
            for (PipelineStage s : stages) {
                if ("AFTER_READ".equals(s.getPosition())) {
                    afterRead = s;
                    break;
                }
            }
            if (afterRead == null) {
                afterRead = new PipelineStage();
                afterRead.setPosition("AFTER_READ");
                afterRead.setName("数据对接");
                afterRead.setSteps(new ArrayList<>());
                stages.add(afterRead);
            }
            PipelineStep step = new PipelineStep();
            step.setType("MAPPING");
            step.setMappingTemplateId(flowConfig.getMappingTemplateId());
            afterRead.getSteps().add(step);
        }

        // BEFORE_WRITE 阶段：后置模板
        if (flowConfig.getPostTemplateId() != null && flowConfig.getPostTemplateId() > 0) {
            PipelineStage stage = new PipelineStage();
            stage.setPosition("BEFORE_WRITE");
            stage.setName("后置处理");
            PipelineStep step = new PipelineStep();
            step.setType("TEMPLATE");
            step.setTemplateId(flowConfig.getPostTemplateId());
            stage.setSteps(Collections.singletonList(step));
            stages.add(stage);
        }

        return stages;
    }

    /**
     * 执行单个管道步骤。
     * 根据步骤类型路由到 applyTemplate 或 applyMapping，逐行处理数据。
     */
    private List<Map<String, Object>> executeStep(PipelineStep step,
            List<Map<String, Object>> data, String flowTemplateParams) {
        if (step == null || data == null || data.isEmpty()) return data;

        if ("TEMPLATE".equals(step.getType())) {
            if (step.getTemplateId() == null) {
                addLog("WARN", "步骤未设置模板ID，跳过");
                return data;
            }
            TemplateEntity template = templateService.getById(step.getTemplateId()).orElse(null);
            if (template == null) {
                addLog("WARN", "模板不存在: " + step.getTemplateId());
                return data;
            }

            // 步骤级 params 优先，否则用流程级 templateParams
            String effectiveParams = step.getParams() != null && !step.getParams().isEmpty()
                    ? toJson(step.getParams()) : flowTemplateParams;

            List<Map<String, Object>> result = new ArrayList<>();
            int failCount = 0;
            for (int i = 0; i < data.size(); i++) {
                try {
                    result.add(applyTemplate(template, data.get(i), effectiveParams));
                } catch (Exception e) {
                    failCount++;
                    addLog("ERROR", "模板[" + template.getName() + "]处理第" + (i + 1) + "条失败: " + e.getMessage());
                }
            }
            addLog("INFO", "模板[" + template.getName() + "]完成: 成功=" + result.size() + ", 失败=" + failCount);
            return result;
        }

        if ("MAPPING".equals(step.getType())) {
            if (step.getMappingTemplateId() == null) {
                addLog("WARN", "步骤未设置映射模板ID，跳过");
                return data;
            }
            MappingTemplate mapping = mappingTemplateService.getById(step.getMappingTemplateId()).orElse(null);
            if (mapping == null || mapping.getMappings() == null || mapping.getMappings().isEmpty()) {
                addLog("WARN", "映射模板不存在或未定义映射关系: " + step.getMappingTemplateId());
                return data;
            }
            List<Map<String, Object>> result = new ArrayList<>();
            int failCount = 0;
            for (int i = 0; i < data.size(); i++) {
                try {
                    result.add(applyMapping(mapping, data.get(i)));
                } catch (Exception e) {
                    failCount++;
                    addLog("ERROR", "映射[" + mapping.getName() + "]处理第" + (i + 1) + "条失败: " + e.getMessage());
                }
            }
            addLog("INFO", "映射[" + mapping.getName() + "]完成: 成功=" + result.size() + ", 失败=" + failCount);
            return result;
        }

        addLog("WARN", "未知步骤类型: " + step.getType() + "，跳过");
        return data;
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return "{}";
        }
    }

    private int writeOutputData(FlowConfig flowConfig, List<Map<String, Object>> data,
            SyncStrategy strategy) {
        DsConfig outputDs = dataSourceService.getById(flowConfig.getOutputDsId()).orElse(null);
        if (outputDs == null || data.isEmpty()) return 0;

        if ("DB".equals(outputDs.getSourceType())) {
            return writeToDatabase(outputDs, data, strategy);
        } else {
            return writeToApi(outputDs, data);
        }
    }

    private int writeToDatabase(DsConfig dsConfig, List<Map<String, Object>> data,
            SyncStrategy strategy) {
        DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
        if (ds == null || data.isEmpty()) return 0;

        String dbType = dsConfig.getDbType();
        int count = 0;
        try (Connection conn = ds.getConnection()) {
            // Auto-create target table from the first row's columns
            Map<String, Object> firstRow = data.get(0);
            // 优先使用输出数据源配置的表名，未指定则用默认表
            String tableName = (dsConfig.getTableName() != null && !dsConfig.getTableName().isEmpty())
                    ? dsConfig.getTableName() : "data_sync_result";
            if (!tableExists(conn, tableName)) {
                String ddl = buildCreateTableDDL(tableName, firstRow, dbType);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(ddl);
                    addLog("DEBUG", "自动创建目标表: " + tableName + " (dbType=" + dbType + ")");
                    log.info("自动创建目标表, table={}, dbType={}", tableName, dbType);
                }
            }

            for (Map<String, Object> row : data) {
                if (row.isEmpty()) continue;
                try {
                    if (strategy == SyncStrategy.FULL) {
                        // Full sync: plain INSERT
                        executeInsert(conn, tableName, row);
                    } else {
                        // Incremental: try INSERT first, fallback to UPDATE on duplicate key
                        executeUpsert(conn, tableName, row, dbType);
                    }
                    count++;
                } catch (Exception e) {
                    addLog("ERROR", "写入行失败: " + e.getMessage());
                    log.warn("写入行失败, table={}", tableName, e);
                }
            }
        } catch (Exception e) {
            log.error("写入数据库失败, dbType={}", dbType, e);
            addLog("ERROR", "写入数据库失败: " + e.getMessage());
        }
        return count;
    }

    private void executeInsert(Connection conn, String tableName, Map<String, Object> row) throws SQLException {
        StringBuilder sql = new StringBuilder("INSERT INTO " + tableName + " (");
        StringBuilder values = new StringBuilder(" VALUES (");
        List<Object> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            sql.append("\"").append(entry.getKey()).append("\",");
            values.append("?,");
            params.add(entry.getValue());
        }
        sql.setLength(sql.length() - 1);
        values.setLength(values.length() - 1);
        sql.append(")").append(values).append(")");

        try (PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            ps.executeUpdate();
        }
    }

    private void executeUpsert(Connection conn, String tableName, Map<String, Object> row,
            String dbType) throws SQLException {
        // Build MERGE/UPSERT based on dbType
        String t = dbType != null ? dbType.toLowerCase() : "";

        if (t.contains("h2")) {
            // H2: MERGE INTO ... USING (VALUES ...) AS t ON ... WHEN MATCHED THEN UPDATE ...
            StringBuilder merge = new StringBuilder("MERGE INTO " + tableName + " (");
            StringBuilder cols = new StringBuilder();
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                cols.append("\"").append(entry.getKey()).append("\",");
                params.add(entry.getValue());
            }
            cols.setLength(cols.length() - 1);
            merge.append(cols).append(") KEY(id) VALUES (");
            for (int i = 0; i < params.size(); i++) {
                merge.append("?,");
            }
            merge.setLength(merge.length() - 1);
            merge.append(")");

            try (PreparedStatement ps = conn.prepareStatement(merge.toString())) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
            }
        } else if (t.contains("mysql") || t.contains("mariadb") || t.contains("tidb") || t.contains("oceanbase")) {
            // MySQL: INSERT ... ON DUPLICATE KEY UPDATE ...
            StringBuilder insert = new StringBuilder("INSERT INTO " + tableName + " (");
            StringBuilder values = new StringBuilder(") VALUES (");
            StringBuilder update = new StringBuilder(") ON DUPLICATE KEY UPDATE ");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String col = "\"" + entry.getKey() + "\"";
                insert.append(col).append(",");
                values.append("?,");
                if (!"id".equalsIgnoreCase(entry.getKey())) {
                    update.append(col).append("=VALUES(").append(col).append("),");
                }
                params.add(entry.getValue());
            }
            insert.setLength(insert.length() - 1);
            values.setLength(values.length() - 1);
            update.setLength(update.length() - 1);
            String sql = insert.toString() + values + update.toString();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
            }
        } else if (t.contains("postgre") || t.contains("greenplum")) {
            // PostgreSQL: INSERT ... ON CONFLICT (id) DO UPDATE SET ...
            StringBuilder insert = new StringBuilder("INSERT INTO " + tableName + " (");
            StringBuilder values = new StringBuilder(") VALUES (");
            StringBuilder update = new StringBuilder(") ON CONFLICT (\"id\") DO UPDATE SET ");
            List<Object> params = new ArrayList<>();
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String col = "\"" + entry.getKey() + "\"";
                insert.append(col).append(",");
                values.append("?,");
                if (!"id".equalsIgnoreCase(entry.getKey())) {
                    update.append(col).append("=EXCLUDED.").append(col).append(",");
                }
                params.add(entry.getValue());
            }
            insert.setLength(insert.length() - 1);
            values.setLength(values.length() - 1);
            update.setLength(update.length() - 1);
            String sql = insert.toString() + values + update.toString();

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (int i = 0; i < params.size(); i++) {
                    ps.setObject(i + 1, params.get(i));
                }
                ps.executeUpdate();
            }
        } else {
            // Generic fallback: try INSERT, if duplicate key, do UPDATE
            try {
                executeInsert(conn, tableName, row);
            } catch (SQLException e) {
                // Build UPDATE: UPDATE table SET col=?,... WHERE id=?
                StringBuilder update = new StringBuilder("UPDATE " + tableName + " SET ");
                List<Object> params = new ArrayList<>();
                Object idVal = null;
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if ("id".equalsIgnoreCase(entry.getKey())) {
                        idVal = entry.getValue();
                    } else {
                        update.append("\"").append(entry.getKey()).append("\"=?,");
                        params.add(entry.getValue());
                    }
                }
                if (idVal == null) {
                    // No id column, re-throw
                    throw e;
                }
                update.setLength(update.length() - 1);
                update.append(" WHERE \"id\"=?");
                params.add(idVal);

                try (PreparedStatement ps = conn.prepareStatement(update.toString())) {
                    for (int i = 0; i < params.size(); i++) {
                        ps.setObject(i + 1, params.get(i));
                    }
                    ps.executeUpdate();
                }
            }
        }
    }

    /**
     * 根据数据库类型生成适配的建表DDL，确保id自增列语法正确。
     */
    private String buildCreateTableDDL(String tableName, Map<String, Object> firstRow, String dbType) {
        StringBuilder ddl = new StringBuilder("CREATE TABLE " + tableName + " (");
        ddl.append(getIdColumnDDL(dbType));
        for (Map.Entry<String, Object> entry : firstRow.entrySet()) {
            ddl.append(", \"").append(entry.getKey()).append("\" ");
            Object val = entry.getValue();
            if (val instanceof Number) {
                if (val instanceof Integer || val instanceof Long || val instanceof Short || val instanceof Byte) {
                    ddl.append("BIGINT");
                } else {
                    ddl.append("DOUBLE");
                }
            } else {
                ddl.append("VARCHAR(2000)");
            }
        }
        ddl.append(")");
        return ddl.toString();
    }

    /**
     * 获取不同数据库的自增主键DDL片段。
     */
    private String getIdColumnDDL(String dbType) {
        if (dbType == null) return "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        String t = dbType.toLowerCase();
        if (t.contains("mysql") || t.contains("mariadb") || t.contains("tidb") || t.contains("oceanbase")) {
            return "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
        if (t.contains("postgre") || t.contains("greenplum")) {
            return "id BIGSERIAL PRIMARY KEY";
        }
        if (t.contains("sql server") || t.contains("sqlserver")) {
            return "id BIGINT IDENTITY(1,1) PRIMARY KEY";
        }
        if (t.contains("oracle")) {
            return "id NUMBER(19) GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
        }
        if (t.contains("db2")) {
            return "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
        }
        if (t.contains("sqlite")) {
            return "id INTEGER PRIMARY KEY AUTOINCREMENT";
        }
        if (t.contains("h2")) {
            return "id BIGINT AUTO_INCREMENT PRIMARY KEY";
        }
        if (t.contains("derby")) {
            return "id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY";
        }
        if (t.contains("hsqldb")) {
            return "id BIGINT IDENTITY PRIMARY KEY";
        }
        if (t.contains("clickhouse")) {
            return "id UUID DEFAULT generateUUIDv4()";
        }
        // 兜底使用SQL标准语法
        return "id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY";
    }

    private boolean tableExists(Connection conn, String tableName) {
        try {
            // 尝试原始名称
            try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName, new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
            // 尝试大写 (Oracle、H2 等)
            try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toUpperCase(), new String[]{"TABLE"})) {
                if (rs.next()) return true;
            }
            // 尝试小写
            try (ResultSet rs = conn.getMetaData().getTables(null, null, tableName.toLowerCase(), new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Exception e) {
            log.debug("表存在性检查失败, 将尝试直接建表: {}", e.getMessage());
        }
        return false;
    }

    private int writeToApi(DsConfig dsConfig, List<Map<String, Object>> data) {
        int count = 0;
        for (Map<String, Object> row : data) {
            try {
                // 使用模板变量替换方式
                Map<String, String> params = new HashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    params.put(entry.getKey(), String.valueOf(entry.getValue()));
                }
                apiClientService.executeRequest(dsConfig, params);
                count++;
            } catch (Exception e) {
                addLog("ERROR", "写入接口失败: " + e.getMessage());
            }
        }
        return count;
    }

    private void addLog(String level, String message) {
        String logLine = LocalDateTime.now() + " [" + level + "] " + message;
        executionLogs.add(logLine);
    }

    public List<String> getExecutionLogs() {
        return new ArrayList<>(executionLogs);
    }

    /**
     * 获取流程的管道配置 JSON 字符串（供 UI 使用）。
     * 如果 pipelineConfig 非空直接返回；否则从旧 3 字段合成。
     */
    public String getPipelineConfigJson(FlowConfig flowConfig) {
        if (flowConfig.getPipelineConfig() != null && !flowConfig.getPipelineConfig().isEmpty()) {
            return flowConfig.getPipelineConfig();
        }
        try {
            List<PipelineStage> stages = buildPipeline(flowConfig);
            return objectMapper.writeValueAsString(stages);
        } catch (Exception e) {
            return "[]";
        }
    }
}
