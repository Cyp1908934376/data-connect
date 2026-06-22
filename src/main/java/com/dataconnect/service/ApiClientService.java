package com.dataconnect.service;

import com.dataconnect.entity.DsConfig;
import com.dataconnect.entity.TemplateEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class ApiClientService {

    private static final Logger log = LoggerFactory.getLogger(ApiClientService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private final OkHttpClient defaultClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();

    @Autowired
    private TemplateService templateService;

    public Map<String, Object> testConnection(DsConfig config) {
        if (config.getApiUrl() == null || config.getApiUrl().isEmpty()) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", false);
            result.put("error", "API URL is required");
            return result;
        }
        try {
            Request request = buildRequest(config);
            OkHttpClient client = buildClient(config);
            try (Response response = client.newCall(request).execute()) {
                Map<String, Object> result = new java.util.LinkedHashMap<>();
                result.put("success", response.isSuccessful());
                result.put("statusCode", response.code());
                result.put("duration", response.receivedResponseAtMillis() - response.sentRequestAtMillis());
                String body = response.body() != null ? response.body().string() : "";
                if (body.length() > 2000) body = body.substring(0, 2000) + "...";
                result.put("body", body);
                return result;
            }
        } catch (Exception e) {
            log.warn("API test failed: {}", e.getMessage());
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            result.put("success", false);
            result.put("error", e.getMessage());
            return result;
        }
    }

    /**
     * Execute API request based on apiMode.
     * SINGLE: direct single API call with variable substitution.
     * CHAIN: multi-step API pipeline with variable extraction.
     * SCRIPT: Groovy template-based orchestration.
     */
    @SuppressWarnings("unchecked")
    public String executeRequest(DsConfig config, Map<String, String> params) throws IOException {
        String mode = config.getApiMode() != null ? config.getApiMode() : "SINGLE";
        log.debug("执行API请求, name={}, mode={}, url={}", config.getName(), mode, config.getApiUrl());
        if ("CHAIN".equals(mode)) {
            Map<String, Object> chainResult = executeChain(config, params);
            try {
                return objectMapper.writeValueAsString(chainResult);
            } catch (Exception e) {
                throw new IOException("Chain result serialization failed: " + e.getMessage(), e);
            }
        } else if ("SCRIPT".equals(mode)) {
            Map<String, Object> scriptResult = executeWithTemplate(config, params);
            try {
                return objectMapper.writeValueAsString(scriptResult);
            } catch (Exception e) {
                throw new IOException("Script result serialization failed: " + e.getMessage(), e);
            }
        }
        // SINGLE mode — default behavior
        return executeSingle(config, params);
    }

    /**
     * Execute SINGLE mode API call and return structured debug information
     * including status code, response headers, and body (with JSON parsing attempt).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeSingleDebug(DsConfig config, Map<String, String> params) {
        Map<String, Object> result = new LinkedHashMap<>();
        String url = config.getApiUrl();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                url = url.replace("${" + entry.getKey() + "}", entry.getValue());
                url = url.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        DsConfig tempConfig = new DsConfig();
        copyApiFields(config, tempConfig);
        tempConfig.setApiUrl(url);

        long start = System.currentTimeMillis();
        try {
            Request request = buildRequest(tempConfig);
            OkHttpClient client = buildClient(tempConfig);
            try (Response response = client.newCall(request).execute()) {
                result.put("success", response.isSuccessful());
                result.put("statusCode", response.code());
                result.put("url", url);
                result.put("method", tempConfig.getApiMethod());

                Map<String, String> respHeaders = new LinkedHashMap<>();
                for (String name : response.headers().names()) {
                    respHeaders.put(name, response.headers().get(name));
                }
                result.put("responseHeaders", respHeaders);

                String body = response.body() != null ? response.body().string() : "";
                // Truncate for display
                String displayBody = body.length() > 10000 ? body.substring(0, 10000) + "..." : body;
                result.put("body", displayBody);

                // Try to parse as JSON
                try {
                    result.put("bodyJson", objectMapper.readValue(body, Object.class));
                } catch (Exception e) {
                    // Not JSON — client displays raw text
                }

                result.put("duration", System.currentTimeMillis() - start);
            }
        } catch (Exception e) {
            log.warn("API debug execute failed: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
            result.put("duration", System.currentTimeMillis() - start);
        }
        return result;
    }

    /**
     * Single API call with variable substitution (original behavior).
     */
    public String executeSingle(DsConfig config, Map<String, String> params) throws IOException {
        String url = config.getApiUrl();
        if (params != null && !params.isEmpty()) {
            for (Map.Entry<String, String> entry : params.entrySet()) {
                url = url.replace("${" + entry.getKey() + "}", entry.getValue());
                url = url.replace("{{" + entry.getKey() + "}}", entry.getValue());
            }
        }
        DsConfig tempConfig = new DsConfig();
        copyApiFields(config, tempConfig);
        tempConfig.setApiUrl(url);

        try {
            Request request = buildRequest(tempConfig);
            OkHttpClient client = buildClient(tempConfig);
            try (Response response = client.newCall(request).execute()) {
                return response.body() != null ? response.body().string() : "";
            }
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Request build failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute a chain of API calls with variable extraction.
     * Each step can extract variables from the response, which are available
     * in subsequent steps via ${varName} substitution.
     *
     * Chain config JSON format:
     * [{"name":"Step1", "url":"...", "method":"GET", "headers":{...}, "extract":{"token":"data.token"}}, ...]
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeChain(DsConfig config, Map<String, String> extraParams) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, String> variables = new LinkedHashMap<>();
        if (extraParams != null) variables.putAll(extraParams);

        List<Map<String, Object>> stepResults = new ArrayList<>();
        String chainConfigStr = config.getApiChainConfig();
        if (chainConfigStr == null || chainConfigStr.isEmpty()) {
            result.put("success", false);
            result.put("error", "CHAIN mode requires apiChainConfig");
            return result;
        }

        try {
            List<Map<String, Object>> steps = objectMapper.readValue(chainConfigStr,
                    new com.fasterxml.jackson.core.type.TypeReference<List<Map<String, Object>>>() {});

            for (int i = 0; i < steps.size(); i++) {
                Map<String, Object> step = steps.get(i);
                String stepName = (String) step.getOrDefault("name", "Step " + (i + 1));
                String url = substitute((String) step.get("url"), variables);
                String method = (String) step.getOrDefault("method", "GET");
                Map<String, String> headers = resolveHeaders((Map<String, Object>) step.get("headers"), variables);
                String body = substitute((String) step.get("body"), variables);

                log.info("Chain step {}/{}: {} {} {}", i + 1, steps.size(), stepName, method, url);

                Request.Builder builder = new Request.Builder().url(url);
                if (headers != null) {
                    for (Map.Entry<String, String> h : headers.entrySet()) {
                        builder.addHeader(h.getKey(), h.getValue());
                    }
                }
                RequestBody requestBody = null;
                if (body != null && !body.isEmpty() && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method))) {
                    requestBody = RequestBody.create(body, MediaType.parse("application/json"));
                }
                builder.method(method, requestBody);

                OkHttpClient client = buildClient(config);
                try (Response response = client.newCall(builder.build()).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Map<String, Object> stepResult = new LinkedHashMap<>();
                    stepResult.put("step", stepName);
                    stepResult.put("statusCode", response.code());
                    stepResult.put("success", response.isSuccessful());

                    // Extract variables from response
                    Map<String, String> extractMap = (Map<String, String>) step.get("extract");
                    if (extractMap != null && !responseBody.isEmpty()) {
                        try {
                            Map<String, Object> responseJson = objectMapper.readValue(responseBody, Map.class);
                            for (Map.Entry<String, String> entry : extractMap.entrySet()) {
                                String varName = entry.getKey();
                                String jsonPath = entry.getValue();
                                Object value = resolveJsonPath(responseJson, jsonPath);
                                if (value != null) {
                                    variables.put(varName, String.valueOf(value));
                                    stepResult.put("extracted_" + varName, value);
                                }
                            }
                        } catch (Exception e) {
                            // Not JSON — store raw body
                            for (Map.Entry<String, String> entry : extractMap.entrySet()) {
                                variables.put(entry.getKey(), responseBody);
                            }
                        }
                    }

                    if (!response.isSuccessful()) {
                        stepResult.put("error", "HTTP " + response.code());
                        stepResult.put("body", truncate(responseBody));
                        stepResults.add(stepResult);
                        result.put("success", false);
                        result.put("error", "Step '" + stepName + "' failed: HTTP " + response.code());
                        result.put("steps", stepResults);
                        return result;
                    }

                    stepResult.put("body", truncate(responseBody));
                    stepResults.add(stepResult);
                }
            }

            result.put("success", true);
            result.put("steps", stepResults);
            result.put("variables", variables);
            // Return the last step's response body as the main data
            if (!stepResults.isEmpty()) {
                Map<String, Object> lastStep = stepResults.get(stepResults.size() - 1);
                result.put("lastResponse", lastStep.get("body"));
            }
        } catch (Exception e) {
            log.error("Chain execution failed: {}", e.getMessage());
            result.put("success", false);
            result.put("error", e.getMessage());
        }
        return result;
    }

    /**
     * Execute API using a Groovy template for complex orchestration.
     * The template script has access to:
     * - http: helper with get(url, headers) and post(url, body, headers) methods
     * - config: the DsConfig object
     * - params: extra parameters
     * - log: for logging
     * Script should return a Map or List.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> executeWithTemplate(DsConfig config, Map<String, String> extraParams) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (config.getTemplateId() == null || config.getTemplateId() == 0) {
            result.put("success", false);
            result.put("error", "SCRIPT mode requires a template");
            return result;
        }

        TemplateEntity template = templateService.getById(config.getTemplateId()).orElse(null);
        if (template == null || template.getContent() == null || template.getContent().isEmpty()) {
            result.put("success", false);
            result.put("error", "Template not found or empty");
            return result;
        }

        try {
            groovy.lang.Binding binding = new groovy.lang.Binding();
            // HTTP helper class for Groovy scripts
            binding.setVariable("http", new HttpHelper(config));
            binding.setVariable("config", config);
            binding.setVariable("params", extraParams != null ? extraParams : Collections.emptyMap());
            binding.setVariable("input", extraParams != null ? extraParams : Collections.emptyMap());
            binding.setVariable("out", new LinkedHashMap<>());

            groovy.lang.GroovyShell shell = new groovy.lang.GroovyShell(binding);
            Object scriptResult = shell.evaluate(template.getContent());

            // Check out variable first
            Object outVar = binding.getVariable("out");
            if (outVar instanceof Map && !((Map<?, ?>) outVar).isEmpty()) {
                result.putAll((Map<String, Object>) outVar);
                result.putIfAbsent("success", true);
                return result;
            }

            if (scriptResult instanceof Map) {
                result.putAll((Map<String, Object>) scriptResult);
                result.putIfAbsent("success", true);
            } else if (scriptResult instanceof List) {
                result.put("data", scriptResult);
                result.put("success", true);
            } else if (scriptResult instanceof String) {
                try {
                    Object parsed = objectMapper.readValue((String) scriptResult, Object.class);
                    if (parsed instanceof Map) {
                        result.putAll((Map<String, Object>) parsed);
                    } else {
                        result.put("data", parsed);
                    }
                    result.putIfAbsent("success", true);
                } catch (Exception e) {
                    result.put("data", scriptResult);
                    result.put("success", true);
                }
            } else {
                result.put("data", scriptResult);
                result.put("success", true);
            }
        } catch (Exception e) {
            log.error("Template execution failed: {}", e.getMessage());
            result.put("success", false);
            result.put("error", "Template execution failed: " + e.getMessage());
        }
        return result;
    }

    /**
     * HTTP helper exposed to Groovy templates.
     */
    public class HttpHelper {
        private final DsConfig config;

        public HttpHelper(DsConfig config) {
            this.config = config;
        }

        public Map<String, Object> get(String url, Map<String, String> headers) throws Exception {
            return doRequest(url, "GET", null, headers);
        }

        public Map<String, Object> post(String url, String body, Map<String, String> headers) throws Exception {
            return doRequest(url, "POST", body, headers);
        }

        public Map<String, Object> put(String url, String body, Map<String, String> headers) throws Exception {
            return doRequest(url, "PUT", body, headers);
        }

        private Map<String, Object> doRequest(String url, String method, String body, Map<String, String> headers) throws Exception {
            Request.Builder builder = new Request.Builder().url(url);
            String contentType = "application/json";
            if (headers != null) {
                for (Map.Entry<String, String> h : headers.entrySet()) {
                    if ("Content-Type".equalsIgnoreCase(h.getKey())) {
                        contentType = h.getValue();
                    } else {
                        builder.addHeader(h.getKey(), h.getValue());
                    }
                }
            }
            RequestBody requestBody;
            if (body != null && !body.isEmpty()) {
                requestBody = RequestBody.create(body, MediaType.parse(contentType));
            } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method)) {
                requestBody = RequestBody.create("", MediaType.parse(contentType));
            } else {
                requestBody = null;
            }
            builder.method(method, requestBody);

            log.debug("HTTP {} {} [Content-Type={}]", method, url, contentType);
            if (body != null && !body.isEmpty() && body.length() < 1000) {
                log.debug("HTTP body: {}", body);
            }

            OkHttpClient client = buildClient(config);
            try (Response response = client.newCall(builder.build()).execute()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("status", response.code());
                result.put("success", response.isSuccessful());
                String responseBody = response.body() != null ? response.body().string() : "";
                log.debug("HTTP response {} {}: {}", method, url, responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                try {
                    result.put("data", objectMapper.readValue(responseBody, Object.class));
                } catch (Exception e) {
                    result.put("data", responseBody);
                }
                return result;
            }
        }
    }

    /**
     * Resolve dot-notation JSON path (e.g. "data.token" → obj.data.token).
     */
    @SuppressWarnings("unchecked")
    private Object resolveJsonPath(Map<String, Object> json, String path) {
        if (path == null || path.isEmpty()) return null;
        String[] parts = path.split("\\.");
        Object current = json;
        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
            } else {
                return null;
            }
        }
        return current;
    }

    /**
     * Substitute ${var} placeholders in a string with values from the variables map.
     */
    private String substitute(String template, Map<String, String> variables) {
        if (template == null || variables == null || variables.isEmpty()) return template;
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("${" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    /**
     * Resolve headers map with variable substitution.
     */
    @SuppressWarnings("unchecked")
    private Map<String, String> resolveHeaders(Map<String, Object> headersConfig, Map<String, String> variables) {
        if (headersConfig == null) return null;
        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : headersConfig.entrySet()) {
            resolved.put(entry.getKey(), substitute(String.valueOf(entry.getValue()), variables));
        }
        return resolved;
    }

    private String truncate(String s) {
        if (s == null) return "";
        return s.length() > 2000 ? s.substring(0, 2000) + "..." : s;
    }

    private OkHttpClient buildClient(DsConfig config) {
        int timeout = config.getApiTimeout() != null ? config.getApiTimeout() : 30;
        return defaultClient.newBuilder()
                .connectTimeout(timeout, TimeUnit.SECONDS)
                .readTimeout(timeout, TimeUnit.SECONDS)
                .writeTimeout(timeout, TimeUnit.SECONDS)
                .build();
    }

    private Request buildRequest(DsConfig config) throws Exception {
        String method = config.getApiMethod() != null ? config.getApiMethod().toUpperCase() : "GET";
        Request.Builder builder = new Request.Builder().url(config.getApiUrl());

        // Headers
        if (config.getApiHeaders() != null && !config.getApiHeaders().isEmpty()) {
            @SuppressWarnings("unchecked")
            Map<String, String> headers = objectMapper.readValue(config.getApiHeaders(), Map.class);
            for (Map.Entry<String, String> entry : headers.entrySet()) {
                builder.addHeader(entry.getKey(), entry.getValue());
            }
        }

        // Auth
        applyAuth(builder, config);

        // Body
        RequestBody body = null;
        String bodyContent = config.getApiBody() != null ? config.getApiBody() : "";
        if (("POST".equals(method) || "PUT".equals(method)) && !bodyContent.isEmpty()) {
            String contentType = "application/json";
            if (config.getApiHeaders() != null) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, String> headers = objectMapper.readValue(config.getApiHeaders(), Map.class);
                    if (headers.containsKey("Content-Type")) {
                        contentType = headers.get("Content-Type");
                    }
                } catch (Exception e) {
                    log.debug("API请求头中Content-Type解析失败, 使用默认值: {}", e.getMessage());
                }
            }
            body = RequestBody.create(bodyContent, MediaType.parse(contentType));
        }

        builder.method(method, body);
        return builder.build();
    }

    private void applyAuth(Request.Builder builder, DsConfig config) throws Exception {
        String authType = config.getApiAuthType();
        if (authType == null || "NONE".equalsIgnoreCase(authType)) return;

        String authConfigStr = config.getApiAuthConfig();
        if (authConfigStr == null || authConfigStr.isEmpty()) return;

        @SuppressWarnings("unchecked")
        Map<String, String> authConfig = objectMapper.readValue(authConfigStr, Map.class);

        if ("BASIC".equalsIgnoreCase(authType)) {
            String user = authConfig.getOrDefault("username", "");
            String pass = authConfig.getOrDefault("password", "");
            String credential = Credentials.basic(user, pass);
            builder.addHeader("Authorization", credential);
        } else if ("BEARER".equalsIgnoreCase(authType)) {
            String token = authConfig.getOrDefault("token", "");
            builder.addHeader("Authorization", "Bearer " + token);
        } else if ("API_KEY".equalsIgnoreCase(authType)) {
            String key = authConfig.getOrDefault("key", "");
            String value = authConfig.getOrDefault("value", "");
            String location = authConfig.getOrDefault("location", "header");
            if ("header".equals(location)) {
                builder.addHeader(key, value);
            } else {
                // query param
                String url = config.getApiUrl();
                url += (url.contains("?") ? "&" : "?") + key + "=" + value;
                builder.url(url);
            }
        }
    }

    private void copyApiFields(DsConfig from, DsConfig to) {
        to.setApiMethod(from.getApiMethod());
        to.setApiUrl(from.getApiUrl());
        to.setApiHeaders(from.getApiHeaders());
        to.setApiBody(from.getApiBody());
        to.setApiAuthType(from.getApiAuthType());
        to.setApiAuthConfig(from.getApiAuthConfig());
        to.setApiTimeout(from.getApiTimeout());
        to.setApiRetryTimes(from.getApiRetryTimes());
        to.setApiRetryInterval(from.getApiRetryInterval());
    }
}
