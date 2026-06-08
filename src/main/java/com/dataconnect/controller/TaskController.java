package com.dataconnect.controller;

import com.dataconnect.dto.ApiResponse;
import com.dataconnect.entity.TaskConfig;
import com.dataconnect.entity.TaskExecutionLog;
import com.dataconnect.service.FlowConfigService;
import com.dataconnect.service.FlowExecutionService;
import com.dataconnect.service.TaskScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/task")
public class TaskController {

    private static final Logger log = LoggerFactory.getLogger(TaskController.class);

    @Autowired
    private TaskScheduleService taskScheduleService;

    @Autowired
    private FlowConfigService flowConfigService;

    @Autowired
    private FlowExecutionService flowExecutionService;

    @GetMapping("/list")
    public String list(Model model) {
        log.info("访问任务列表页");
        model.addAttribute("activeMenu", "task");
        model.addAttribute("pageTitle", "任务管理");
        model.addAttribute("tasks", taskScheduleService.listAll());
        return "task/list";
    }

    @GetMapping("/form")
    public String form(@RequestParam(required = false) Long id, Model model) {
        log.info("访问任务表单页, id={}", id);
        TaskConfig task = id != null ?
                taskScheduleService.getById(id).orElse(new TaskConfig()) : new TaskConfig();
        model.addAttribute("activeMenu", "task");
        model.addAttribute("pageTitle", id != null ? "编辑任务" : "新增任务");
        model.addAttribute("task", task);
        model.addAttribute("flows", flowConfigService.listAll());
        if (id != null) {
            model.addAttribute("execLogs", taskScheduleService.getExecutionLogs(id));
        }
        return "task/form";
    }

    @PostMapping("/save")
    public String save(TaskConfig task) {
        log.info("保存任务, id={}, name={}, cron={}", task.getId(), task.getName(), task.getCronExpr());
        try {
            if (task.getId() != null) {
                taskScheduleService.update(task.getId(), task);
            } else {
                taskScheduleService.save(task);
            }
            log.info("任务保存成功, id={}", task.getId());
        } catch (Exception e) {
            log.error("保存任务失败, id={}", task.getId(), e);
            throw e;
        }
        return "redirect:/task/list";
    }

    @PostMapping("/delete/{id}")
    public String delete(@PathVariable Long id) {
        log.info("删除任务, id={}", id);
        try {
            taskScheduleService.delete(id);
            log.info("任务删除成功, id={}", id);
        } catch (Exception e) {
            log.error("删除任务失败, id={}", id, e);
            throw e;
        }
        return "redirect:/task/list";
    }

    // === REST API ===
    @PostMapping("/api/start/{id}")
    @ResponseBody
    public ApiResponse<Void> start(@PathVariable Long id) {
        log.info("启动任务, id={}", id);
        taskScheduleService.startTask(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/pause/{id}")
    @ResponseBody
    public ApiResponse<Void> pause(@PathVariable Long id) {
        log.info("暂停任务, id={}", id);
        taskScheduleService.pauseTask(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/stop/{id}")
    @ResponseBody
    public ApiResponse<Void> stop(@PathVariable Long id) {
        log.info("停止任务, id={}", id);
        taskScheduleService.stopTask(id);
        return ApiResponse.success(null);
    }

    @PostMapping("/api/executeOnce/{id}")
    @ResponseBody
    public ApiResponse<Void> executeOnce(@PathVariable Long id) {
        log.info("手动执行任务一次, id={}", id);
        taskScheduleService.executeOnce(id);
        return ApiResponse.success(null);
    }

    @GetMapping("/api/logs/{taskId}")
    @ResponseBody
    public ApiResponse<List<TaskExecutionLog>> getLogs(@PathVariable Long taskId) {
        log.debug("查询任务执行日志, taskId={}", taskId);
        return ApiResponse.success(taskScheduleService.getExecutionLogs(taskId));
    }
}
