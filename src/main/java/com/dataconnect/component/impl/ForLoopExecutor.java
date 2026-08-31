package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * For循环组件执行器
 * 遍历集合数据
 */
@Component
public class ForLoopExecutor implements ComponentExecutor {

    @Override
    public String getType() {
        return "FOR_LOOP";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        // 获取循环配置
        String loopVar = getStringConfig(config, "loopVar", "item");
        String indexVar = getStringConfig(config, "indexVar", "loopIndex");
        String totalVar = getStringConfig(config, "totalVar", "loopTotal");

        // 如果输入为空，返回空结果
        if (input == null || input.isEmpty()) {
            context.debug("循环输入为空，跳过");
            return DataPacket.empty();
        }

        List<Map<String, Object>> rows = input.getRows();
        int total = rows.size();
        context.info("开始循环, 总数=" + total);

        // 注意：实际的循环控制由流程引擎处理
        // 这里只是设置循环变量，返回当前迭代的数据
        // 流程引擎会多次调用此组件，每次传入不同的索引

        // 从配置或变量中获取当前迭代索引
        Object indexObj = input.getVariable("_loopIndex");
        int currentIndex = 0;
        if (indexObj instanceof Number) {
            currentIndex = ((Number) indexObj).intValue();
        }

        if (currentIndex >= total) {
            context.debug("循环结束, currentIndex=" + currentIndex);
            return DataPacket.empty();
        }

        // 获取当前行
        Map<String, Object> currentRow = new LinkedHashMap<>(rows.get(currentIndex));

        // 设置循环变量
        currentRow.put(loopVar, currentRow);
        currentRow.put(indexVar, currentIndex);
        currentRow.put(totalVar, total);
        currentRow.put("isFirst", currentIndex == 0);
        currentRow.put("isLast", currentIndex == total - 1);

        // 设置控制信号（如果不是最后一行，继续循环）
        DataPacket result = DataPacket.of(currentRow);
        result.setVariable(loopVar, currentRow);
        result.setVariable(indexVar, currentIndex);
        result.setVariable(totalVar, total);

        context.debug("循环迭代: " + currentIndex + "/" + total);
        return result;
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        // 循环组件不需要特殊配置验证
        return null;
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"currentItem", "index", "total"};
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
