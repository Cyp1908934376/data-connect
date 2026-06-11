# Data Connect - 数据对接服务

零环境依赖、开箱即用的 ETL 数据集成平台。支持多种数据库与 HTTP 接口之间的数据同步、转换与调度。

## 特性

- **零环境依赖** — 内嵌 H2 数据库、内嵌 Tomcat，`java -jar` 一键启动
- **多数据源支持** — 内置 20+ 主流数据库 JDBC 驱动，同时支持 HTTP/HTTPS 接口数据源
- **可视化配置** — 全部通过 Web 界面管理，无需编辑配置文件
- **Groovy 脚本引擎** — 内置 Groovy 运行时，支持自定义数据转换逻辑，带版本管理与回滚
- **灵活的任务调度** — 支持 Cron 表达式定时执行，全量/增量同步策略
- **Pipeline 流水线** — 多阶段数据处理管道（读取后/写入前/写入后），支持模板转换与字段映射
- **三种 API 调用模式** — SINGLE（单次调用）、CHAIN（链式调用）、SCRIPT（脚本编排）
- **自动建表** — 向目标数据库写入时，若表不存在则自动根据数据生成 CREATE TABLE DDL
- **简单认证** — Cookie 会话认证，默认账号 admin/admin

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

### 目录说明

```
data-connect/
├── data/                     # 运行时 H2 数据库文件（自动生成）
├── logs/                     # 运行时日志
│   └── flow/                 # 流水线执行日志 + 增量水印文件
├── src/
│   ├── main/java/com/dataconnect/
│   │   ├── controller/       # Web 控制器
│   │   ├── service/          # 业务服务层
│   │   ├── entity/           # JPA 实体
│   │   ├── repository/       # 数据访问层
│   │   ├── config/           # 配置类（认证拦截器、MVC、数据源）
│   │   ├── dto/              # 数据传输对象
│   │   └── pipeline/         # Pipeline 阶段/步骤模型
│   └── main/resources/
│       ├── application.yml   # 应用配置
│       ├── schema.sql        # DDL 建表脚本
│       ├── data.sql          # 初始化种子数据
│       ├── templates/        # FreeMarker 页面模板
│       └── static/           # 静态资源（Bootstrap 5 + jQuery）
└── pom.xml                   # Maven 构建文件
```

## 功能模块

### 1. 数据源管理

管理数据库与接口两种类型的数据源。

**数据库数据源** — 支持 MySQL、PostgreSQL、Oracle、SQL Server、SQLite、ClickHouse、DB2、Trino、DuckDB、Snowflake 等 20+ 数据库。提供连接测试、SQL 查询调试、表结构浏览功能。

**接口数据源** — 支持 HTTP/HTTPS API，三种调用模式：
- **SINGLE** — 单次 HTTP 调用，支持 `${var}` 变量替换
- **CHAIN** — 多步骤链式调用，支持响应变量提取与传递
- **SCRIPT** — Groovy 脚本编排，可编程控制调用逻辑与错误处理

### 2. 模板管理

- 树形分类管理，支持文件夹层级
- Groovy 脚本编辑器（CodeMirror 代码高亮）
- 版本历史记录与回滚
- 可复用的代码片段库
- 模板变量定义（JSON Schema）

### 3. 数据映射

- 字段列定义（接收列/推送列）
- 字段映射模板（receiveKey → pushKey）
- 支持 Postman Collection JSON 导入

### 4. 对接流程（Pipeline）

四步向导式配置：
1. **选择输入** — 指定源数据源（DB 表或 API）
2. **选择输出** — 指定目标数据源
3. **配置管线** — 添加模板转换/字段映射步骤（AFTER_READ / BEFORE_WRITE / AFTER_WRITE）
4. **执行查看** — 执行流水线并查看结果

同步策略：
- **FULL** — 全量同步
- **INCREMENTAL_ID** — 基于自增 ID 的增量同步
- **INCREMENTAL_TIME** — 基于时间字段的增量同步

### 5. 任务调度

- Cron 表达式定时触发
- 状态管理：RUNNING / PAUSED / STOPPED
- 执行历史记录（SQL 日志 + 控制台日志）
- 支持手动单次执行

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
| 前端 | Bootstrap 5 + jQuery + CodeMirror 5 |

## 内置数据库驱动

MySQL (含 TiDB/OceanBase) · MariaDB · PostgreSQL (含 Greenplum/TimescaleDB/Redshift) · Oracle · SQL Server · SQLite · H2 · ClickHouse · DB2 · Trino · Derby · HSQLDB · TDengine · DuckDB · Firebird · Apache Drill · Presto · Neo4j · SAP HANA · Snowflake

## 配置说明

主要配置项位于 `application.yml`：

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `server.port` | 服务端口 | `8080` |
| `app.auth.enabled` | 启用登录认证 | `true` |
| `spring.datasource.url` | H2 元数据库路径 | `jdbc:h2:file:./data/dataconnect` |
| `spring.h2.console.enabled` | H2 Web 控制台 | `true` |
| `logging.file.path` | 日志路径 | `./logs` |

H2 控制台访问路径：`http://localhost:8080/h2-console`（JDBC URL 使用 `jdbc:h2:file:./data/dataconnect`，用户名 `sa`，无密码）

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
