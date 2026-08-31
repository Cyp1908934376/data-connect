import urllib.request, json, urllib.parse

BASE='http://localhost:8010'
def post(path,d):
    req=urllib.request.Request(BASE+path,json.dumps(d).encode(),headers={'Content-Type':'application/json'},method='POST')
    return json.loads(urllib.request.urlopen(req).read())

# 1. 查看真实数据
print('=== s_case_box_print_job 当前数据 ===')
url=f'{BASE}/datasource/api/previewData?id=1&tableName=s_case_box_print_job&limit=10'
data=json.loads(urllib.request.urlopen(url).read())
rows=data['data']['rows']
print(f'  共{len(rows)}行')
for r in rows:
    print(f'  id={r["id"]} status={r.get("status")} detail={r.get("status_detail","")}')

# 2. 创建子模板: 更新status
r=post('/visual/api/save',{'name':'SUB_自动处理Job','description':'更新print job','eventType':'CUSTOM',
    'eventConfig':'{"input":{"inputType":"MANUAL"},"steps":[{"type":"OPERATION","dsId":1,"sourceType":"DB","operationType":"DB_UPDATE","tableName":"s_case_box_print_job","fieldMappings":[{"field":"status","valueSource":"FIXED_VALUE","value":"1"},{"field":"status_detail","valueSource":"FIXED_VALUE","value":"自动处理完成"}],"whereConditions":[{"field":"id","operator":"=","value":"${id}"}]}],"output":{"outputMode":"RETURN"}}',
    'inputParams':'[]','outputParams':'[]'})
sub_id=r['data']['id']; print(f'\n子模板 id={sub_id}')

# 3. 创建父模板: 查status=0 -> 逐行调子模板
cfg={'input':{'inputType':'MANUAL'},'steps':[{'type':'DATA_SOURCE','dsId':1,'sql':"SELECT id, case_box_id, status FROM s_case_box_print_job WHERE status=0"}],'output':{'outputMode':'CALL_TEMPLATE','callTemplateId':sub_id,'passMode':'ROW','timeout':60,'onError':'STOP'}}
r=post('/visual/api/save',{'name':'PARENT_自动处理Job','description':'检查status=0并自动处理','eventType':'CUSTOM','eventConfig':json.dumps(cfg),'inputParams':'[]','outputParams':'[]'})
pid=r['data']['id']; print(f'父模板 id={pid}')

# 4. 执行!
print('\n=== 执行条件自动处理 ===')
r=post(f'/visual/api/execute/{pid}',{})
d=r['data']
print(f'  success={d["success"]} 影响行数={d["rowCount"]}')
for row in d['rows']:
    print(f'  -> affectedRows={row["affectedRows"]}')

# 5. 验证结果
print('\n=== 处理后验证 ===')
url=f'{BASE}/datasource/api/previewData?id=1&tableName=s_case_box_print_job&limit=10'
data=json.loads(urllib.request.urlopen(url).read())
for r in data['data']['rows']:
    ch=' <-- 自动处理!' if r.get('status_detail')=='自动处理完成' else ''
    print(f'  id={r["id"]} status={r.get("status")} detail={r.get("status_detail","")}{ch}')
