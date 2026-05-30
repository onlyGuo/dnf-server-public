package com.aiyi.game.dnfserver.entity.common;

import cn.hutool.json.JSONObject;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.github.houbb.opencc4j.util.ZhConverterUtil;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 物品实体
 * @author xiatian
 */
public class Item {

    /**
     * 物品ID
     */
    private int id;

    /**
     * 装备稀有度: 0-白装, 1-蓝装, 2-紫装, 3-粉装, 4-橙装, 5-史诗, 6-神话
     */
    private int rarity;

    /**
     * 物品名称
     */
    private String name;

    /**
     * 物品类型
     */
    private ItemType type;

    /**
     * 可使用职业
     */
    private List<UsableJob> usableJobs;

    private List<String> usableJobsStr;

    /**
     * 绑定/交易类型
     */
    private AttachType attachType;


    /**
     * 最低使用等级
     */
    private int minimumLevel;

    /**
     * 物品描述
     */
    private String description;

    /**
     * 物品说明面板
     */
    private String explain;

    /**
     * 携带上限
     */
    private int stackLimit;

    private ItemIcon icon;

    public void parseForScript(JSONObject script){
        if (script.containsKey("[rarity]")){
            this.rarity = script.getJSONArray("[rarity]").getInt(0);
        }
        this.name = ZhConverterUtil.toSimple(script.getStr("[name]", "[\"未命名\"]"));
//        this.type = EquipmentType.forType(equipmentScript.getJSONArray("[equipment type]").getStr(0));
        if (script.containsKey("[equipment type]")){
            this.type = ItemType.equipment;
        } else if (script.containsKey("[stackable type]")){
            this.type = ItemType.stackable;
        }else{
            this.type = ItemType.other;
        }
        if (script.containsKey("[attach type]")){
            this.attachType = AttachType.forType(script.getJSONArray("[attach type]").getStr(0));
        }else{
            this.attachType = AttachType.trade;
        }
        if (script.containsKey(("[minimum level]"))){
            this.minimumLevel = script.getJSONArray("[minimum level]").getInt(0);
        }
        this.description = ZhConverterUtil.toSimple(script.getStr("[flavor text]", "[\"\"]"));
        this.explain = ZhConverterUtil.toSimple(
                script.getStr("[detail explain]",
                        script.getStr("[basic explain]", "[\"\"]")));
        if (script.containsKey("[stack limit]")){
            this.stackLimit = script.getJSONArray("[stack limit]").getInt(0);
        }else{
            this.stackLimit = 1;
        }
        // usable jobs
        try {
            this.usableJobs = script.getJSONArray("[usable job]").toList(String.class).stream()
                    .map(UsableJob::forJobKey)
                    .collect(Collectors.toList());
        }catch (Exception e){
            this.usableJobs = new ArrayList<>();
            this.usableJobs.add(UsableJob.all);
        }
        JSONArray objects2 = JSON.parseArray(this.name);
        if (null != objects2 && !objects2.isEmpty()) {
            this.name = objects2.getString(0);
        }else {
            this.name = "未命名";
        }

        JSONArray objects = JSON.parseArray(this.description);
        if (null != objects && !objects.isEmpty()) {
            this.description = objects.getString(0);
        }else{
            this.description = "";
        }

        JSONArray objects1 = JSON.parseArray(this.explain);
        if (null != objects1 && !objects1.isEmpty()) {
            this.explain = objects1.getString(0);
        }else {
            this.explain = "";
        }

        if (script.containsKey("[icon]") && !script.getJSONArray("[icon]").isEmpty()){
            JSONArray arrays = JSON.parseArray(script.getStr("[icon]"));
            ItemIcon itemIcon = new ItemIcon();
            itemIcon.setPath(arrays.getString(0).toLowerCase());
            if (arrays.size() >= 2){
                itemIcon.setIndex(arrays.getIntValue(1));
            }
            this.icon = itemIcon;
        } else {
            this.icon = null;
        }
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getRarity() {
        return rarity;
    }

    public void setRarity(int rarity) {
        this.rarity = rarity;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public ItemType getType() {
        return type;
    }

    public void setType(ItemType type) {
        this.type = type;
    }

    public List<UsableJob> getUsableJobs() {
        return usableJobs;
    }

    public void setUsableJobs(List<UsableJob> usableJobs) {
        this.usableJobs = usableJobs;
    }

    public AttachType getAttachType() {
        return attachType;
    }

    public void setAttachType(AttachType attachType) {
        this.attachType = attachType;
    }

    public int getMinimumLevel() {
        return minimumLevel;
    }

    public void setMinimumLevel(int minimumLevel) {
        this.minimumLevel = minimumLevel;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExplain() {
        return explain;
    }

    public void setExplain(String explain) {
        this.explain = explain;
    }

    public int getStackLimit() {
        return stackLimit;
    }

    public void setStackLimit(int stackLimit) {
        this.stackLimit = stackLimit;
    }

    public List<String> getUsableJobsStr() {
        List<String> strings = new ArrayList<>();
        if (usableJobs == null) {
            return strings;
        }
        for (UsableJob usableJob : usableJobs) {
            strings.add(usableJob.getJobDesc());
        }
        return strings;
    }

    public String getAttachTypeStr() {
        return attachType == null ? "" : attachType.getDesc();
    }

    public ItemIcon getIcon() {
        return icon;
    }
}
