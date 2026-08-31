"""一键初始化测试数据：数据源 + 模板"""
import urllib.request, json

BASE = "http://localhost:8010"

def post(path, d):
    req = urllib.request.Request(f"{BASE}{path}",
        data=json.dumps(d).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))

# ============ 数据源 ============
print("创建数据源...")
post("/datasource/save", {
    "name": "MySQL_test_db", "sourceType": "DB", "dbType": "MySQL",
    "host": "127.0.0.1", "port": 3306, "dbName": "test_db",
    "tableNames": "employees", "username": "root", "password": "123456",
    "charset": "UTF-8"
})
print("  MySQL_test_db OK")

# ============ 模板 ============
print("创建模板...")

# T1: DB查询 → 返回
post("/visual/api/save", {"name":"T1_DB→RETURN","description":"最简查询","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 5"}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T1_DB→RETURN OK")

# T2: DB查询 → FILTER → 返回
post("/visual/api/save", {"name":"T2_DB→FILTER→RETURN","description":"查询后过滤","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 10"},{"type":"FILTER","filterField":"age","filterOperator":">","filterValue":"25"}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T2_DB→FILTER→RETURN OK")

# T3: DB查询 → MAPPING → 返回
post("/visual/api/save", {"name":"T3_DB→MAP→RETURN","description":"字段映射","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 5"}],"mapping":{"type":"MAPPING","mappings":[{"src":"name","dst":"userName"},{"src":"age","dst":"userAge"},{"src":"salary","dst":"userSalary"}]},"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T3_DB→MAP→RETURN OK")

# T4: OPERATION DB_QUERY → 返回
post("/visual/api/save", {"name":"T4_OP_QUERY→RETURN","description":"操作事件查询","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_QUERY","sql":"SELECT * FROM employees WHERE age > 25 LIMIT 3"}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T4_OP_QUERY→RETURN OK")

# T5: DB查询 → EVENT(Base64) → 返回
post("/visual/api/save", {"name":"T5_DB→BASE64→RETURN","description":"Base64编码","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 2"},{"type":"EVENT","eventCode":"BASE64_ENCODE","params":{"sourceField":"name","targetField":"name_b64"}}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T5_DB→BASE64→RETURN OK")

# T6: DB查询 → EVENT(MASK) → 返回
post("/visual/api/save", {"name":"T6_DB→MASK→RETURN","description":"姓名脱敏","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 3"},{"type":"EVENT","eventCode":"MASK_NAME","params":{"sourceField":"name","targetField":"name_masked"}}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T6_DB→MASK→RETURN OK")

# T7: DB查询 → FILTER → MD5 → 返回
post("/visual/api/save", {"name":"T7_DB→FILTER→MD5","description":"过滤后Hash","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 5"},{"type":"FILTER","filterField":"salary","filterOperator":">=","filterValue":"15000"},{"type":"EVENT","eventCode":"MD5_HASH","params":{"sourceField":"name","targetField":"name_md5"}}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T7_DB→FILTER→MD5 OK")

# T8: 跨表条件修改 - 子模板(逐行更新)
resp8 = post("/visual/api/save", {"name":"SUB_逐行更新","description":"逐行更新子模板","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_UPDATE","tableName":"employees","fieldMappings":[{"field":"salary","valueSource":"INPUT_PARAM","value":"${newSalary}"},{"field":"department","valueSource":"FIXED_VALUE","value":"已处理"}],"whereConditions":[{"field":"id","operator":"=","value":"${id}"}]}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
sub_id = resp8["data"]["id"]
print(f"  SUB_逐行更新 OK (id={sub_id})")

# T9: 跨表条件修改 - 父模板(定时检查→逐行调用子模板)
cfg9 = {
    "input": {"inputType": "CRON", "cronExpr": "*/5 * * * * ?"},
    "steps": [{"type": "DATA_SOURCE", "dsId": 1,
        "sql": "SELECT id, name, salary, ROUND(salary*1.1,0) AS newSalary FROM employees WHERE salary > 15000 AND department != '已处理'"}],
    "output": {"outputMode": "CALL_TEMPLATE", "callTemplateId": sub_id, "passMode": "ROW", "timeout": 60, "onError": "STOP"}
}
post("/visual/api/save", {"name":"PARENT_定时条件修改","description":"定时检查→条件修改","eventType":"CUSTOM",
    "eventConfig": json.dumps(cfg9, ensure_ascii=False),
    "inputParams":"[]","outputParams":"[]"})
print(f"  PARENT_定时条件修改 OK")

# T10: INSERT(仅保存不执行)
post("/visual/api/save", {"name":"T10_OP_INSERT","description":"INSERT操作","eventType":"CUSTOM",
    "eventConfig":'{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_INSERT","tableName":"employees","fieldMappings":[{"field":"name","valueSource":"FIXED_VALUE","value":"测试用户"},{"field":"age","valueSource":"FIXED_VALUE","value":"30"},{"field":"department","valueSource":"FIXED_VALUE","value":"测试部"},{"field":"salary","valueSource":"FIXED_VALUE","value":"10000"}]}],"output":{"outputMode":"RETURN"}}',
    "inputParams":"[]","outputParams":"[]"})
print("  T10_OP_INSERT OK")

print("\n初始化完成!")
