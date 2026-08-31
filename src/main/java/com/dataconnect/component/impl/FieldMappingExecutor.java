package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 字段映射组件执行器
 * 将输入字段映射到输出字段
 */
@Component
public class FieldMappingExecutor implements ComponentExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "FIELD_MAPPING";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        if (input == null || input.isEmpty()) {
            context.debug("字段映射: 输入为空");
            return DataPacket.empty();
        }

        // 获取映射规则
        List<Map<String, String>> mappings = getMappings(config);
        boolean keepUnmapped = getBooleanConfig(config, "keepUnmapped", true);
        String defaultValue = getStringConfig(config, "defaultValue", "");

        context.info("字段映射: " + mappings.size() + " 条规则, keepUnmapped=" + keepUnmapped);

        List<Map<String, Object>> resultRows = new ArrayList<>();

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> newRow = new LinkedHashMap<>();

            // 应用映射规则
            for (Map<String, String> mapping : mappings) {
                String sourceField = mapping.get("source");
                String targetField = mapping.get("target");
                String transform = mapping.get("transform");

                if (sourceField == null || targetField == null) {
                    continue;
                }

                Object value = row.get(sourceField);

                // 处理空值默认值
                if (value == null && !defaultValue.isEmpty()) {
                    value = defaultValue;
                }

                // 应用转换
                if (transform != null && !transform.isEmpty()) {
                    value = applyTransform(value, transform, row);
                }

                newRow.put(targetField, value);
            }

            // 保留未映射字段
            if (keepUnmapped) {
                for (Map.Entry<String, Object> entry : row.entrySet()) {
                    if (!newRow.containsKey(entry.getKey())) {
                        newRow.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            resultRows.add(newRow);
        }

        context.info("字段映射完成: " + resultRows.size() + " 条记录");
        return DataPacket.ofList(resultRows);
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        List<Map<String, String>> mappings = getMappings(config);
        if (mappings.isEmpty()) {
            return "请配置映射规则";
        }
        return null;
    }

    /**
     * 获取映射规则
     */
    private List<Map<String, String>> getMappings(Map<String, Object> config) {
        Object mappingsObj = config.get("mappings");
        if (mappingsObj instanceof List) {
            try {
                return objectMapper.convertValue(mappingsObj, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        // 尝试从JSON字符串解析
        if (mappingsObj instanceof String) {
            try {
                return objectMapper.readValue((String) mappingsObj, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    /**
     * 应用转换
     */
    private Object applyTransform(Object value, String transform, Map<String, Object> row) {
        if (value == null) return null;

        String strValue = String.valueOf(value);

        // 内置转换函数
        if ("UPPER".equalsIgnoreCase(transform)) {
            return strValue.toUpperCase();
        }
        if ("LOWER".equalsIgnoreCase(transform)) {
            return strValue.toLowerCase();
        }
        if ("TRIM".equalsIgnoreCase(transform)) {
            return strValue.trim();
        }
        if ("LENGTH".equalsIgnoreCase(transform)) {
            return strValue.length();
        }
        if ("REVERSE".equalsIgnoreCase(transform)) {
            return new StringBuilder(strValue).reverse().toString();
        }

        // 值映射: MAP(val1,result1,val2,result2)
        if (transform.toUpperCase().startsWith("MAP(")) {
            return applyMapTransform(strValue, transform);
        }

        // 条件: IF(condition,trueVal,falseVal)
        if (transform.toUpperCase().startsWith("IF(")) {
            return applyIfTransform(strValue, transform);
        }

        // 数学运算: MULTIPLY(factor), DIVIDE(divisor), ROUND(decimals)
        if (transform.toUpperCase().startsWith("MULTIPLY(")) {
            return applyMathTransform(strValue, transform, "MULTIPLY");
        }
        if (transform.toUpperCase().startsWith("DIVIDE(")) {
            return applyMathTransform(strValue, transform, "DIVIDE");
        }
        if (transform.toUpperCase().startsWith("ROUND(")) {
            return applyMathTransform(strValue, transform, "ROUND");
        }

        // 默认返回原值
        return value;
    }

    /**
     * 值映射转换
     */
    private String applyMapTransform(String value, String transform) {
        // MAP(val1,result1,val2,result2)
        String params = transform.substring(4, transform.length() - 1);
        String[] pairs = params.split(",");
        for (int i = 0; i < pairs.length - 1; i += 2) {
            if (pairs[i].trim().equals(value)) {
                return pairs[i + 1].trim();
            }
        }
        return value;
    }

    /**
     * 条件转换
     */
    private String applyIfTransform(String value, String transform) {
        // 简化实现：检查值是否为真
        boolean isTrue = !value.isEmpty() && !"0".equals(value) && !"false".equals(value);
        String params = transform.substring(3, transform.length() - 1);
        String[] parts = params.split(",", 2);
        if (parts.length == 2) {
            return isTrue ? parts[0].trim() : parts[1].trim();
        }
        return value;
    }

    /**
     * 数学运算转换
     */
    private Object applyMathTransform(String value, String transform, String operation) {
        try {
            double num = Double.parseDouble(value);
            String params = transform.substring(operation.length() + 1, transform.length() - 1);
            double factor = Double.parseDouble(params.trim());

            switch (operation) {
                case "MULTIPLY":
                    return num * factor;
                case "DIVIDE":
                    return factor != 0 ? num / factor : num;
                case "ROUND":
                    double multiplier = Math.pow(10, factor);
                    return Math.round(num * multiplier) / multiplier;
                default:
                    return value;
            }
        } catch (NumberFormatException e) {
            return value;
        }
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }
}
