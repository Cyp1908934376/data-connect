package com.dataconnect.controller;

import com.dataconnect.DataConnectApplication;
import com.dataconnect.dto.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SystemController {

    @PostMapping("/api/restart")
    @ResponseBody
    public ApiResponse<String> restart() {
        DataConnectApplication.restart();
        return ApiResponse.success("应用正在重启，请等待 5 秒后刷新页面...");
    }
}
