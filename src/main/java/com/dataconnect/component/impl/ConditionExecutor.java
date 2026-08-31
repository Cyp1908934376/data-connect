package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 条件判断组件执行器
 * 根据条件表达式判断数据流向
 */
@Component
public class ConditionExecutor implements ComponentExecutor {

    @Override
    public String getType() {
        return "CONDITION";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String expression = getStringConfig(config, "expression", "true");
        context.info("条件判断: " + expression);

        try {
            // 简单的条件评估
            boolean result = evaluateExpression(input, expression, config);
            context.info("条件结果: " + result);

            // 设置条件结果变量
            DataPacket output = input.copy();
            output.setVariable("conditionResult", result);

            return output;
        } catch (Exception e) {
            context.error("条件判断失败: " + e.getMessage());
            return DataPacket.error("CONDITION_ERROR", "条件判断失败: " + e.getMessage());
        }
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        String expression = getStringConfig(config, "expression", "");
        if (expression.isEmpty()) {
            return "条件表达式不能为空";
        }
        return null;
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"true", "false"};
    }

    /**
     * 评估条件表达式
     */
    private boolean evaluateExpression(DataPacket input, String expression, Map<String, Object> config) {
        // 获取比较配置
        String field = getStringConfig(config, "field", "");
        String operator = getStringConfig(config, "operator", "==");
        String compareValue = getStringConfig(config, "value", "");
        boolean caseSensitive = getBooleanConfig(config, "caseSensitive", true);

        // 如果有字段配置，使用字段比较
        if (!field.isEmpty()) {
            Object fieldValue = input.getValue(field);
            return compareValues(fieldValue, operator, compareValue, caseSensitive);
        }

        // 否则使用简单表达式评估
        return evaluateSimpleExpression(input, expression);
    }

    /**
     * 比较值
     */
    private boolean compareValues(Object fieldValue, String operator, String compareValue, boolean caseSensitive) {
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
            case "IS_NULL":
                return false;
            case "IS_NOT_NULL":
                return true;
            case "IS_EMPTY":
                return fieldStr.isEmpty();
            case "IS_NOT_EMPTY":
                return !fieldStr.isEmpty();
            case "IN":
                return java.util.Arrays.asList(compareValue.split(",")).contains(fieldStr);
            case "NOT_IN":
                return !java.util.Arrays.asList(compareValue.split(",")).contains(fieldStr);
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

    /**
     * 简单表达式评估
     */
    private boolean evaluateSimpleExpression(DataPacket input, String expression) {
        // 简单实现：检查表达式中的变量
        // 替换变量
        String evaluated = expression;
        if (input.getVariables() != null) {
            for (Map.Entry<String, Object> entry : input.getVariables().entrySet()) {
                evaluated = evaluated.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        // 处理布尔值
        evaluated = evaluated.trim().toLowerCase();
        return "true".equals(evaluated) || "1".equals(evaluated) || "yes".equals(evaluated);
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
