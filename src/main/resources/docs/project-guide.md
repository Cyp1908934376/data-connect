# 数据对接服务 — 运维与使用手册

## 目录

1. [项目概述](#1-项目概述)
2. [系统配置](#2-系统配置)
3. [登录与权限](#3-登录与权限)
4. [界面导航](#4-界面导航)
5. [同步策略说明](#5-同步策略说明)
6. [运行时文件说明](#6-运行时文件说明)
7. [如何重置](#7-如何重置)
8. [论文归档流程 (407/408)](#8-论文归档流程-407408)
9. [常见问题](#9-常见问题)
10. [API 接口](#10-api-接口)

---

## 1. 项目概述

数据对接服务是一个基于 Spring Boot 的 ETL 平台，支持：

- **数据源管理**：数据库（MySQL、PostgreSQL、SQL Server 等）和 API 接口
- **模板引擎**：Groovy 脚本处理数据转换
- **字段映射**：接收列 → 推送列的灵活映射
- **管线执行**：读取 → 映射 → 写入 三阶段流程
- **定时任务**：Cron 表达式驱动的周期执行
- **论文归档**：专门对接 Nottingham 论文系统 → 档案管理系统的全流程

**端口**：8010
**数据库**：H2 文件模式（`./data/dataconnect.mv.db`）
**模板引擎**：FreeMarker (.ftl)

---

## 2. 系统配置

配置文件：`application.yml`（打包后在 jar 内）

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `server.port` | 8010 | 服务端口 |
| `spring.datasource.url` | `jdbc:h2:file:./data/dataconnect` | H2 数据库文件位置 |
| `spring.sql.init.mode` | `always` | **每次启动自动执行 schema.sql 和 data.sql** |
| `spring.jpa.hibernate.ddl-auto` | `none` | 表结构由 schema.sql 管理 |
| `app.auth.enabled` | `true` | 是否启用登录认证 |
| `app.driver.path` | `drivers/` | JDBC 驱动 jar 存放目录 |
| `app.data-dir` | `data/` | H2 数据库文件目录 |
| `logging.file.path` | `./logs` | 日志文件目录 |

**重要**：`spring.sql.init.mode=always` 意味着每次重启都会执行 `data.sql`，所有 `MERGE INTO ... KEY(id)` 语句会更新已有数据。如果你手动修改了数据库配置，重启后**可能被 data.sql 覆盖**。

---

## 3. 登录与权限

- 默认账号：**admin / admin**
- 登录地址：`http://{host}:8010/login`
- Token 存储在内存中（`dc_token` Cookie），**重启后所有登录状态丢失**，需重新登录
- 设置 `app.auth.enabled=false` 可关闭认证（开发环境）

开放路径（无需登录）：
- `/login`、`/static/**`、`/h2-console`、`/doc.html`、`/swagger-ui/**`

---

## 4. 界面导航

| 按钮 | 地址 | 功能 |
|---|---|---|
| 数据源管理 | `/datasource/list` | 管理输入/输出数据源配置 |
| 对接模板 | `/mapping/templateList` | 管理字段映射模板 |
| 列配置 | `/mapping/columnConfig` | 管理输入/输出列定义 |
| 流程配置 | `/flow/list` | 创建和管理对接流程 |
| 任务管理 | `/task/list` | 创建和管理定时任务 |
| 模板管理 | `/template/list` | 管理 Groovy 模板和代码片段 |
| 驱动管理 | `/driver/list` | 管理 JDBC 驱动 |
| API 文档 | `/doc.html` | Knife4j 接口文档 |
| 策略文档 | `/docs/sync-strategy` | 同步策略说明（本页面） |
| H2 控制台 | `/h2-console` | 数据库管理界面 |

---

## 5. 同步策略说明

流程配置时有四种同步策略可选：

| 策略 | 枚举值 | 原理 | 适用场景 |
|---|---|---|---|
| 全量同步 | `FULL` | 每次拉全量，所有数据重新推送 | 数据量小或需要完全覆盖 |
| 按时间增量 | `INCREMENTAL_TIME` | 记录最大时间字段值，下次只推 `>=` 该值的数据 | 数据源有可靠时间戳（如 `update_time`） |
| 按ID增量 | `INCREMENTAL_ID` | 记录最大ID值，下次只推 `>` 该值的数据 | 数据源有自增ID字段 |
| 已同步去重 | `SYNCED_SET` | 记录每次成功推送的 UUID 集合，下次跳过已推送的 | 无可靠时间戳/自增ID，仅靠 UUID 去重 |

### 水位线机制（INCREMENTAL_TIME / INCREMENTAL_ID）

- 每次执行成功后写入 `logs/flow/{flowId}/watermark.json`
- 下次执行时读取水位线，自动过滤
- 单次最多处理 **1000 条**

### 已同步集合机制（SYNCED_SET）

- 每次执行成功后追加 UUID 到 `logs/flow/{flowId}/synced-ids.json`
- 下次执行时读取集合，跳过已同步的 UUID
- 集合会持续增长，无自动清理

---

## 6. 运行时文件说明

所有路径相对于 jar 运行目录：

| 路径 | 说明 |
|---|---|
| `data/` | **H2 数据库文件**（dataconnect.mv.db），核心数据存储 |
| `logs/spring.log` | 应用日志，自动轮转 |
| `logs/flow/{flowId}/watermark.json` | **水位线文件**，增量策略使用 |
| `logs/flow/{flowId}/synced-ids.json` | **已同步UUID列表**，SYNCED_SET 策略使用 |
| `logs/flow/{flowId}/execution-*.json` | 每次执行的详细日志（JSON格式） |
| `drivers/` | JDBC 驱动 jar 文件 |
| `exec-{id}.json` | 旧版执行结果文件（可能残留） |

---

## 7. 如何重置

### 7.1 让增量策略重新全量执行

删除对应流程的水位线文件：

```bash
rm logs/flow/{flowId}/watermark.json
```

### 7.2 清空已同步集合

```bash
rm logs/flow/{flowId}/synced-ids.json
```

### 7.3 清除所有执行记录

```bash
rm -rf logs/flow/{flowId}/
```

### 7.4 重置整个数据库到初始状态

```bash
# 停止服务后删除数据库文件，重启即可
rm data/dataconnect.mv.db
```

重启后 `spring.sql.init.mode=always` 会自动重建数据库和初始数据。

### 7.5 热重载 data.sql（不重启）

```bash
curl -X POST http://{host}:8010/api/reload-data-sql
```

### 7.6 热重启应用

```bash
curl -X POST http://{host}:8010/api/restart
```

---

## 8. 论文归档流程 (407/408)

### 8.1 流程 407（旧网关）

- **数据源**：ds_config 401（诺丁汉论文数据源）
- **模板**：template 400（Groovy 脚本，Api-Key 认证）
- **API**：`research.nottingham.edu.cn`
- **映射**：mapping_template 301
- **输出**：ds_config 601（ARCHIVE 模式 → file2Archives）

### 8.2 流程 408（新网关）

- **数据源**：ds_config 602（新网关论文数据源）
- **模板**：template 402（Groovy 脚本，Cookie 认证 + Person API 增强 + Token 自动刷新）
- **API**：`api.nottingham.edu.cn`
- **映射**：mapping_template 301
- **输出**：ds_config 601（ARCHIVE 模式 → file2Archives）
- **同步策略**：SYNCED_SET

### 8.3 归档流程内部处理

`ThesisArchiveService` 对每条论文执行以下步骤：

1. 解析 PDF 下载链接（从 `documents` 数组中取 `application/pdf` 类型）
2. 下载 PDF 文件（新网关用 Cookie 认证，旧网关用 Api-Key）
3. 计算 PDF 的 MD5
4. 按档案系统规范生成 `元数据.xml`（33个字段）
5. 将所有 PDF + 元数据.xml 打包成 ZIP
6. 调档案系统 `getPublicKey` → RSA 加密密码 → `getToken` 获取 JWT
7. multipart/form-data 上传 ZIP 到 `file2Archives` 接口

### 8.4 归档涉及的外部系统账号

| 系统 | 凭证 | 位置 |
|---|---|---|
| 档案系统 token | `appkey=sysadmin, password=UNNCunnc1` | ds_config 601 → api_auth_config |
| 档案系统 URL | `https://docmgt.nottingham.edu.cn/Archives/open_api/file2Archives` | ds_config 601 → api_url |
| Nottingham 新网关 | `efile / ynYZVCeB74LQJ9@k` | template 402 / ThesisArchiveService |
| Nottingham 旧网关 Api-Key | `e3c5f52d-a905-43ac-a10c-4ea5255e368d` | template 400 / ThesisArchiveService |

---

## 9. 常见问题

### Q: 流程瞬间退出，没有数据处理

检查应用日志 `logs/spring.log`，搜索 `Template execution failed`。常见原因：
- 模板中 Groovy 字符串插值 `GStringImpl` 类型转换失败 → 给所有 `${...}` 加 `.toString()`
- API 返回的数据结构与模板预期不符（如只返回引用 `{"systemName":"..."}` 而非完整对象）

### Q: 流程卡在步骤5（写入输出）

步骤5 是逐条处理归档推送的，每条包含 PDF 下载、MD5 计算、ZIP 打包、档案系统上传。单条约 20-25 秒。如果全量 1051 条，总耗时约 5-6 小时。

### Q: 归档推送失败 HTTP 401 "outOfTime"

档案系统返回 401，检查：
1. `ds_config 601` 的 `api_mode` 是否为 `ARCHIVE`
2. `api_auth_config` 中 `appkey` 和 `password` 是否正确
3. 档案系统 URL 是否正确
4. 网络是否能连通档案系统

### Q: 定时任务的 cron 在哪儿配

1. 进入「任务管理」→ 新建/编辑任务
2. 设置 Cron 表达式（如 `0 */5 * * * *` 表示每5分钟）
3. 保存后点击「启动」

### Q: 数据库改了配置后重启又还原了

因为 `spring.sql.init.mode=always`，每次重启 data.sql 会覆盖数据库。修改配置后记得同步到 data.sql，或通过 `/api/export-data-sql` 导出最新数据。

### Q: 图标在离线环境不显示

已被修复。图标文件打包在 jar 内 `static/css/bootstrap-icons.css` + `static/css/fonts/`，不依赖 CDN。

---

## 10. API 接口

完整接口文档见 `/doc.html`（Knife4j）。常用管理接口：

| 接口 | 说明 |
|---|---|
| `POST /flow/api/execute?flowConfigId={id}` | 手动执行流程 |
| `POST /flow/api/cancel` | 取消当前执行 |
| `GET /flow/api/status` | 查看执行状态 |
| `POST /task/api/executeOnce/{id}` | 手动触发一次定时任务 |
| `POST /task/api/start/{id}` | 启动定时任务 |
| `POST /task/api/stop/{id}` | 停止定时任务 |
| `POST /api/restart` | 热重启应用 |
| `POST /api/export-data-sql` | 导出数据库到 data.sql |
| `POST /api/reload-data-sql` | 重新加载 data.sql |
| `GET /h2-console` | H2 数据库控制台 |
