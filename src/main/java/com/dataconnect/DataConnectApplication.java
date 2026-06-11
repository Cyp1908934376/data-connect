package com.dataconnect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class DataConnectApplication {

    private static final Logger log = LoggerFactory.getLogger(DataConnectApplication.class);

    private static ConfigurableApplicationContext context;
    private static String[] appArgs;

    public static void main(String[] args) {
        appArgs = args;
        context = SpringApplication.run(DataConnectApplication.class, args);
        log.info("========================================");
        log.info("  Data-Connect 数据对接服务 启动完成");
        log.info("  H2 Console: http://localhost:8080/h2-console");
        log.info("  数据文件: ./data/");
        log.info("========================================");
    }

    /** 触发应用重启 */
    public static synchronized void restart() {
        log.info("====== 应用重启中... ======");
        Thread thread = new Thread(() -> {
            try {
                Thread.sleep(1500); // 等待 HTTP 响应返回
            } catch (InterruptedException ignored) {}
            context.close();
            try {
                Thread.sleep(2000); // 等待端口释放
            } catch (InterruptedException ignored) {}
            context = SpringApplication.run(DataConnectApplication.class, appArgs);
            log.info("====== 应用重启完成 ======");
        });
        thread.setDaemon(false);
        thread.start();
    }
}
