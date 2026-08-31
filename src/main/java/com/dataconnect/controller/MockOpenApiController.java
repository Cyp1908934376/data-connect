package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.service.MockOpenApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 模拟第三方 OpenAPI，供「取 token → 带 access_token 拉数 → 写 MySQL」联调。
 */
@RestController
@RequestMapping("/mock/openapi")
public class MockOpenApiController {

    @Autowired
    private MockOpenApiService mockOpenApiService;

    @GetMapping("/token")
    public Map<String, Object> getToken() {
        return mockOpenApiService.issueToken();
    }

    @PostMapping("/token")
    public Map<String, Object> postToken() {
        return mockOpenApiService.issueToken();
    }

    @GetMapping("/data")
    public Map<String, Object> getData(@RequestParam(value = "access_token", required = false) String accessToken,
                                       @RequestParam(value = "page", required = false) Integer page,
                                       @RequestParam(value = "per_page", required = false) Integer perPage,
                                       @RequestParam(value = "since", required = false) String since,
                                       @RequestParam(value = "id", required = false) String idSince) {
        String watermark = since != null && !since.isEmpty() ? since : idSince;
        return mockOpenApiService.queryData(accessToken, page, perPage, watermark);
    }

    /**
     * 走可视化模板同一条执行链：取 token、带 query 拉数、写入已配置的 MySQL 源。
     */
    @PostMapping("/run-pipeline")
    public ApiResponse<Map<String, Object>> runPipeline(
            @RequestParam(value = "mysqlDsId", required = false) Long mysqlDsId,
            @RequestParam(value = "tableName", required = false) String tableName) {
        return ApiResponse.success(mockOpenApiService.runPipeline(mysqlDsId, tableName));
    }

    @GetMapping("/run-pipeline")
    public ApiResponse<Map<String, Object>> runPipelineGet(
            @RequestParam(value = "mysqlDsId", required = false) Long mysqlDsId,
            @RequestParam(value = "tableName", required = false) String tableName) {
        return ApiResponse.success(mockOpenApiService.runPipeline(mysqlDsId, tableName));
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("tokenUrl", mockOpenApiService.tokenUrl());
        info.put("dataUrl", mockOpenApiService.dataUrl() + "?access_token={access_token}");
        info.put("runPipeline", mockOpenApiService.baseUrl() + "/mock/openapi/run-pipeline");
        info.put("tokenExtractPath", "result.access_token");
        info.put("dataListPath", "result.data");
        info.put("mysqlTable", MockOpenApiService.TABLE_NAME);
        return info;
    }
}
