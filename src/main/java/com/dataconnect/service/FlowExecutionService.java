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

    @Autowired
    private ThesisArchiveService thesisArchiveService;

    // ==================== 执行控制 ====================
    private volatile boolean executionCancelled = false;
    private volatile boolean executionPaused = false;
    private final Object pauseLock = new Object();
    private volatile String executionStatus = "idle"; // idle|running|paused|cancelled|completed|failed
    private volatile Long currentFlowConfigId = null;
    private volatile int currentRow = 0;
    private volatile int totalRows = 0;

    // 遇到错误是否立即停止（默认 true）
    private volatile boolean stopOnError = true;

    private final List<Map<String, Object>> executionLogs = new CopyOnWriteArrayList<>();

    private enum SyncStrategy {
        FULL, INCREMENTAL_TIME, INCREMENTAL_ID, SYNCED_SET;

        static SyncStrategy from(String s) {
            try { return valueOf(s); }
            catch (Exception e) { return FULL; }
        }
    }

    // SYNCED_SET: 本次执行成功同步的 UUID 集合，执行结束后持久化
    private final Set<String> syncedIds = new LinkedHashSet<>();

    public Map<String, Object> execute(Long flowConfigId) {
        executionLogs.clear();
        Map<String, Object> result = new LinkedHashMap<>();
        long startTime = System.currentTimeMillis();

        // 初始化执行控制状态
        executionCancelled = false;
        executionPaused = false;
        executionStatus = "running";
        currentFlowConfigId = flowConfigId;
        currentRow = 0;
        totalRows = 0;

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

            // Load watermark / synced IDs for non-FULL strategies
            Map<String, Object> watermarkBefore = null;
            syncedIds.clear();
            if (strategy == SyncStrategy.SYNCED_SET) {
                Set<String> loaded = executionLogFileService.loadSyncedIds(flowConfigId);
                syncedIds.addAll(loaded);
                addLog("INFO", "加载已同步ID集合, count=" + syncedIds.size());
                // 构造 watermark 用于传入 extraParams
                watermarkBefore = new LinkedHashMap<>();
                watermarkBefore.put("syncedIds", new ArrayList<>(syncedIds));
            } else if (strategy != SyncStrategy.FULL) {
                watermarkBefore = executionLogFileService.loadWatermark(flowConfigId);
                if (watermarkBefore != null) {
                    addLog("INFO", "加载水位线: " + flowConfig.getIncrementalColumn()
                            + " > " + watermarkBefore.get("lastValue"));
                } else {
                    addLog("INFO", "首次执行，无历史水位线，将按全量处理");
                }
            }

            // Step 1: 读取输入数据 (with watermark filter for incremental)
            addLog("INFO", "步骤1: 从输入数据源读取数据...");
            List<Map<String, Object>> inputData = readInputData(flowConfig, strategy, watermarkBefore);
            if (inputData == null) {
                result.put("success", false);
                result.put("error", "读取输入数据失败");
                writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                        0, 0, 0, watermarkBefore, null, "FAILED", "读取输入数据失败");
                return result;
            }
            addLog("INFO", "读取到 " + inputData.size() + " 条数据");
            if (inputData.size() >= 1000 && strategy != SyncStrategy.FULL) {
                addLog("WARN", "增量读取达到1000条上限，可能存在未同步数据");
            }
            totalRows = inputData.size();

            // 检查是否被取消
            if (executionCancelled) {
                throw new RuntimeException("执行已被用户取消");
            }

            List<Map<String, Object>> processedData = inputData;
            int failCount = 0;

            // 构建管道配置（优先读取pipelineConfig，为空时从旧的3字段合成）
            List<PipelineStage> pipeline = buildPipeline(flowConfig);

            // 按阶段顺序执行管道
            for (PipelineStage stage : pipeline) {
                addLog("INFO", "执行阶段: " + stage.getName() + " [" + stage.getPosition() + "]");

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
            if (executionCancelled) {
                throw new RuntimeException("执行已被用户取消");
            }
            addLog("INFO", "步骤5: 写入输出数据源...");
            int writeCount = writeOutputData(flowConfig, processedData, strategy);
            addLog("INFO", "成功写入 " + writeCount + " 条数据");

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
                    addLog("INFO", "水位线已更新: " + flowConfig.getIncrementalColumn()
                            + " = " + newHighWater);
                } else {
                    addLog("WARN", "增量列 [" + flowConfig.getIncrementalColumn() + "] 在结果中未找到，水位线未更新");
                }
            }

            // SYNCED_SET: 保存本次成功同步的 UUID
            if (strategy == SyncStrategy.SYNCED_SET && !syncedIds.isEmpty()) {
                executionLogFileService.saveSyncedIds(flowConfigId, syncedIds);
                addLog("INFO", "已同步ID已保存, 本次新增=" + writeCount + ", 累计=" + syncedIds.size());
            }

            // AFTER_WRITE 阶段：写入后执行（通知、级联同步等）
            for (PipelineStage stage : pipeline) {
                if ("AFTER_WRITE".equals(stage.getPosition())) {
                    addLog("INFO", "执行后置阶段: " + stage.getName());
                    for (PipelineStep step : stage.getSteps()) {
                        executeStep(step, processedData, flowConfig.getTemplateParams());
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            addLog("INFO", "执行完成, 总耗时: " + duration + "ms");

            log.info("流程执行完成, id={}, name={}, readCount={}, writeCount={}, duration={}ms",
                    flowConfig.getId(), flowConfig.getName(), inputData.size(), writeCount, duration);

            // Write execution log to file
            writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                    inputData.size(), processedData.size(), writeCount,
                    watermarkBefore, watermarkAfter, "SUCCESS", null);

            executionStatus = "completed";
            result.put("success", true);
            result.put("totalCount", inputData.size());
            result.put("successCount", processedData.size());
            result.put("failCount", failCount);
            result.put("writeCount", writeCount);
            result.put("duration", duration);
            result.put("logs", getExecutionLogs());
        } catch (Exception e) {
            log.error("Flow execution failed", e);
            boolean isCancelled = "执行已被用户取消".equals(e.getMessage())
                    || (e.getCause() != null && "执行已被用户取消".equals(e.getCause().getMessage()));
            executionStatus = isCancelled ? "cancelled" : "failed";
            writeExecutionLogToFile(flowConfigId, flowConfig, strategy, startTime,
                    0, 0, 0, null, null,
                    isCancelled ? "CANCELLED" : "FAILED", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("cancelled", isCancelled);
            result.put("logs", getExecutionLogs());
        } finally {
            currentFlowConfigId = null;
        }

        return result;
    }

    private void writeExecutionLogToFile(Long flowConfigId, FlowConfig flowConfig,
            SyncStrategy strategy, long startTime,
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
            execData.put("stepLogs", new ArrayList<>(executionLogs));
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
            if (strategy == SyncStrategy.SYNCED_SET) {
                extraParams = new HashMap<>();
                extraParams.put("strategy", "SYNCED_SET");
                extraParams.put("syncedIds", new ArrayList<>(syncedIds).toString());
            } else if (strategy != SyncStrategy.FULL && watermark != null && watermark.get("lastValue") != null) {
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

                    // 跳过 null 值，避免推送 "null" 字符串
                    if (value != null) {
                        result.put(pushKey, value);
                    }
                }
            }
            // 保留未在映射中的字段（跳过 null 值）
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getValue() != null) {
                    result.putIfAbsent(entry.getKey(), entry.getValue());
                }
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
                checkPauseAndCancel();
                if (executionCancelled) throw new RuntimeException("执行已被用户取消");
                currentRow = i + 1;
                try {
                    result.add(applyTemplate(template, data.get(i), effectiveParams));
                } catch (Exception e) {
                    failCount++;
                    addLog("ERROR", "模板[" + template.getName() + "]处理第" + (i + 1) + "条失败: " + e.getMessage());
                    if (stopOnError) throw new RuntimeException("模板处理第" + (i + 1) + "条失败: " + e.getMessage(), e);
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
            addLog("INFO", "使用映射模板: [" + mapping.getId() + "] " + mapping.getName());
            // 打印涉及 fdext 的映射关系
            try {
                List<Map<String, String>> dbgMappings = objectMapper.readValue(mapping.getMappings(),
                        new TypeReference<List<Map<String, String>>>() {});
                for (Map<String, String> m : dbgMappings) {
                    String pk = m.get("pushKey");
                    if (pk != null && pk.startsWith("fdext")) {
                        addLog("INFO", "  映射: " + m.get("receiveKey") + " → " + pk);
                    }
                }
            } catch (Exception ignored) {}
            // 打印第一条数据的可用字段
            if (!data.isEmpty()) {
                addLog("INFO", "源数据字段: " + data.get(0).keySet());
            }
            List<Map<String, Object>> result = new ArrayList<>();
            int failCount = 0;
            for (int i = 0; i < data.size(); i++) {
                checkPauseAndCancel();
                if (executionCancelled) throw new RuntimeException("执行已被用户取消");
                currentRow = i + 1;
                try {
                    result.add(applyMapping(mapping, data.get(i)));
                } catch (Exception e) {
                    failCount++;
                    addLog("ERROR", "映射[" + mapping.getName() + "]处理第" + (i + 1) + "条失败: " + e.getMessage());
                    if (stopOnError) throw new RuntimeException("映射处理第" + (i + 1) + "条失败: " + e.getMessage(), e);
                }
            }
            addLog("INFO", "映射[" + mapping.getName() + "]完成: 成功=" + result.size() + ", 失败=" + failCount);
            // 打印第一条映射结果的 fdext 字段
            if (!result.isEmpty()) {
                Map<String, Object> firstRow = result.get(0);
                StringBuilder fdextInfo = new StringBuilder("第一条映射结果中的fdext字段: ");
                boolean hasFdext = false;
                for (String key : firstRow.keySet()) {
                    if (key.startsWith("fdext")) {
                        hasFdext = true;
                        fdextInfo.append(key).append("='").append(firstRow.get(key)).append("' ");
                    }
                }
                if (hasFdext) {
                    addLog("INFO", fdextInfo.toString());
                } else {
                    addLog("WARN", "第一条映射结果中没有任何 fdext 字段！");
                    addLog("INFO", "第一条映射结果的字段: " + firstRow.keySet());
                }
            }
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
        addLog("INFO", "开始写入数据库, dbType=" + dbType + ", 数据量=" + data.size());
        try (Connection conn = ds.getConnection()) {
            Map<String, Object> firstRow = data.get(0);
            String tableName = (dsConfig.getTableName() != null && !dsConfig.getTableName().isEmpty())
                    ? dsConfig.getTableName() : "data_sync_result";
            if (!tableExists(conn, tableName)) {
                String ddl = buildCreateTableDDL(tableName, firstRow, dbType);
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(ddl);
                    addLog("INFO", "自动建表: " + tableName + "\nDDL: " + ddl);
                    log.info("自动创建目标表, table={}, dbType={}", tableName, dbType);
                }
            }

            int rowNum = 0;
            for (Map<String, Object> row : data) {
                checkPauseAndCancel();
                if (executionCancelled) throw new RuntimeException("执行已被用户取消");
                rowNum++;
                currentRow = rowNum;
                if (row.isEmpty()) continue;
                try {
                    if (strategy == SyncStrategy.FULL) {
                        executeInsert(conn, tableName, row);
                    } else {
                        executeUpsert(conn, tableName, row, dbType);
                    }
                    if (rowNum <= 3) { // Log first 3 SQLs
                        addLog("DEBUG", "[" + rowNum + "/" + data.size() + "] 写入DB成功, 字段: " + row.keySet());
                    }
                    count++;
                } catch (Exception e) {
                    addLog("ERROR", "[" + rowNum + "/" + data.size() + "] 写入行失败: " + e.getMessage());
                    log.warn("写入行失败, table={}", tableName, e);
                    if (stopOnError) throw new RuntimeException("第" + rowNum + "条写入DB失败: " + e.getMessage(), e);
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
        if ("ARCHIVE".equalsIgnoreCase(dsConfig.getApiMode())) {
            return writeToArchive(dsConfig, data);
        }
        int count = 0;
        int rowNum = 0;
        for (Map<String, Object> row : data) {
            checkPauseAndCancel();
            if (executionCancelled) throw new RuntimeException("执行已被用户取消");
            rowNum++;
            currentRow = rowNum;
            try {
                Map<String, String> params = new HashMap<>();
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    Object val = entry.getValue();
                    if (val == null) continue;
                    if (val instanceof List || val instanceof Map) {
                        params.put(entry.getKey(), objectMapper.writeValueAsString(val));
                    } else {
                        params.put(entry.getKey(), String.valueOf(val));
                    }
                }
                addLog("INFO", "[" + rowNum + "/" + data.size() + "] 推送数据行, 字段: " + params.keySet());
                String resp = apiClientService.executeRequest(dsConfig, params);
                addLog("INFO", "[" + rowNum + "/" + data.size() + "] 推送完成, 响应长度: " + (resp != null ? resp.length() : 0));
                if (resp != null && resp.length() < 500) {
                    addLog("INFO", "[" + rowNum + "/" + data.size() + "] 响应内容: " + resp);
                }
                count++;
            } catch (Exception e) {
                addLog("ERROR", "[" + rowNum + "/" + data.size() + "] 写入接口失败: " + e.getMessage());
                if (stopOnError) throw new RuntimeException("第" + rowNum + "条写入失败: " + e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * 论文归档模式：逐条调用 ThesisArchiveService 生成XML+打包+推送。
     */
    private int writeToArchive(DsConfig dsConfig, List<Map<String, Object>> data) {
        int count = 0;
        int rowNum = 0;
        // 按实体分类号分别计数，每个分类号从1开始
        Map<String, Integer> entityCaseCounters = new LinkedHashMap<>();
        for (Map<String, Object> row : data) {
            checkPauseAndCancel();
            if (executionCancelled) throw new RuntimeException("执行已被用户取消");
            rowNum++;
            currentRow = rowNum;
            try {
                String entityClassNum = computeEntityClassNum(row);
                int caseNum = entityCaseCounters.getOrDefault(entityClassNum, 0) + 1;
                entityCaseCounters.put(entityClassNum, caseNum);
                row.put("案卷号", String.valueOf(caseNum));
                Object fid = row.get("标识");
                Object enrichFailed = row.get("_enrichmentFailed");
                if (enrichFailed != null && !"false".equals(String.valueOf(enrichFailed))) {
                    addLog("WARN", "[" + rowNum + "/" + data.size() + "] 数据增强失败(学号/学院未获取到), _enrichmentFailed=" + enrichFailed + ", 跳过归档, 标识: " + fid);
                    continue;
                }
                addLog("INFO", "[" + rowNum + "/" + data.size() + "] 论文归档, 标识: " + fid);
                Map<String, Object> result = thesisArchiveService.execute(row, dsConfig);
                if (Boolean.TRUE.equals(result.get("success"))) {
                    addLog("INFO", "[" + rowNum + "/" + data.size() + "] 归档推送成功");
                    if (fid != null) syncedIds.add(fid.toString());
                } else {
                    addLog("WARN", "[" + rowNum + "/" + data.size() + "] 归档推送失败: " + result.getOrDefault("error", "未知"));
                }
                count++;
            } catch (Exception e) {
                addLog("ERROR", "[" + rowNum + "/" + data.size() + "] 归档异常: " + e.getMessage());
                if (stopOnError) throw new RuntimeException("第" + rowNum + "条归档失败: " + e.getMessage(), e);
            }
        }
        return count;
    }

    /**
     * 根据行数据计算实体分类号（年份-二级目录），与 ThesisArchiveService 逻辑一致。
     */
    private String computeEntityClassNum(Map<String, Object> row) {
        String c2Val = strVal(row, "二级目录", "JX16");
        String timeVal = strVal(row, "时间", "");
        if (timeVal.isEmpty()) timeVal = strVal(row, "submissionDate", "").replaceAll("-", "");
        String year = timeVal.length() >= 4 ? timeVal.substring(0, 4)
                : String.valueOf(java.time.LocalDate.now().getYear());
        return year + "-" + c2Val;
    }

    private String strVal(Map<String, Object> row, String key, String def) {
        Object v = row.get(key);
        return v != null ? v.toString() : def;
    }

    private void addLog(String level, String message) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("timestamp", LocalDateTime.now().toString());
        entry.put("level", level);
        entry.put("message", message);
        executionLogs.add(entry);
    }

    public List<String> getExecutionLogs() {
        List<String> result = new ArrayList<>();
        for (Map<String, Object> entry : executionLogs) {
            String line = entry.get("timestamp") + " [" + entry.get("level") + "] " + entry.get("message");
            result.add(line);
        }
        return result;
    }

    // ==================== 执行控制方法 ====================

    /**
     * 在行循环中检查暂停/取消状态。
     * 暂停时阻塞等待，取消时直接返回（由调用方检查 executionCancelled 标志）。
     */
    private void checkPauseAndCancel() {
        while (executionPaused && !executionCancelled) {
            synchronized (pauseLock) {
                try {
                    executionStatus = "paused";
                    pauseLock.wait(1000); // 每秒醒来检查一次
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    /** 取消当前执行 */
    public void cancel() {
        executionCancelled = true;
        executionPaused = false;
        executionStatus = "cancelled";
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
    }

    /** 暂停当前执行 */
    public void pause() {
        executionPaused = true;
        addLog("WARN", "执行已暂停，等待恢复...");
    }

    /** 恢复当前执行 */
    public void resume() {
        executionPaused = false;
        executionStatus = "running";
        synchronized (pauseLock) {
            pauseLock.notifyAll();
        }
        addLog("INFO", "执行已恢复");
    }

    /** 获取当前执行状态 */
    public Map<String, Object> getExecutionStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("status", executionStatus);
        status.put("flowConfigId", currentFlowConfigId);
        status.put("currentRow", currentRow);
        status.put("totalRows", totalRows);
        status.put("logs", getExecutionLogs());
        return status;
    }

    public void setStopOnError(boolean stopOnError) {
        this.stopOnError = stopOnError;
    }

    public boolean isStopOnError() {
        return stopOnError;
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
