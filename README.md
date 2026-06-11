# Data Connect - 数据对接服务

零环境依赖、开箱即用的 ETL 数据集成平台。支持多种数据库与 HTTP 接口之间的数据同步、转换与调度。

## 特性

- **零环境依赖** — 内嵌 H2 数据库、内嵌 Tomcat，`java -jar` 一键启动
- **多数据源支持** — 内置 20+ 主流数据库 JDBC 驱动，同时支持 HTTP/HTTPS 接口数据源
- **驱动管理** — Web 界面管理 JDBC 驱动，支持多镜像下载（含阿里云/清华镜像）、手动上传、运行时动态加载
- **可视化配置** — 全部通过 Web 界面管理，无需编辑配置文件
- **Groovy 脚本引擎** — 内置 Groovy 运行时，支持自定义数据转换逻辑，带版本管理与回滚
- **灵活的任务调度** — 支持 Cron 表达式定时执行，全量/增量同步策略
- **Pipeline 流水线** — 多阶段数据处理管道（读取后/写入前/写入后），支持模板转换与字段映射
- **三种 API 调用模式** — SINGLE（单次调用）、CHAIN（链式调用）、SCRIPT（脚本编排）
- **自动建表** — 向目标数据库写入时，若表不存在则自动根据数据生成 CREATE TABLE DDL
- **API 接口文档** — 集成 Knife4j，启动后访问 `/doc.html` 查看所有接口文档并在线调试
- **应用内重启** — 页面右上角一键重启，修改配置后无需手动重启进程

## 快速开始

### 环境要求

- JDK 8+
- 现代浏览器（Chrome 80+ / Firefox 75+ / Edge 80+）

### 启动

```bash
# 方式一：Maven 启动（开发）
mvn spring-boot:run

# 方式二：打包后启动（部署）
mvn clean package -DskipTests
java -jar target/data-connect-1.0.0.jar
```

启动后访问：**http://localhost:8080**

默认账号：`admin` / `admin`

API 文档：**http://localhost:8080/doc.html**

### 目录说明

```
data-connect/
├── data/                     # 运行时 H2 数据库文件（自动生成）
├── drivers/                  # 外部 JDBC 驱动 jar 存放目录
├── logs/                     # 运行时日志
│   └── flow/                 # 流水线执行日志 + 增量水印文件
├── src/
│   ├── main/java/com/dataconnect/
│   │   ├── controller/       # Web 控制器（含驱动管理、系统管理）
│   │   ├── service/          # 业务服务层（含驱动下载/加载服务）
│   │   ├── entity/           # JPA 实体
│   │   ├── repository/       # 数据访问层
│   │   ├── config/           # 配置类（认证拦截器、MVC、驱动初始化）
│   │   ├── dto/              # 数据传输对象
│   │   └── pipeline/         # Pipeline 阶段/步骤模型
│   └── main/resources/
│       ├── application.yml   # 应用配置
│       ├── schema.sql        # DDL 建表脚本
│       ├── data.sql          # 初始化种子数据
│       ├── loader.properties # 外部驱动加载配置
│       ├── templates/        # FreeMarker 页面模板
│       └── static/           # 静态资源（Bootstrap 5 + jQuery）
└── pom.xml                   # Maven 构建文件
```

## 功能模块

### 1. 数据源管理

管理数据库与接口两种类型的数据源。数据库类型下拉框根据已安装驱动动态生成。

**数据库数据源** — 支持 MySQL、PostgreSQL、Oracle、SQL Server、SQLite、ClickHouse、DB2、Trino、DuckDB、Snowflake 等 20+ 数据库。提供连接测试、SQL 查询调试、表结构浏览功能。

**接口数据源** — 支持 HTTP/HTTPS API，三种调用模式：
- **SINGLE** — 单次 HTTP 调用，支持 `${var}` 变量替换
- **CHAIN** — 多步骤链式调用，支持响应变量提取与传递
- **SCRIPT** — Groovy 脚本编排，可编程控制调用逻辑与错误处理

### 2. 驱动管理

Web 界面管理 JDBC 驱动，支持以下操作：

