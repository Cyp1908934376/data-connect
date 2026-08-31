package com.dataconnect.service;

import com.dataconnect.entity.EventDefinition;
import com.dataconnect.repository.EventDefinitionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 事件定义服务
 */
@Service
public class EventDefinitionService {

    private static final Logger log = LoggerFactory.getLogger(EventDefinitionService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private EventDefinitionRepository eventDefinitionRepository;

    /**
     * 初始化内置事件。已存在的 code 不覆盖，缺失的会补齐。
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initBuiltinEvents() {
        try {
            eventDefinitionRepository.count();
        } catch (Exception e) {
            log.warn("无法查询事件定义表（可能尚未创建），跳过内置事件初始化: {}", e.getMessage());
            return;
        }

        log.info("检查内置事件定义...");
        eventDefinitionRepository.findByCode("THESIS_ARCHIVE").ifPresent(e -> {
            eventDefinitionRepository.delete(e);
            log.info("已移除数据库事件 THESIS_ARCHIVE（改为可视化模板写死步骤）");
        });
        int added = 0;
        for (EventDefinition def : allBuiltinDefs()) {
            if (!eventDefinitionRepository.findByCode(def.getCode()).isPresent()) {
                eventDefinitionRepository.save(def);
                added++;
                log.info("补齐内置事件: {}", def.getCode());
            }
        }
        log.info("内置事件检查完成, 新增={}", added);
    }

    private List<EventDefinition> allBuiltinDefs() {
        List<EventDefinition> builtins = new ArrayList<>();

        // 编码转换
        builtins.add(buildEvent("Base64编码", "BASE64_ENCODE", "编码转换", "将指定字段值编码为Base64",
                "bi-shield-lock", "BUILTIN", "base64Encode",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true,\"description\":\"需要编码的字段名\"},{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":false,\"description\":\"编码后输出的字段名，默认覆盖源字段\"}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"对指定字段进行Base64编码\"}"));

        builtins.add(buildEvent("Base64解码", "BASE64_DECODE", "编码转换", "将Base64编码的字段值解码",
                "bi-shield-lock", "BUILTIN", "base64Decode",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"对指定字段进行Base64解码\"}"));

        builtins.add(buildEvent("URL编码", "URL_ENCODE", "编码转换", "对指定字段值进行URL编码",
                "bi-link", "BUILTIN", "urlEncode",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"对指定字段进行URL编码\"}"));

        builtins.add(buildEvent("URL解码", "URL_DECODE", "编码转换", "对URL编码的字段值进行解码",
                "bi-link", "BUILTIN", "urlDecode",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"对指定字段进行URL解码\"}"));

        // 加密安全
        builtins.add(buildEvent("MD5哈希", "MD5_HASH", "加密安全", "计算指定字段的MD5哈希值",
                "bi-lock", "BUILTIN", "md5Hash",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true},{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":false}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"计算MD5哈希值\"}"));

        builtins.add(buildEvent("SHA256哈希", "SHA256_HASH", "加密安全", "计算指定字段的SHA-256哈希值",
                "bi-lock-fill", "BUILTIN", "sha256Hash",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"计算SHA-256哈希值\"}"));

        // 数据脱敏
        builtins.add(buildEvent("手机号脱敏", "MASK_PHONE", "数据脱敏", "手机号脱敏处理，如138****1234",
                "bi-eye-slash", "BUILTIN", "maskPhone",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true,\"description\":\"包含手机号的字段名\"}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"手机号中间四位替换为****\"}"));

        builtins.add(buildEvent("身份证脱敏", "MASK_IDCARD", "数据脱敏", "身份证号脱敏处理，保留前6后4位",
                "bi-eye-slash", "BUILTIN", "maskIdCard",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"身份证号中间替换为****\"}"));

        builtins.add(buildEvent("姓名脱敏", "MASK_NAME", "数据脱敏", "姓名脱敏处理，如张**",
                "bi-eye-slash", "BUILTIN", "maskName",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"姓名只保留第一个字，其余替换为*\"}"));

        builtins.add(buildEvent("邮箱脱敏", "MASK_EMAIL", "数据脱敏", "邮箱地址脱敏处理，如t***@example.com",
                "bi-eye-slash", "BUILTIN", "maskEmail",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"邮箱用户名部分脱敏\"}"));

        // 格式转换
        builtins.add(buildEvent("JSON转XML", "JSON_TO_XML", "格式转换", "将JSON数据转换为XML格式",
                "bi-arrow-left-right", "BUILTIN", "jsonToXml",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true,\"description\":\"包含JSON字符串的字段\"}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"JSON转换为XML字符串\"}"));

        builtins.add(buildEvent("XML转JSON", "XML_TO_JSON", "格式转换", "将XML数据转换为JSON格式",
                "bi-arrow-left-right", "BUILTIN", "xmlToJson",
                "[{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true}]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"XML转换为JSON字符串\"}"));

        builtins.add(buildEvent("自增序号", "AUTO_INCREMENT", "数据生成",
                "按行生成自增序号，写入目标字段。同一批运行内连续编号；可按分组字段分别从起始值计数。",
                "bi-123", "BUILTIN", "autoIncrement",
                "["
                        + "{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":true,\"description\":\"序号写入的字段名，如 案卷号\"},"
                        + "{\"name\":\"startValue\",\"label\":\"起始值\",\"type\":\"number\",\"required\":false,\"description\":\"默认 1\"},"
                        + "{\"name\":\"step\",\"label\":\"步长\",\"type\":\"number\",\"required\":false,\"description\":\"默认 1\"},"
                        + "{\"name\":\"padLength\",\"label\":\"补零位数\",\"type\":\"number\",\"required\":false,\"description\":\"如 4 则输出 0001，0 或不填则不补零\"},"
                        + "{\"name\":\"prefix\",\"label\":\"前缀\",\"type\":\"string\",\"required\":false,\"description\":\"可选，如 A- 则输出 A-1\"},"
                        + "{\"name\":\"groupField\",\"label\":\"分组字段\",\"type\":\"string\",\"required\":false,\"description\":\"可选，相同分组值各自从起始值编号\"}"
                        + "]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"为每行写入自增序号\"}"));

        builtins.add(buildEvent("四位序号", "SEQ_PAD4", "数据生成",
                "按行生成 0001、0002 这样的四位序号。默认写入「文件号」。同一运行内连续编号，可按案卷号等字段分组。",
                "bi-123", "BUILTIN", "autoIncrementPad4",
                "["
                        + "{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":false,\"defaultValue\":\"文件号\",\"description\":\"默认 文件号\"},"
                        + "{\"name\":\"startValue\",\"label\":\"起始值\",\"type\":\"number\",\"required\":false,\"defaultValue\":1,\"description\":\"默认 1\"},"
                        + "{\"name\":\"step\",\"label\":\"步长\",\"type\":\"number\",\"required\":false,\"defaultValue\":1,\"description\":\"默认 1\"},"
                        + "{\"name\":\"padLength\",\"label\":\"补零位数\",\"type\":\"number\",\"required\":false,\"defaultValue\":4,\"description\":\"默认 4，输出 0001\"},"
                        + "{\"name\":\"prefix\",\"label\":\"前缀\",\"type\":\"string\",\"required\":false,\"description\":\"可选，如 A- 则输出 A-0001\"},"
                        + "{\"name\":\"groupField\",\"label\":\"分组字段\",\"type\":\"string\",\"required\":false,\"description\":\"可选，如 案卷号，相同值各自从 0001 编号\"}"
                        + "]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"生成四位补零序号\"}"));

        builtins.add(buildEvent("四位补零", "PAD_LEFT", "格式转换",
                "把已有编号左侧补零。默认 4 位：1 → 0001，12 → 0012。教务已有文件号时用这个，不要用四位序号。",
                "bi-input-cursor", "BUILTIN", "padLeft",
                "["
                        + "{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true,\"defaultValue\":\"文件号\",\"description\":\"要补零的字段，如 文件号\"},"
                        + "{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":false,\"defaultValue\":\"文件号\",\"description\":\"默认覆盖源字段\"},"
                        + "{\"name\":\"padLength\",\"label\":\"补零位数\",\"type\":\"number\",\"required\":false,\"defaultValue\":4,\"description\":\"默认 4\"}"
                        + "]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"左侧补零到指定位数\"}"));

        builtins.add(buildEvent("中文首字母", "PINYIN_INITIAL", "格式转换",
                "将中文转为拼音首字母大写，如 信息中心 → XXZX。字母数字保留，标点忽略。",
                "bi-fonts", "BUILTIN", "pinyinInitial",
                "["
                        + "{\"name\":\"sourceField\",\"label\":\"源字段\",\"type\":\"string\",\"required\":true,\"description\":\"中文内容所在字段\"},"
                        + "{\"name\":\"targetField\",\"label\":\"目标字段\",\"type\":\"string\",\"required\":false,\"description\":\"输出字段，默认覆盖源字段\"}"
                        + "]",
                "{\"type\":\"FIELD_MODIFY\",\"description\":\"中文转拼音首字母\"}"));

        return builtins;
    }

    private EventDefinition buildEvent(String name, String code, String category, String description,
                                        String icon, String handlerType, String handlerConfig,
                                        String inputSchema, String outputSchema) {
        EventDefinition event = new EventDefinition();
        event.setName(name);
        event.setCode(code);
        event.setCategory(category);
        event.setDescription(description);
        event.setIcon(icon);
        event.setHandlerType(handlerType);
        event.setHandlerConfig(handlerConfig);
        event.setInputSchema(inputSchema);
        event.setOutputSchema(outputSchema);
        event.setIsBuiltin(1);
        event.setIsEnabled(1);
        return event;
    }

    public List<EventDefinition> listAll() {
        return eventDefinitionRepository.findByIsEnabledOrderByCategoryAscNameAsc(1);
    }

    public List<EventDefinition> listByCategory(String category) {
        return eventDefinitionRepository.findByCategoryAndIsEnabled(category, 1);
    }

    public Optional<EventDefinition> getById(Long id) {
        return eventDefinitionRepository.findById(id);
    }

    public Optional<EventDefinition> getByCode(String code) {
        return eventDefinitionRepository.findByCode(code);
    }

    public EventDefinition save(EventDefinition event) {
        log.info("保存事件定义, name={}, code={}", event.getName(), event.getCode());
        return eventDefinitionRepository.save(event);
    }

    public EventDefinition update(Long id, EventDefinition updated) {
        EventDefinition existing = eventDefinitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("事件定义不存在: " + id));

        existing.setName(updated.getName());
        existing.setCode(updated.getCode());
        existing.setCategory(updated.getCategory());
        existing.setDescription(updated.getDescription());
        existing.setIcon(updated.getIcon());
        existing.setInputSchema(updated.getInputSchema());
        existing.setOutputSchema(updated.getOutputSchema());
        existing.setHandlerType(updated.getHandlerType());
        existing.setHandlerConfig(updated.getHandlerConfig());
        existing.setIsEnabled(updated.getIsEnabled());

        log.info("事件定义已更新, id={}", id);
        return eventDefinitionRepository.save(existing);
    }

    public void delete(Long id) {
        EventDefinition event = eventDefinitionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("事件定义不存在: " + id));
        if (event.getIsBuiltin() == 1) {
            throw new RuntimeException("内置事件不可删除");
        }
        eventDefinitionRepository.deleteById(id);
        log.info("事件定义已删除, id={}", id);
    }

    public List<Map<String, Object>> listForApi() {
        List<Map<String, Object>> result = new ArrayList<>();
        for (EventDefinition event : listAll()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", event.getId());
            item.put("name", event.getName());
            item.put("code", event.getCode());
            item.put("category", event.getCategory());
            item.put("description", event.getDescription());
            item.put("icon", event.getIcon());
            item.put("handlerType", event.getHandlerType());
            item.put("isBuiltin", event.getIsBuiltin());
            item.put("inputSchema", normalizeInputSchema(event));
            result.add(item);
        }
        return result;
    }

    /**
     * 前端 EVENT 步骤只认 {description, fields:[]}。
     * 旧内置事件存的是字段数组，这里统一包一层。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> normalizeInputSchema(EventDefinition event) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("description", event.getDescription() != null ? event.getDescription() : "");
        schema.put("fields", new ArrayList<>());
        String raw = event.getInputSchema();
        if (raw == null || raw.isEmpty()) {
            return schema;
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List) {
                schema.put("fields", parsed);
            } else if (parsed instanceof Map) {
                Map<String, Object> map = (Map<String, Object>) parsed;
                if (map.get("description") != null) {
                    schema.put("description", map.get("description"));
                }
                Object fields = map.get("fields");
                if (fields instanceof List) {
                    schema.put("fields", fields);
                }
            }
        } catch (Exception e) {
            log.debug("解析事件 inputSchema 失败, code={}: {}", event.getCode(), e.getMessage());
        }
        return schema;
    }
}
