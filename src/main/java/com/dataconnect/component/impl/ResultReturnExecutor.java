package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 结果返回组件执行器
 * 作为流程的终止节点，返回最终结果
 */
@Component
public class ResultReturnExecutor implements ComponentExecutor {

    @Override
    public String getType() {
        return "RESULT_RETURN";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        context.info("结果返回: " + (input != null ? input.size() : 0) + " 条记录");

        // 直接返回输入数据
        if (input == null) {
            return DataPacket.empty();
        }

        // 可以根据配置选择返回字段
        String outputFields = getStringConfig(config, "outputFields", "");
        if (!outputFields.isEmpty()) {
            return selectFields(input, outputFields);
        }

        return input;
    }

    @Override
    public String[] getInputPorts() {
        return new String[]{"input"};
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{};  // 终止节点没有输出端口
    }

    /**
     * 选择指定字段
     */
    private DataPacket selectFields(DataPacket input, String outputFields) {
        String[] fields = outputFields.split(",");
        DataPacket result = DataPacket.empty();

        for (Map<String, Object> row : input.getRows()) {
            Map<String, Object> newRow = new java.util.LinkedHashMap<>();
            for (String field : fields) {
                String fieldName = field.trim();
                if (row.containsKey(fieldName)) {
                    newRow.put(fieldName, row.get(fieldName));
                }
            }
            result.getRows().add(newRow);
        }

        result.setDataType(DataPacket.DataType.LIST);
        return result;
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
