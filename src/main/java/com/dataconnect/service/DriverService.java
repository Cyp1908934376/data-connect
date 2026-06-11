package com.dataconnect.service;

import com.dataconnect.dto.DriverInfo;
import com.dataconnect.dto.DriverInfo.Source;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
public class DriverService {

    private static final Logger log = LoggerFactory.getLogger(DriverService.class);

    @Value("${app.driver.path:drivers/}")
    private String driverPath;

    @Value("${app.driver.maven-urls:https://repo1.maven.org/maven2}")
    private String mavenUrlsConfig;

    private List<String> mavenUrls;

    private final Map<String, DriverInfo> builtInDrivers = new LinkedHashMap<>();
    private final Map<String, DriverInfo> catalogDrivers = new LinkedHashMap<>();
    private final Map<String, DriverInfo> externalDrivers = new LinkedHashMap<>();

    // ——— 内置驱动定义 ———

    private void initBuiltInDrivers() {
        addBuiltIn("H2", "h2", "org.h2.Driver", "H2", "嵌入式元数据数据库（系统必备）");
        addBuiltIn("MySQL", "mysql", "com.mysql.cj.jdbc.Driver", "MySQL", "MySQL / TiDB / OceanBase");
        addBuiltIn("PostgreSQL", "postgresql", "org.postgresql.Driver", "PostgreSQL", "PostgreSQL / Greenplum / Redshift");
        addBuiltIn("SQL Server", "sqlserver", "com.microsoft.sqlserver.jdbc.SQLServerDriver", "SqlServer", "Microsoft SQL Server");
    }

    private void addBuiltIn(String name, String key, String driverClass, String dbType, String desc) {
        DriverInfo info = new DriverInfo();
        info.setName(name);
        info.setKey(key);
        info.setDriverClass(driverClass);
        info.setDbType(dbType);
        info.setSource(Source.BUILT_IN);
        info.setLocked(true);
        info.setVersion("内置");
        info.setSize("-");
        builtInDrivers.put(key, info);
    }

    // ——— 驱动目录定义 ———

    private void initCatalogDrivers() {
        addCatalog("MariaDB", "mariadb", "org.mariadb.jdbc.Driver", "MariaDB",
                "org.mariadb.jdbc", "mariadb-java-client", "3.1.4");
        addCatalog("Oracle", "oracle", "oracle.jdbc.OracleDriver", "Oracle",
                "com.oracle.database.jdbc", "ojdbc8", "19.8.0.0");
        addCatalog("SQLite", "sqlite", "org.sqlite.JDBC", "SQLite",
                "org.xerial", "sqlite-jdbc", "3.36.0.3");
        addCatalog("ClickHouse", "clickhouse", "com.clickhouse.jdbc.ClickHouseDriver", "ClickHouse",
                "com.clickhouse", "clickhouse-jdbc", "0.4.6");
        addCatalog("DB2", "db2", "com.ibm.db2.jcc.DB2Driver", "DB2",
                "com.ibm.db2", "jcc", "11.5.8.0");
        addCatalog("DuckDB", "duckdb", "org.duckdb.DuckDBDriver", "DuckDB",
                "org.duckdb", "duckdb_jdbc", "0.9.2");
        addCatalog("Derby", "derby", "org.apache.derby.jdbc.EmbeddedDriver", "Derby",
                "org.apache.derby", "derby", "10.14.2.0");
        addCatalog("Firebird", "firebird", "org.firebirdsql.jdbc.FBDriver", "Firebird",
                "org.firebirdsql.jdbc", "jaybird", "4.0.9.java8");
        addCatalog("HSQLDB", "hsqldb", "org.hsqldb.jdbc.JDBCDriver", "HSQLDB",
                "org.hsqldb", "hsqldb", "2.7.1");
        addCatalog("InfluxDB", "influxdb", "", "InfluxDB",
                "com.influxdb", "influxdb-client-java", "6.8.0");
        addCatalog("Neo4j", "neo4j", "org.neo4j.jdbc.Driver", "Neo4j",
                "org.neo4j", "neo4j-jdbc-driver", "4.0.1");
        addCatalog("Presto", "presto", "com.facebook.presto.jdbc.PrestoDriver", "Presto",
                "com.facebook.presto", "presto-jdbc", "0.283");
        addCatalog("SAP HANA", "sap_hana", "com.sap.db.jdbc.Driver", "SAP_HANA",
                "com.sap.cloud.db.jdbc", "ngdbc", "2.17.12");
        addCatalog("Snowflake", "snowflake", "net.snowflake.client.jdbc.SnowflakeDriver", "Snowflake",
                "net.snowflake", "snowflake-jdbc", "3.14.0");
        addCatalog("TDengine", "tdengine", "com.taosdata.jdbc.TSDBDriver", "TDengine",
                "com.taosdata.jdbc", "taos-jdbcdriver", "3.2.4");
        addCatalog("Trino", "trino", "io.trino.jdbc.TrinoDriver", "Trino",
                "io.trino", "trino-jdbc", "392");
        addCatalog("Apache Drill", "drill", "org.apache.drill.jdbc.Driver", "Drill",
                "org.apache.drill.exec", "drill-jdbc", "1.21.1");
    }

