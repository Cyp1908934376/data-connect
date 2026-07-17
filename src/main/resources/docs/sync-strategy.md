# 同步策略说明

## 四种策略

| 策略 | 枚举值 | 原理 | 适用场景 |
|---|---|---|---|
| 全量同步 | `FULL` | 每次拉全量，所有数据重新推送 | 数据量小或需要完全覆盖 |
| 按时间增量 | `INCREMENTAL_TIME` | 记录最大时间字段值，下次只推 >= 该值的数据 | 数据源有时间戳字段（如 update_time） |
| 按ID增量 | `INCREMENTAL_ID` | 记录最大ID值，下次只推 > 该值的数据 | 数据源有自增ID字段 |
| 已同步去重 | `SYNCED_SET` | 记录每次成功推送的 UUID 集合，下次跳过已推送的 | 无可靠时间戳/自增ID，仅靠 UUID 去重 |

---

## 水位线/同步记录文件位置

所有持久化文件存放在 `./logs/flow/{flowConfigId}/` 目录下：

| 策略 | 文件 | 内容 |
|---|---|---|
| `INCREMENTAL_TIME` / `INCREMENTAL_ID` | `watermark.json` | `{"lastValue":"2024-06-01","incrementalColumn":"submissionDate",...}` |
| `SYNCED_SET` | `synced-ids.json` | `["uuid-1","uuid-2","uuid-3",...]` |
| 所有策略 | `execution-*.json` | 每次执行的详细日志 |

---

## 如何重置

### 重置水位线（让增量策略重新全量执行）

删除 `./logs/flow/{flowConfigId}/watermark.json` 文件。

### 重置已同步集合（让 SYNCED_SET 重新全量执行）

删除 `./logs/flow/{flowConfigId}/synced-ids.json` 文件。

### 清空所有执行记录

删除 `./logs/flow/{flowConfigId}/` 整个目录。

---

## 408 流程使用 SYNCED_SET

流程 408（新网关论文归档）使用 `SYNCED_SET` 策略：

1. **首次执行**：拉全量 1051 条，全部推送，记录所有 UUID 到 `synced-ids.json`
2. **后续执行**：拉全量列表 → 模板过滤已同步 UUID → 只推新论文 → 追加新 UUID 到文件
3. **重置**：删除 `./logs/flow/408/synced-ids.json` 即可重新全量推送
