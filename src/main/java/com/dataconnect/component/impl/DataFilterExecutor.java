package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据过滤组件执行器
 * 根据条件过滤数据
 */
@Component
public class DataFilterExecutor implements ComponentExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "DATA_FILTER";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        if (input == null || input.isEmpty()) {
            context.debug("数据过滤: 输入为空");
            return DataPacket.empty();
        }

        // 获取过滤配置
        List<Map<String, Object>> conditions = getConditions(config);
        String logic = getStringConfig(config, "logic", "AND");
        boolean caseSensitive = getBooleanConfig(config, "caseSensitive", true);

        context.info("数据过滤: " + conditions.size() + " 个条件, logic=" + logic);

        List<Map<String, Object>> filteredRows = new ArrayList<>();

        for (Map<String, Object> row : input.getRows()) {
            boolean match = evaluateConditions(row, conditions, logic, caseSensitive);
            if (match) {
                filteredRows.add(row);
            }
        }

        context.info("数据过滤完成: " + input.size() + " -> " + filteredRows.size() + " 条记录");
        return DataPacket.ofList(filteredRows);
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        List<Map<String, Object>> conditions = getConditions(config);
        if (conditions.isEmpty()) {
            return "请配置过滤条件";
        }
        return null;
    }

    /**
     * 获取过滤条件
     */
    private List<Map<String, Object>> getConditions(Map<String, Object> config) {
        Object conditionsObj = config.get("conditions");
        if (conditionsObj instanceof List) {
            try {
                return objectMapper.convertValue(conditionsObj, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        // 尝试从JSON字符串解析
        if (conditionsObj instanceof String) {
            try {
                return objectMapper.readValue((String) conditionsObj, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    /**
     * 评估条件
     */
    private boolean evaluateConditions(Map<String, Object> row, List<Map<String, Object>> conditions, 
                                       String logic, boolean caseSensitive) {
        if (conditions.isEmpty()) {
            return true;
        }

        boolean isAnd = "AND".equalsIgnoreCase(logic);

        for (Map<String, Object> condition : conditions) {
            String field = getStringValue(condition, "field", "");
            String operator = getStringValue(condition, "operator", "==");
            String value = getStringValue(condition, "value", "");
            boolean conditionCaseSensitive = getBooleanValue(condition, "caseSensitive", caseSensitive);

            boolean match = evaluateCondition(row, field, operator, value, conditionCaseSensitive);

            if (isAnd && !match) {
                return false;  // AND模式，一个不满足就返回false
            }
            if (!isAnd && match) {
                return true;   // OR模式，一个满足就返回true
            }
        }

        return isAnd;  // AND模式全部满足返回true，OR模式全部不满足返回false
    }

    /**
     * 评估单个条件
     */
    private boolean evaluateCondition(Map<String, Object> row, String field, String operator, 
                                      String compareValue, boolean caseSensitive) {
        Object fieldValue = row.get(field);

        // 空值处理
        if (fieldValue == null) {
            return "IS_NULL".equals(operator) || "IS_EMPTY".equals(operator);
        }

        String fieldStr = String.valueOf(fieldValue);
        if (!caseSensitive) {
            fieldStr = fieldStr.toLowerCase();
            compareValue = compareValue.toLowerCase();
        }

        switch (operator) {
            case "==":
                return fieldStr.equals(compareValue);
            case "!=":
                return !fieldStr.equals(compareValue);
            case ">":
                return compareNumeric(fieldStr, compareValue) > 0;
            case "<":
                return compareNumeric(fieldStr, compareValue) < 0;
            case ">=":
                return compareNumeric(fieldStr, compareValue) >= 0;
            case "<=":
                return compareNumeric(fieldStr, compareValue) <= 0;
            case "LIKE":
                return fieldStr.contains(compareValue);
            case "NOT_LIKE":
                return !fieldStr.contains(compareValue);
            case "STARTS_WITH":
                return fieldStr.startsWith(compareValue);
            case "ENDS_WITH":
                return fieldStr.endsWith(compareValue);
            case "CONTAINS":
                return fieldStr.contains(compareValue);
            case "NOT_CONTAINS":
                return !fieldStr.contains(compareValue);
            case "IS_NULL":
                return false;
            case "IS_NOT_NULL":
                return true;
            case "IS_EMPTY":
                return fieldStr.isEmpty();
            case "IS_NOT_EMPTY":
                return !fieldStr.isEmpty();
            case "IN":
                return Arrays.asList(compareValue.split(",")).contains(fieldStr.trim());
            case "NOT_IN":
                return !Arrays.asList(compareValue.split(",")).contains(fieldStr.trim());
            case "REGEX":
                return fieldStr.matches(compareValue);
            default:
                return false;
        }
    }

    /**
     * 数字比较
     */
    private int compareNumeric(String a, String b) {
        try {
            double numA = Double.parseDouble(a);
            double numB = Double.parseDouble(b);
            return Double.compare(numA, numB);
        } catch (NumberFormatException e) {
            return a.compareTo(b);
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

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }
}
