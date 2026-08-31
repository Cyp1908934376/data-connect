import urllib.request, json

BASE = "http://localhost:8010"

def api_post(path, data=None):
    d = json.dumps(data or {}).encode("utf-8")
    req = urllib.request.Request(f"{BASE}{path}", data=d,
        headers={"Content-Type": "application/json"}, method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))

print("="*60)
print("端到端验证：条件自动修改")
print("="*60)

# 1. 看当前数据
print("\n1. 当前数据:")
rows_before = api_post("/visual/api/execute/8", {})
for r in rows_before["data"]["rows"]:
    match = "MATCH!" if r["salary"] > 10000 and r["department"] == "测试部" else ""
    print(f"   id={r['id']} salary={r['salary']} dept={r['department']} {match}")

# 2. 创建测试用父模板（匹配测试部且薪资>10000）
print("\n2. 创建父模板...")
parent_cfg = {
    "input": {"inputType": "MANUAL"},
    "steps": [{
        "type": "DATA_SOURCE",
        "dsId": 1,
        "sql": "SELECT id, name, salary, ROUND(salary*1.5,0) AS newSalary FROM employees WHERE salary > 10000 AND department = '测试部'"
    }],
    "output": {
        "outputMode": "CALL_TEMPLATE",
        "callTemplateId": 17,  # SUB_逐行更新
        "passMode": "ROW",
        "timeout": 60,
        "onError": "IGNORE"
    }
}
resp = api_post("/visual/api/save", {
    "name": "TEST_实时验证",
    "description": "test",
    "eventType": "CUSTOM",
    "eventConfig": json.dumps(parent_cfg, ensure_ascii=False),
    "inputParams": "[]", "outputParams": "[]"
})
tid = resp["data"]["id"]
print(f"   模板ID={tid}")

# 3. 执行
print("\n3. 执行条件修改...")
resp = api_post(f"/visual/api/execute/{tid}", {})
data = resp["data"]
print(f"   success={data['success']} 影响行数={data['rowCount']}")
for r in data["rows"]:
    print(f"   -> affectedRows={r.get('affectedRows','?')}")

# 4. 验证结果
print("\n4. 修改后验证:")
rows_after = api_post("/visual/api/execute/8", {})
for r in rows_after["data"]["rows"]:
    changed = " <-- 被自动修改!" if r["department"] == "定时处理" else ""
    print(f"   id={r['id']} salary={r['salary']} dept={r['department']}{changed}")

print("\n" + "="*60)
print("验证完成")
