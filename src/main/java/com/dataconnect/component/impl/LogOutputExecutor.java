package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 日志输出组件执行器
 * 输出日志信息
 */
@Component
public class LogOutputExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(LogOutputExecutor.class);

    @Override
    public String getType() {
        return "LOG_OUTPUT";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        String level = getStringConfig(config, "level", "INFO");
        String message = getStringConfig(config, "message", "");

        // 替换消息中的变量
        String finalMessage = replaceVariables(message, input);

        // 输出日志
        switch (level.toUpperCase()) {
            case "DEBUG":
                context.debug(finalMessage);
                log.debug(finalMessage);
                break;
            case "WARN":
                context.warn(finalMessage);
                log.warn(finalMessage);
                break;
            case "ERROR":
                context.error(finalMessage);
                log.error(finalMessage);
                break;
            default:
                context.info(finalMessage);
                log.info(finalMessage);
                break;
        }

        // 透传输入数据
        return input != null ? input : DataPacket.empty();
    }

    @Override
    public String[] getInputPorts() {
        return new String[]{"input"};
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"output"};
    }

    /**
     * 替换消息中的变量
     */
    private String replaceVariables(String message, DataPacket input) {
        if (input == null || message.isEmpty()) {
            return message;
        }

        String result = message;

        // 替换变量
        if (input.getVariables() != null) {
            for (Map.Entry<String, Object> entry : input.getVariables().entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        // 替换第一行数据中的字段
        if (input.getFirstRow() != null) {
            for (Map.Entry<String, Object> entry : input.getFirstRow().entrySet()) {
                result = result.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        // 替换特殊变量
        result = result.replace("${rowCount}", String.valueOf(input.size()));
        result = result.replace("${timestamp}", String.valueOf(System.currentTimeMillis()));

        return result;
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
}
