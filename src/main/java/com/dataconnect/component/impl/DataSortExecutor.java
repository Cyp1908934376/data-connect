package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 数据排序组件执行器
 * 按字段排序数据
 */
@Component
public class DataSortExecutor implements ComponentExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "DATA_SORT";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        if (input == null || input.isEmpty()) {
            context.debug("数据排序: 输入为空");
            return DataPacket.empty();
        }

        // 获取排序配置
        List<Map<String, Object>> sortFields = getSortFieldsConfig(config);
        boolean caseSensitive = getBooleanConfig(config, "caseSensitive", true);

        context.info("数据排序: " + sortFields.size() + " 个排序字段");

        // 复制数据避免修改原始数据
        List<Map<String, Object>> sortedRows = new ArrayList<>(input.getRows());

        // 执行排序
        sortedRows.sort((row1, row2) -> {
            for (Map<String, Object> sortField : sortFields) {
                String field = (String) sortField.get("field");
                boolean ascending = !Boolean.FALSE.equals(sortField.get("ascending"));

                Object val1 = row1.get(field);
                Object val2 = row2.get(field);

                int cmp = compareValues(val1, val2, caseSensitive);
                if (cmp != 0) {
                    return ascending ? cmp : -cmp;
                }
            }
            return 0;
        });

        context.info("数据排序完成: " + sortedRows.size() + " 条记录");
        return DataPacket.ofList(sortedRows);
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        List<Map<String, Object>> sortFields = getSortFieldsConfig(config);
        if (sortFields.isEmpty()) {
            return "请配置排序字段";
        }
        return null;
    }

    /**
     * 获取排序字段配置
     */
    private List<Map<String, Object>> getSortFieldsConfig(Map<String, Object> config) {
        Object value = config.get("sortFields");
        if (value instanceof List) {
            try {
                return objectMapper.convertValue(value, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        if (value instanceof String) {
            try {
                return objectMapper.readValue((String) value, new TypeReference<List<Map<String, Object>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }
        return new ArrayList<>();
    }

    /**
     * 比较两个值
     */
    private int compareValues(Object val1, Object val2, boolean caseSensitive) {
        // 处理null值
        if (val1 == null && val2 == null) return 0;
        if (val1 == null) return -1;
        if (val2 == null) return 1;

        // 数字比较
        if (val1 instanceof Number && val2 instanceof Number) {
            double d1 = ((Number) val1).doubleValue();
            double d2 = ((Number) val2).doubleValue();
            return Double.compare(d1, d2);
        }

        // 字符串比较
        String s1 = String.valueOf(val1);
        String s2 = String.valueOf(val2);
        if (!caseSensitive) {
            s1 = s1.toLowerCase();
            s2 = s2.toLowerCase();
        }
        return s1.compareTo(s2);
    }

    private boolean getBooleanConfig(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) return (Boolean) value;
        if (value instanceof String) return Boolean.parseBoolean((String) value);
        return defaultValue;
    }
}
