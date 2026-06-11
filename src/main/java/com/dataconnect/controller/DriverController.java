package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.dto.DriverInfo;
import com.dataconnect.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/driver")
public class DriverController {

    private static final Logger log = LoggerFactory.getLogger(DriverController.class);

    @Autowired
    private DriverService driverService;

    // === 页面路由 ===

    @GetMapping("/list")
    public String list(Model model) {
        model.addAttribute("activeMenu", "driver");
        model.addAttribute("pageTitle", "驱动管理");
        return "driver/list";
    }

    // === REST API ===

    @GetMapping("/api/list")
    @ResponseBody
    public ApiResponse<List<DriverInfo>> apiList() {
        return ApiResponse.success(driverService.listInstalled());
    }

    @GetMapping("/api/catalog")
    @ResponseBody
    public ApiResponse<List<DriverInfo>> apiCatalog() {
        return ApiResponse.success(driverService.getCatalog());
    }

    @PostMapping("/api/upload")
    @ResponseBody
    public ApiResponse<DriverInfo> apiUpload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return ApiResponse.error("请选择文件");
        }
        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase().endsWith(".jar")) {
            return ApiResponse.error("只支持 .jar 文件");
        }
        try {
            DriverInfo info = driverService.uploadJar(file);
            return ApiResponse.success(info);
        } catch (Exception e) {
            log.error("Upload driver failed", e);
            return ApiResponse.error("上传失败: " + e.getMessage());
        }
    }

    @PostMapping("/api/download")
    @ResponseBody
    public ApiResponse<DriverInfo> apiDownload(@RequestBody Map<String, Object> body) {
        String key = (String) body.get("key");
        if (key == null || key.isEmpty()) {
            return ApiResponse.error("缺少驱动标识 key");
        }
        int mirrorIndex = body.containsKey("mirrorIndex") ? ((Number) body.get("mirrorIndex")).intValue() : 0;
        try {
            DriverInfo info = driverService.downloadDriver(key, mirrorIndex);
            return ApiResponse.success(info);
        } catch (Exception e) {
            log.error("Download driver failed, key={}", key, e);
            return ApiResponse.error("下载失败: " + e.getMessage());
        }
    }

    @DeleteMapping("/api/{key}")
    @ResponseBody
    public ApiResponse<Void> apiDelete(@PathVariable String key) {
        try {
            driverService.deleteDriver(key);
            return ApiResponse.success(null);
        } catch (Exception e) {
            log.error("Delete driver failed, key={}", key, e);
            return ApiResponse.error("删除失败: " + e.getMessage());
        }
    }
}
