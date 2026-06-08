package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "flow_config")
public class FlowConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "input_ds_id")
    private Long inputDsId;

    @Column(name = "output_ds_id")
    private Long outputDsId;

    // 前置模板: 来自模板管理，处理数据前置问题（清洗、规范化等）
    @Column(name = "pre_template_id")
    private Long preTemplateId;

    // 数据对接模板: 来自数据对接模板管理，处理键值对映射关系
    @Column(name = "mapping_template_id")
    private Long mappingTemplateId;

    // 后置模板: 来自模板管理，处理数据后置问题（校验、格式转换等）
    @Column(name = "post_template_id")
    private Long postTemplateId;

    @Column(name = "template_params", columnDefinition = "TEXT")
    private String templateParams;

    @Column(name = "pipeline_config", columnDefinition = "TEXT")
    private String pipelineConfig;

    @Column(name = "sync_strategy", length = 20)
    private String syncStrategy;

    @Column(name = "incremental_column", length = 100)
    private String incrementalColumn;

    @Column(name = "incremental_column_type", length = 20)
    private String incrementalColumnType;

    @Column(name = "create_time")
    private LocalDateTime createTime;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @PrePersist
    protected void onCreate() {
        createTime = LocalDateTime.now();
        updateTime = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getInputDsId() { return inputDsId; }
    public void setInputDsId(Long inputDsId) { this.inputDsId = inputDsId; }
    public Long getOutputDsId() { return outputDsId; }
    public void setOutputDsId(Long outputDsId) { this.outputDsId = outputDsId; }
    public Long getPreTemplateId() { return preTemplateId; }
    public void setPreTemplateId(Long preTemplateId) { this.preTemplateId = preTemplateId; }
    public Long getMappingTemplateId() { return mappingTemplateId; }
    public void setMappingTemplateId(Long mappingTemplateId) { this.mappingTemplateId = mappingTemplateId; }
    public Long getPostTemplateId() { return postTemplateId; }
    public void setPostTemplateId(Long postTemplateId) { this.postTemplateId = postTemplateId; }
    public String getTemplateParams() { return templateParams; }
    public void setTemplateParams(String templateParams) { this.templateParams = templateParams; }
    public String getPipelineConfig() { return pipelineConfig; }
    public void setPipelineConfig(String pipelineConfig) { this.pipelineConfig = pipelineConfig; }
    public String getSyncStrategy() { return syncStrategy; }
    public void setSyncStrategy(String syncStrategy) { this.syncStrategy = syncStrategy; }
    public String getIncrementalColumn() { return incrementalColumn; }
    public void setIncrementalColumn(String incrementalColumn) { this.incrementalColumn = incrementalColumn; }
    public String getIncrementalColumnType() { return incrementalColumnType; }
    public void setIncrementalColumnType(String incrementalColumnType) { this.incrementalColumnType = incrementalColumnType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