- **多镜像下载** — 内置三个 Maven 镜像源（国际 / 阿里云 / 清华），点击按钮选择镜像下载，自动安装并加载到运行时 classpath
- **上传驱动** — 支持拖拽或点击上传 `.jar` 驱动文件，自动匹配驱动元数据
- **删除驱动** — 删除外部驱动 jar 文件（建议删除后重启以完全卸载）
- **内置驱动** — H2、MySQL、PostgreSQL、SQL Server 四个驱动打包在 jar 内，不可删除

数据源管理的"数据库类型"下拉框会根据已安装驱动动态变化，下载或上传新驱动后即可在数据源表单中选择。

### 3. 模板管理

- 树形分类管理，支持文件夹层级
- Groovy 脚本编辑器（CodeMirror 代码高亮）
- 版本历史记录与回滚
- 可复用的代码片段库
- 模板变量定义（JSON Schema）

### 4. 数据映射

- 字段列定义（接收列/推送列）
- 字段映射模板（receiveKey → pushKey）
- 支持 Postman Collection JSON 导入

### 5. 对接流程（Pipeline）

四步向导式配置：
1. **选择输入** — 指定源数据源（DB 表或 API）
2. **选择输出** — 指定目标数据源
3. **配置管线** — 添加模板转换/字段映射步骤（AFTER_READ / BEFORE_WRITE / AFTER_WRITE）
4. **执行查看** — 执行流水线并查看结果

同步策略：
- **FULL** — 全量同步
- **INCREMENTAL_ID** — 基于自增 ID 的增量同步
- **INCREMENTAL_TIME** — 基于时间字段的增量同步

### 6. 任务调度

- Cron 表达式定时触发
- 状态管理：RUNNING / PAUSED / STOPPED
- 执行历史记录（SQL 日志 + 控制台日志）
- 支持手动单次执行

### 7. 系统管理

- **应用内重启** — 页面右上角重启按钮，修改配置后无需到服务器操作
- **API 接口文档** — 右上角文档按钮，新窗口打开 Knife4j 中文接口文档，支持在线调试

## 技术栈

| 类别 | 技术 |
|------|------|
| 框架 | Spring Boot 2.7.18 |
| 语言 | Java 8 |
| 构建 | Maven |
| 模板引擎 | FreeMarker |
| 元数据存储 | H2 嵌入式数据库 |
| 连接池 | HikariCP |
| HTTP 客户端 | OkHttp 4 |
| 脚本引擎 | Groovy 3.0 |
| API 文档 | Knife4j 4.3 + springdoc-openapi |
| 前端 | Bootstrap 5 + jQuery + CodeMirror 5 |

## 配置说明

主要配置项位于 `application.yml`：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | `8080` |
| `app.auth.enabled` | 启用登录认证 | `true` |
| `app.driver.path` | 外部 JDBC 驱动存放目录 | `drivers/` |
| `app.driver.maven-urls` | Maven 镜像地址（逗号分隔） | Maven Central + 阿里云 + 清华 |
| `app.data-dir` | H2 数据库文件目录 | `data/` |
| `spring.datasource.url` | H2 元数据库路径 | `jdbc:h2:file:./data/dataconnect` |
| `spring.h2.console.enabled` | H2 Web 控制台 | `true` |
| `logging.file.path` | 日志路径 | `./logs` |
| `knife4j.enable` | 启用 API 文档 | `true` |

H2 控制台访问路径：`http://localhost:8080/h2-console`（JDBC URL 使用 `jdbc:h2:file:./data/dataconnect`，用户名 `sa`，无密码）

国内用户建议将 `app.driver.maven-urls` 中的阿里云或清华镜像放在首位，加快驱动下载速度。

## 构建与部署

```bash
# 打包
mvn clean package -DskipTests

# 启动
java -jar target/data-connect-1.0.0.jar

# 指定端口
java -jar target/data-connect-1.0.0.jar --server.port=9090

# 关闭认证（开发模式）
java -jar target/data-connect-1.0.0.jar --app.auth.enabled=false
```

## License

MIT
