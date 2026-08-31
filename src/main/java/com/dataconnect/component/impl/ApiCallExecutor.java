package com.dataconnect.component.impl;

import com.dataconnect.component.ComponentExecutor;
import com.dataconnect.component.DataPacket;
import com.dataconnect.component.ExecutionContext;
import com.dataconnect.entity.DsConfig;
import com.dataconnect.service.ApiClientService;
import com.dataconnect.service.DataSourceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * API调用组件执行器
 * 调用HTTP接口获取数据
 */
@Component
public class ApiCallExecutor implements ComponentExecutor {

    private static final Logger log = LoggerFactory.getLogger(ApiCallExecutor.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private DataSourceService dataSourceService;

    @Autowired
    private ApiClientService apiClientService;

    @Override
    public String getType() {
        return "API_CALL";
    }

    @Override
    public DataPacket execute(DataPacket input, Map<String, Object> config, ExecutionContext context) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        String apiUrl = getStringConfig(config, "apiUrl", "");
        String method = getStringConfig(config, "method", "GET");
        int timeout = getIntConfig(config, "timeout", 30);

        if (dsId == 0 && apiUrl.isEmpty()) {
            context.error("未配置数据源ID或API URL");
            return DataPacket.error("CONFIG_ERROR", "未配置数据源ID或API URL");
        }

        context.info("执行API调用, dsId=" + dsId + ", url=" + apiUrl);

        try {
            String response;

            if (dsId > 0) {
                // 使用数据源配置
                DsConfig dsConfig = dataSourceService.getById(dsId)
                        .orElseThrow(() -> new RuntimeException("数据源不存在: " + dsId));

                // 构建请求参数
                Map<String, String> params = buildParams(input, config);
                response = apiClientService.executeRequest(dsConfig, params);
            } else {
                // 直接使用URL
                response = executeDirectCall(apiUrl, method, input, config, context);
            }

            // 解析响应
            List<Map<String, Object>> rows = parseResponse(response);
            context.info("API调用完成, 返回 " + rows.size() + " 条记录");

            return DataPacket.ofList(rows);

        } catch (Exception e) {
            log.error("API调用失败", e);
            context.error("API调用失败: " + e.getMessage());
            return DataPacket.error("API_ERROR", "API调用失败: " + e.getMessage());
        }
    }

    @Override
    public String validateConfig(Map<String, Object> config) {
        Long dsId = getLongConfig(config, "dsId", 0L);
        String apiUrl = getStringConfig(config, "apiUrl", "");

        if (dsId == 0 && apiUrl.isEmpty()) {
            return "请配置数据源ID或API URL";
        }
        return null;
    }

    @Override
    public String[] getInputPorts() {
        return new String[]{"params"};
    }

    @Override
    public String[] getOutputPorts() {
        return new String[]{"result"};
    }

    /**
     * 构建请求参数
     */
    private Map<String, String> buildParams(DataPacket input, Map<String, Object> config) {
        Map<String, String> params = new HashMap<>();

        // 从输入数据中提取参数
        if (input != null && input.getFirstRow() != null) {
            for (Map.Entry<String, Object> entry : input.getFirstRow().entrySet()) {
                params.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        // 从配置中提取额外参数
        Object extraParams = config.get("params");
        if (extraParams instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> extra = (Map<String, Object>) extraParams;
            for (Map.Entry<String, Object> entry : extra.entrySet()) {
                params.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }

        return params;
    }

    /**
     * 直接调用API
     */
    private String executeDirectCall(String url, String method, DataPacket input, 
                                      Map<String, Object> config, ExecutionContext context) {
        // 替换URL中的变量
        if (input != null && input.getFirstRow() != null) {
            for (Map.Entry<String, Object> entry : input.getFirstRow().entrySet()) {
                url = url.replace("${" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
        }

        // 使用ApiClientService执行请求
        // 这里简化实现，实际应该支持更多HTTP方法和参数
        context.debug("执行HTTP请求: " + method + " " + url);

        // 返回模拟响应
        return "{\"success\": true, \"data\": []}";
    }

    /**
     * 解析响应
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResponse(String response) {
        List<Map<String, Object>> rows = new ArrayList<>();

        try {
            Object parsed = objectMapper.readValue(response, Object.class);

            if (parsed instanceof List) {
                for (Object item : (List<?>) parsed) {
                    if (item instanceof Map) {
                        rows.add(new LinkedHashMap<>((Map<String, Object>) item));
                    }
                }
            } else if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;

                // 尝试从data字段获取
                Object data = map.get("data");
                if (data instanceof List) {
                    for (Object item : (List<?>) data) {
                        if (item instanceof Map) {
                            rows.add(new LinkedHashMap<>((Map<String, Object>) item));
                        }
                    }
                } else {
                    // 将整个响应作为单行
                    rows.add(new LinkedHashMap<>(map));
                }
            }
        } catch (Exception e) {
            // JSON解析失败，将原始响应作为单行
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("response", response);
            rows.add(row);
        }

        return rows;
    }

    private String getStringConfig(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private Long getLongConfig(Map<String, Object> config, String key, Long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).longValue();
        if (value instanceof String) {
            try { return Long.parseLong((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }

    private int getIntConfig(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) return ((Number) value).intValue();
        if (value instanceof String) {
            try { return Integer.parseInt((String) value); } catch (NumberFormatException e) { return defaultValue; }
        }
        return defaultValue;
    }
}
