package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 类型转换组件执行器
 * 转换字段的数据类型
 */
@Component
public class TypeConvertExecutor implements ComponentExecutor {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String getType() {
        return "TYPE_CONVERT";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        if (input == null || input.isEmpty()) {
            context.debug("类型转换: 输入为空");
            return DataPacket.empty();
        }

        // 获取转换配置
        List<Map<String, String>> conversions = getConversions(config);
        String errorHandling = getStringConfig(config, "errorHandling", "SKIP");
        String defaultValue = getStringConfig(config, "defaultValue", "");

        context.info("类型转换: " + conversions.size() + " 个字段");

        List<Map<String, Object>> resultRows = new ArrayList<>();
        int errorCount = 0;

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> newRow = new LinkedHashMap<>(row);
            boolean hasError = false;

            for (Map<String, String> conversion : conversions) {
                String field = conversion.get("field");
                String targetType = conversion.get("targetType");
                String format = conversion.get("format");

                if (field == null || targetType == null) {
                    continue;
                }

                Object value = newRow.get(field);
                try {
                    Object converted = convertValue(value, targetType, format);
                    newRow.put(field, converted);
                } catch (Exception e) {
                    errorCount++;
                    if ("ERROR".equalsIgnoreCase(errorHandling)) {
                        return DataPacket.error("CONVERT_ERROR", "字段 " + field + " 转换失败: " + e.getMessage());
                    } else if ("DEFAULT".equalsIgnoreCase(errorHandling)) {
                        newRow.put(field, defaultValue);
                    } else {
                        // SKIP: 跳过该字段，保持原值
                        hasError = true;
                    }
                }
            }

            if (!hasError || !"SKIP".equalsIgnoreCase(errorHandling)) {
                resultRows.add(newRow);
            }
        }

        if (errorCount > 0) {
            context.warn("类型转换: " + errorCount + " 个字段转换失败");
        }

        context.info("类型转换完成: " + resultRows.size() + " 条记录");
        return DataPacket.ofList(resultRows);
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        List<Map<String, String>> conversions = getConversions(config);
        if (conversions.isEmpty()) {
            return "请配置转换规则";
        }
        return null;
    }

    /**
     * 获取转换配置
     */
    private List<Map<String, String>> getConversions(Map<String, Object> config) {
        Object conversionsObj = config.get("conversions");
        if (conversionsObj instanceof List) {
            try {
                return objectMapper.convertValue(conversionsObj, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        // 尝试从JSON字符串解析
        if (conversionsObj instanceof String) {
            try {
                return objectMapper.readValue((String) conversionsObj, new TypeReference<List<Map<String, String>>>() {});
            } catch (Exception e) {
                return new ArrayList<>();
            }
        }

        return new ArrayList<>();
    }

    /**
     * 转换值
     */
    private Object convertValue(Object value, String targetType, String format) {
        if (value == null) {
            return null;
        }

        String strValue = String.valueOf(value).trim();

        switch (targetType.toUpperCase()) {
            case "STRING":
                return strValue;
            case "INTEGER":
            case "INT":
                return Integer.parseInt(strValue);
            case "LONG":
                return Long.parseLong(strValue);
            case "DOUBLE":
                return Double.parseDouble(strValue);
            case "FLOAT":
                return Float.parseFloat(strValue);
            case "BOOLEAN":
                return parseBoolean(strValue);
            case "DATE":
                return parseDate(strValue, format);
            case "NUMBER":
                return parseNumber(strValue);
            default:
                return value;
        }
    }

    /**
     * 解析布尔值
     */
    private boolean parseBoolean(String value) {
        String lower = value.toLowerCase();
        return "true".equals(lower) || "1".equals(lower) || "yes".equals(lower) || "y".equals(lower);
    }

    /**
     * 解析数字
     */
    private Number parseNumber(String value) {
        try {
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 解析日期
     */
    private Date parseDate(String value, String format) {
        if (format == null || format.isEmpty()) {
            format = "yyyy-MM-dd HH:mm:ss";
        }

        try {
            SimpleDateFormat sdf = new SimpleDateFormat(format);
            return sdf.parse(value);
        } catch (ParseException e) {
            // 尝试其他格式
            String[] formats = {
                "yyyy-MM-dd",
                "yyyy/MM/dd",
                "dd-MM-yyyy",
                "MM/dd/yyyy",
                "yyyyMMdd"
            };

            for (String fmt : formats) {
                try {
                    return new SimpleDateFormat(fmt).parse(value);
                } catch (ParseException ignored) {
                }
            }

            throw new RuntimeException("无法解析日期: " + value);
        }
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
