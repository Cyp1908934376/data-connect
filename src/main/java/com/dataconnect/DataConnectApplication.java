package com.dataconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataConnectApplication {

    private static final Logger log = LoggerFactory.getLogger(DataConnectApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(DataConnectApplication.class, args);
        log.info("========================================");
        log.info("  Data-Connect 数据对接服务 启动完成");
        log.info("  H2 Console: http://localhost:8080/h2-console");
        log.info("  数据文件: ./data/");
        log.info("========================================");
    }
}
