package com.dataconnect.dto;

import java.util.List;

/**
 * JDBC 驱动信息 DTO
 */
public class DriverInfo {

    public enum Source {
        /** 内置驱动(打包在 jar 中, 不可删除) */
        BUILT_IN,
        /** 外部驱动(用户上传) */
        EXTERNAL,
        /** 可下载目录(未安装) */
        CATALOG
    }

    /** 显示名称 */
    private String name;
    /** 唯一标识 key */
    private String key;
    /** 驱动类完整名称 */
    private String driverClass;
    /** 数据库类型(对应 DsConfig.dbType) */
    private String dbType;
    /** Maven groupId */
    private String groupId;
    /** Maven artifactId */
    private String artifactId;
    /** 版本号 */
    private String version;
    /** 文件大小(人类可读) */
    private String size;
    /** 驱动来源类型 */
    private Source source;
    /** 是否锁定(内置驱动不可删除) */
    private boolean locked;
    /** Maven Central 一键下载 URL */
    private String mavenCentralUrl;
    /** 手动下载页面 URL */
    private String manualDownloadUrl;
    /** 所有镜像下载 URL（含国内外） */
    private List<String> mirrorUrls;
    /** 磁盘上的文件名(外部驱动) */
    private String filename;

    public DriverInfo() {}

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDriverClass() { return driverClass; }
    public void setDriverClass(String driverClass) { this.driverClass = driverClass; }

    public String getDbType() { return dbType; }
    public void setDbType(String dbType) { this.dbType = dbType; }

    public String getGroupId() { return groupId; }
    public void setGroupId(String groupId) { this.groupId = groupId; }

    public String getArtifactId() { return artifactId; }
    public void setArtifactId(String artifactId) { this.artifactId = artifactId; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public Source getSource() { return source; }
    public void setSource(Source source) { this.source = source; }

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }

    public String getMavenCentralUrl() { return mavenCentralUrl; }
    public void setMavenCentralUrl(String mavenCentralUrl) { this.mavenCentralUrl = mavenCentralUrl; }

    public String getManualDownloadUrl() { return manualDownloadUrl; }
    public void setManualDownloadUrl(String manualDownloadUrl) { this.manualDownloadUrl = manualDownloadUrl; }

    public List<String> getMirrorUrls() { return mirrorUrls; }
    public void setMirrorUrls(List<String> mirrorUrls) { this.mirrorUrls = mirrorUrls; }

    public String getFilename() { return filename; }
    public void setFilename(String filename) { this.filename = filename; }
}
