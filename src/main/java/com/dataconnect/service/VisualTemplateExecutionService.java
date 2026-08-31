package com.dataconnect.service;

import com.dataconnect.component.*;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.util.SqlDialect;
import com.dataconnect.util.SqlPageWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 可视化模板执行引擎
 * 负责解析画布配置、构建执行图、执行节点
 */
@Service
public class VisualTemplateExecutionService {

    private static final Logger log = LoggerFactory.getLogger(VisualTemplateExecutionService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private VisualTemplateService visualTemplateService;

    @Autowired
    private ComponentExecutorFactory executorFactory;

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private DynamicDsManager dynamicDsManager;

    @Autowired
    private ThesisArchiveService thesisArchiveService;

    @Autowired
    private ExecutionLogFileService executionLogFileService;

    @Autowired
    private TemplateRunRegistry templateRunRegistry;

    @Value("${server.port:8010}")
    private int serverPort;

    private final java.util.concurrent.ExecutorService templateRunPool = java.util.concurrent.Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "visual-template-run");
        t.setDaemon(true);
        return t;
    });

    private static final DateTimeFormatter LOG_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    /**
     * 执行可视化模板
     * 
     * @param templateId 模板ID
     * @param input 输入数据
     * @return 执行结果
     */
    public DataPacket execute(Long templateId, DataPacket input) {
        VisualTemplate template = visualTemplateService.getById(templateId)
                .orElseThrow(() -> new RuntimeException("可视化模板不存在: " + templateId));

        return execute(template, input);
    }

    /**
     * 执行可视化模板
     * 
     * @param template 模板对象
     * @param input 输入数据
     * @return 执行结果
     */
    public DataPacket execute(VisualTemplate template, DataPacket input) {
        return executeWithRunId(template, input, java.util.UUID.randomUUID().toString());
    }

    /**
     * 后台执行模板，立即返回 runId，页面轮询日志。
     */
    public String startAsync(Long templateId, DataPacket input) {
        VisualTemplate template = visualTemplateService.getById(templateId)
                .orElseThrow(() -> new RuntimeException("可视化模板不存在: " + templateId));
        String runId = java.util.UUID.randomUUID().toString();
        templateRunRegistry.start(runId, template);
        DataPacket packet = input != null ? input : DataPacket.empty();
        templateRunPool.execute(() -> {
            try {
                executeWithRunId(template, packet, runId);
            } catch (Exception e) {
                log.error("异步执行模板失败, id={}", templateId, e);
                templateRunRegistry.fail(runId, e.getMessage());
            }
        });
        return runId;
    }

    public Map<String, Object> getLiveRun(String runId) {
        return templateRunRegistry.snapshot(runId);
    }

    private DataPacket executeWithRunId(VisualTemplate template, DataPacket input, String runId) {
        log.info("开始执行可视化模板, id={}, name={}, runId={}", template.getId(), template.getName(), runId);
        ExecutionContext context = new ExecutionContext();
        context.setExecutionId(runId);
        context.setFlowConfigId(template.getId());
        templateRunRegistry.start(runId, template);
        context.setLogListener(item -> templateRunRegistry.append(runId, item));
        return executeInternal(template, input, context, runId);
    }

    /**
     * 子模板与父运行共用日志通道，推送/下载等步骤会出现在当前执行日志里。
     */
    private DataPacket executeNested(Long templateId, DataPacket input, ExecutionContext parentContext) {
        VisualTemplate template = visualTemplateService.getById(templateId)
                .orElseThrow(() -> new RuntimeException("可视化模板不存在: " + templateId));
        String prefix = "[子模板:" + template.getName() + "] ";
        ExecutionContext child = new ExecutionContext();
        child.setExecutionId(parentContext != null ? parentContext.getExecutionId() : child.getExecutionId());
        child.setFlowConfigId(template.getId());
        if (parentContext != null && parentContext.isCancelled()) {
            child.cancel();
        }
        child.setLogListener(item -> {
            if (parentContext == null || item == null) {
                return;
            }
            String msg = item.getMessage() != null ? item.getMessage() : "";
            parentContext.log(item.getLevel() != null ? item.getLevel() : ExecutionContext.LogLevel.INFO, prefix + msg);
        });
        return executeInternal(template, input, child, null);
    }

    private DataPacket executeInternal(VisualTemplate template, DataPacket input, ExecutionContext context, String rootRunId) {
        long startMs = System.currentTimeMillis();
        context.info("开始执行模板: " + template.getName() + " (id=" + template.getId() + "), 开始时间=" + formatTime(startMs)
                + (input != null ? ", 入参行数=" + input.size() : ""));
        DataPacket result = DataPacket.error("EXECUTION_ERROR", "未开始执行");
        try {
            CanvasConfig canvasConfig = parseCanvasConfig(template.getCanvasConfig());
            if (canvasConfig != null && canvasConfig.getNodes() != null && !canvasConfig.getNodes().isEmpty()) {
                ExecutionGraph graph = buildExecutionGraph(canvasConfig);
                context.info("执行图构建完成, 节点数=" + graph.getNodes().size() + ", 边数=" + graph.getEdges().size());
                result = executeGraph(graph, input, context);
            } else if (template.getEventConfig() != null && !template.getEventConfig().isEmpty()
                    && !"{}".equals(template.getEventConfig())) {
                context.info("使用事件步骤模式执行");
                result = executeEventSteps(template, input, context);
            } else {
                context.error("画布配置和事件配置均为空");
                result = DataPacket.error("CONFIG_ERROR", "模板配置为空");
            }
        } catch (Exception e) {
            log.error("可视化模板执行失败", e);
            context.error("执行失败: " + e.getMessage());
            result = DataPacket.error("EXECUTION_ERROR", "执行失败: " + e.getMessage());
        } finally {
            long endMs = System.currentTimeMillis();
            long duration = endMs - startMs;
            boolean ok = result != null && result.isSuccess();
            context.info("模板执行" + (ok ? "完成" : "结束(失败)") + ", 结束时间=" + formatTime(endMs)
                    + ", 总耗时=" + duration + "ms, 返回行数=" + (result != null ? result.size() : 0)
                    + (ok || result == null || result.getErrorMessage() == null ? "" : ", 原因=" + result.getErrorMessage()));
            log.info("模板执行结束, id={}, name={}, success={}, rows={}, durationMs={}",
                    template.getId(), template.getName(), ok, result != null ? result.size() : 0, duration);
            try {
                persistTemplateRunLog(template, context, result, startMs, endMs);
            } catch (Exception persistEx) {
                log.error("保存模板运行日志失败, id={}", template.getId(), persistEx);
            }
            if (rootRunId != null) {
                templateRunRegistry.finish(rootRunId, result);
            }
        }
        return result;
    }

    /**
     * 解析画布配置
     */
    private CanvasConfig parseCanvasConfig(String configJson) {
        if (configJson == null || configJson.isEmpty() || "{}".equals(configJson)) {
            return null;
        }

        try {
            return objectMapper.readValue(configJson, CanvasConfig.class);
        } catch (Exception e) {
            log.error("解析画布配置失败", e);
            return null;
        }
    }

    /**
     * 构建执行图
     */
    private ExecutionGraph buildExecutionGraph(CanvasConfig canvasConfig) {
        ExecutionGraph graph = new ExecutionGraph();

        // 添加所有节点
        for (Map.Entry<String, CanvasNode> entry : canvasConfig.getNodes().entrySet()) {
            String nodeId = entry.getKey();
            CanvasNode node = entry.getValue();
            graph.addNode(nodeId, node);
        }

        // 添加所有边（连接）
        if (canvasConfig.getConnections() != null) {
            for (CanvasConnection conn : canvasConfig.getConnections()) {
                graph.addEdge(conn.getSource(), conn.getTarget());
            }
        }

        return graph;
    }

    /**
     * 执行图
     */
    private DataPacket executeGraph(ExecutionGraph graph, DataPacket input, ExecutionContext context) {
        // 拓扑排序
        List<String> executionOrder = graph.topologicalSort();
        if (executionOrder == null) {
            throw new RuntimeException("执行图存在循环依赖");
        }

        context.info("执行顺序: " + executionOrder);

        // 节点执行结果缓存
        Map<String, DataPacket> nodeResults = new LinkedHashMap<>();
        DataPacket finalResult = DataPacket.empty();

        // 按拓扑序执行节点
        for (String nodeId : executionOrder) {
            // 检查是否取消
            if (context.isCancelled()) {
                context.warn("执行已取消");
                break;
            }

            // 等待暂停恢复
            context.waitIfPaused();

            CanvasNode node = graph.getNode(nodeId);
            if (node == null) {
                continue;
            }

            context.setCurrentNodeId(nodeId);
            context.info("执行节点: " + nodeId + " (" + node.getType() + ")");

            try {
                // 收集节点输入
                DataPacket nodeInput = collectNodeInput(nodeId, graph, nodeResults, input, context);

                // 获取执行器
                ComponentExecutor executor = executorFactory.getExecutor(node.getType());

                // 执行节点
                DataPacket nodeResult = executor.execute(nodeInput, node.getConfig(), context);
                nodeResult.setSourceNodeId(nodeId);

                // 缓存结果
                nodeResults.put(nodeId, nodeResult);
                context.setNodeResult(nodeId, nodeResult);

                // 检查控制信号
                if (nodeResult.getSignal() == DataPacket.ControlSignal.STOP) {
                    context.warn("收到STOP信号，终止执行");
                    finalResult = nodeResult;
                    break;
                }

                // 如果是结果返回节点，记录最终结果
                if ("RESULT_RETURN".equals(node.getType())) {
                    finalResult = nodeResult;
                }

                context.info("节点执行完成: " + nodeId + ", 输出行数=" + nodeResult.size());

            } catch (Exception e) {
                log.error("节点执行失败: " + nodeId, e);
                context.error("节点执行失败: " + e.getMessage());
                
                // 创建错误结果
                DataPacket errorResult = DataPacket.error("NODE_ERROR", "节点 " + nodeId + " 执行失败: " + e.getMessage());
                nodeResults.put(nodeId, errorResult);
                context.setNodeResult(nodeId, errorResult);
            }
        }

        // 如果没有结果返回节点，返回最后一个节点的结果
        if (finalResult.isEmpty() && !nodeResults.isEmpty()) {
            List<DataPacket> results = new ArrayList<>(nodeResults.values());
            finalResult = results.get(results.size() - 1);
        }

        return finalResult;
    }

    /**
     * 执行事件步骤模式（内联编辑器保存的 eventConfig）
     * eventConfig 格式: {input: {inputType, ...}, steps: [{type, ...}], output: {outputTarget, ...}}
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeEventSteps(VisualTemplate template, DataPacket input, ExecutionContext context) {
        try {
            log.info("进入事件步骤执行模式, templateId={}", template.getId());
            Map<String, Object> eventConfig = objectMapper.readValue(template.getEventConfig(),
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});

            Map<String, Object> inputConfig = (Map<String, Object>) eventConfig.getOrDefault("input", new HashMap<>());
            List<Map<String, Object>> steps = (List<Map<String, Object>>) eventConfig.getOrDefault("steps", new ArrayList<>());
            Map<String, Object> outputConfig = (Map<String, Object>) eventConfig.getOrDefault("output", new HashMap<>());

            // 验证输入参数，并回填默认值到入参行（供后续 ${} 与归档步骤使用）
            String inputParamsJson = template.getInputParams();
            if (inputParamsJson != null && !inputParamsJson.isEmpty() && !"[]".equals(inputParamsJson)) {
                List<Map<String, Object>> paramDefs = objectMapper.readValue(inputParamsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
                List<Map<String, Object>> inputRows = input.getRows();
                if (inputRows == null) {
                    inputRows = new ArrayList<>();
                    input.setRows(inputRows);
                }
                Map<String, Object> inputData;
                if (inputRows.isEmpty()) {
                    inputData = new LinkedHashMap<>();
                    inputRows.add(inputData);
                } else {
                    inputData = inputRows.get(0);
                    if (inputData == null) {
                        inputData = new LinkedHashMap<>();
                        inputRows.set(0, inputData);
                    }
                }
                for (Map<String, Object> paramDef : paramDefs) {
                    String paramName = (String) paramDef.get("name");
                    if (paramName == null || paramName.isEmpty()) continue;
                    Boolean required = (Boolean) paramDef.getOrDefault("required", true);
                    Object cur = inputData.get(paramName);
                    if (cur == null || cur.toString().trim().isEmpty()) {
                        Object defVal = paramDef.get("defaultValue");
                        if (defVal != null && !defVal.toString().isEmpty()) {
                            inputData.put(paramName, defVal);
                            cur = defVal;
                        }
                    }
                    if (Boolean.TRUE.equals(required) && (cur == null || cur.toString().trim().isEmpty())) {
                        context.error("缺少必填参数: " + paramName);
                        return DataPacket.error("PARAM_MISSING", "缺少必填参数: " + paramName);
                    }
                }
            }

            context.setGlobalVariable("_templateInput", firstRow(input));
            bindWatermark(template, steps, context);

            Map<String, Object> mappingConfig = (Map<String, Object>) eventConfig.get("mapping");
            log.info("步骤数: {}, 输入数据行数: {}", steps.size(), input.size());
            context.info("步骤数: " + steps.size() + ", 输入数据行数=" + input.size());

            int dsIndex = indexOfDataSourceStep(steps);
            DataPacket result;
            if (dsIndex >= 0 && shouldBatchDataSource(steps.get(dsIndex))) {
                result = executeEventStepsBatched(template, input, context, steps, dsIndex, mappingConfig, outputConfig);
            } else {
                result = executeEventStepsLinear(template, input, context, steps, mappingConfig, outputConfig);
            }
            if (result != null && result.isSuccess()) {
                persistWatermark(template, context, dsIndex >= 0 ? steps.get(dsIndex) : null);
            }
            return result;

        } catch (Exception e) {
            log.error("执行事件步骤失败", e);
            return DataPacket.error("STEP_ERROR", "执行事件步骤失败: " + e.getMessage());
        }
    }

    private DataPacket executeEventStepsLinear(VisualTemplate template, DataPacket input, ExecutionContext context,
            List<Map<String, Object>> steps, Map<String, Object> mappingConfig, Map<String, Object> outputConfig) {
        DataPacket currentData = runStepRange(steps, 0, steps.size(), input, input, context);
        if (currentData != null && !currentData.isSuccess()) {
            return currentData;
        }
        return applyMappingAndOutput(template, currentData, mappingConfig, outputConfig, context, true);
    }

    private DataPacket executeEventStepsBatched(VisualTemplate template, DataPacket input, ExecutionContext context,
            List<Map<String, Object>> steps, int dsIndex, Map<String, Object> mappingConfig,
            Map<String, Object> outputConfig) {
        Map<String, Object> dsStep = steps.get(dsIndex);
        int batchSize = getIntFromConfig(dsStep, "batchSize", 100);
        int maxBatches = getIntFromConfig(dsStep, "maxBatches", 0);
        String batchPerRun = getRawString(dsStep, "batchPerRun", "ALL").toUpperCase();
        boolean onePerRun = "ONE".equals(batchPerRun);
        if (onePerRun) {
            maxBatches = 1;
        }
        String outputMode = resolveOutputMode(outputConfig);

        DataPacket prefix = runStepRange(steps, 0, dsIndex, input, input, context);
        if (prefix != null && !prefix.isSuccess()) {
            return prefix;
        }

        List<Map<String, Object>> allRows = new ArrayList<>();
        DataPacket last = DataPacket.ofList(new ArrayList<Map<String, Object>>());
        int offset = 0;
        if (onePerRun && !isIncremental(dsStep)) {
            offset = intFromObject(context.getGlobalVariable("_watermarkLastOffset"), 0);
        }
        int batchNo = 0;
        String lastFingerprint = null;
        boolean apiSource = isApiDataSourceStep(dsStep);
        context.info("数据源分批执行: 每批=" + batchSize + " 条, 最多批次=" + (maxBatches > 0 ? maxBatches : "不限")
                + (onePerRun ? ", 本次只跑一批(定时续跑)" : ", 本轮拉完")
                + (apiSource ? ", 接口分页" : ", 数据库分页")
                + ", 起始offset=" + offset);

        while (true) {
            if (context.isCancelled()) {
                context.warn("执行已取消");
                break;
            }
            batchNo++;
            if (maxBatches > 0 && batchNo > maxBatches) {
                context.info("已达到最多批次 " + maxBatches + "，停止循环");
                break;
            }
            context.info("第 " + batchNo + " 批开始, offset=" + offset);
            DataPacket page = executeDataSourceStep(dsStep, input, context, offset, batchSize);
            if (page == null || !page.isSuccess()) {
                return page != null ? page : DataPacket.error("QUERY_ERROR", "分批查询失败");
            }
            int pageRows = page.size();
            int rawCount = page.getVariables() != null
                    ? intFromObject(page.getVariables().get("_rawRowCount"), pageRows) : pageRows;
            if (rawCount == 0) {
                context.info("第 " + batchNo + " 批无数据，结束循环");
                context.setGlobalVariable("_nextOffset", 0);
                break;
            }
            if (pageRows == 0) {
                context.info("第 " + batchNo + " 批原始 " + rawCount + " 条均被增量过滤，跳过");
                if (rawCount < batchSize) {
                    context.setGlobalVariable("_nextOffset", 0);
                    break;
                }
                offset += batchSize;
                context.setGlobalVariable("_nextOffset", offset);
                continue;
            }
            if (apiSource) {
                String fp = fingerprintRows(page);
                if (fp.equals(lastFingerprint)) {
                    context.warn("接口本批数据与上批相同，停止循环（请检查分页参数名是否与接口一致）");
                    break;
                }
                lastFingerprint = fp;
            }
            DataPacket batchResult = runStepRange(steps, dsIndex + 1, steps.size(), page, input, context);
            if (batchResult != null && !batchResult.isSuccess()
                    && batchResult.getErrorCode() != null) {
                return batchResult;
            }
            batchResult = applyMappingAndOutput(template, batchResult, mappingConfig, outputConfig, context, false);
            if (batchResult != null && batchResult.getRows() != null) {
                allRows.addAll(batchResult.getRows());
            }
            last = batchResult;
            bumpWatermarkFromRows(context, dsStep, page.getRows());
            context.info("第 " + batchNo + " 批结束, 本批=" + pageRows + " 条, 累计处理=" + allRows.size());
            if (pageRows < batchSize) {
                context.info("本批不足 " + batchSize + " 条，已到末页");
                context.setGlobalVariable("_nextOffset", 0);
                break;
            }
            offset += batchSize;
            context.setGlobalVariable("_nextOffset", offset);
        }

        DataPacket combined = DataPacket.ofList(allRows);
        if (last != null && last.getVariables() != null) {
            if (combined.getVariables() == null) {
                combined.setVariables(new LinkedHashMap<String, Object>());
            }
            combined.getVariables().putAll(last.getVariables());
        }
        combined.getVariables().put("_batchCount", batchNo);
        combined.getVariables().put("_totalProcessed", allRows.size());
        if ("FILE".equals(outputMode) && !allRows.isEmpty()) {
            return executeOutputToFile(outputConfig, combined, context);
        }
        if ("RETURN".equals(outputMode) || outputMode == null || outputMode.isEmpty()) {
            return buildReturnResult(combined, template.getOutputParams());
        }
        return combined;
    }

    private DataPacket runStepRange(List<Map<String, Object>> steps, int from, int to,
            DataPacket currentData, DataPacket input, ExecutionContext context) {
        DataPacket current = currentData;
        for (int i = from; i < to; i++) {
            Map<String, Object> step = steps.get(i);
            String stepType = (String) step.get("type");
            String label = stepLabel(stepType);
            int stepIndex = i + 1;
            context.info("开始步骤 " + stepIndex + " [" + label + "]");
            long stepStart = System.currentTimeMillis();
            log.info("执行步骤: type={}", stepType);
            current = executeConfiguredStep(step, current, input, context);
            long stepMs = System.currentTimeMillis() - stepStart;
            boolean stepOk = current != null && current.isSuccess();
            context.info("步骤结束 " + stepIndex + " [" + label + "], 成功=" + stepOk
                    + ", 行数=" + (current != null ? current.size() : 0)
                    + ", 耗时=" + stepMs + "ms");
            if ("CALL_TEMPLATE".equals(stepType) && current != null && !current.isSuccess()) {
                return current;
            }
        }
        return current;
    }

    private DataPacket executeConfiguredStep(Map<String, Object> step, DataPacket currentData,
            DataPacket input, ExecutionContext context) {
        String stepType = (String) step.get("type");
        if ("DATA_SOURCE".equals(stepType)) {
            return executeDataSourceStep(step, input, context);
        } else if ("MAPPING".equals(stepType)) {
            return executeMappingStep(step, currentData, context);
        } else if ("FILTER".equals(stepType)) {
            return executeFilterStep(step, currentData, context);
        } else if ("CALL_TEMPLATE".equals(stepType)) {
            return executeCallTemplateStep(step, currentData, context);
        } else if ("OPERATION".equals(stepType)) {
            return executeOperationStep(step, currentData, context);
        } else if ("EVENT".equals(stepType)) {
            return executeEventStep(step, currentData, context);
        } else if ("THESIS_ARCHIVE".equals(stepType)) {
            return executeThesisArchiveStep(step, currentData, input, context);
        } else if ("FILE_DOWNLOAD".equals(stepType)) {
            return executeFileDownloadStep(step, currentData, context);
        }
        context.warn("未知步骤类型: " + stepType);
        return currentData;
    }

    private DataPacket applyMappingAndOutput(VisualTemplate template, DataPacket currentData,
            Map<String, Object> mappingConfig, Map<String, Object> outputConfig, ExecutionContext context,
            boolean finalizeOutput) {
        if (mappingConfig != null) {
            log.info("执行固定映射步骤: mappingConfig={}", mappingConfig);
            context.info("开始固定映射步骤");
            currentData = executeMappingStep(mappingConfig, currentData, context);
        }
        String outputMode = resolveOutputMode(outputConfig);
        log.info("输出模式: {}, 当前数据行数: {}", outputMode, currentData != null ? currentData.size() : 0);
        context.info("输出模式: " + outputMode + ", 当前行数=" + (currentData != null ? currentData.size() : 0));
        if ("CALL_TEMPLATE".equals(outputMode)) {
            return executeOutputViaTemplate(outputConfig, currentData, context);
        }
        if (!finalizeOutput) {
            return currentData;
        }
        if ("FILE".equals(outputMode)) {
            return executeOutputToFile(outputConfig, currentData, context);
        }
        return buildReturnResult(currentData, template.getOutputParams());
    }

    private String resolveOutputMode(Map<String, Object> outputConfig) {
        if (outputConfig == null) {
            return "RETURN";
        }
        String outputMode = (String) outputConfig.getOrDefault("outputMode", "RETURN");
        if (outputMode == null || outputMode.isEmpty()) {
            outputMode = (String) outputConfig.getOrDefault("outputTarget", "RETURN");
            if ("DATABASE".equals(outputMode) || "API".equals(outputMode)) {
                outputMode = "CALL_TEMPLATE";
            } else if ("EMAIL".equals(outputMode)) {
                outputMode = "RETURN";
            }
        }
        return outputMode;
    }

    private int indexOfDataSourceStep(List<Map<String, Object>> steps) {
        if (steps == null) {
            return -1;
        }
        for (int i = 0; i < steps.size(); i++) {
            if ("DATA_SOURCE".equals(steps.get(i).get("type"))) {
                return i;
            }
        }
        return -1;
    }

    private boolean shouldBatchDataSource(Map<String, Object> step) {
        int batchSize = getIntFromConfig(step, "batchSize", 100);
        return batchSize > 0;
    }

    private boolean isApiDataSourceStep(Map<String, Object> step) {
        if (step == null) {
            return false;
        }
        String apiUrl = (String) step.get("apiUrl");
        return apiUrl != null && !apiUrl.trim().isEmpty();
    }

    private static String fingerprintRows(DataPacket page) {
        if (page == null || page.getRows() == null || page.getRows().isEmpty()) {
            return "empty";
        }
        Map<String, Object> first = page.getRows().get(0);
        Map<String, Object> last = page.getRows().get(page.getRows().size() - 1);
        return page.size() + "|" + String.valueOf(first) + "|" + String.valueOf(last);
    }

    /**
     * 执行数据源步骤 - 实际执行数据库查询
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeDataSourceStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        return executeDataSourceStep(step, input, context, 0, 0);
    }

    @SuppressWarnings("unchecked")
    private DataPacket executeDataSourceStep(Map<String, Object> step, DataPacket input, ExecutionContext context,
            int offset, int pageSize) {
        try {
            Object dsIdObj = step.get("dsId");
            Long dsId = 0L;
            if (dsIdObj instanceof Number) {
                dsId = ((Number) dsIdObj).longValue();
            } else if (dsIdObj instanceof String) {
                try { dsId = Long.parseLong((String) dsIdObj); } catch (NumberFormatException e) { /* keep 0 */ }
            }

            // 检测API数据源（有apiUrl就走HTTP，不管dsId）
            String apiUrl = (String) step.get("apiUrl");
            if (apiUrl != null && !apiUrl.isEmpty()) {
                DsConfig dsConfig = dsId > 0 ? dataSourceService.getById(dsId).orElse(null) : null;
                return executeApiDataSourceStep(step, input, context, dsConfig, offset, pageSize);
            }

            String sql = (String) step.getOrDefault("sql", "");
            log.info("数据源步骤: dsId={}, sql={}", dsId, sql);

            if (dsId == 0 || sql.isEmpty()) {
                context.error("数据源步骤配置不完整: dsId=" + dsId);
                return DataPacket.error("CONFIG_ERROR", "数据源步骤配置不完整");
            }

            // 获取数据源配置
            final Long finalDsId = dsId;
            DsConfig dsConfig = dataSourceService.getById(dsId)
                    .orElseThrow(() -> new RuntimeException("数据源不存在: " + finalDsId));

            boolean customPage = hasPagingPlaceholder(sql);
            Map<String, String> pageVars = buildPagingVars(step, offset, pageSize);
            putWatermarkVars(pageVars, context);
            String finalSql = applyPagingPlaceholders(sql, pageVars);
            Map<String, Object> inputRow = input.getRows() != null && !input.getRows().isEmpty()
                    ? input.getRows().get(0) : new HashMap<>();
            finalSql = SqlDialect.substitutePlaceholders(finalSql, inputRow, false);
            finalSql = finalSql.replaceAll(";+\\s*$", "").trim();
            finalSql = applyIncrementalWhere(finalSql, step, context, dsConfig.getDbType());
            if (finalSql.contains("${")) {
                finalSql = SqlDialect.substitutePlaceholders(finalSql, Collections.emptyMap(), true);
                context.warn("SQL 中仍有未替换的参数，已置为 NULL");
            }
            if (pageSize > 0 && !customPage) {
                if (sqlHasOffsetOrFetch(finalSql)) {
                    context.warn("SQL 已含 OFFSET/FETCH，无法自动分批，将按原SQL执行");
                } else {
                    finalSql = applyPageSql(finalSql, dsConfig.getDbType(), offset, pageSize);
                }
            }

            context.info("执行SQL: " + finalSql);

            // 执行查询
            DataSource ds = dynamicDsManager.getOrCreate(dsConfig);
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Connection conn = ds.getConnection();
                 Statement stmt = conn.createStatement()) {
                if (pageSize > 0) {
                    stmt.setFetchSize(pageSize);
                    stmt.setMaxRows(pageSize);
                }
                try (ResultSet rs = stmt.executeQuery(finalSql)) {
                    ResultSetMetaData meta = rs.getMetaData();
                    int columnCount = meta.getColumnCount();
                    while (rs.next()) {
                        Map<String, Object> row = new LinkedHashMap<>();
                        for (int i = 1; i <= columnCount; i++) {
                            String label = meta.getColumnLabel(i);
                            if (label != null && "_ds_rn".equalsIgnoreCase(label)) {
                                continue;
                            }
                            row.put(label, rs.getObject(i));
                        }
                        rows.add(row);
                    }
                }
            }

            context.info("查询返回 " + rows.size() + " 条记录");
            log.info("数据源查询返回 {} 条记录, SQL: {}", rows.size(), finalSql);
            return afterFetch(step, context, rows);

        } catch (Exception e) {
            log.error("数据源查询失败", e);
            context.error("数据源查询失败: " + e.getMessage());
            return DataPacket.error("QUERY_ERROR", "数据源查询失败: " + e.getMessage());
        }
    }

    /**
     * 页面「测试请求」：与正式执行同一套 token + 拉数逻辑。
     */
    public DataPacket previewApiDataSource(Map<String, Object> step, DataPacket input) {
        ExecutionContext context = new ExecutionContext();
        DataPacket in = input != null ? input : DataPacket.empty();
        return executeApiDataSourceStep(step, in, context, null, 0, 0);
    }

    /**
     * 执行API数据源步骤 - 可先取 token，再带 access_token 发起HTTP请求
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeApiDataSourceStep(Map<String, Object> step, DataPacket input,
                                                  ExecutionContext context, DsConfig dsConfig,
                                                  int offset, int pageSize) {
        try {
            String apiUrl = (String) step.get("apiUrl");
            if ((apiUrl == null || apiUrl.isEmpty()) && dsConfig != null) {
                apiUrl = dsConfig.getApiUrl();
            }
            String apiMethod = (String) step.get("apiMethod");
            if ((apiMethod == null || apiMethod.isEmpty()) && dsConfig != null) {
                apiMethod = dsConfig.getApiMethod();
            }
            String apiHeaders = (String) step.get("apiHeaders");
            if ((apiHeaders == null || apiHeaders.isEmpty()) && dsConfig != null) {
                apiHeaders = dsConfig.getApiHeaders();
            }
            String apiBody = (String) step.get("apiBody");
            if ((apiBody == null || apiBody.isEmpty()) && dsConfig != null) {
                apiBody = dsConfig.getApiBody();
            }
            int timeout = resolveApiTimeout(step, dsConfig);
            int retryTimes = resolveApiRetryTimes(step, dsConfig);
            int retryInterval = resolveApiRetryInterval(step, dsConfig);

            Map<String, String> pageVars = buildPagingVars(step, offset, pageSize);
            putWatermarkVars(pageVars, context);
            boolean urlHasPlace = containsPagingPlaceholder(apiUrl, pageVars);
            boolean bodyHasPlace = containsPagingPlaceholder(apiBody, pageVars);
            apiUrl = applyPagingPlaceholders(apiUrl, pageVars);
            apiBody = applyPagingPlaceholders(apiBody, pageVars);
            apiHeaders = applyPagingPlaceholders(apiHeaders, pageVars);

            String tokenUrl = applyPagingPlaceholders(getRawString(step, "tokenUrl", ""), pageVars);
            tokenUrl = applyInputPlaceholders(tokenUrl, input, false);
            tokenUrl = toAbsoluteLocalUrl(tokenUrl);
            DataPacket apiInput = input;
            if (tokenUrl != null && !tokenUrl.isEmpty()) {
                String accessToken = fetchAccessToken(step, tokenUrl, input, context, timeout, retryTimes, retryInterval);
                if (accessToken == null || accessToken.isEmpty()) {
                    return DataPacket.error("TOKEN_ERROR", "获取 access_token 失败");
                }
                Map<String, Object> merged = new LinkedHashMap<>(firstRow(input));
                merged.put("access_token", accessToken);
                apiInput = DataPacket.of(merged);
                context.info("已获取 access_token, 长度=" + accessToken.length());
            }

            apiUrl = applyInputPlaceholders(apiUrl, apiInput, false);
            apiBody = applyInputPlaceholders(apiBody, apiInput, false);
            apiHeaders = applyInputPlaceholders(apiHeaders, apiInput, false);
            apiUrl = toAbsoluteLocalUrl(apiUrl);

            Object tokenObj = firstRow(apiInput).get("access_token");
            if (tokenObj != null) {
                String queryParam = getRawString(step, "tokenQueryParam", "access_token");
                apiUrl = ensureQueryParam(apiUrl, queryParam, String.valueOf(tokenObj));
            }

            if (pageSize > 0 && !urlHasPlace) {
                apiUrl = appendPagingQuery(apiUrl, step, pageVars);
            }
            if (pageSize > 0 && !bodyHasPlace
                    && ("POST".equalsIgnoreCase(apiMethod) || "PUT".equalsIgnoreCase(apiMethod))) {
                apiBody = mergePagingJson(apiBody, step, pageVars);
            }
            apiUrl = appendIncrementalQuery(apiUrl, step, context);
            apiBody = mergeIncrementalJson(apiBody, step, context, apiMethod);

            if (apiUrl == null || apiUrl.isEmpty()) {
                return DataPacket.error("CONFIG_ERROR", "API数据源未配置URL");
            }

            context.info("API数据源请求: " + apiMethod + " " + maskAccessToken(apiUrl)
                    + ", 超时=" + timeout + "s, 重试=" + retryTimes + "次");

            HttpCallResult call = doHttpWithRetry(apiMethod, apiUrl, apiHeaders, apiBody,
                    timeout, retryTimes, retryInterval, context, "数据接口");
            context.info("API响应: code=" + call.statusCode + ", len=" + (call.body != null ? call.body.length() : 0));

            if (call.parsed instanceof Map) {
                Map<String, Object> parsedMap = (Map<String, Object>) call.parsed;
                if (!isBizOk(parsedMap.get("code"))) {
                    return DataPacket.error("API_ERROR", "接口业务失败: code=" + parsedMap.get("code")
                            + ", message=" + parsedMap.get("message"));
                }
            }

            List<Map<String, Object>> rows = new ArrayList<>();
            try {
                if (call.parsed != null) {
                    String listPath = getRawString(step, "apiListPath", "");
                    rows = extractApiRows(call.parsed, listPath);
                } else {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("statusCode", call.statusCode);
                    row.put("responseBody", call.body);
                    rows.add(row);
                }
            } catch (Exception e) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("statusCode", call.statusCode);
                row.put("responseBody", call.body);
                rows.add(row);
            }
            context.info("API解析记录数: " + rows.size());

            return afterFetch(step, context, rows);

        } catch (Exception e) {
            log.error("API数据源请求失败", e);
            return DataPacket.error("API_ERROR", "API请求失败: " + e.getMessage());
        }
    }

    /**
     * 执行映射步骤
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeMappingStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        try {
            Object mappingsObj = step.get("mappings");
            if (mappingsObj == null) return input;

            List<Map<String, String>> mappings;
            if (mappingsObj instanceof String) {
                String mappingsJson = (String) mappingsObj;
                if (mappingsJson.isEmpty()) return input;
                mappings = objectMapper.readValue(mappingsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, String>>>() {});
            } else if (mappingsObj instanceof List) {
                // Jackson已解析为List，直接转换
                mappings = new ArrayList<>();
                for (Object item : (List<?>) mappingsObj) {
                    if (item instanceof Map) {
                        Map<String, String> mapping = new HashMap<>();
                        for (Map.Entry<?, ?> entry : ((Map<?, ?>) item).entrySet()) {
                            mapping.put(String.valueOf(entry.getKey()),
                                    entry.getValue() != null ? String.valueOf(entry.getValue()) : null);
                        }
                        mappings.add(mapping);
                    }
                }
            } else {
                return input;
            }

            if (mappings.isEmpty()) {
                context.warn("映射步骤: 规则为空，原样通过, 行数=" + input.size());
                return input;
            }

            Set<String> dstFields = new LinkedHashSet<>();
            for (Map<String, String> mapping : mappings) {
                String dst = mapping.get("dst");
                if (dst != null && !dst.trim().isEmpty()) {
                    dstFields.add(dst.trim());
                }
            }
            context.info("映射: " + mappings.size() + " 条规则, 目标字段=" + dstFields + ", 输入行数=" + input.size());

            List<Map<String, Object>> resultRows = new ArrayList<>();
            for (Map<String, Object> row : input.getRows()) {
                Map<String, Object> newRow = new LinkedHashMap<>();
                for (Map<String, String> mapping : mappings) {
                    String src = mapping.get("src");
                    String dst = mapping.get("dst");
                    if (dst == null || dst.trim().isEmpty()) continue;
                    String transform = mapping.get("transform");
                    boolean autoInc = isAutoIncrementTransform(transform);
                    if (!autoInc && (src == null || src.trim().isEmpty())) continue;
                    Object value = autoInc ? null : resolveMappingSrcValue(row, src, mapping);
                    if (transform != null && !transform.trim().isEmpty()) {
                        value = applyMappingTransform(value, transform.trim(), dst, context);
                    }
                    newRow.put(dst, value);
                }
                newRow.put("_mappingDstFields", dstFields);
                resultRows.add(newRow);
            }
            context.info("映射完成: 输出 " + resultRows.size() + " 行");
            return DataPacket.ofList(resultRows);
        } catch (Exception e) {
            context.error("映射步骤失败: " + e.getMessage());
            return input;
        }
    }

    /**
     * 源字段取值：行里有该列就取数据源的值；没有该列才当固定值（如 1、2026）。
     * 即使误标了 literal，只要列存在也优先用数据，避免 XML 里出现 name、id 这种字段名。
     */
    private Object resolveMappingSrcValue(Map<String, Object> row, String src, Map<String, String> mapping) {
        if (src == null) {
            return null;
        }
        src = src.trim();
        if (row != null) {
            if (row.containsKey(src)) {
                return row.get(src);
            }
            for (Map.Entry<String, Object> e : row.entrySet()) {
                if (e.getKey() != null && e.getKey().equalsIgnoreCase(src)) {
                    return e.getValue();
                }
            }
            if (src.contains(".")) {
                Object nested = getNestedValue(row, src);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return src;
    }

    /**
     * 映射行转换：UPPER/LOWER/TRIM，或事件管理中的事件编码。
     */
    private Object applyMappingTransform(Object value, String transform, String fieldName,
            ExecutionContext context) {
        if (transform == null || transform.trim().isEmpty()) {
            return value;
        }
        transform = transform.trim();
        if (isAutoIncrementTransform(transform)) {
            try {
                ComponentExecutor executor = executorFactory.getExecutor("EVENT");
                Map<String, Object> row = new LinkedHashMap<>();
                Map<String, Object> params = new LinkedHashMap<>();
                params.put("targetField", fieldName);
                int pad = padLengthFromTransform(transform);
                if (pad > 0) {
                    params.put("padLength", pad);
                }
                Map<String, Object> config = new LinkedHashMap<>();
                config.put("eventCode", "AUTO_INCREMENT");
                config.put("params", params);
                DataPacket out = executor.execute(DataPacket.of(row), config, context);
                if (out != null && out.isSuccess() && out.getRows() != null && !out.getRows().isEmpty()) {
                    Object seq = out.getRows().get(0).get(fieldName);
                    return seq != null ? seq : value;
                }
            } catch (Exception e) {
                context.warn("映射自增失败: " + e.getMessage());
            }
            return value;
        }
        if (isPadLeftTransform(transform)) {
            if (value == null) {
                return null;
            }
            int pad = padLengthFromTransform(transform);
            if (pad <= 0) {
                pad = 4;
            }
            return com.dataconnect.component.impl.EventStepExecutor.padLeft(value.toString(), pad);
        }
        if (value == null) {
            return null;
        }
        if ("UPPER".equalsIgnoreCase(transform)) {
            return value.toString().toUpperCase();
        }
        if ("LOWER".equalsIgnoreCase(transform)) {
            return value.toString().toLowerCase();
        }
        if ("TRIM".equalsIgnoreCase(transform)) {
            return value.toString().trim();
        }
        if ("PINYIN_INITIAL".equalsIgnoreCase(transform)) {
            return com.dataconnect.util.PinyinInitials.from(value);
        }
        try {
            ComponentExecutor executor = executorFactory.getExecutor("EVENT");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(fieldName, value);
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("sourceField", fieldName);
            params.put("targetField", fieldName);
            Map<String, Object> config = new LinkedHashMap<>();
            config.put("eventCode", transform);
            config.put("params", params);
            DataPacket out = executor.execute(DataPacket.of(row), config, context);
            if (out != null && out.isSuccess() && out.getRows() != null && !out.getRows().isEmpty()) {
                Object transformed = out.getRows().get(0).get(fieldName);
                return transformed != null ? transformed : value;
            }
        } catch (Exception e) {
            context.warn("映射转换事件失败: " + transform + ", " + e.getMessage());
        }
        return value;
    }

    private static boolean isAutoIncrementTransform(String transform) {
        if (transform == null || transform.trim().isEmpty()) {
            return false;
        }
        String t = transform.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if ("AUTO_INCREMENT".equals(u) || "SEQ_PAD4".equals(u) || "PAD4_SEQ".equals(u)) {
            return true;
        }
        return u.startsWith("AUTO_INCREMENT:") || u.matches("SEQ_PAD\\d+");
    }

    private static boolean isPadLeftTransform(String transform) {
        if (transform == null || transform.trim().isEmpty()) {
            return false;
        }
        String u = transform.trim().toUpperCase(Locale.ROOT);
        return "PAD_LEFT".equals(u) || "PAD4".equals(u) || u.startsWith("PAD_LEFT:");
    }

    private static int padLengthFromTransform(String transform) {
        if (transform == null) {
            return 0;
        }
        String t = transform.trim();
        String u = t.toUpperCase(Locale.ROOT);
        if ("SEQ_PAD4".equals(u) || "PAD4".equals(u) || "PAD_LEFT".equals(u)) {
            return 4;
        }
        int colon = u.indexOf(':');
        if (colon > 0 && (u.startsWith("AUTO_INCREMENT:") || u.startsWith("PAD_LEFT:"))) {
            try {
                return Integer.parseInt(t.substring(colon + 1).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        if (u.startsWith("SEQ_PAD") && u.length() > 7) {
            try {
                return Integer.parseInt(t.substring(7).trim());
            } catch (NumberFormatException e) {
                return 0;
            }
        }
        return 0;
    }

    /**
     * 从嵌套Map中按点号路径取值，如 "address.city" → row.get("address").get("city")
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> row, String path) {
        if (path == null || !path.contains(".")) {
            return row.get(path);
        }
        String[] keys = path.split("\\.");
        Object current = row;
        for (String key : keys) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(key);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * 执行过滤步骤
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeFilterStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        String field = (String) step.get("filterField");
        String operator = (String) step.get("filterOperator");
        String value = (String) step.get("filterValue");

        if (field == null || operator == null) {
            context.warn("过滤步骤: 未配置字段或运算符，原样通过");
            return input;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : input.getRows()) {
            Object cellValue = row.get(field);
            if (matchesFilter(cellValue, operator, value)) {
                filtered.add(row);
            }
        }
        context.info("过滤: " + field + " " + operator + " " + (value != null ? value : "")
                + ", " + input.size() + " -> " + filtered.size());
        return DataPacket.ofList(filtered);
    }

    private boolean matchesFilter(Object cellValue, String operator, String compareValue) {
        String cellStr = cellValue != null ? cellValue.toString() : null;
        switch (operator) {
            case "==": return cellStr != null && cellStr.equals(compareValue);
            case "!=": return cellStr == null || !cellStr.equals(compareValue);
            case ">": return cellStr != null && compareValue != null && cellStr.compareTo(compareValue) > 0;
            case "<": return cellStr != null && compareValue != null && cellStr.compareTo(compareValue) < 0;
            case ">=": return cellStr != null && compareValue != null && cellStr.compareTo(compareValue) >= 0;
            case "<=": return cellStr != null && compareValue != null && cellStr.compareTo(compareValue) <= 0;
            case "LIKE": return cellStr != null && compareValue != null && cellStr.contains(compareValue.replace("%", ""));
            case "IS NULL": return cellStr == null;
            case "IS NOT NULL": return cellStr != null;
            default: return true;
        }
    }

    /**
     * 执行调用模板步骤。callParams 会按行合并进子模板入参，值支持 ${字段名}。
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeCallTemplateStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        try {
            Object idObj = step.get("callTemplateId");
            if (idObj == null || idObj.toString().trim().isEmpty() || "0".equals(idObj.toString().trim())) {
                return DataPacket.error("CONFIG_ERROR", "调用模板未选择目标模板");
            }
            Long callTemplateId = Long.valueOf(idObj.toString().trim());
            Map<String, Object> callParams = parseCallParams(step.get("callParams"));
            DataPacket childInput = applyCallParams(input, callParams, context);
            VisualTemplate target = visualTemplateService.getById(callTemplateId).orElse(null);
            String targetName = target != null ? target.getName() : String.valueOf(callTemplateId);
            context.info("调用子模板: " + targetName + " (id=" + callTemplateId + "), 传递参数=" + callParams
                    + ", 行数=" + (childInput != null ? childInput.size() : 0));
            return executeNested(callTemplateId, childInput, context);
        } catch (Exception e) {
            context.error("调用子模板失败: " + e.getMessage());
            return DataPacket.error("CALL_ERROR", "调用子模板失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseCallParams(Object raw) {
        if (raw == null) {
            return Collections.emptyMap();
        }
        if (raw instanceof Map) {
            return (Map<String, Object>) raw;
        }
        String s = raw.toString().trim();
        if (s.isEmpty() || "{}".equals(s)) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(s, new TypeReference<Map<String, Object>>() {});
            return parsed != null ? parsed : Collections.emptyMap();
        } catch (Exception e) {
            log.warn("解析 callParams 失败: {}", s);
            return Collections.emptyMap();
        }
    }

    private DataPacket applyCallParams(DataPacket input, Map<String, Object> callParams, ExecutionContext context) {
        if (callParams == null || callParams.isEmpty()) {
            return input;
        }
        DataPacket copy = input != null ? input.copy() : DataPacket.empty();
        List<Map<String, Object>> rows = copy.getRows();
        if (rows == null || rows.isEmpty()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : callParams.entrySet()) {
                row.put(e.getKey(), resolveCallParamValue(e.getValue(), Collections.emptyMap(), context));
            }
            copy.setRows(new ArrayList<>(Collections.singletonList(row)));
            return copy;
        }
        List<Map<String, Object>> mergedRows = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> merged = row != null ? new LinkedHashMap<>(row) : new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : callParams.entrySet()) {
                merged.put(e.getKey(), resolveCallParamValue(e.getValue(), merged, context));
            }
            mergedRows.add(merged);
        }
        copy.setRows(mergedRows);
        return copy;
    }

    @SuppressWarnings("unchecked")
    private Object resolveCallParamValue(Object value, Map<String, Object> row, ExecutionContext context) {
        if (value == null) {
            return null;
        }
        String s = value.toString();
        if (!s.contains("${")) {
            return value;
        }
        if (row != null) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String placeholder = "${" + e.getKey() + "}";
                if (s.contains(placeholder)) {
                    s = s.replace(placeholder, e.getValue() != null ? e.getValue().toString() : "");
                }
            }
        }
        Object templateInput = context != null ? context.getGlobalVariable("_templateInput") : null;
        if (templateInput instanceof Map) {
            Map<String, Object> inputRow = (Map<String, Object>) templateInput;
            for (Map.Entry<String, Object> e : inputRow.entrySet()) {
                String placeholder = "${" + e.getKey() + "}";
                if (s.contains(placeholder)) {
                    s = s.replace(placeholder, e.getValue() != null ? e.getValue().toString() : "");
                }
            }
        }
        return s;
    }

    /**
     * 构建返回结果（根据输出参数定义提取字段）
     */
    @SuppressWarnings("unchecked")
    private DataPacket buildReturnResult(DataPacket data, String outputParamsJson) {
        if (data == null) {
            return DataPacket.empty();
        }
        if (outputParamsJson == null || outputParamsJson.isEmpty() || "[]".equals(outputParamsJson)) {
            return data;
        }

        try {
            List<Map<String, Object>> paramDefs = objectMapper.readValue(outputParamsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});
            if (paramDefs.isEmpty()) return data;

            List<Map<String, Object>> srcRows = data.getRows();
            if (srcRows == null) {
                return data;
            }
            List<Map<String, Object>> resultRows = new ArrayList<>();
            for (Map<String, Object> row : srcRows) {
                Map<String, Object> resultRow = new LinkedHashMap<>();
                for (Map<String, Object> paramDef : paramDefs) {
                    String name = (String) paramDef.get("name");
                    String sourceField = (String) paramDef.get("sourceField");
                    if (name != null) {
                        resultRow.put(name, sourceField != null ? row.get(sourceField) : null);
                    }
                }
                resultRows.add(resultRow);
            }
            return DataPacket.ofList(resultRows);
        } catch (Exception e) {
            log.warn("解析输出参数失败，返回原始数据", e);
            return data;
        }
    }

    /**
     * 写死的论文归档步骤：ZIP 打包 + 取 Token + 推 file2Archives。
     * Token 参数（apiUrl/appkey/password/ccode）从步骤配置或模板入参解析，不读数据库。
     */
    private DataPacket executeThesisArchiveStep(Map<String, Object> step, DataPacket currentData,
            DataPacket originalInput, ExecutionContext context) {
        Map<String, Object> inputRow = firstRow(originalInput);
        String apiUrl = resolveArchiveParam(step, inputRow, "apiUrl", "apiUrl", null);
        if (apiUrl == null || apiUrl.isEmpty()) {
            apiUrl = resolveArchiveParam(step, inputRow, "apiUrl", "archiveUrl", null);
        }
        String appkey = resolveArchiveParam(step, inputRow, "appkey", "appkey", null);
        String password = resolveArchiveParam(step, inputRow, "password", "password", null);
        String ccode = resolveArchiveParam(step, inputRow, "ccode", "ccode", "lwdj");

        if (apiUrl == null || apiUrl.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "缺少档案接口 URL，请在模板入参或步骤中提供 apiUrl");
        }
        if (appkey == null || appkey.isEmpty() || password == null || password.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "缺少 Token 参数，请在模板入参中提供 appkey、password");
        }

        List<Map<String, Object>> rows = currentData.getRows();
        if (rows == null || rows.isEmpty()) {
            context.warn("论文归档: 无数据行，跳过");
            return currentData;
        }

        Map<String, String> archiveConfig = new LinkedHashMap<>();
        archiveConfig.put("appkey", appkey);
        archiveConfig.put("password", password);
        archiveConfig.put("ccode", ccode != null && !ccode.isEmpty() ? ccode : "lwdj");

        context.info("论文归档推送: url=" + apiUrl + ", ccode=" + archiveConfig.get("ccode") + ", 行数=" + rows.size());
        Map<String, Integer> entityCaseCounters = new LinkedHashMap<>();
        List<Map<String, Object>> resultRows = new ArrayList<>();
        int rowNum = 0;
        int pushOk = 0;
        int pushFail = 0;

        for (Map<String, Object> row : rows) {
            Map<String, Object> resultRow = new LinkedHashMap<>(row);
            rowNum++;
            try {
                String entityClassNum = computeEntityClassNum(resultRow);
                int caseNum = entityCaseCounters.getOrDefault(entityClassNum, 0) + 1;
                entityCaseCounters.put(entityClassNum, caseNum);
                resultRow.put("案卷号", String.valueOf(caseNum));

                Object fid = resultRow.get("标识");
                Object enrichFailed = resultRow.get("_enrichmentFailed");
                if (enrichFailed != null && !"false".equals(String.valueOf(enrichFailed))) {
                    pushFail++;
                    context.warn("[" + rowNum + "/" + rows.size() + "] 数据增强失败，跳过归档, 标识: " + fid);
                    resultRow.put("_archiveSuccess", false);
                    resultRow.put("_archiveMessage", "数据增强失败，已跳过");
                    resultRows.add(resultRow);
                    continue;
                }
                Object downloadOk = resultRow.get("_downloadSuccess");
                if (downloadOk != null && !Boolean.TRUE.equals(downloadOk)
                        && !"true".equalsIgnoreCase(String.valueOf(downloadOk))) {
                    pushFail++;
                    context.warn("[" + rowNum + "/" + rows.size() + "] 附件下载失败，跳过归档, 标识: " + fid
                            + ", 原因=" + resultRow.get("_downloadMessage"));
                    resultRow.put("_archiveSuccess", false);
                    resultRow.put("_archiveMessage", "附件下载失败，已跳过: " + resultRow.get("_downloadMessage"));
                    resultRows.add(resultRow);
                    continue;
                }

                context.info("[" + rowNum + "/" + rows.size() + "] 开始归档推送, 标识: " + fid
                        + ", 案卷号=" + caseNum + ", 分类号=" + entityClassNum);
                Map<String, Object> archiveResult = thesisArchiveService.execute(resultRow, apiUrl, archiveConfig);
                boolean success = Boolean.TRUE.equals(archiveResult.get("success"));
                resultRow.put("_archiveSuccess", success);
                Object msg = archiveResult.get("error");
                if (msg == null) {
                    msg = archiveResult.get("msg");
                }
                resultRow.put("_archiveMessage", msg != null ? msg : (success ? "OK" : "未知"));
                String detail = "标识=" + (archiveResult.get("fileIdentifierCode") != null
                        ? archiveResult.get("fileIdentifierCode") : fid)
                        + ", pdf=" + archiveResult.get("pdfCount")
                        + ", xmlBytes=" + archiveResult.get("xmlBytes")
                        + ", zipBytes=" + archiveResult.get("zipBytes")
                        + ", token=" + (Boolean.TRUE.equals(archiveResult.get("tokenObtained")) ? "已获取" : "未获取")
                        + ", HTTP=" + archiveResult.get("httpStatus");
                Object respBody = archiveResult.get("responseBody");
                if (respBody == null) {
                    respBody = archiveResult.get("msg");
                }
                if (respBody != null) {
                    detail += ", 响应=" + truncate(String.valueOf(respBody), 300);
                }
                if (success) {
                    pushOk++;
                    context.info("[" + rowNum + "/" + rows.size() + "] 归档推送成功, " + detail);
                } else {
                    pushFail++;
                    context.warn("[" + rowNum + "/" + rows.size() + "] 归档推送失败: "
                            + resultRow.get("_archiveMessage") + " | " + detail);
                }
            } catch (Exception e) {
                pushFail++;
                log.warn("论文归档异常: row={}, msg={}", rowNum, e.getMessage(), e);
                context.error("[" + rowNum + "/" + rows.size() + "] 归档异常: " + e.getMessage());
                resultRow.put("_archiveSuccess", false);
                resultRow.put("_archiveMessage", e.getMessage());
            }
            resultRows.add(resultRow);
        }
        context.info("论文归档结束: 成功 " + pushOk + " / 失败 " + pushFail + " / 共 " + rows.size());
        return DataPacket.ofList(resultRows);
    }

    private Map<String, Object> firstRow(DataPacket packet) {
        if (packet != null && packet.getRows() != null && !packet.getRows().isEmpty()
                && packet.getRows().get(0) != null) {
            return packet.getRows().get(0);
        }
        return new HashMap<>();
    }

    /**
     * 步骤字段可写字面量或 ${入参名}；为空则回落到模板入参同名键。
     */
    private String resolveArchiveParam(Map<String, Object> step, Map<String, Object> inputRow,
            String stepKey, String inputKey, String defaultVal) {
        Object stepVal = step.get(stepKey);
        String s = stepVal != null ? stepVal.toString().trim() : "";
        if (!s.isEmpty()) {
            if (s.contains("${") && inputRow != null) {
                for (Map.Entry<String, Object> e : inputRow.entrySet()) {
                    String placeholder = "${" + e.getKey() + "}";
                    if (s.contains(placeholder)) {
                        s = s.replace(placeholder, e.getValue() != null ? e.getValue().toString() : "");
                    }
                }
            }
            s = s.trim();
            if (!s.isEmpty() && !s.contains("${")) {
                return s;
            }
        }
        if (inputRow != null) {
            Object fromInput = inputRow.get(inputKey);
            if (fromInput != null && !fromInput.toString().trim().isEmpty()) {
                return fromInput.toString().trim();
            }
        }
        return defaultVal;
    }

    private String computeEntityClassNum(Map<String, Object> row) {
        Object c2 = row.get("二级目录");
        String c2Val = c2 != null && !c2.toString().isEmpty() ? c2.toString() : "JX16";
        Object timeObj = row.get("时间");
        String timeVal = timeObj != null ? timeObj.toString() : "";
        if (timeVal.isEmpty()) {
            Object sd = row.get("submissionDate");
            timeVal = sd != null ? sd.toString().replace("-", "") : "";
        }
        String year = timeVal.length() >= 4 ? timeVal.substring(0, 4)
                : String.valueOf(java.time.LocalDate.now().getYear());
        return year + "-" + c2Val;
    }

    private DataPacket executeFileDownloadStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        try {
            ComponentExecutor executor = executorFactory.getExecutor("FILE_DOWNLOAD");
            return executor.execute(input, step, context);
        } catch (Exception e) {
            log.error("附件下载步骤执行失败", e);
            context.error("附件下载步骤执行失败: " + e.getMessage());
            return DataPacket.error("DOWNLOAD_ERROR", "附件下载失败: " + e.getMessage());
        }
    }

    /**
     * 执行操作事件步骤 - 委托给 OperationEventExecutor
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeOperationStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        try {
            Map<String, Object> cfg = new LinkedHashMap<>(step);
            Object tplInput = context.getGlobalVariable("_templateInput");
            if (tplInput instanceof Map) {
                Map<String, Object> inputMap = (Map<String, Object>) tplInput;
                Object tableObj = cfg.get("tableName");
                if (tableObj != null && String.valueOf(tableObj).contains("${")) {
                    cfg.put("tableName", applyMapPlaceholders(String.valueOf(tableObj), inputMap));
                }
            }
            Long dsId = getLongFromConfig(cfg, "dsId");
            String sourceType = cfg.get("sourceType") != null ? String.valueOf(cfg.get("sourceType")) : "DB";
            String operationType = cfg.get("operationType") != null ? String.valueOf(cfg.get("operationType")) : "";
            if ("DB".equalsIgnoreCase(sourceType) && (dsId == null || dsId == 0)
                    && ("DB_INSERT".equals(operationType) || "DB_UPDATE".equals(operationType)
                    || "DB_QUERY".equals(operationType) || "DB_DELETE".equals(operationType))) {
                if (tplInput instanceof Map) {
                    Object v = ((Map<?, ?>) tplInput).get("mysqlDsId");
                    if (v instanceof Number) {
                        dsId = ((Number) v).longValue();
                    } else if (v != null && !v.toString().trim().isEmpty()) {
                        try { dsId = Long.parseLong(v.toString().trim()); } catch (NumberFormatException ignore) {}
                    }
                }
                if (dsId == null || dsId == 0) {
                    DsConfig mysql = dataSourceService.findFirstMysql().orElse(null);
                    if (mysql != null) {
                        dsId = mysql.getId();
                        context.info("未指定数据源，使用 MySQL: " + mysql.getName() + " id=" + dsId);
                    }
                }
                if (dsId != null && dsId > 0) {
                    cfg.put("dsId", dsId);
                }
            }
            ComponentExecutor executor = executorFactory.getExecutor("OPERATION");
            return executor.execute(input, cfg, context);
        } catch (Exception e) {
            log.error("操作事件步骤执行失败", e);
            context.error("操作事件步骤执行失败: " + e.getMessage());
            return DataPacket.error("OPERATION_ERROR", "操作事件步骤执行失败: " + e.getMessage());
        }
    }

    /**
     * 执行事件处理步骤 - 委托给 EventStepExecutor
     */
    private DataPacket executeEventStep(Map<String, Object> step, DataPacket input, ExecutionContext context) {
        try {
            ComponentExecutor executor = executorFactory.getExecutor("EVENT");
            return executor.execute(input, step, context);
        } catch (Exception e) {
            log.error("事件处理步骤执行失败", e);
            context.error("事件处理步骤执行失败: " + e.getMessage());
            return DataPacket.error("EVENT_ERROR", "事件处理步骤失败: " + e.getMessage());
        }
    }

    /**
     * 输出模式: 调用模板处理
     * 将当前数据传给另一个模板，由该模板负责落库/调API等实际操作
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeOutputViaTemplate(Map<String, Object> outputConfig, DataPacket data, ExecutionContext context) {
        try {
            Long callTemplateId = getLongFromConfig(outputConfig, "callTemplateId");
            if (callTemplateId == null || callTemplateId == 0) {
                context.error("输出-调用模板: 未配置目标模板");
                return DataPacket.error("CONFIG_ERROR", "未配置输出目标模板");
            }

            String passMode = (String) outputConfig.getOrDefault("passMode", "PACKET");
            int timeout = getIntFromConfig(outputConfig, "timeout", 60);
            String onError = (String) outputConfig.getOrDefault("onError", "STOP");
            VisualTemplate target = visualTemplateService.getById(callTemplateId).orElse(null);
            String targetName = target != null ? target.getName() : String.valueOf(callTemplateId);

            context.info("输出-调用模板: " + targetName + " (id=" + callTemplateId + "), passMode=" + passMode
                    + ", 行数=" + data.size() + ", timeout=" + timeout + "s, onError=" + onError);

            DataPacket result;
            int callOk = 0;
            int callFail = 0;
            switch (passMode) {
                case "ROW":
                    List<Map<String, Object>> allResults = new ArrayList<>();
                    List<Map<String, Object>> rowList = data.getRows() != null ? data.getRows() : Collections.emptyList();
                    int rowIdx = 0;
                    for (Map<String, Object> row : rowList) {
                        rowIdx++;
                        context.info("输出-逐行调用 [" + rowIdx + "/" + rowList.size() + "]");
                        DataPacket rowPacket = DataPacket.of(row);
                        DataPacket rowResult = executeNested(callTemplateId, rowPacket, context);
                        if (rowResult != null && rowResult.isSuccess()) {
                            callOk++;
                            if (rowResult.getRows() != null) {
                                allResults.addAll(rowResult.getRows());
                            }
                        } else {
                            callFail++;
                            context.warn("输出-逐行调用 [" + rowIdx + "/" + rowList.size() + "] 失败: "
                                    + (rowResult != null ? rowResult.getErrorMessage() : "空结果"));
                            if ("STOP".equals(onError)) {
                                return rowResult != null ? rowResult : DataPacket.error("OUTPUT_ERROR", "调用模板失败");
                            }
                        }
                    }
                    result = DataPacket.ofList(allResults);
                    break;
                case "BATCH":
                    int batchSize = getIntFromConfig(outputConfig, "batchSize", 100);
                    List<Map<String, Object>> batchResults = new ArrayList<>();
                    List<Map<String, Object>> rows = data.getRows() != null ? data.getRows() : Collections.emptyList();
                    int batchNo = 0;
                    for (int i = 0; i < rows.size(); i += batchSize) {
                        int end = Math.min(i + batchSize, rows.size());
                        batchNo++;
                        List<Map<String, Object>> batch = rows.subList(i, end);
                        context.info("输出-分批调用 第 " + batchNo + " 批, 行 " + (i + 1) + "-" + end + " / " + rows.size());
                        DataPacket batchPacket = DataPacket.ofList(new ArrayList<>(batch));
                        DataPacket batchResult = executeNested(callTemplateId, batchPacket, context);
                        if (batchResult != null && batchResult.isSuccess()) {
                            callOk++;
                            if (batchResult.getRows() != null) {
                                batchResults.addAll(batchResult.getRows());
                            }
                        } else {
                            callFail++;
                            context.warn("输出-分批调用 第 " + batchNo + " 批失败: "
                                    + (batchResult != null ? batchResult.getErrorMessage() : "空结果"));
                            if ("STOP".equals(onError)) {
                                return batchResult != null ? batchResult : DataPacket.error("OUTPUT_ERROR", "调用模板失败");
                            }
                        }
                    }
                    result = DataPacket.ofList(batchResults);
                    break;
                default:
                    result = executeNested(callTemplateId, data, context);
                    if (result != null && result.isSuccess()) {
                        callOk++;
                    } else {
                        callFail++;
                        context.warn("输出-调用模板失败: " + (result != null ? result.getErrorMessage() : "空结果"));
                    }
                    break;
            }

            context.info("输出-调用模板完成: " + targetName + ", 成功=" + callOk + ", 失败=" + callFail
                    + ", 返回行数=" + (result != null ? result.size() : 0));
            return result != null ? result : DataPacket.error("OUTPUT_ERROR", "调用模板无结果");

        } catch (Exception e) {
            log.error("输出-调用模板失败", e);
            String onError = (String) outputConfig.getOrDefault("onError", "STOP");
            if ("IGNORE".equals(onError)) {
                context.warn("输出-调用模板失败，忽略继续: " + e.getMessage());
                return data;
            }
            context.error("输出-调用模板失败: " + e.getMessage());
            return DataPacket.error("OUTPUT_ERROR", "调用模板输出失败: " + e.getMessage());
        }
    }

    /**
     * 输出模式: 下载文件
     * 将数据序列化为文件内容，返回给浏览器下载
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeOutputToFile(Map<String, Object> outputConfig, DataPacket data, ExecutionContext context) {
        try {
            String fileFormat = (String) outputConfig.getOrDefault("fileFormat", "JSON");
            Map<String, Object> fileOptions = (Map<String, Object>) outputConfig.getOrDefault("fileOptions", new HashMap<>());

            context.info("输出-生成下载文件: format=" + fileFormat + ", rows=" + data.size());

            // 根据格式序列化
            String content;
            String contentType;
            String fileExt;
            switch (fileFormat.toUpperCase()) {
                case "JSON":
                    content = serializeToJson(data, fileOptions);
                    contentType = "application/json";
                    fileExt = ".json";
                    break;
                case "CSV":
                    content = serializeToCsv(data, fileOptions);
                    contentType = "text/csv";
                    fileExt = ".csv";
                    break;
                case "XML":
                    content = serializeToXml(data);
                    contentType = "application/xml";
                    fileExt = ".xml";
                    break;
                default:
                    content = serializeToText(data);
                    contentType = "text/plain";
                    fileExt = ".txt";
                    break;
            }

            String fileName = "export_" + java.time.LocalDate.now().toString() + fileExt;

            context.info("输出-文件生成完成, size=" + content.length() + " bytes");

            DataPacket result = DataPacket.empty();
            result.getVariables().put("_download_content", content);
            result.getVariables().put("_download_contentType", contentType);
            result.getVariables().put("_download_fileName", fileName);
            result.getVariables().put("_download", true);
            return result;

        } catch (Exception e) {
            log.error("输出-生成文件失败", e);
            context.error("输出-生成文件失败: " + e.getMessage());
            return DataPacket.error("FILE_ERROR", "文件输出失败: " + e.getMessage());
        }
    }

    private String serializeToJson(DataPacket data, Map<String, Object> options) throws Exception {
        boolean pretty = Boolean.TRUE.equals(options.getOrDefault("pretty", true));
        boolean jsonLines = Boolean.TRUE.equals(options.getOrDefault("jsonLines", false));
        List<Map<String, Object>> rows = data.getRows();

        if (jsonLines) {
            StringBuilder sb = new StringBuilder();
            for (Map<String, Object> row : rows) {
                sb.append(objectMapper.writeValueAsString(row)).append("\n");
            }
            return sb.toString();
        } else {
            if (pretty) {
                return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(rows);
            }
            return objectMapper.writeValueAsString(rows);
        }
    }

    private String serializeToCsv(DataPacket data, Map<String, Object> options) {
        boolean includeHeader = Boolean.TRUE.equals(options.getOrDefault("includeHeader", true));
        String delimiter = (String) options.getOrDefault("delimiter", ",");
        List<Map<String, Object>> rows = data.getRows();

        if (rows.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();
        Set<String> allKeys = new LinkedHashSet<>();

        // 收集所有字段
        for (Map<String, Object> row : rows) {
            allKeys.addAll(row.keySet());
        }
        List<String> headers = new ArrayList<>(allKeys);

        // 表头
        if (includeHeader) {
            sb.append(String.join(delimiter, headers)).append("\n");
        }

        // 数据行
        for (Map<String, Object> row : rows) {
            List<String> values = new ArrayList<>();
            for (String key : headers) {
                Object val = row.get(key);
                String strVal = val != null ? val.toString() : "";
                // CSV转义: 包含分隔符或引号时加引号
                if (strVal.contains(delimiter) || strVal.contains("\"") || strVal.contains("\n")) {
                    strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                }
                values.add(strVal);
            }
            sb.append(String.join(delimiter, values)).append("\n");
        }

        return sb.toString();
    }

    private String serializeToXml(DataPacket data) throws Exception {
        List<Map<String, Object>> rows = data.getRows();
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append("<rows>\n");
        for (Map<String, Object> row : rows) {
            sb.append("  <row>\n");
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                String key = entry.getKey().replaceAll("[^a-zA-Z0-9_-]", "_");
                Object val = entry.getValue();
                String strVal = val != null ? val.toString()
                        .replace("&", "&amp;").replace("<", "&lt;")
                        .replace(">", "&gt;").replace("\"", "&quot;") : "";
                sb.append("    <").append(key).append(">").append(strVal).append("</").append(key).append(">\n");
            }
            sb.append("  </row>\n");
        }
        sb.append("</rows>\n");
        return sb.toString();
    }

    private String serializeToText(DataPacket data) {
        List<Map<String, Object>> rows = data.getRows();
        StringBuilder sb = new StringBuilder();
        for (Map<String, Object> row : rows) {
            sb.append(row.toString()).append("\n");
        }
        return sb.toString();
    }

    private Long getLongFromConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    private int getIntFromConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private String getRawString(Map<String, Object> config, String key, String defaultWhenMissing) {
        if (config == null || !config.containsKey(key) || config.get(key) == null) {
            return defaultWhenMissing;
        }
        return config.get(key).toString().trim();
    }

    private Map<String, String> buildPagingVars(Map<String, Object> step, int offset, int pageSize) {
        Map<String, String> vars = new LinkedHashMap<>();
        int size = pageSize > 0 ? pageSize : 0;
        int pageStart = getIntFromConfig(step, "apiPageStart", 1);
        int page = size > 0 ? pageStart + (offset / size) : pageStart;
        int batch = size > 0 ? offset / size + 1 : 1;
        vars.put("offset", String.valueOf(offset));
        vars.put("pageSize", String.valueOf(size));
        vars.put("limit", String.valueOf(size));
        vars.put("size", String.valueOf(size));
        vars.put("page", String.valueOf(page));
        vars.put("batch", String.valueOf(batch));
        String pageField = getRawString(step, "apiPageField", "page");
        String sizeField = getRawString(step, "apiSizeField", "pageSize");
        String offsetField = getRawString(step, "apiOffsetField", "");
        if (pageField != null && !pageField.isEmpty()) {
            vars.put(pageField, String.valueOf(page));
        }
        if (sizeField != null && !sizeField.isEmpty()) {
            vars.put(sizeField, String.valueOf(size));
        }
        if (offsetField != null && !offsetField.isEmpty()) {
            vars.put(offsetField, String.valueOf(offset));
        }
        return vars;
    }

    private static String applyPagingPlaceholders(String text, Map<String, String> vars) {
        if (text == null || text.isEmpty() || vars == null || vars.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, String> e : vars.entrySet()) {
            out = out.replace("${" + e.getKey() + "}", e.getValue() != null ? e.getValue() : "");
        }
        return out;
    }

    private static boolean containsPagingPlaceholder(String text, Map<String, String> vars) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        if (text.contains("${offset}") || text.contains("${page}") || text.contains("${pageSize}")
                || text.contains("${limit}") || text.contains("${size}") || text.contains("${batch}")) {
            return true;
        }
        if (vars != null) {
            for (String key : vars.keySet()) {
                if (text.contains("${" + key + "}")) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasPagingPlaceholder(String text) {
        return containsPagingPlaceholder(text, null);
    }

    private String applyInputPlaceholders(String text, DataPacket input, boolean quote) {
        if (text == null || text.isEmpty() || input == null || input.getRows() == null || input.getRows().isEmpty()) {
            return text;
        }
        Map<String, Object> inputRow = input.getRows().get(0);
        if (inputRow == null) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, Object> entry : inputRow.entrySet()) {
            String key = entry.getKey();
            if ("offset".equals(key) || "page".equals(key) || "pageSize".equals(key)
                    || "limit".equals(key) || "size".equals(key) || "batch".equals(key)) {
                continue;
            }
            String placeholder = "${" + key + "}";
            if (out.contains(placeholder)) {
                String val = entry.getValue() != null ? entry.getValue().toString() : "";
                if (quote) {
                    val = "'" + val.replace("'", "''") + "'";
                }
                out = out.replace(placeholder, val);
            }
        }
        return out;
    }

    private static String applyMapPlaceholders(String text, Map<String, Object> values) {
        if (text == null || text.isEmpty() || values == null || values.isEmpty()) {
            return text;
        }
        String out = text;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String placeholder = "${" + entry.getKey() + "}";
            if (out.contains(placeholder)) {
                out = out.replace(placeholder, entry.getValue() != null ? entry.getValue().toString() : "");
            }
        }
        return out;
    }

    private String appendPagingQuery(String apiUrl, Map<String, Object> step, Map<String, String> vars) {
        if (apiUrl == null || apiUrl.isEmpty() || pageSizeOf(vars) <= 0) {
            return apiUrl;
        }
        String pageField = getRawString(step, "apiPageField", "page");
        String sizeField = getRawString(step, "apiSizeField", "pageSize");
        String offsetField = getRawString(step, "apiOffsetField", "");
        StringBuilder extra = new StringBuilder();
        appendQueryIfAbsent(extra, apiUrl, pageField, vars.get(pageField));
        appendQueryIfAbsent(extra, apiUrl, sizeField, vars.get(sizeField));
        appendQueryIfAbsent(extra, apiUrl, offsetField, vars.get(offsetField));
        if (extra.length() == 0) {
            return apiUrl;
        }
        return apiUrl + (apiUrl.contains("?") ? "&" : "?") + extra.substring(1);
    }

    private static int pageSizeOf(Map<String, String> vars) {
        if (vars == null || vars.get("pageSize") == null) {
            return 0;
        }
        try {
            return Integer.parseInt(vars.get("pageSize"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void appendQueryIfAbsent(StringBuilder extra, String apiUrl, String name, String value) {
        if (name == null || name.isEmpty() || value == null) {
            return;
        }
        String lower = apiUrl.toLowerCase();
        String key = name.toLowerCase() + "=";
        if (lower.contains("?" + key) || lower.contains("&" + key)) {
            return;
        }
        try {
            extra.append('&').append(name).append('=')
                    .append(java.net.URLEncoder.encode(value, "UTF-8"));
        } catch (Exception e) {
            extra.append('&').append(name).append('=').append(value);
        }
    }

    private String mergePagingJson(String body, Map<String, Object> step, Map<String, String> vars) {
        Map<String, Object> toPut = new LinkedHashMap<>();
        putPagingJsonField(toPut, getRawString(step, "apiPageField", "page"), vars);
        putPagingJsonField(toPut, getRawString(step, "apiSizeField", "pageSize"), vars);
        putPagingJsonField(toPut, getRawString(step, "apiOffsetField", ""), vars);
        if (toPut.isEmpty()) {
            return body;
        }
        try {
            if (body == null || body.trim().isEmpty()) {
                return objectMapper.writeValueAsString(toPut);
            }
            Object parsed = objectMapper.readValue(body, Object.class);
            if (parsed instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> map = (Map<String, Object>) parsed;
                for (Map.Entry<String, Object> e : toPut.entrySet()) {
                    if (!map.containsKey(e.getKey())) {
                        map.put(e.getKey(), e.getValue());
                    }
                }
                return objectMapper.writeValueAsString(map);
            }
        } catch (Exception e) {
            log.warn("接口请求体无法自动写入分页参数，按原文发送");
        }
        return body;
    }

    private static void putPagingJsonField(Map<String, Object> toPut, String name, Map<String, String> vars) {
        if (name == null || name.isEmpty() || vars == null || !vars.containsKey(name)) {
            return;
        }
        String raw = vars.get(name);
        try {
            toPut.put(name, Integer.valueOf(raw));
        } catch (Exception e) {
            toPut.put(name, raw);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractApiRows(Object parsed, String listPath) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (parsed == null) {
            return rows;
        }
        if (parsed instanceof List) {
            return toRowList((List<?>) parsed);
        }
        if (!(parsed instanceof Map)) {
            return rows;
        }
        Map<String, Object> map = (Map<String, Object>) parsed;
        if (listPath != null && !listPath.isEmpty()) {
            Object found = resolveNestedValue(map, listPath);
            if (found instanceof List) {
                return toRowList((List<?>) found);
            }
        }
        String[] keys = {"data", "list", "rows", "records", "result", "items", "content"};
        for (String key : keys) {
            Object v = map.get(key);
            if (v instanceof List) {
                return toRowList((List<?>) v);
            }
            if (v instanceof Map) {
                Map<?, ?> nested = (Map<?, ?>) v;
                for (String nk : keys) {
                    Object nv = nested.get(nk);
                    if (nv instanceof List) {
                        return toRowList((List<?>) nv);
                    }
                }
            }
        }
        rows.add(map);
        return rows;
    }

    private Object resolveNestedValue(Map<String, Object> map, String path) {
        Object cur = map;
        for (String part : path.split("[./]")) {
            if (part == null || part.trim().isEmpty()) {
                continue;
            }
            if (!(cur instanceof Map)) {
                return null;
            }
            cur = ((Map<?, ?>) cur).get(part.trim());
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> toRowList(List<?> list) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (list == null) {
            return rows;
        }
        for (Object item : list) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            } else {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", item);
                rows.add(row);
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    private String fetchAccessToken(Map<String, Object> step, String tokenUrl, DataPacket input,
            ExecutionContext context, int timeout, int retryTimes, int retryInterval) throws Exception {
        String method = getRawString(step, "tokenMethod", "POST");
        String headers = applyInputPlaceholders(getRawString(step, "tokenHeaders", ""), input, false);
        String body = applyInputPlaceholders(getRawString(step, "tokenBody", ""), input, false);
        context.info("获取 Token: " + method + " " + tokenUrl);
        HttpCallResult call = doHttpWithRetry(method, tokenUrl, headers, body,
                timeout, retryTimes, retryInterval, context, "Token 接口");
        context.info("Token 响应: code=" + call.statusCode + ", len=" + (call.body != null ? call.body.length() : 0));
        if (!(call.parsed instanceof Map)) {
            context.error("Token 响应不是 JSON 对象: " + truncate(call.body, 300));
            return null;
        }
        Map<String, Object> map = (Map<String, Object>) call.parsed;
        if (!isBizOk(map.get("code"))) {
            context.error("获取 Token 业务失败: code=" + map.get("code") + ", message=" + map.get("message"));
            return null;
        }
        String path = getRawString(step, "tokenExtractPath", "result.access_token");
        Object value = resolveNestedValue(map, path);
        return value != null ? String.valueOf(value) : null;
    }

    private static final int DEFAULT_API_TIMEOUT_SEC = 180;
    private static final int DEFAULT_API_RETRY_TIMES = 3;
    private static final int DEFAULT_API_RETRY_INTERVAL_MS = 1000;

    private int resolveApiTimeout(Map<String, Object> step, DsConfig dsConfig) {
        int fromStep = getIntFromConfig(step, "apiTimeout", 0);
        if (fromStep > 0) {
            return fromStep;
        }
        if (dsConfig != null && dsConfig.getApiTimeout() != null && dsConfig.getApiTimeout() > 0) {
            return dsConfig.getApiTimeout();
        }
        return DEFAULT_API_TIMEOUT_SEC;
    }

    private int resolveApiRetryTimes(Map<String, Object> step, DsConfig dsConfig) {
        if (step != null && step.containsKey("apiRetryTimes") && step.get("apiRetryTimes") != null
                && !step.get("apiRetryTimes").toString().trim().isEmpty()) {
            return Math.max(0, getIntFromConfig(step, "apiRetryTimes", DEFAULT_API_RETRY_TIMES));
        }
        if (dsConfig != null && dsConfig.getApiRetryTimes() != null) {
            return Math.max(0, dsConfig.getApiRetryTimes());
        }
        return DEFAULT_API_RETRY_TIMES;
    }

    private int resolveApiRetryInterval(Map<String, Object> step, DsConfig dsConfig) {
        int fromStep = getIntFromConfig(step, "apiRetryInterval", -1);
        if (fromStep >= 0 && step != null && step.get("apiRetryInterval") != null
                && !step.get("apiRetryInterval").toString().trim().isEmpty()) {
            return fromStep;
        }
        if (dsConfig != null && dsConfig.getApiRetryInterval() != null && dsConfig.getApiRetryInterval() >= 0) {
            return dsConfig.getApiRetryInterval();
        }
        return DEFAULT_API_RETRY_INTERVAL_MS;
    }

    private HttpCallResult doHttpWithRetry(String method, String apiUrl, String apiHeaders, String apiBody,
            int timeout, int retryTimes, int retryIntervalMs, ExecutionContext context, String label)
            throws Exception {
        int attempts = Math.max(0, retryTimes) + 1;
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            if (context != null && context.isCancelled()) {
                throw new RuntimeException(label + " 已取消");
            }
            try {
                HttpCallResult call = doHttp(method, apiUrl, apiHeaders, apiBody, timeout);
                if (shouldRetryHttpStatus(call.statusCode) && i < attempts) {
                    if (context != null) {
                        context.warn(label + " 第 " + i + " 次失败(HTTP " + call.statusCode + ")，"
                                + retryIntervalMs + "ms 后重试");
                    }
                    sleepRetry(retryIntervalMs);
                    continue;
                }
                if (i > 1 && context != null) {
                    context.info(label + " 第 " + i + " 次成功, HTTP " + call.statusCode);
                }
                return call;
            } catch (Exception e) {
                last = e;
                if (i < attempts && isRetryableHttpError(e)) {
                    if (context != null) {
                        context.warn(label + " 第 " + i + " 次失败: " + e.getMessage() + "，"
                                + retryIntervalMs + "ms 后重试");
                    }
                    sleepRetry(retryIntervalMs);
                    continue;
                }
                throw e;
            }
        }
        throw last != null ? last : new RuntimeException(label + " 请求失败");
    }

    private static boolean shouldRetryHttpStatus(int statusCode) {
        return statusCode == 408 || statusCode == 429 || statusCode >= 500;
    }

    private static boolean isRetryableHttpError(Throwable e) {
        Throwable cur = e;
        while (cur != null) {
            if (cur instanceof java.net.SocketTimeoutException
                    || cur instanceof java.net.ConnectException
                    || cur instanceof java.net.SocketException
                    || cur instanceof java.net.UnknownHostException) {
                return true;
            }
            String msg = cur.getMessage();
            if (msg != null) {
                String lower = msg.toLowerCase();
                if (lower.contains("timed out") || lower.contains("timeout")
                        || lower.contains("connection reset") || lower.contains("connection refused")) {
                    return true;
                }
            }
            cur = cur.getCause();
        }
        return false;
    }

    private static void sleepRetry(int millis) {
        if (millis <= 0) {
            return;
        }
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private HttpCallResult doHttp(String method, String apiUrl, String apiHeaders, String apiBody, int timeout)
            throws Exception {
        java.net.HttpURLConnection conn = null;
        try {
            java.net.URL url = new java.net.URL(apiUrl);
            conn = (java.net.HttpURLConnection) url.openConnection();
            String httpMethod = method != null && !method.isEmpty() ? method.toUpperCase() : "GET";
            conn.setRequestMethod(httpMethod);
            conn.setConnectTimeout(timeout * 1000);
            conn.setReadTimeout(timeout * 1000);
            conn.setRequestProperty("Accept", "application/json");

            if (apiHeaders != null && !apiHeaders.isEmpty()) {
                try {
                    Map<String, String> headersMap = objectMapper.readValue(apiHeaders,
                            new TypeReference<Map<String, String>>() {});
                    for (Map.Entry<String, String> entry : headersMap.entrySet()) {
                        conn.setRequestProperty(entry.getKey(), entry.getValue());
                    }
                } catch (Exception e) { /* ignore */ }
            }

            if (("POST".equalsIgnoreCase(httpMethod) || "PUT".equalsIgnoreCase(httpMethod)
                    || "PATCH".equalsIgnoreCase(httpMethod))) {
                conn.setDoOutput(true);
                if (conn.getRequestProperty("Content-Type") == null) {
                    conn.setRequestProperty("Content-Type", "application/json");
                }
                byte[] bodyBytes = (apiBody != null ? apiBody : "").getBytes(StandardCharsets.UTF_8);
                try (java.io.OutputStream os = conn.getOutputStream()) {
                    os.write(bodyBytes);
                }
            }

            int responseCode = conn.getResponseCode();
            StringBuilder responseBody = new StringBuilder();
            try (java.io.InputStream is = responseCode >= 200 && responseCode < 300
                    ? conn.getInputStream() : conn.getErrorStream()) {
                if (is != null) {
                    java.io.BufferedReader reader = new java.io.BufferedReader(
                            new java.io.InputStreamReader(is, StandardCharsets.UTF_8));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        responseBody.append(line);
                    }
                }
            }
            HttpCallResult result = new HttpCallResult();
            result.statusCode = responseCode;
            result.body = responseBody.toString();
            try {
                if (result.body != null && !result.body.isEmpty()) {
                    result.parsed = objectMapper.readValue(result.body, Object.class);
                }
            } catch (Exception ignore) {
                result.parsed = null;
            }
            return result;
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String toAbsoluteLocalUrl(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        if (url.startsWith("/")) {
            return "http://127.0.0.1:" + serverPort + url;
        }
        return url;
    }

    private static String ensureQueryParam(String url, String name, String value) {
        if (url == null || url.isEmpty() || name == null || name.isEmpty() || value == null) {
            return url;
        }
        String encoded = urlEncode(value);
        String prefix = name + "=";
        int q = url.indexOf('?');
        if (q < 0) {
            return url + "?" + prefix + encoded;
        }
        String base = url.substring(0, q);
        String query = url.substring(q + 1);
        String[] parts = query.split("&");
        StringBuilder nb = new StringBuilder(base).append('?');
        boolean found = false;
        boolean first = true;
        for (String p : parts) {
            if (p.isEmpty()) {
                continue;
            }
            if (!first) {
                nb.append('&');
            }
            first = false;
            if (p.startsWith(prefix) || p.equals(name)) {
                nb.append(prefix).append(encoded);
                found = true;
            } else {
                nb.append(p);
            }
        }
        if (!found) {
            if (!first) {
                nb.append('&');
            }
            nb.append(prefix).append(encoded);
        }
        return nb.toString();
    }

    private static String urlEncode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (Exception e) {
            return value;
        }
    }

    private static String maskAccessToken(String url) {
        if (url == null) {
            return "";
        }
        return url.replaceAll("([?&]access_token=)[^&]*", "$1***");
    }

    private static boolean isBizOk(Object code) {
        if (code == null) {
            return true;
        }
        if (code instanceof Number) {
            int n = ((Number) code).intValue();
            return n == 10000 || n == 0 || n == 200;
        }
        String s = String.valueOf(code);
        return "10000".equals(s) || "0".equals(s) || "200".equals(s) || "ok".equalsIgnoreCase(s);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    private static class HttpCallResult {
        int statusCode;
        String body;
        Object parsed;
    }

    private static boolean sqlHasOffsetOrFetch(String sql) {
        if (sql == null) {
            return false;
        }
        String upper = sql.toUpperCase();
        return upper.contains(" OFFSET ") || upper.contains("FETCH NEXT") || upper.contains("FETCH FIRST");
    }

    private static String applyPageSql(String sql, String dbType, int offset, int pageSize) {
        return SqlPageWrapper.wrap(sql, dbType, offset, pageSize);
    }

    /**
     * 收集节点输入
     */
    private DataPacket collectNodeInput(String nodeId, ExecutionGraph graph, 
                                         Map<String, DataPacket> nodeResults,
                                         DataPacket globalInput, ExecutionContext context) {
        // 获取所有指向当前节点的边
        List<String> sourceNodes = graph.getIncomingEdges(nodeId);

        if (sourceNodes.isEmpty()) {
            // 没有输入边，使用全局输入
            return globalInput != null ? globalInput : DataPacket.empty();
        }

        // 合并所有输入
        DataPacket mergedInput = DataPacket.empty();
        for (String sourceId : sourceNodes) {
            DataPacket sourceResult = nodeResults.get(sourceId);
            if (sourceResult != null && sourceResult.isSuccess()) {
                mergedInput.merge(sourceResult);
            }
        }

        return mergedInput;
    }

    private void persistTemplateRunLog(VisualTemplate template, ExecutionContext context,
            DataPacket result, long startMs, long endMs) {
        if (template == null) {
            return;
        }
        List<Map<String, Object>> lines = new ArrayList<>();
        if (context != null && context.getLogs() != null) {
            for (ExecutionContext.ExecutionLog item : context.getLogs()) {
                Map<String, Object> line = new LinkedHashMap<>();
                line.put("time", formatTime(item.getTimestamp()));
                line.put("level", item.getLevel() != null ? item.getLevel().name() : "INFO");
                line.put("message", item.getMessage());
                lines.add(line);
            }
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("templateId", template.getId());
        record.put("templateName", template.getName());
        record.put("success", result != null && result.isSuccess());
        record.put("rowCount", result != null ? result.size() : 0);
        record.put("errorCode", result != null ? result.getErrorCode() : null);
        record.put("errorMessage", result != null ? result.getErrorMessage() : null);
        record.put("startTime", formatTime(startMs));
        record.put("endTime", formatTime(endMs));
        record.put("durationMs", endMs - startMs);
        record.put("logs", lines);
        String filename = null;
        if (template.getId() != null) {
            filename = executionLogFileService.writeTemplateExecutionLog(template.getId(), record);
        }
        if (result != null) {
            if (result.getVariables() == null) {
                result.setVariables(new LinkedHashMap<String, Object>());
            }
            result.getVariables().put("_executionLog", record);
            if (filename != null) {
                result.getVariables().put("_executionLogFile", filename);
            }
        }
    }

    private static String formatTime(long epochMs) {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneId.systemDefault()).format(LOG_TIME);
    }

    private static String stepLabel(String type) {
        if (type == null) {
            return "未知";
        }
        switch (type) {
            case "DATA_SOURCE":
                return "数据源";
            case "MAPPING":
                return "映射";
            case "FILTER":
                return "过滤";
            case "CALL_TEMPLATE":
                return "调用模板";
            case "OPERATION":
                return "查询/操作";
            case "EVENT":
                return "事件";
            case "THESIS_ARCHIVE":
                return "论文归档";
            case "FILE_DOWNLOAD":
                return "附件下载";
            default:
                return type;
        }
    }

    private void bindWatermark(VisualTemplate template, List<Map<String, Object>> steps, ExecutionContext context) {
        if (template == null || template.getId() == null) {
            return;
        }
        Map<String, Object> wm = executionLogFileService.loadVisualWatermark(template.getId());
        Map<String, Object> dsStep = null;
        int idx = indexOfDataSourceStep(steps);
        if (idx >= 0) {
            dsStep = steps.get(idx);
        }
        if (wm != null) {
            context.setGlobalVariable("_watermarkLastValue", wm.get("lastValue"));
            context.setGlobalVariable("_watermarkLastOffset", wm.get("lastOffset"));
            context.info("加载水位线: field=" + (dsStep != null ? incrementalField(dsStep) : wm.get("incrementalField"))
                    + ", lastValue=" + wm.get("lastValue")
                    + ", lastOffset=" + wm.get("lastOffset"));
        } else if (dsStep != null && isIncremental(dsStep)) {
            context.info("首次增量执行，无历史水位线，本次按全量拉取后写入水位");
        }
        if (dsStep != null && isIncremental(dsStep)) {
            String field = incrementalField(dsStep);
            if (field.isEmpty()) {
                context.warn("已选增量同步，但未填写增量字段");
            }
        }
    }

    private void persistWatermark(VisualTemplate template, ExecutionContext context, Map<String, Object> dsStep) {
        if (template == null || template.getId() == null || dsStep == null) {
            return;
        }
        boolean incremental = isIncremental(dsStep);
        boolean onePerRun = "ONE".equalsIgnoreCase(getRawString(dsStep, "batchPerRun", "ALL"));
        Object pending = context.getGlobalVariable("_watermarkPending");
        Object nextOffset = context.getGlobalVariable("_nextOffset");
        if (!incremental && !onePerRun) {
            return;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("templateId", template.getId());
        data.put("templateName", template.getName());
        data.put("syncMode", getRawString(dsStep, "syncMode", "FULL"));
        data.put("incrementalField", incrementalField(dsStep));
        data.put("lastValue", pending != null ? pending : context.getGlobalVariable("_watermarkLastValue"));
        data.put("lastOffset", nextOffset != null ? nextOffset : 0);
        data.put("lastExecTime", LocalDateTime.now().toString());
        data.put("lastExecStatus", "SUCCESS");
        executionLogFileService.saveVisualWatermark(template.getId(), data);
        context.info("水位线已保存: lastValue=" + data.get("lastValue") + ", lastOffset=" + data.get("lastOffset"));
    }

    private DataPacket afterFetch(Map<String, Object> step, ExecutionContext context, List<Map<String, Object>> rows) {
        int raw = rows != null ? rows.size() : 0;
        List<Map<String, Object>> filtered = filterIncrementalRows(step, context, rows);
        bumpWatermarkFromRows(context, step, filtered);
        DataPacket packet = DataPacket.ofList(filtered);
        packet.getVariables().put("_rawRowCount", raw);
        return packet;
    }

    private List<Map<String, Object>> filterIncrementalRows(Map<String, Object> step, ExecutionContext context,
            List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty() || !isIncremental(step)) {
            return rows != null ? rows : new ArrayList<Map<String, Object>>();
        }
        Object last = context.getGlobalVariable("_watermarkLastValue");
        String field = incrementalField(step);
        if (last == null || String.valueOf(last).trim().isEmpty() || field.isEmpty()) {
            return rows;
        }
        List<Map<String, Object>> kept = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            if (compareWatermark(row.get(field), last) > 0) {
                kept.add(row);
            }
        }
        if (kept.size() != rows.size()) {
            context.info("增量过滤 " + field + " > " + last + ": " + rows.size() + " -> " + kept.size());
        }
        return kept;
    }

    private void bumpWatermarkFromRows(ExecutionContext context, Map<String, Object> step,
            List<Map<String, Object>> rows) {
        String field = incrementalField(step);
        if (field.isEmpty() || rows == null) {
            return;
        }
        Object current = context.getGlobalVariable("_watermarkPending");
        if (current == null) {
            current = context.getGlobalVariable("_watermarkLastValue");
        }
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            Object v = row.get(field);
            if (compareWatermark(v, current) > 0) {
                current = v;
            }
        }
        if (current != null) {
            context.setGlobalVariable("_watermarkPending", current);
        }
    }

    private void putWatermarkVars(Map<String, String> vars, ExecutionContext context) {
        Object last = context.getGlobalVariable("_watermarkLastValue");
        if (last == null || vars == null) {
            return;
        }
        String s = String.valueOf(last);
        vars.put("lastValue", s);
        vars.put("watermark", s);
    }

    private String applyIncrementalWhere(String sql, Map<String, Object> step, ExecutionContext context, String dbType) {
        if (sql == null || sql.isEmpty() || !isIncremental(step)) {
            return sql;
        }
        Object last = context.getGlobalVariable("_watermarkLastValue");
        if (last == null || String.valueOf(last).trim().isEmpty()) {
            return sql;
        }
        String field = incrementalField(step);
        if (!SqlDialect.isSafeIdent(field)) {
            return sql;
        }
        if (sql.contains("${watermark}") || sql.contains("${lastValue}")) {
            return sql;
        }
        String cond = SqlDialect.quoteIdent(field, dbType) + " > " + SqlDialect.quoteLiteral(last);
        String lower = sql.toLowerCase();
        int insertAt = sql.length();
        int orderBy = indexOfSqlKeyword(lower, "order by");
        int limit = indexOfSqlKeyword(lower, "limit");
        int offsetKw = indexOfSqlKeyword(lower, "offset");
        int fetchKw = indexOfSqlKeyword(lower, "fetch first");
        int fetchNext = indexOfSqlKeyword(lower, "fetch next");
        if (orderBy >= 0) {
            insertAt = Math.min(insertAt, orderBy);
        }
        if (limit >= 0) {
            insertAt = Math.min(insertAt, limit);
        }
        if (offsetKw >= 0) {
            insertAt = Math.min(insertAt, offsetKw);
        }
        if (fetchKw >= 0) {
            insertAt = Math.min(insertAt, fetchKw);
        }
        if (fetchNext >= 0) {
            insertAt = Math.min(insertAt, fetchNext);
        }
        boolean hasWhere = lower.contains(" where ");
        String injected = hasWhere ? " AND " + cond + " " : " WHERE " + cond + " ";
        return sql.substring(0, insertAt) + injected + sql.substring(insertAt);
    }

    private static int indexOfSqlKeyword(String sqlLower, String keyword) {
        String padded = " " + sqlLower + " ";
        int i = padded.indexOf(" " + keyword + " ");
        return i >= 0 ? i : -1;
    }

    private String appendIncrementalQuery(String apiUrl, Map<String, Object> step, ExecutionContext context) {
        if (apiUrl == null || apiUrl.isEmpty() || !isIncremental(step)) {
            return apiUrl;
        }
        Object last = context.getGlobalVariable("_watermarkLastValue");
        if (last == null || String.valueOf(last).trim().isEmpty()) {
            return apiUrl;
        }
        String param = incrementalParam(step);
        if (param.isEmpty()) {
            return apiUrl;
        }
        return ensureQueryParam(apiUrl, param, String.valueOf(last));
    }

    private String mergeIncrementalJson(String apiBody, Map<String, Object> step, ExecutionContext context,
            String apiMethod) {
        if (!isIncremental(step) || apiMethod == null
                || !("POST".equalsIgnoreCase(apiMethod) || "PUT".equalsIgnoreCase(apiMethod))) {
            return apiBody;
        }
        Object last = context.getGlobalVariable("_watermarkLastValue");
        if (last == null) {
            return apiBody;
        }
        String param = incrementalParam(step);
        if (param.isEmpty()) {
            return apiBody;
        }
        try {
            Map<String, Object> map;
            if (apiBody == null || apiBody.trim().isEmpty()) {
                map = new LinkedHashMap<>();
            } else {
                map = objectMapper.readValue(apiBody, new TypeReference<Map<String, Object>>() {});
            }
            if (!map.containsKey(param)) {
                map.put(param, last);
            }
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return apiBody;
        }
    }

    private static boolean isIncremental(Map<String, Object> step) {
        return step != null && "INCREMENTAL".equalsIgnoreCase(String.valueOf(step.getOrDefault("syncMode", "FULL")));
    }

    private String incrementalField(Map<String, Object> step) {
        String field = getRawString(step, "incrementalField", "");
        if (field.isEmpty()) {
            field = getRawString(step, "timeField", "");
        }
        return field;
    }

    private String incrementalParam(Map<String, Object> step) {
        String param = getRawString(step, "incrementalParam", "");
        if (param.isEmpty()) {
            param = incrementalField(step);
        }
        return param;
    }

    private static int intFromObject(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value).trim());
            } catch (NumberFormatException ignore) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static int compareWatermark(Object a, Object b) {
        if (a == null && b == null) {
            return 0;
        }
        if (a == null) {
            return -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        }
        String sa = String.valueOf(a).trim();
        String sb = String.valueOf(b).trim();
        try {
            return Double.compare(Double.parseDouble(sa), Double.parseDouble(sb));
        } catch (NumberFormatException ignore) {
            return sa.compareTo(sb);
        }
    }

    // ============ 内部数据结构 ============

    /**
     * 画布配置
     */
    public static class CanvasConfig {
        private Map<String, CanvasNode> nodes;
        private List<CanvasConnection> connections;

        public Map<String, CanvasNode> getNodes() { return nodes; }
        public void setNodes(Map<String, CanvasNode> nodes) { this.nodes = nodes; }
        public List<CanvasConnection> getConnections() { return connections; }
        public void setConnections(List<CanvasConnection> connections) { this.connections = connections; }
    }

    /**
     * 画布节点
     */
    public static class CanvasNode {
        private String id;
        private String type;
        private double x;
        private double y;
        private Map<String, Object> config;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public Map<String, Object> getConfig() { return config; }
        public void setConfig(Map<String, Object> config) { this.config = config; }
    }

    /**
     * 画布连接
     */
    public static class CanvasConnection {
        private String source;
        private String target;

        public String getSource() { return source; }
        public void setSource(String source) { this.source = source; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
    }

    /**
     * 执行图
     */
    public static class ExecutionGraph {
        private final Map<String, CanvasNode> nodes = new LinkedHashMap<>();
        private final Map<String, List<String>> adjacencyList = new LinkedHashMap<>();
        private final Map<String, List<String>> incomingEdges = new LinkedHashMap<>();

        public void addNode(String nodeId, CanvasNode node) {
            nodes.put(nodeId, node);
            adjacencyList.putIfAbsent(nodeId, new ArrayList<>());
            incomingEdges.putIfAbsent(nodeId, new ArrayList<>());
        }

        public void addEdge(String source, String target) {
            adjacencyList.computeIfAbsent(source, k -> new ArrayList<>()).add(target);
            incomingEdges.computeIfAbsent(target, k -> new ArrayList<>()).add(source);
        }

        public CanvasNode getNode(String nodeId) {
            return nodes.get(nodeId);
        }

        public Map<String, CanvasNode> getNodes() {
            return nodes;
        }

        public List<String> getEdges() {
            List<String> edges = new ArrayList<>();
            for (Map.Entry<String, List<String>> entry : adjacencyList.entrySet()) {
                for (String target : entry.getValue()) {
                    edges.add(entry.getKey() + " -> " + target);
                }
            }
            return edges;
        }

        public List<String> getIncomingEdges(String nodeId) {
            return incomingEdges.getOrDefault(nodeId, new ArrayList<>());
        }

        /**
         * 拓扑排序
         * @return 排序后的节点ID列表，如果存在循环则返回null
         */
        public List<String> topologicalSort() {
            List<String> result = new ArrayList<>();
            Map<String, Integer> inDegree = new LinkedHashMap<>();
            Queue<String> queue = new LinkedList<>();

            // 计算入度
            for (String nodeId : nodes.keySet()) {
                inDegree.put(nodeId, 0);
            }
            for (List<String> targets : adjacencyList.values()) {
                for (String target : targets) {
                    inDegree.merge(target, 1, Integer::sum);
                }
            }

            // 找出入度为0的节点
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() == 0) {
                    queue.offer(entry.getKey());
                }
            }

            // BFS
            while (!queue.isEmpty()) {
                String current = queue.poll();
                result.add(current);

                for (String neighbor : adjacencyList.getOrDefault(current, new ArrayList<>())) {
                    int newDegree = inDegree.get(neighbor) - 1;
                    inDegree.put(neighbor, newDegree);
                    if (newDegree == 0) {
                        queue.offer(neighbor);
                    }
                }
            }

            // 检查是否存在循环
            if (result.size() != nodes.size()) {
                return null; // 存在循环
            }

            return result;
        }
    }
}
