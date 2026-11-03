package com.aiyi.game.dnfserver.service.impl;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.thread.ThreadUtil;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.AssistConfigDao;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.entity.AssistConfig;
import com.aiyi.game.dnfserver.service.AssistConfigService;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.*;

/**
 * 内辅/客户端配置 Service 实现
 *
 * @author Copilot
 */
@Service
public class AssistConfigServiceImpl implements AssistConfigService {

    @Resource
    private AssistConfigDao assistConfigDao;

    @Resource
    private AccountVODao accountVODao;

    private static final int SINGLETON_ID = 1;

    @Override
    public Map<String, Object> getConfig() {
        AssistConfig cfg = assistConfigDao.get(SINGLETON_ID);
        if (cfg == null) {
            cfg = new AssistConfig();
            cfg.setId(SINGLETON_ID);
            cfg.setConfigJson(JSON.toJSONString(defaultConfig()));
            cfg.setUpdateTime(new Date());
            assistConfigDao.add(cfg);
        }

        if (cfg.getConfigJson() == null || cfg.getConfigJson().trim().isEmpty()) {
            return defaultConfig();
        }

        try {
            JSONObject obj = JSON.parseObject(cfg.getConfigJson());
            // 保持 key 顺序
            return new LinkedHashMap<>(obj);
        } catch (Exception e) {
            return defaultConfig();
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> saveConfig(Map<String, Object> config) {
        AccountVO accountVO = accountVODao.get(ThreadUtil.getUserId());
        if (!accountVO.isSuperAdmin()) {
            throw new ValidationException("只有最高管理员才能执行此操作");
        }
        if (config == null) {
            throw new ValidationException("参数不能为空");
        }

        AssistConfig exists = assistConfigDao.get(SINGLETON_ID);
        if (exists == null) {
            exists = new AssistConfig();
            exists.setId(SINGLETON_ID);
        }

        exists.setConfigJson(JSON.toJSONString(config));
        exists.setUpdateTime(new Date());

        if (assistConfigDao.get(SINGLETON_ID) == null) {
            assistConfigDao.add(exists);
        } else {
            assistConfigDao.update(exists);
        }

        return config;
    }

    @Override
    public String getConfigText() {
        Map<String, Object> cfg = getConfig();
        return toConfigText(cfg);
    }

    private String toConfigText(Map<String, Object> cfg) {
        StringBuilder sb = new StringBuilder();

        // 顶层普通键（分组键最后写）
        List<String> groupKeys = Arrays.asList("自动拾取", "自动翻牌", "史诗闪光", "补丁信息");

        for (Map.Entry<String, Object> e : cfg.entrySet()) {
            String key = e.getKey();
            if (groupKeys.contains(key)) {
                continue;
            }
            sb.append(formatLine(key, e.getValue())).append("\n");
        }

        // 分组
        for (String groupKey : groupKeys) {
            Object groupVal = cfg.get(groupKey);
            if (!(groupVal instanceof Map)) {
                continue;
            }
            sb.append("\n");
            sb.append("[\"").append(groupKey).append("\"]\n");

            @SuppressWarnings("unchecked")
            Map<String, Object> group = (Map<String, Object>) groupVal;
            for (Map.Entry<String, Object> e : group.entrySet()) {
                sb.append(formatLine(e.getKey(), e.getValue())).append("\n");
            }
        }

        return sb.toString().trim() + "\n";
    }

    private String formatLine(String key, Object value) {
        return "\"" + key + "\" = " + formatValue(value);
    }

    private String formatValue(Object value) {
        if (value == null) {
            return "\"\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        if (value instanceof String) {
            return "\"" + escape((String) value) + "\"";
        }
        if (value instanceof Collection) {
            return formatArray(((Collection<?>) value).toArray());
        }
        if (value.getClass().isArray()) {
            int len = java.lang.reflect.Array.getLength(value);
            Object[] arr = new Object[len];
            for (int i = 0; i < len; i++) {
                arr[i] = java.lang.reflect.Array.get(value, i);
            }
            return formatArray(arr);
        }
        // 兜底：对象转字符串
        return "\"" + escape(String.valueOf(value)) + "\"";
    }

    private String formatArray(Object[] arr) {
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(", ");
            Object v = arr[i];
            if (v instanceof Number || v instanceof Boolean) {
                sb.append(v);
            } else {
                sb.append("\"").append(escape(String.valueOf(v))).append("\"");
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private String escape(String s) {
        if (s == null) return "";
        // 简单转义：反斜杠和双引号
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("服务器地址", "49.232.12.79");
        m.put("角色等级上限", 70);
        m.put("一键卖分品级", 2);
        m.put("含宠物装备", 0);
        m.put("SSS评分开关", 1);
        m.put("本地GM开关", 0);
        m.put("史诗自动确认开关", 0);
        m.put("英雄级开关", 1);
        m.put("物品图标开关", 1);
        m.put("name2开关", 1);
        m.put("品级文本开关", 1);
        m.put("连发按键组", new String[]{"X"});
        m.put("快捷键前置", "Ctrl");
        m.put("无损画质", 16);
        m.put("难度命名", new String[]{"普通级", "冒险级", "王者级", "地狱级", "英雄级"});
        m.put("品级命名", new String[]{"普通", "高级", "稀有", "神器", "史诗", "勇者", "传说", "神话"});
        m.put("简体PVF", 0);
        m.put("隐藏功能", 0);

        Map<String, Object> autoPick = new LinkedHashMap<>();
        autoPick.put("拾取模式", 4);
        autoPick.put("自定义拾取代码组", new int[]{0, 6515});
        m.put("自动拾取", autoPick);

        Map<String, Object> autoCard = new LinkedHashMap<>();
        autoCard.put("上", 0);
        autoCard.put("下", 0);
        m.put("自动翻牌", autoCard);

        Map<String, Object> epicFlash = new LinkedHashMap<>();
        epicFlash.put("闪光开关", 1);
        epicFlash.put("闪光代码", 9413);
        m.put("史诗闪光", epicFlash);

        Map<String, Object> patchInfo = new LinkedHashMap<>();
        patchInfo.put("补丁名称", "DOF补丁大合集V7");
        patchInfo.put("补丁声明", "本软件永久免费！用途仅限于测试实验、研究学习为目的，请勿用于商业途径及非法运营，严禁将本软件用于与中国现行法律相违背的一切行为！否则，请停止使用，若坚持使用，造成的一切法律责任及所有后果均由使用方承担，与作者无关，特此声明！");
        m.put("补丁信息", patchInfo);

        return m;
    }
}
