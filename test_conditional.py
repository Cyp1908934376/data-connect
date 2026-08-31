import urllib.request, json

BASE = "http://localhost:8010"

def api_post(path, data):
    req = urllib.request.Request(f"{BASE}{path}",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))

def api_get(path):
    return json.loads(urllib.request.urlopen(f"{BASE}{path}").read().decode("utf-8"))

print("="*60)
print("方案: 父模板(CRON定时检查) → 逐行调用子模板(执行修改)")
print("="*60)

# Step 1: 创建子模板 - 接收一行数据，执行UPDATE
print("\n--- 1. 创建子模板(逐行处理) ---")
sub_config = {
    "input": {"inputType": "MANUAL"},
    "steps": [{
        "type": "OPERATION",
        "dsId": 1,
        "sourceType": "DB",
        "operationType": "DB_UPDATE",
        "tableName": "employees",
        "fieldMappings": [
            {"field": "salary", "valueSource": "INPUT_PARAM", "value": "${newSalary}"},
            {"field": "department", "valueSource": "FIXED_VALUE", "value": "定时处理"}
        ],
        "whereConditions": [
            {"field": "id", "operator": "=", "value": "${id}"}
        ]
    }],
    "output": {"outputMode": "RETURN"}
}

resp = api_post("/visual/api/save", {
    "name": "SUB_逐行更新",
    "description": "接收一行数据，更新employees表",
    "eventType": "CUSTOM",
    "eventConfig": json.dumps(sub_config, ensure_ascii=False),
    "inputParams": "[]",
    "outputParams": "[]"
})
sub_id = resp["data"]["id"]
print(f"  子模板ID={sub_id}")

# Step 2: 创建父模板 - CRON定时 → 查询 → 逐行调用子模板
print("\n--- 2. 创建父模板(定时检查→逐行调用子模板) ---")
parent_config = {
    "input": {"inputType": "CRON", "cronExpr": "*/5 * * * * ?"},
    "steps": [{
        "type": "DATA_SOURCE",
        "dsId": 1,
        "sql": "SELECT id, name, salary, ROUND(salary*1.1,0) AS newSalary FROM employees WHERE salary > 15000 AND department != '定时处理'"
    }],
    "output": {
        "outputMode": "CALL_TEMPLATE",
        "callTemplateId": sub_id,
        "passMode": "ROW",
        "timeout": 60,
        "onError": "IGNORE"
    }
}

resp = api_post("/visual/api/save", {
    "name": "PARENT_定时加薪",
    "description": "每5分钟查salary>15000的员工，加薪10%",
    "eventType": "CUSTOM",
    "eventConfig": json.dumps(parent_config, ensure_ascii=False),
    "inputParams": "[]",
    "outputParams": "[]"
})
parent_id = resp["data"]["id"]
print(f"  父模板ID={parent_id}")

# Step 3: 测试执行 - 先看当前数据
print("\n--- 3. 修改前数据 ---")
resp = api_post("/visual/api/execute/8", {})  # T1 查询
for r in resp.get("data",{}).get("rows",[])[:5]:
    print(f"  id={r['id']} name={r['name']} salary={r['salary']} dept={r['department']}")

# Step 4: 手动触发执行父模板
print("\n--- 4. 执行父模板（模拟定时触发）---")
resp = api_post(f"/visual/api/execute/{parent_id}", {})
data = resp.get("data", {})
print(f"  success={data.get('success')} rows={data.get('rowCount',0)}")
rows = data.get("rows", [])
for r in rows[:5]:
    print(f"  {r}")

# Step 5: 验证修改后数据
print("\n--- 5. 修改后数据验证 ---")
resp = api_post("/visual/api/execute/8", {})
for r in resp.get("data",{}).get("rows",[])[:5]:
    print(f"  id={r['id']} name={r['name']} salary={r['salary']} dept={r['department']}")

print("\n" + "="*60)
print("测试完成！无需修改任何代码，纯配置实现定时条件跨表修改")
