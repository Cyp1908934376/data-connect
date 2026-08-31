package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.EventDefinition;
import com.dataconnect.service.EventDefinitionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 事件步骤执行器
 * 根据事件定义执行编码/加密/脱敏/格式转换等处理
 */
@Component
public class EventStepExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(EventStepExecutor.class);

    @Autowired
    private EventDefinitionService eventDefinitionService;

    @Override
    public String getType() {
        return "EVENT";
    }

    @Override
    @SuppressWarnings("unchecked")
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String eventCode = (String) config.get("eventCode");
        Map<String, Object> params = (Map<String, Object>) config.get("params");

        if (eventCode == null || eventCode.isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "未配置事件编码");
        }

        if (params == null) {
            params = new HashMap<>();
        }

        context.info("执行事件处理: eventCode=" + eventCode);

        try {
            Optional<EventDefinition> optDef = eventDefinitionService.getByCode(eventCode);
            if (!optDef.isPresent()) {
                return DataPacket.error("EVENT_NOT_FOUND", "事件定义不存在: " + eventCode);
            }

            EventDefinition eventDef = optDef.get();
            String handlerType = eventDef.getHandlerType();
            String handlerConfig = eventDef.getHandlerConfig();

            if ("BUILTIN".equals(handlerType)) {
                return executeBuiltin(handlerConfig, input, params, context);
            } else if ("GROOVY".equals(handlerType)) {
                return executeGroovy(handlerConfig, input, params, context);
            } else {
                return DataPacket.error("UNSUPPORTED", "不支持的事件处理器类型: " + handlerType);
            }
        } catch (Exception e) {
            log.error("事件处理失败: eventCode={}", eventCode, e);
            context.error("事件处理失败: " + e.getMessage());
            return DataPacket.error("EVENT_ERROR", "事件处理失败: " + e.getMessage());
        }
    }

    /**
     * 执行内置事件处理
     */
    private DataPacket executeBuiltin(String handlerId, DataPacket input, Map<String, Object> params, ExecutionContext context) {
        context.info("执行内置事件: " + handlerId);

        List<Map<String, Object>> rows = input.getRows();
        if (rows == null) {
            rows = new ArrayList<>();
        }
        List<Map<String, Object>> resultRows = new ArrayList<>();

        for (Map<String, Object> row : rows) {
            Map<String, Object> resultRow = new LinkedHashMap<>(row);
            try {
                switch (handlerId) {
                    case "base64Encode":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> Base64.getEncoder().encodeToString(val.getBytes(StandardCharsets.UTF_8)));
                        break;
                    case "base64Decode":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> new String(Base64.getDecoder().decode(val), StandardCharsets.UTF_8));
                        break;
                    case "urlEncode":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField", val -> {
                            try { return URLEncoder.encode(val, "UTF-8"); } catch (Exception e) { return val; }
                        });
                        break;
                    case "urlDecode":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField", val -> {
                            try { return URLDecoder.decode(val, "UTF-8"); } catch (Exception e) { return val; }
                        });
                        break;
                    case "md5Hash":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> hashString(val, "MD5"));
                        break;
                    case "sha256Hash":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> hashString(val, "SHA-256"));
                        break;
                    case "maskPhone":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> val.replaceAll("(\\d{3})\\d{4}(\\d{4})", "$1****$2"));
                        break;
                    case "maskIdCard":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> val.length() > 10 ? val.substring(0, 6) + "********" + val.substring(val.length() - 4) : val);
                        break;
                    case "maskName":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                val -> val.length() > 1 ? val.charAt(0) + val.substring(1).replaceAll(".", "*") : val + "*");
                        break;
                    case "maskEmail":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField", val -> {
                            int atIdx = val.indexOf('@');
                            if (atIdx > 1) {
                                return val.charAt(0) + "***" + val.substring(atIdx);
                            }
                            return val;
                        });
                        break;
                    case "jsonToXml":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField", val -> {
                            try {
                                return simpleJsonToXml(val);
                            } catch (Exception e) {
                                return val;
                            }
                        });
                        break;
                    case "xmlToJson":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField", val -> {
                            try {
                                return simpleXmlToJson(val);
                            } catch (Exception e) {
                                return val;
                            }
                        });
                        break;
                    case "autoIncrement":
                        applyAutoIncrement(resultRow, params, context);
                        break;
                    case "autoIncrementPad4":
                        applyAutoIncrementPad4(resultRow, params, context);
                        break;
                    case "padLeft":
                        applyPadLeft(resultRow, params);
                        break;
                    case "pinyinInitial":
                        applyFieldTransform(resultRow, params, "sourceField", "targetField",
                                com.dataconnect.util.PinyinInitials::from);
                        break;
                    default:
                        context.warn("未知的内置事件处理器: " + handlerId);
                }
            } catch (Exception e) {
                log.warn("内置事件处理异常: handlerId={}, msg={}", handlerId, e.getMessage());
            }
            resultRows.add(resultRow);
        }

        return DataPacket.ofList(resultRows);
    }

    /**
     * 执行Groovy脚本事件
     */
    @SuppressWarnings("unchecked")
    private DataPacket executeGroovy(String script, DataPacket input, Map<String, Object> params, ExecutionContext context) {
        if (script == null || script.trim().isEmpty()) {
            return DataPacket.error("CONFIG_ERROR", "Groovy脚本为空");
        }

        context.info("执行Groovy事件脚本");

        try {
            ScriptEngineManager manager = new ScriptEngineManager();
            ScriptEngine engine = manager.getEngineByName("groovy");

            if (engine == null) {
                // Groovy引擎不可用，回退到内置处理器
                context.warn("Groovy引擎不可用，尝试作为内置处理器执行");
                return executeBuiltin(script.trim(), input, params, context);
            }

            engine.put("input", input);
            engine.put("params", params);
            engine.put("context", context);
            engine.put("log", log);

            Object result = engine.eval(script);

            if (result instanceof DataPacket) {
                return (DataPacket) result;
            } else if (result instanceof List) {
                List<Map<String, Object>> rows = new ArrayList<>();
                for (Object item : (List<?>) result) {
                    if (item instanceof Map) {
                        rows.add((Map<String, Object>) item);
                    }
                }
                return DataPacket.ofList(rows);
            } else if (result instanceof Map) {
                return DataPacket.of((Map<String, Object>) result);
            } else {
                // 返回脚本结果作为变量
                Map<String, Object> resultRow = new LinkedHashMap<>();
                if (input.getFirstRow() != null) {
                    resultRow.putAll(input.getFirstRow());
                }
                resultRow.put("_scriptResult", result);
                return DataPacket.of(resultRow);
            }
        } catch (Exception e) {
            log.error("Groovy脚本执行失败", e);
            return DataPacket.error("SCRIPT_ERROR", "Groovy脚本执行失败: " + e.getMessage());
        }
    }

    // ==================== 辅助方法 ====================

    private void applyAutoIncrementPad4(Map<String, Object> row, Map<String, Object> params, ExecutionContext context) {
        Map<String, Object> p = new LinkedHashMap<String, Object>(params);
        if (firstNonBlank(p.get("targetField"), p.get("sourceField")) == null) {
            p.put("targetField", "文件号");
        }
        if (parseLongParam(p.get("padLength"), 0L) <= 0) {
            p.put("padLength", 4);
        }
        applyAutoIncrement(row, p, context);
    }

    private void applyPadLeft(Map<String, Object> row, Map<String, Object> params) {
        String sourceField = firstNonBlank(params.get("sourceField"), params.get("targetField"));
        if (sourceField == null) {
            sourceField = "文件号";
        }
        String targetField = firstNonBlank(params.get("targetField"), sourceField);
        int pad = (int) parseLongParam(params.get("padLength"), 4L);
        if (pad <= 0) {
            pad = 4;
        }
        Object value = row.get(sourceField);
        if (value == null && row.get("文件号") != null && "文件号".equals(sourceField)) {
            value = row.get("文件号");
        }
        if (value == null) {
            return;
        }
        row.put(targetField, padLeft(value.toString(), pad));
    }

    public static String padLeft(String raw, int padLength) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.matches("\\d+\\.0+")) {
            s = s.substring(0, s.indexOf('.'));
        }
        if (padLength <= 0 || s.length() >= padLength) {
            return s;
        }
        StringBuilder sb = new StringBuilder(padLength);
        for (int i = s.length(); i < padLength; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }

    private void applyAutoIncrement(Map<String, Object> row, Map<String, Object> params, ExecutionContext context) {
        String targetField = firstNonBlank(params.get("targetField"), params.get("sourceField"));
        if (targetField == null || targetField.isEmpty()) {
            targetField = "seq";
        }
        long start = parseLongParam(params.get("startValue"), 1L);
        long step = parseLongParam(params.get("step"), 1L);
        if (step == 0) {
            step = 1L;
        }
        int pad = (int) parseLongParam(params.get("padLength"), 0L);
        String prefix = params.get("prefix") != null ? params.get("prefix").toString() : "";
        String groupField = params.get("groupField") != null ? params.get("groupField").toString().trim() : "";
        String groupVal = "";
        if (!groupField.isEmpty() && row.get(groupField) != null) {
            groupVal = String.valueOf(row.get(groupField));
        }
        String key = "_autoInc:" + targetField + ":" + groupVal;
        Object cur = context.getGlobalVariable(key);
        long next = cur instanceof Number ? ((Number) cur).longValue() + step : start;
        context.setGlobalVariable(key, next);
        row.put(targetField, formatIncrement(next, pad, prefix));
    }

    private static String formatIncrement(long value, int padLength, String prefix) {
        String num;
        if (padLength > 0) {
            String fmt = "%0" + padLength + "d";
            num = String.format(fmt, value);
        } else {
            num = String.valueOf(value);
        }
        if (prefix != null && !prefix.isEmpty()) {
            return prefix + num;
        }
        return num;
    }

    private static String firstNonBlank(Object a, Object b) {
        if (a != null && !a.toString().trim().isEmpty()) {
            return a.toString().trim();
        }
        if (b != null && !b.toString().trim().isEmpty()) {
            return b.toString().trim();
        }
        return null;
    }

    private static long parseLongParam(Object value, long defaultValue) {
        if (value == null || value.toString().trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void applyFieldTransform(Map<String, Object> row, Map<String, Object> params,
                                      String srcParam, String tgtParam, java.util.function.Function<String, String> transform) {
        String sourceField = (String) params.get(srcParam);
        if (sourceField == null) {
            // 也尝试驼峰命名
            sourceField = (String) params.get("sourceField");
        }
        if (sourceField == null) return;

        String targetField = (String) params.get(tgtParam);
        if (targetField == null) {
            targetField = (String) params.get("targetField");
        }
        if (targetField == null || targetField.isEmpty()) {
            targetField = sourceField; // 默认覆盖源字段
        }

        Object value = row.get(sourceField);
        if (value != null) {
            row.put(targetField, transform.apply(value.toString()));
        }
    }

    private String hashString(String input, String algorithm) {
        try {
            MessageDigest md = MessageDigest.getInstance(algorithm);
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return input;
        }
    }

    private String simpleJsonToXml(String json) {
        // 简单JSON到XML转换（单层对象）
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> map = mapper.readValue(json,
                    new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
            StringBuilder sb = new StringBuilder();
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<root>\n");
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                sb.append("  <").append(entry.getKey()).append(">")
                        .append(entry.getValue() != null ? entry.getValue().toString() : "")
                        .append("</").append(entry.getKey()).append(">\n");
            }
            sb.append("</root>");
            return sb.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private String simpleXmlToJson(String xml) {
        // 简单XML到JSON转换（提取标签内容）
        try {
            Pattern pattern = Pattern.compile("<(\\w+)>([^<]*)</\\1>");
            Matcher matcher = pattern.matcher(xml);
            Map<String, Object> map = new LinkedHashMap<>();
            while (matcher.find()) {
                map.put(matcher.group(1), matcher.group(2));
            }
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map);
        } catch (Exception e) {
            return xml;
        }
    }
}
