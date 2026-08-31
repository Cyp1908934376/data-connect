package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

/**
 * 组件定义实体
 * 定义可视化模板中使用的组件类型
 */
@Entity
@Table(name = "component_definition")
public class ComponentDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "category", nullable = false, length = 50)
    private String category;  // FLOW/DATA/API/DB/OUTPUT/TRANSFORM

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "input_schema", columnDefinition = "TEXT")
    private String inputSchema;

    @Column(name = "output_schema", columnDefinition = "TEXT")
    private String outputSchema;

    @Column(name = "config_schema", columnDefinition = "TEXT")
    private String configSchema;

    @Column(name = "execution_type", nullable = false, length = 50)
    private String executionType;

    @Column(name = "script_template", columnDefinition = "TEXT")
    private String scriptTemplate;

    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    @Column(name = "enabled")
    private Integer enabled = 1;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        if (enabled == null) enabled = 1;
        if (sortOrder == null) sortOrder = 0;
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getInputSchema() { return inputSchema; }
    public void setInputSchema(String inputSchema) { this.inputSchema = inputSchema; }
    public String getOutputSchema() { return outputSchema; }
    public void setOutputSchema(String outputSchema) { this.outputSchema = outputSchema; }
    public String getConfigSchema() { return configSchema; }
    public void setConfigSchema(String configSchema) { this.configSchema = configSchema; }
    public String getExecutionType() { return executionType; }
    public void setExecutionType(String executionType) { this.executionType = executionType; }
    public String getScriptTemplate() { return scriptTemplate; }
    public void setScriptTemplate(String scriptTemplate) { this.scriptTemplate = scriptTemplate; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getEnabled() { return enabled; }
    public void setEnabled(Integer enabled) { this.enabled = enabled; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