    private void addCatalog(String name, String key, String driverClass, String dbType,
                            String groupId, String artifactId, String version) {
        String groupPath = groupId.replace('.', '/');
        String filename = artifactId + "-" + version + ".jar";
        List<String> mirrorUrls = new ArrayList<>();
        for (String baseUrl : mavenUrls) {
            mirrorUrls.add(String.format("%s/%s/%s/%s/%s",
                    baseUrl, groupPath, artifactId, version, filename));
        }
        String mavenCentralUrl = mirrorUrls.get(0);
        String manualUrl = mavenCentralUrl;

        DriverInfo info = new DriverInfo();
        info.setName(name);
        info.setKey(key);
        info.setDriverClass(driverClass);
        info.setDbType(dbType);
        info.setGroupId(groupId);
        info.setArtifactId(artifactId);
        info.setVersion(version);
        info.setSource(Source.CATALOG);
        info.setLocked(false);
        info.setMavenCentralUrl(mavenCentralUrl);
        info.setManualDownloadUrl(manualUrl);
        info.setMirrorUrls(mirrorUrls);
        info.setFilename(filename);
        catalogDrivers.put(key, info);
    }

    // ——— 初始化 ———

    @PostConstruct
    public void init() {
        // 解析 Maven 镜像地址
        mavenUrls = new ArrayList<>();
        if (mavenUrlsConfig != null && !mavenUrlsConfig.isEmpty()) {
            for (String url : mavenUrlsConfig.split(",")) {
                String trimmed = url.trim();
                if (!trimmed.isEmpty()) {
                    mavenUrls.add(trimmed);
                }
            }
        }
        // 兜底：至少有一个默认地址
        if (mavenUrls.isEmpty()) {
            mavenUrls.add("https://repo1.maven.org/maven2");
        }
        log.info("Maven mirrors configured: {}", mavenUrls);
        initBuiltInDrivers();
        initCatalogDrivers();
    }

    // ——— 扫描外部驱动 ———

