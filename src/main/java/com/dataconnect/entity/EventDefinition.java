package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 事件定义实体
 * 可注册、可配置、可复用的数据处理单元
 */
@Entity
@Table(name = "event_definition")
public class EventDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", nullable = false, length = 100, unique = true)
    private String code;

    @Column(name = "category", length = 50)
    private String category;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "input_schema", columnDefinition = "TEXT")
    private String inputSchema;  // JSON: 输入参数定义

    @Column(name = "output_schema", columnDefinition = "TEXT")
    private String outputSchema;  // JSON: 输出说明

    @Column(name = "handler_type", length = 20)
    private String handlerType;  // BUILTIN / GROOVY / SHELL / TEMPLATE

    @Column(name = "handler_config", columnDefinition = "TEXT")
    private String handlerConfig;  // 类名 / Groovy脚本 / Shell命令 / 模板ID

    @Column(name = "is_builtin")
    private Integer isBuiltin = 0;  // 1=内置不可删

    @Column(name = "is_enabled")
    private Integer isEnabled = 1;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
        if (isBuiltin == null) isBuiltin = 0;
        if (isEnabled == null) isEnabled = 1;
        if (icon == null) icon = "bi-lightning";
        if (handlerType == null) handlerType = "GROOVY";
        if (category == null) category = "自定义";
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
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }
    public String getHandlerType() { return handlerType; }
    public void setHandlerType(String handlerType) { this.handlerType = handlerType; }
    public String getHandlerConfig() { return handlerConfig; }
    public void setHandlerConfig(String handlerConfig) { this.handlerConfig = handlerConfig; }
    public Integer getIsBuiltin() { return isBuiltin; }
    public void setIsBuiltin(Integer isBuiltin) { this.isBuiltin = isBuiltin; }
    public Integer getIsEnabled() { return isEnabled; }
    public void setIsEnabled(Integer isEnabled) { this.isEnabled = isEnabled; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
