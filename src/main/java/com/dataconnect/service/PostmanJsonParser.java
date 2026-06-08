package com.dataconnect.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class PostmanJsonParser {

    private static final Logger log = LoggerFactory.getLogger(PostmanJsonParser.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<Map<String, String>> parse(String postmanJson) {
        List<Map<String, String>> result = new ArrayList<>();
        try {
            Map<String, Object> root = objectMapper.readValue(postmanJson,
                    new TypeReference<Map<String, Object>>() {});
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) root.getOrDefault("item", Collections.emptyList());
            for (Map<String, Object> item : items) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> responses = (List<Map<String, Object>>) item.get("response");
                if (responses != null) {
                    for (Map<String, Object> response : responses) {
                        String bodyStr = (String) response.get("body");
                        if (bodyStr == null || bodyStr.isEmpty()) continue;
                        try {
                            Object parsed = objectMapper.readValue(bodyStr, Object.class);
                            extractFields(parsed, "", result);
                        } catch (Exception e) {
                            log.debug("Not valid JSON body in Postman response, skipping");
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("Failed to parse Postman JSON: {}", e.getMessage());
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private void extractFields(Object obj, String prefix, List<Map<String, String>> result) {
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                Object value = entry.getValue();
                if (value instanceof Map || value instanceof List) {
                    extractFields(value, key, result);
                } else {
                    Map<String, String> kv = new LinkedHashMap<>();
                    kv.put("key", key);
                    kv.put("value", value != null ? String.valueOf(value) : "");
                    result.add(kv);
                }
            }
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            for (int i = 0; i < list.size(); i++) {
                extractFields(list.get(i), prefix + "[" + i + "]", result);
            }
        }
    }
}
