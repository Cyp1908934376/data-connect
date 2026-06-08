package com.dataconnect.entity;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "mapping_template")
public class MappingTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "ds_config_id")
    private Long dsConfigId;

    @Column(name = "column_config_id")
    private Long columnConfigId;

    @Column(name = "mappings", columnDefinition = "TEXT")
    private String mappings;

    @Column(name = "postman_json", columnDefinition = "TEXT")
    private String postmanJson;

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
    public Long getDsConfigId() { return dsConfigId; }
    public void setDsConfigId(Long dsConfigId) { this.dsConfigId = dsConfigId; }
    public Long getColumnConfigId() { return columnConfigId; }
    public void setColumnConfigId(Long columnConfigId) { this.columnConfigId = columnConfigId; }
    public String getMappings() { return mappings; }
    public void setMappings(String mappings) { this.mappings = mappings; }
    public String getPostmanJson() { return postmanJson; }
    public void setPostmanJson(String postmanJson) { this.postmanJson = postmanJson; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
