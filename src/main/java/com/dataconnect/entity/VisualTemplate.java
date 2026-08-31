package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 可视化模板实体
 * 表单配置式模板系统，支持事件类型和模板调用
 */
@Entity
@Table(name = "visual_template")
public class VisualTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "category_id")
    private Long categoryId = 0L;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;  // TRIGGER/DATA_SOURCE/PROCESS/OUTPUT/CONTROL

    @Column(name = "event_config", columnDefinition = "TEXT")
    private String eventConfig;  // 事件配置JSON

    @Column(name = "call_templates", columnDefinition = "TEXT")
    private String callTemplates;  // 调用的模板列表JSON

    @Column(name = "canvas_config", columnDefinition = "TEXT")
    private String canvasConfig;  // 画布配置JSON（兼容旧字段）

    @Column(name = "input_params", columnDefinition = "TEXT")
    private String inputParams;  // 输入参数定义JSON

    @Column(name = "output_params", columnDefinition = "TEXT")
    private String outputParams;  // 输出参数定义JSON

    /** 系统内置模板标识，如 FILE_DOWNLOAD / THESIS_ARCHIVE；非空则不可删除 */
    @Column(name = "builtin_code", length = 50)
    private String builtinCode;

    @Column(name = "version")
    private Integer version = 1;

    @Column(name = "is_deleted")
    private Integer isDeleted = 0;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (version == null) version = 1;
        if (isDeleted == null) isDeleted = 0;
        if (categoryId == null) categoryId = 0L;
        if (eventType == null) eventType = "DATA_SOURCE";
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public String getEventConfig() { return eventConfig; }
    public void setEventConfig(String eventConfig) { this.eventConfig = eventConfig; }
    public String getCallTemplates() { return callTemplates; }
    public void setCallTemplates(String callTemplates) { this.callTemplates = callTemplates; }
    public String getCanvasConfig() { return canvasConfig; }
    public void setCanvasConfig(String canvasConfig) { this.canvasConfig = canvasConfig; }
    public String getInputParams() { return inputParams; }
    public void setInputParams(String inputParams) { this.inputParams = inputParams; }
    public String getOutputParams() { return outputParams; }
    public void setOutputParams(String outputParams) { this.outputParams = outputParams; }
    public String getBuiltinCode() { return builtinCode; }
    public void setBuiltinCode(String builtinCode) { this.builtinCode = builtinCode; }
    public boolean isBuiltin() {
        return builtinCode != null && !builtinCode.trim().isEmpty();
    }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Integer getIsDeleted() { return isDeleted; }
    public void setIsDeleted(Integer isDeleted) { this.isDeleted = isDeleted; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
