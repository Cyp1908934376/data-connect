package com.dataconnect.config;

import com.dataconnect.service.DriverService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * 应用启动初始化:
 * 1. 创建 drivers/ 目录(外部JDBC驱动存放)
 * 2. 创建 data/ 目录(H2数据库文件存放)
 * 3. 扫描并加载已存在的外部驱动 jar
 */
@Component
public class DriverInitRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DriverInitRunner.class);

    @Autowired
    private DriverService driverService;

    @Value("${app.driver.path:drivers/}")
    private String driverPath;

    @Value("${app.data-dir:data/}")
    private String dataDir;

    @Override
    public void run(ApplicationArguments args) {
        ensureDir(driverPath, "drivers");
        ensureDir(dataDir, "data");
        driverService.scanExternalDrivers();
    }

    private void ensureDir(String path, String label) {
        File dir = new File(path);
        if (!dir.exists()) {
            boolean created = dir.mkdirs();
            if (created) {
                log.info("Created {} directory: {}", label, dir.getAbsolutePath());
            }
        }
    }
}
