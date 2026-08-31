package com.dataconnect.service;

import com.dataconnect.component.DataPacket;
import com.dataconnect.entity.FlowConfig;
import com.dataconnect.entity.PublishConfig;
import com.dataconnect.entity.VisualTemplate;
import com.dataconnect.repository.FlowConfigRepository;
import com.dataconnect.repository.PublishConfigRepository;
import com.dataconnect.repository.VisualTemplateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 发布服务
 * 负责管理已发布的API服务（通过主应用端口 + 动态路径暴露）
 */
@Service
public class PublishService {

    private static final Logger log = LoggerFactory.getLogger(PublishService.class);

    @Autowired
    private PublishConfigService publishConfigService;

    @Autowired
    private FlowConfigRepository flowConfigRepository;

    @Autowired
    private FlowExecutionService flowExecutionService;

    @Autowired
    private VisualTemplateExecutionService visualTemplateExecutionService;

    @Autowired
    private VisualTemplateRepository visualTemplateRepository;

    @Autowired
    private PublishEndpointManager endpointManager;

    // 存储正在运行的发布配置ID
    private final Set<Long> runningServices = ConcurrentHashMap.newKeySet();

    /**
     * 启动发布服务（标记为运行中）
     */
    public void startService(Long publishId) {
        PublishConfig config = publishConfigService.getById(publishId)
                .orElseThrow(() -> new RuntimeException("发布配置不存在: " + publishId));

        // 验证绑定目标
        validateBinding(config);

        if (runningServices.contains(publishId)) {
            log.warn("发布服务已在运行, id={}", publishId);
            return;
        }

        try {
            log.info("启动发布服务, id={}, port={}, apiPath={}", publishId, config.getPort(), config.getApiPath());
            runningServices.add(publishId);

            // 启动独立端点（在配置的端口上）
            try {
                endpointManager.startEndpoint(config);
            } catch (Exception e) {
                log.warn("启动独立端点失败(端口可能被占用), 仅使用内部路径: {}", e.getMessage());
            }

            publishConfigService.updateStatus(publishId, "RUNNING");
            log.info("发布服务启动成功, id={}", publishId);
        } catch (Exception e) {
            log.error("启动发布服务失败, id={}", publishId, e);
            publishConfigService.updateError(publishId, e.getMessage());
            throw new RuntimeException("启动发布服务失败: " + e.getMessage(), e);
        }
    }

    /**
     * 验证发布配置的绑定目标
     */
    private void validateBinding(PublishConfig config) {
        boolean hasFlow = config.getFlowConfigId() != null && config.getFlowConfigId() > 0;
        boolean hasTemplate = config.getVisualTemplateId() != null && config.getVisualTemplateId() > 0;

        if (!hasFlow && !hasTemplate) {
            throw new RuntimeException("必须关联可视化模板");
        }
        if (hasFlow) {
            flowConfigRepository.findById(config.getFlowConfigId())
                    .orElseThrow(() -> new RuntimeException("关联的对接流程不存在: " + config.getFlowConfigId()));
        }
        if (hasTemplate) {
            visualTemplateRepository.findById(config.getVisualTemplateId())
                    .orElseThrow(() -> new RuntimeException("关联的可视化模板不存在: " + config.getVisualTemplateId()));
        }
    }

    /**
     * 停止发布服务
     */
    public void stopService(Long publishId) {
        if (!runningServices.contains(publishId)) {
            log.warn("发布服务未运行, id={}", publishId);
            publishConfigService.updateStatus(publishId, "STOPPED");
            return;
        }

        try {
            log.info("停止发布服务, id={}", publishId);
            endpointManager.stopEndpoint(publishId);
            runningServices.remove(publishId);
            publishConfigService.updateStatus(publishId, "STOPPED");
            log.info("发布服务已停止, id={}", publishId);
        } catch (Exception e) {
            log.error("停止发布服务失败, id={}", publishId, e);
            publishConfigService.updateError(publishId, e.getMessage());
        }
    }

    /**
     * 重启发布服务
     */
    public void restartService(Long publishId) {
        stopService(publishId);
        startService(publishId);
    }

    /**
     * 检查服务是否运行中
     */
    public boolean isRunning(Long publishId) {
        return runningServices.contains(publishId);
    }

    /**
     * 执行发布服务（处理API请求）
     */
    public Map<String, Object> execute(Long publishId, Map<String, Object> params) {
        if (!runningServices.contains(publishId)) {
            throw new RuntimeException("发布服务未运行");
        }

        PublishConfig config = publishConfigService.getById(publishId)
                .orElseThrow(() -> new RuntimeException("发布配置不存在: " + publishId));

        boolean hasTemplate = config.getVisualTemplateId() != null && config.getVisualTemplateId() > 0;

        if (hasTemplate) {
            return executeVisualTemplate(config.getVisualTemplateId(), params);
        } else {
            return executeFlow(config.getFlowConfigId(), params);
        }
    }

    /**
     * 执行可视化模板
     */
    private Map<String, Object> executeVisualTemplate(Long templateId, Map<String, Object> params) {
        try {
            // 将参数 Map 转为 DataPacket
            DataPacket input = DataPacket.of(params != null ? params : new HashMap<>());

            DataPacket result = visualTemplateExecutionService.execute(templateId, input);

            // 将 DataPacket 转为 Map
            Map<String, Object> response = new HashMap<>();
            if (result.getDataType() == DataPacket.DataType.ERROR) {
                response.put("success", false);
                response.put("error", result.getErrorMessage() != null ? result.getErrorMessage() : "执行失败");
                response.put("code", result.getErrorCode() != null ? result.getErrorCode() : "ERROR");
            } else {
                response.put("success", true);
                if (result.getRows() != null && !result.getRows().isEmpty()) {
                    if (result.getRows().size() == 1) {
                        response.put("data", result.getRows().get(0));
                    } else {
                        response.put("data", result.getRows());
                    }
                    response.put("total", result.getRows().size());
                }
            }
            return response;
        } catch (Exception e) {
            log.error("执行可视化模板失败, templateId={}", templateId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * 执行对接流程
     */
    private Map<String, Object> executeFlow(Long flowId, Map<String, Object> params) {
        try {
            Map<String, Object> result = flowExecutionService.execute(flowId);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", result);
            return response;
        } catch (Exception e) {
            log.error("执行对接流程失败, flowId={}", flowId, e);
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return error;
        }
    }

    /**
     * 停止所有服务（应用关闭时调用）
     */
    public void stopAll() {
        log.info("停止所有发布服务, count={}", runningServices.size());
        for (Long publishId : new ArrayList<>(runningServices)) {
            try {
                stopService(publishId);
            } catch (Exception e) {
                log.error("停止发布服务失败, id={}", publishId, e);
            }
        }
    }

    /**
     * 获取运行状态
     */
    public Map<Long, Boolean> getRunningStatus() {
        Map<Long, Boolean> status = new ConcurrentHashMap<>();
        for (Long publishId : runningServices) {
            status.put(publishId, true);
        }
        return status;
    }
}
