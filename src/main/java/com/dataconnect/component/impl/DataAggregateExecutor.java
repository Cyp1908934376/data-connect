package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据聚合组件执行器
 * 按字段分组统计
 */
@Component
public class DataAggregateExecutor implements ComponentExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "DATA_AGGREGATE";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        if (input == null || input.isEmpty()) {
            context.debug("数据聚合: 输入为空");
            return DataPacket.empty();
        }

        // 获取配置
        List<String> groupByFields = getStringListConfig(config, "groupByFields");
        List<Map<String, String>> functions = getFunctionListConfig(config, "functions");

        context.info("数据聚合: 分组字段=" + groupByFields + ", 聚合函数数=" + functions.size());

        // 执行聚合
        List<Map<String, Object>> resultRows;

        if (groupByFields.isEmpty()) {
            // 全局聚合
            resultRows = aggregateGlobal(input.getRows(), functions);
        } else {
            // 分组聚合
            resultRows = aggregateGroupBy(input.getRows(), groupByFields, functions);
        }

        context.info("数据聚合完成: " + resultRows.size() + " 条记录");
        return DataPacket.ofList(resultRows);
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        List<Map<String, String>> functions = getFunctionListConfig(config, "functions");
        if (functions.isEmpty()) {
            return "请配置聚合函数";
        }
        return null;
    }

    /**
     * 全局聚合
     */
    private List<Map<String, Object>> aggregateGlobal(List<Map<String, Object>> rows, 
                                                        List<Map<String, String>> functions) {
        Map<String, Object> result = new LinkedHashMap<>();

        for (Map<String, String> func : functions) {
            String function = func.get("function");
            String sourceField = func.get("sourceField");
            String targetField = func.get("targetField");

            if (targetField == null || targetField.isEmpty()) {
                targetField = function.toLowerCase() + "_" + (sourceField != null ? sourceField : "all");
            }

            result.put(targetField, calculateAggregate(rows, function, sourceField));
        }

        List<Map<String, Object>> resultList = new ArrayList<>();
        resultList.add(result);
        return resultList;
    }

    /**
     * 分组聚合
     */
    private List<Map<String, Object>> aggregateGroupBy(List<Map<String, Object>> rows,
                                                         List<String> groupByFields,
                                                         List<Map<String, String>> functions) {
        // 分组
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            String groupKey = buildGroupKey(row, groupByFields);
            groups.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(row);
        }

        // 对每组执行聚合
        List<Map<String, Object>> resultRows = new ArrayList<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : groups.entrySet()) {
            Map<String, Object> resultRow = new LinkedHashMap<>();

            // 添加分组字段值
            List<Map<String, Object>> groupRows = entry.getValue();
            if (!groupRows.isEmpty()) {
                Map<String, Object> firstRow = groupRows.get(0);
                for (String field : groupByFields) {
                    resultRow.put(field, firstRow.get(field));
                }
            }

            // 执行聚合函数
            for (Map<String, String> func : functions) {
                String function = func.get("function");
                String sourceField = func.get("sourceField");
                String targetField = func.get("targetField");

                if (targetField == null || targetField.isEmpty()) {
                    targetField = function.toLowerCase() + "_" + (sourceField != null ? sourceField : "all");
                }

                resultRow.put(targetField, calculateAggregate(groupRows, function, sourceField));
            }

            resultRows.add(resultRow);
        }

        return resultRows;
    }

    /**
     * 构建分组键
     */
    private String buildGroupKey(Map<String, Object> row, List<String> groupByFields) {
        StringBuilder key = new StringBuilder();
        for (String field : groupByFields) {
            if (key.length() > 0) key.append("|");
            key.append(row.getOrDefault(field, "NULL"));
        }
        return key.toString();
    }

    /**
     * 计算聚合值
     */
    private Object calculateAggregate(List<Map<String, Object>> rows, String function, String sourceField) {
        switch (function.toUpperCase()) {
            case "COUNT":
                return rows.size();
            case "COUNT_DISTINCT":
                return countDistinct(rows, sourceField);
            case "SUM":
                return sum(rows, sourceField);
            case "AVG":
                return avg(rows, sourceField);
            case "MIN":
                return min(rows, sourceField);
            case "MAX":
                return max(rows, sourceField);
            case "FIRST":
                return rows.isEmpty() ? null : rows.get(0).get(sourceField);
            case "LAST":
                return rows.isEmpty() ? null : rows.get(rows.size() - 1).get(sourceField);
            default:
                return null;
        }
    }

    private int countDistinct(List<Map<String, Object>> rows, String field) {
        Set<Object> distinct = new HashSet<>();
        for (Map<String, Object> row : rows) {
            distinct.add(row.get(field));
        }
        return distinct.size();
    }

    private double sum(List<Map<String, Object>> rows, String field) {
        double total = 0;
        for (Map<String, Object> row : rows) {
            Object val = row.get(field);
            if (val instanceof Number) {
                total += ((Number) val).doubleValue();
            }
        }
        return total;
    }

    private double avg(List<Map<String, Object>> rows, String field) {
        if (rows.isEmpty()) return 0;
        return sum(rows, field) / rows.size();
    }

    private Object min(List<Map<String, Object>> rows, String field) {
        Object min = null;
        for (Map<String, Object> row : rows) {
            Object val = row.get(field);
            if (val != null && (min == null || compareValues(val, min) < 0)) {
                min = val;
            }
        }
        return min;
    }

    private Object max(List<Map<String, Object>> rows, String field) {
        Object max = null;
        for (Map<String, Object> row : rows) {
            Object val = row.get(field);
            if (val != null && (max == null || compareValues(val, max) > 0)) {
                max = val;
            }
        }
        return max;
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a instanceof Comparable && b instanceof Comparable) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return String.valueOf(a).compareTo(String.valueOf(b));
    }

    private List<String> getStringListConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            try {
                return objectMapper.convertValue(value, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return Arrays.asList(((String) value).split(","));
            }
        }
        return new ArrayList<>();
    }

    private List<Map<String, String>> getFunctionListConfig(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof List) {
            try {
                return objectMapper.convertValue(value, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }
}