    public void scanExternalDrivers() {
        File dir = new File(driverPath);
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] jars = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".jar"));
        if (jars == null) return;

        for (File jar : jars) {
            String name = jar.getName();
            // 尝试匹配目录
            boolean matched = false;
            for (DriverInfo cat : catalogDrivers.values()) {
                if (cat.getFilename() != null && cat.getFilename().equalsIgnoreCase(name)) {
                    DriverInfo info = new DriverInfo();
                    info.setName(cat.getName());
                    info.setKey(cat.getKey());
                    info.setDriverClass(cat.getDriverClass());
                    info.setDbType(cat.getDbType());
                    info.setGroupId(cat.getGroupId());
                    info.setArtifactId(cat.getArtifactId());
                    info.setVersion(cat.getVersion());
                    info.setSource(Source.EXTERNAL);
                    info.setLocked(false);
                    info.setFilename(name);
                    info.setSize(formatSize(jar.length()));
                    externalDrivers.put(cat.getKey(), info);
                    matched = true;
                    log.info("External driver matched: {} → {}", name, cat.getName());
                    break;
                }
            }
            if (!matched) {
                DriverInfo info = new DriverInfo();
                info.setName(name);
                info.setKey(sanitizeKey(name));
                info.setSource(Source.EXTERNAL);
                info.setLocked(false);
                info.setFilename(name);
                info.setSize(formatSize(jar.length()));
                externalDrivers.put(info.getKey(), info);
                log.info("External driver (unmatched): {}", name);
            }
            // 动态加载到 classpath
            addJarToClasspath(jar);
        }
    }

    // ——— 获取已安装驱动的数据库类型列表 ———

    public List<String> getInstalledDbTypes() {
        Set<String> types = new LinkedHashSet<>();
        for (DriverInfo info : builtInDrivers.values()) {
            if (info.getDbType() != null && !info.getDbType().isEmpty()) {
                types.add(info.getDbType());
            }
        }
        for (DriverInfo info : externalDrivers.values()) {
            if (info.getDbType() != null && !info.getDbType().isEmpty()) {
                types.add(info.getDbType());
            }
        }
        return new ArrayList<>(types);
    }

    // ——— 列出已安装驱动 ———

    public List<DriverInfo> listInstalled() {
        List<DriverInfo> list = new ArrayList<>();
        list.addAll(builtInDrivers.values());
        list.addAll(externalDrivers.values());
        return list;
    }

    // ——— 列出可下载目录(排除已安装的) ———

    public List<DriverInfo> getCatalog() {
        List<DriverInfo> list = new ArrayList<>();
        for (DriverInfo cat : catalogDrivers.values()) {
            if (!externalDrivers.containsKey(cat.getKey())) {
                list.add(cat);
            }
        }
        return list;
    }

    // ——— 上传驱动 jar ———

    public synchronized DriverInfo uploadJar(MultipartFile file) throws Exception {
        String originalName = file.getOriginalFilename();
        if (originalName == null || !originalName.toLowerCase().endsWith(".jar")) {
            throw new IllegalArgumentException("只支持 .jar 文件");
        }

        // 安全检查文件名
        String safeName = originalName.replaceAll("[\\\\/:*?\"<>|]", "_");
        File destDir = new File(driverPath);
        if (!destDir.exists()) destDir.mkdirs();

        File destFile = new File(destDir, safeName);
        file.transferTo(destFile);
        log.info("Driver jar saved: {}", destFile.getAbsolutePath());

        // 动态加载
        addJarToClasspath(destFile);

        // 尝试匹配目录
        DriverInfo info = tryMatchCatalog(safeName, destFile.length());
        if (info == null) {
            info = new DriverInfo();
            info.setName(safeName);
            info.setKey(sanitizeKey(safeName));
            info.setSource(Source.EXTERNAL);
            info.setLocked(false);
            info.setFilename(safeName);
            info.setSize(formatSize(destFile.length()));
        }
        externalDrivers.put(info.getKey(), info);
        return info;
    }

    // ——— 从 Maven Central 下载 ———

    public synchronized DriverInfo downloadDriver(String key, int mirrorIndex) throws Exception {
        DriverInfo cat = catalogDrivers.get(key);
        if (cat == null) {
            throw new IllegalArgumentException("未知驱动: " + key);
        }
        if (externalDrivers.containsKey(key)) {
            throw new IllegalStateException("驱动已安装: " + key);
        }

        File destDir = new File(driverPath);
        if (!destDir.exists()) destDir.mkdirs();

        File destFile = new File(destDir, cat.getFilename());

        List<String> urls = cat.getMirrorUrls();
        if (urls == null || urls.isEmpty()) {
            urls = Collections.singletonList(cat.getMavenCentralUrl());
        }

        if (mirrorIndex < 0 || mirrorIndex >= urls.size()) {
            throw new IllegalArgumentException("镜像索引无效: " + mirrorIndex + "，可选范围 0~" + (urls.size() - 1));
        }

        String url = urls.get(mirrorIndex);
        log.info("Downloading driver from mirror[{}]: {}", mirrorIndex, url);

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .followRedirects(true)
                .build();

        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code() + ": " + response.message());
            }
            byte[] body = response.body() != null ? response.body().bytes() : new byte[0];
            try (FileOutputStream fos = new FileOutputStream(destFile)) {
                fos.write(body);
            }
            log.info("Driver downloaded: {} ({})", destFile.getName(), formatSize(body.length));
        } catch (IOException e) {
            throw new RuntimeException("下载失败，请尝试其他镜像", e);
        }

        addJarToClasspath(destFile);

        DriverInfo info = new DriverInfo();
        info.setName(cat.getName());
        info.setKey(cat.getKey());
        info.setDriverClass(cat.getDriverClass());
        info.setDbType(cat.getDbType());
        info.setGroupId(cat.getGroupId());
        info.setArtifactId(cat.getArtifactId());
        info.setVersion(cat.getVersion());
        info.setSource(Source.EXTERNAL);
        info.setLocked(false);
        info.setFilename(cat.getFilename());
        info.setSize(formatSize(destFile.length()));
        info.setMavenCentralUrl(cat.getMavenCentralUrl());
        info.setManualDownloadUrl(cat.getManualDownloadUrl());
        info.setMirrorUrls(cat.getMirrorUrls());
        externalDrivers.put(key, info);
        return info;
    }

    // ——— 删除外部驱动 ———

    public void deleteDriver(String key) {
        DriverInfo info = externalDrivers.get(key);
        if (info == null) {
            throw new IllegalArgumentException("驱动不存在: " + key);
        }
        File file = new File(driverPath, info.getFilename());
        if (file.exists()) {
            if (!file.delete()) {
                log.warn("Failed to delete driver file: {}", file.getAbsolutePath());
            }
        }
        externalDrivers.remove(key);
        log.info("Driver deleted: {}, file={}. Please restart for full cleanup.", key, info.getFilename());
    }

    // ——— 动态类加载 ———

    private void addJarToClasspath(File jarFile) {
        try {
            ClassLoader classLoader = ClassLoader.getSystemClassLoader();
            if (classLoader instanceof URLClassLoader) {
                Method method = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                method.setAccessible(true);
                method.invoke(classLoader, jarFile.toURI().toURL());
                log.info("Jar added to classpath: {}", jarFile.getName());
            } else {
                log.warn("System classloader is not URLClassLoader ({}), jar not auto-loaded: {}. Please restart.",
                        classLoader.getClass().getName(), jarFile.getName());
            }
        } catch (Exception e) {
            log.error("Failed to add jar to classpath: {}", jarFile.getName(), e);
        }
    }

    // ——— 辅助方法 ———

    private DriverInfo tryMatchCatalog(String filename, long fileSize) {
        for (DriverInfo cat : catalogDrivers.values()) {
            if (cat.getFilename() != null && cat.getFilename().equalsIgnoreCase(filename)) {
                DriverInfo info = new DriverInfo();
                info.setName(cat.getName());
                info.setKey(cat.getKey());
                info.setDriverClass(cat.getDriverClass());
                info.setDbType(cat.getDbType());
                info.setGroupId(cat.getGroupId());
                info.setArtifactId(cat.getArtifactId());
                info.setVersion(cat.getVersion());
                info.setSource(Source.EXTERNAL);
                info.setLocked(false);
                info.setFilename(filename);
                info.setSize(formatSize(fileSize));
                return info;
            }
        }
        return null;
    }

    private String sanitizeKey(String filename) {
        // 去掉 .jar 后缀，其他特殊字符替换为下划线
        String name = filename.replaceAll("\\.jar$", "");
        return name.replaceAll("[^a-zA-Z0-9_\\-]", "_").toLowerCase();
    }

    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
