import urllib.request
import json

BASE = "http://localhost:8010"

def api_post(path, data):
    req = urllib.request.Request(f"{BASE}{path}",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST")
    return json.loads(urllib.request.urlopen(req).read().decode("utf-8"))

def api_get(path):
    return json.loads(urllib.request.urlopen(f"{BASE}{path}").read().decode("utf-8"))

# 测试模板定义
templates = [
    # [名称, 描述, eventConfig, outputParams]
    ["T1_DB_RETURN", "DB查询->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 3"}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T2_DB_FILTER_RETURN", "DB查询->过滤->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 10"},{"type":"FILTER","filterField":"age","filterOperator":">","filterValue":"25"}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T3_DB_MAP_RETURN", "DB查询->映射->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 5"}],"mapping":{"type":"MAPPING","mappings":[{"src":"name","dst":"userName"},{"src":"age","dst":"userAge"},{"src":"salary","dst":"userSalary"}]},"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T4_OP_QUERY_RETURN", "操作事件DB查询->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_QUERY","sql":"SELECT * FROM employees WHERE age > 25 LIMIT 3","tableName":"employees"}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T5_DB_EVENT_RETURN", "DB查询->Base64事件->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 2"},{"type":"EVENT","eventCode":"BASE64_ENCODE","params":{"sourceField":"name","targetField":"name_b64"}}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T6_DB_MASK_RETURN", "DB查询->姓名脱敏->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 2"},{"type":"EVENT","eventCode":"MASK_NAME","params":{"sourceField":"name","targetField":"name_masked"}}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T7_DB_FILTER_HASH", "DB查询->过滤->MD5->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 5"},{"type":"FILTER","filterField":"salary","filterOperator":">=","filterValue":"15000"},{"type":"EVENT","eventCode":"MD5_HASH","params":{"sourceField":"name","targetField":"name_md5"}}],"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T8_DB_EVENT_MAP", "DB查询->脱敏->映射->返回",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"DATA_SOURCE","dsId":1,"sql":"SELECT * FROM employees LIMIT 3"},{"type":"EVENT","eventCode":"MASK_NAME","params":{"sourceField":"name","targetField":"name_masked"}}],"mapping":{"type":"MAPPING","mappings":[{"src":"name_masked","dst":"姓名"},{"src":"department","dst":"部门"},{"src":"salary","dst":"薪资"}]},"output":{"outputMode":"RETURN"}}',
     '[]'],

    ["T9_OP_INSERT", "操作事件INSERT(仅保存不执行)",
     '{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_INSERT","tableName":"employees","fieldMappings":[{"field":"name","valueSource":"FIXED_VALUE","value":"测试用户"},{"field":"age","valueSource":"FIXED_VALUE","value":"30"},{"field":"department","valueSource":"FIXED_VALUE","value":"测试部"},{"field":"salary","valueSource":"FIXED_VALUE","value":"10000"}]}],"output":{"outputMode":"RETURN"}}',
     '[]'],
]

print("=" * 60)
print("创建测试模板...")
ids = {}
for name, desc, config, out_params in templates:
    resp = api_post("/visual/api/save", {
        "name": name, "description": desc, "eventType": "CUSTOM",
        "eventConfig": config, "inputParams": "[]", "outputParams": out_params
    })
    tid = resp["data"]["id"]
    ids[name] = tid
    print(f"  {name} -> id={tid}")

print()
print("=" * 60)
print("执行测试模板...")
for name in ["T1_DB_RETURN", "T2_DB_FILTER_RETURN", "T3_DB_MAP_RETURN",
             "T4_OP_QUERY_RETURN", "T5_DB_EVENT_RETURN", "T6_DB_MASK_RETURN",
             "T7_DB_FILTER_HASH", "T8_DB_EVENT_MAP"]:
    tid = ids.get(name)
    if not tid: continue
    try:
        # 执行模板（需要找到执行端点）
        resp = api_post(f"/visual/api/execute/{tid}", {})
        code = resp.get("code", -1)
        data = resp.get("data", {})
        if code == 0 and data:
            rows = data.get("rows", [])
            row_count = len(rows) if rows else 0
            print(f"  {name}: OK rows={row_count}")
            if rows and len(rows) > 0:
                first = {k: str(v)[:60] for k,v in list(rows[0].items())[:5]}
                print(f"    first_row: {first}")
        else:
            print(f"  {name}: code={code} msg={resp.get('message','?')}")
    except Exception as e:
        print(f"  {name}: ERROR - {str(e)[:100]}")

print()
print("=" * 60)
print("测试完成!")
