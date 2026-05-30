package com.aiyi.game.dnfserver.entity.equipment;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import com.aiyi.game.dnfserver.entity.common.Item;
import com.alibaba.fastjson.JSON;

/**
 * 装备实体
 * @author xiatian
 */
public class Equipment extends Item {

    private EquipmentType equipmentType;
    private String equipmentTypeStr;

    /** 物品子类型名称 (来自 [item group name]) */
    private String itemGroupStr;

    // ---- 基础攻击/防御属性 ----
    /** 物理攻击力 [base, min] */
    private int[] physicalAttack;
    /** 魔法攻击力 [base, min] */
    private int[] magicalAttack;
    /** 独立攻击力 [base, min] */
    private int[] separateAttack;
    /** 物理防御力 [base, min] */
    private int[] physicalDefense;
    /** 魔法防御力 [base, min] */
    private int[] magicalDefense;

    // ---- 四维属性 ----
    /** 力量 [physical attack] */
    private Integer strength;
    /** 智力 [magical attack] */
    private Integer intelligence;
    /** 体力 [physical defense] */
    private Integer vitality;
    /** 精神 [magical defense] */
    private Integer spirit;

    // ---- HP/MP ----
    /** HP最大值 */
    private Integer hpMax;
    /** MP最大值 */
    private Integer mpMax;
    /** HP恢复速度 */
    private Integer hpRegenSpeed;
    /** MP恢复速度 */
    private Integer mpRegenSpeed;

    // ---- 速度与暴击 (存储值为原始值，显示时需除以10加%) ----
    /** 攻击速度 */
    private Integer attackSpeed;
    /** 施放速度 */
    private Integer castSpeed;
    /** 移动速度 */
    private Integer moveSpeed;
    /** 物理暴击率 */
    private Integer physicalCriticalHit;
    /** 魔法暴击率 */
    private Integer magicalCriticalHit;

    // ---- 命中/回避/跳跃 ----
    /** 命中率 [stuck] 取相反数 */
    private Integer hitRate;
    /** 回避率 [stuck resistance] */
    private Integer dodge;
    /** 硬直 */
    private Integer hitRecovery;
    /** 跳跃力 */
    private Integer jumpPower;
    /** 负重 */
    private Integer inventoryLimit;
    /** 抗魔值 */
    private Integer antiEvil;

    // ---- 属性攻击与抗性 ----
    /** 火属性攻击 */
    private Integer fireElement;
    /** 冰属性攻击 */
    private Integer waterElement;
    /** 光属性攻击 */
    private Integer lightElement;
    /** 暗属性攻击 */
    private Integer darkElement;
    /** 火属性强化 */
    private Integer fireAttack;
    /** 冰属性强化 */
    private Integer waterAttack;
    /** 光属性强化 */
    private Integer lightAttack;
    /** 暗属性强化 */
    private Integer darkAttack;
    /** 全属性强化 */
    private Integer allElementalAttack;
    /** 火属性抗性 */
    private Integer fireResistance;
    /** 冰属性抗性 */
    private Integer waterResistance;
    /** 光属性抗性 */
    private Integer lightResistance;
    /** 暗属性抗性 */
    private Integer darkResistance;
    /** 所有属性抗性 */
    private Integer allElementalResistance;

    // ---- 异常状态抗性 ----
    /** 失明抗性 */
    private Integer blindResistance;
    /** 感电抗性 */
    private Integer lightningResistance;
    /** 灼伤抗性 */
    private Integer burnResistance;
    /** 冰冻抗性 */
    private Integer freezeResistance;
    /** 束缚抗性 */
    private Integer holdResistance;
    /** 睡眠抗性 */
    private Integer sleepResistance;
    /** 出血抗性 */
    private Integer bleedingResistance;
    /** 混乱抗性 */
    private Integer confuseResistance;
    /** 诅咒抗性 */
    private Integer curseResistance;
    /** 石化抗性 */
    private Integer stoneResistance;
    /** 所有异常状态抗性 */
    private Integer allActiveStatusResistance;

    // ---- 其他属性 ----
    /** 品级 */
    private Integer grade;
    /** 重量 (克) */
    private Integer weight;
    /** 耐久度 */
    private Integer durability;
    /** NPC价格 */
    private Integer price;
    /** 修理价格 */
    private Integer repairPrice;
    /** 出售价格 */
    private Integer value;
    /** 城镇移动速度 */
    private Integer roomListMoveSpeedRate;

    public static Equipment forScript(JSONObject equipmentScript) {
        Equipment equipment = new Equipment();
        equipment.parseForScript(equipmentScript);

        // 装备类型
        JSONArray typeArr = equipmentScript.getJSONArray("[equipment type]");
        if (typeArr != null && !typeArr.isEmpty()) {
            equipment.equipmentType = EquipmentType.forType(typeArr.getStr(0));
        } else {
            equipment.equipmentType = EquipmentType.weapon;
        }

        // 子类型名称
        String groupName = equipmentScript.getStr("[item group name]", "[]");
        if (!groupName.isEmpty()) {
            com.alibaba.fastjson.JSONArray objects = JSON.parseArray(groupName);
            if (objects != null && !objects.isEmpty()) {
                groupName = objects.getString(0);
            }
            equipment.itemGroupStr = getItemGroupDisplayName(groupName);
        }

        // 基础攻击/防御属性
        equipment.physicalAttack = getArrayInt(equipmentScript, "[equipment physical attack]");
        equipment.magicalAttack = getArrayInt(equipmentScript, "[equipment magical attack]");
        equipment.separateAttack = getArrayInt(equipmentScript, "[separate attack]");
        equipment.physicalDefense = getArrayInt(equipmentScript, "[equipment physical defense]");
        equipment.magicalDefense = getArrayInt(equipmentScript, "[equipment magical defense]");

        // 四维属性
        equipment.strength = getIntOrNull(equipmentScript, "[physical attack]");
        equipment.intelligence = getIntOrNull(equipmentScript, "[magical attack]");
        equipment.vitality = getIntOrNull(equipmentScript, "[physical defense]");
        equipment.spirit = getIntOrNull(equipmentScript, "[magical defense]");

        // HP/MP
        equipment.hpMax = getIntOrNull(equipmentScript, "[HP MAX]");
        equipment.mpMax = getIntOrNull(equipmentScript, "[MP MAX]");
        equipment.hpRegenSpeed = getIntOrNull(equipmentScript, "[HP regen speed]");
        equipment.mpRegenSpeed = getIntOrNull(equipmentScript, "[MP regen speed]");

        // 速度与暴击
        equipment.attackSpeed = getIntOrNull(equipmentScript, "[attack speed]");
        equipment.castSpeed = getIntOrNull(equipmentScript, "[cast speed]");
        equipment.moveSpeed = getIntOrNull(equipmentScript, "[move speed]");
        equipment.physicalCriticalHit = getIntOrNull(equipmentScript, "[physical critical hit]");
        equipment.magicalCriticalHit = getIntOrNull(equipmentScript, "[magical critical hit]");

        // 命中/回避/跳跃/负重/抗魔
        equipment.hitRate = getIntOrNull(equipmentScript, "[stuck]");
        if (equipment.hitRate != null) equipment.hitRate = -equipment.hitRate;
        equipment.dodge = getIntOrNull(equipmentScript, "[stuck resistance]");
        equipment.hitRecovery = getIntOrNull(equipmentScript, "[hit recovery]");
        equipment.jumpPower = getIntOrNull(equipmentScript, "[jump power]");
        equipment.inventoryLimit = getIntOrNull(equipmentScript, "[inventory limit]");
        equipment.antiEvil = getIntOrNull(equipmentScript, "[anti evil]");

        // 属性攻击与抗性
        equipment.fireElement = getIntOrNull(equipmentScript, "[fire element]");
        equipment.waterElement = getIntOrNull(equipmentScript, "[water element]");
        equipment.lightElement = getIntOrNull(equipmentScript, "[light element]");
        equipment.darkElement = getIntOrNull(equipmentScript, "[dark element]");
        equipment.fireAttack = getIntOrNull(equipmentScript, "[fire attack]");
        equipment.waterAttack = getIntOrNull(equipmentScript, "[water attack]");
        equipment.lightAttack = getIntOrNull(equipmentScript, "[light attack]");
        equipment.darkAttack = getIntOrNull(equipmentScript, "[dark attack]");
        equipment.allElementalAttack = getIntOrNull(equipmentScript, "[all elemental attack]");
        equipment.fireResistance = getIntOrNull(equipmentScript, "[fire resistance]");
        equipment.waterResistance = getIntOrNull(equipmentScript, "[water resistance]");
        equipment.lightResistance = getIntOrNull(equipmentScript, "[light resistance]");
        equipment.darkResistance = getIntOrNull(equipmentScript, "[dark resistance]");
        equipment.allElementalResistance = getIntOrNull(equipmentScript, "[all elemental resistance]");

        // 异常状态抗性
        equipment.blindResistance = getIntOrNull(equipmentScript, "[blind resistance]");
        equipment.lightningResistance = getIntOrNull(equipmentScript, "[lightning resistance]");
        equipment.burnResistance = getIntOrNull(equipmentScript, "[burn resistance]");
        equipment.freezeResistance = getIntOrNull(equipmentScript, "[freeze resistance]");
        equipment.holdResistance = getIntOrNull(equipmentScript, "[hold resistance]");
        equipment.sleepResistance = getIntOrNull(equipmentScript, "[sleep resistance]");
        equipment.bleedingResistance = getIntOrNull(equipmentScript, "[bleeding resistance]");
        equipment.confuseResistance = getIntOrNull(equipmentScript, "[confuse resistance]");
        equipment.curseResistance = getIntOrNull(equipmentScript, "[curse resistance]");
        equipment.stoneResistance = getIntOrNull(equipmentScript, "[stone resistance]");
        equipment.allActiveStatusResistance = getIntOrNull(equipmentScript, "[all activestatus resistance]");

        // 其他属性
        equipment.grade = getIntOrNull(equipmentScript, "[grade]");
        equipment.weight = getIntOrNull(equipmentScript, "[weight]");
        equipment.durability = getIntOrNull(equipmentScript, "[durability]");
        equipment.price = getIntOrNull(equipmentScript, "[price]");
        equipment.repairPrice = getIntOrNull(equipmentScript, "[repair price]");
        equipment.value = getIntOrNull(equipmentScript, "[value]");
        equipment.roomListMoveSpeedRate = getIntOrNull(equipmentScript, "[room list move speed rate]");

        return equipment;
    }

    private static int[] getArrayInt(JSONObject script, String key) {
        if (!script.containsKey(key)) return null;
        JSONArray arr = script.getJSONArray(key);
        if (arr == null || arr.isEmpty()) return null;
        if (arr.size() == 1) return new int[]{arr.getInt(0)};
        return new int[]{arr.getInt(0), arr.getInt(1)};
    }

    private static Integer getIntOrNull(JSONObject script, String key) {
        if (!script.containsKey(key)) return null;
        JSONArray arr = script.getJSONArray(key);
        if (arr == null || arr.isEmpty()) return null;
        return arr.getInt(0);
    }

    /**
     * item group name 转中文显示名称
     */
    private static String getItemGroupDisplayName(String groupName) {
        switch (groupName) {
            // 首饰/特殊
            case "amulet":      return "项链";
            case "wrist":       return "手镯";
            case "ring":        return "戒指";
            case "support":     return "辅助装备";
            case "magic stone": return "魔法石";
            case "title":       return "称号";
            // 通用防具部位
            case "coat":        return "上衣";
            case "pants":       return "下装";
            case "shoulder":    return "护肩";
            case "waist":       return "腰带";
            case "shoes":       return "靴子";
            // 布甲
            case "cl coat":     return "布甲上衣";
            case "cl pants":    return "布甲下装";
            case "cl waist":    return "布甲腰带";
            case "cl shoes":    return "布甲鞋子";
            case "cl shoulder": return "布甲护肩";
            // 皮甲
            case "lt coat":     return "皮甲上衣";
            case "lt pants":    return "皮甲下装";
            case "lt waist":    return "皮甲腰带";
            case "lt shoes":    return "皮甲鞋子";
            case "lt shoulder": return "皮甲护肩";
            // 轻甲
            case "la coat":     return "轻甲上衣";
            case "la pants":    return "轻甲下装";
            case "la waist":    return "轻甲腰带";
            case "la shoes":    return "轻甲鞋子";
            case "la shoulder": return "轻甲护肩";
            // 重甲
            case "ha coat":     return "重甲上衣";
            case "ha pants":    return "重甲下装";
            case "ha waist":    return "重甲腰带";
            case "ha shoes":    return "重甲鞋子";
            case "ha shoulder": return "重甲护肩";
            // 板甲
            case "mt coat":     return "板甲上衣";
            case "mt pants":    return "板甲下装";
            case "mt waist":    return "板甲腰带";
            case "mt shoes":    return "板甲鞋子";
            case "mt shoulder": return "板甲护肩";
            // 装扮
            case "hat avatar":      return "帽子";
            case "hair avatar":     return "发型";
            case "face avatar":     return "脸部";
            case "breast avatar":   return "颈部";
            case "coat avatar":     return "上衣(装扮)";
            case "pants avatar":    return "裤子(装扮)";
            case "waist avatar":    return "腰带(装扮)";
            case "shoes avatar":    return "鞋子(装扮)";
            case "skin avatar":     return "皮肤";
            case "aurora avatar":   return "光环";
            // 宠物装备
            case "artifact blue":   return "宠物装备(蓝)";
            case "artifact green":  return "宠物装备(绿)";
            case "artifact red":    return "宠物装备(红)";
            // 武器 - 鬼剑士
            case "ssword":      return "短剑";
            case "katana":      return "太刀";
            case "club":        return "钝器";
            case "lswd":        return "巨剑";
            case "beamswd":     return "光剑";
            // 武器 - 格斗家
            case "knuckle":     return "手套";
            case "claw":        return "爪";
            case "tonfa":       return "东方棍";
            case "gauntlet":    return "臂铠";
            case "bglove":      return "拳套";
            // 武器 - 神枪手
            case "automatic":   return "自动步枪";
            case "revolver":    return "左轮";
            case "bowgun":      return "手弩";
            case "musket":      return "步枪";
            case "hcannon":     return "手炮";
            // 武器 - 魔法师
            case "rod":         return "魔杖";
            case "staff":       return "法杖";
            case "pole":        return "棍棒";
            case "spear":       return "矛";
            case "broom":       return "扫把";
            case "wand":        return "手杖";
            // 武器 - 圣职者
            case "cross":       return "十字架";
            case "rosary":      return "念珠";
            case "totem":       return "图腾";
            case "axe":         return "斧头";
            case "scythe":      return "镰刀";
            // 武器 - 暗夜使者
            case "dagger":      return "匕首";
            case "twinswd":     return "双剑";
            default:            return groupName;
        }
    }

    /**
     * 获取稀有度名称
     */
    public String getRarityName() {
        switch (getRarity()) {
            case 0: return "普通";
            case 1: return "高级";
            case 2: return "稀有";
            case 3: return "神器";
            case 4: return "史诗";
            case 5: return "传说";
            case 6: return "神话";
            default: return "未知";
        }
    }

    // ---- Getters & Setters ----

    public EquipmentType getEquipmentType() { return equipmentType; }
    public void setEquipmentType(EquipmentType equipmentType) { this.equipmentType = equipmentType; }
    public String getEquipmentTypeStr() { return equipmentType.getDesc(); }

    public String getItemGroupStr() { return itemGroupStr; }
    public void setItemGroupStr(String itemGroupStr) { this.itemGroupStr = itemGroupStr; }

    public int[] getPhysicalAttack() { return physicalAttack; }
    public void setPhysicalAttack(int[] physicalAttack) { this.physicalAttack = physicalAttack; }

    public int[] getMagicalAttack() { return magicalAttack; }
    public void setMagicalAttack(int[] magicalAttack) { this.magicalAttack = magicalAttack; }

    public int[] getSeparateAttack() { return separateAttack; }
    public void setSeparateAttack(int[] separateAttack) { this.separateAttack = separateAttack; }

    public int[] getPhysicalDefense() { return physicalDefense; }
    public void setPhysicalDefense(int[] physicalDefense) { this.physicalDefense = physicalDefense; }

    public int[] getMagicalDefense() { return magicalDefense; }
    public void setMagicalDefense(int[] magicalDefense) { this.magicalDefense = magicalDefense; }

    public Integer getStrength() { return strength; }
    public void setStrength(Integer strength) { this.strength = strength; }

    public Integer getIntelligence() { return intelligence; }
    public void setIntelligence(Integer intelligence) { this.intelligence = intelligence; }

    public Integer getVitality() { return vitality; }
    public void setVitality(Integer vitality) { this.vitality = vitality; }

    public Integer getSpirit() { return spirit; }
    public void setSpirit(Integer spirit) { this.spirit = spirit; }

    public Integer getHpMax() { return hpMax; }
    public void setHpMax(Integer hpMax) { this.hpMax = hpMax; }

    public Integer getMpMax() { return mpMax; }
    public void setMpMax(Integer mpMax) { this.mpMax = mpMax; }

    public Integer getHpRegenSpeed() { return hpRegenSpeed; }
    public void setHpRegenSpeed(Integer hpRegenSpeed) { this.hpRegenSpeed = hpRegenSpeed; }

    public Integer getMpRegenSpeed() { return mpRegenSpeed; }
    public void setMpRegenSpeed(Integer mpRegenSpeed) { this.mpRegenSpeed = mpRegenSpeed; }

    public Integer getAttackSpeed() { return attackSpeed; }
    public void setAttackSpeed(Integer attackSpeed) { this.attackSpeed = attackSpeed; }

    public Integer getCastSpeed() { return castSpeed; }
    public void setCastSpeed(Integer castSpeed) { this.castSpeed = castSpeed; }

    public Integer getMoveSpeed() { return moveSpeed; }
    public void setMoveSpeed(Integer moveSpeed) { this.moveSpeed = moveSpeed; }

    public Integer getPhysicalCriticalHit() { return physicalCriticalHit; }
    public void setPhysicalCriticalHit(Integer physicalCriticalHit) { this.physicalCriticalHit = physicalCriticalHit; }

    public Integer getMagicalCriticalHit() { return magicalCriticalHit; }
    public void setMagicalCriticalHit(Integer magicalCriticalHit) { this.magicalCriticalHit = magicalCriticalHit; }

    public Integer getHitRate() { return hitRate; }
    public void setHitRate(Integer hitRate) { this.hitRate = hitRate; }

    public Integer getDodge() { return dodge; }
    public void setDodge(Integer dodge) { this.dodge = dodge; }

    public Integer getHitRecovery() { return hitRecovery; }
    public void setHitRecovery(Integer hitRecovery) { this.hitRecovery = hitRecovery; }

    public Integer getJumpPower() { return jumpPower; }
    public void setJumpPower(Integer jumpPower) { this.jumpPower = jumpPower; }

    public Integer getInventoryLimit() { return inventoryLimit; }
    public void setInventoryLimit(Integer inventoryLimit) { this.inventoryLimit = inventoryLimit; }

    public Integer getAntiEvil() { return antiEvil; }
    public void setAntiEvil(Integer antiEvil) { this.antiEvil = antiEvil; }

    public Integer getFireElement() { return fireElement; }
    public void setFireElement(Integer fireElement) { this.fireElement = fireElement; }

    public Integer getWaterElement() { return waterElement; }
    public void setWaterElement(Integer waterElement) { this.waterElement = waterElement; }

    public Integer getLightElement() { return lightElement; }
    public void setLightElement(Integer lightElement) { this.lightElement = lightElement; }

    public Integer getDarkElement() { return darkElement; }
    public void setDarkElement(Integer darkElement) { this.darkElement = darkElement; }

    public Integer getFireAttack() { return fireAttack; }
    public void setFireAttack(Integer fireAttack) { this.fireAttack = fireAttack; }

    public Integer getWaterAttack() { return waterAttack; }
    public void setWaterAttack(Integer waterAttack) { this.waterAttack = waterAttack; }

    public Integer getLightAttack() { return lightAttack; }
    public void setLightAttack(Integer lightAttack) { this.lightAttack = lightAttack; }

    public Integer getDarkAttack() { return darkAttack; }
    public void setDarkAttack(Integer darkAttack) { this.darkAttack = darkAttack; }

    public Integer getAllElementalAttack() { return allElementalAttack; }
    public void setAllElementalAttack(Integer allElementalAttack) { this.allElementalAttack = allElementalAttack; }

    public Integer getFireResistance() { return fireResistance; }
    public void setFireResistance(Integer fireResistance) { this.fireResistance = fireResistance; }

    public Integer getWaterResistance() { return waterResistance; }
    public void setWaterResistance(Integer waterResistance) { this.waterResistance = waterResistance; }

    public Integer getLightResistance() { return lightResistance; }
    public void setLightResistance(Integer lightResistance) { this.lightResistance = lightResistance; }

    public Integer getDarkResistance() { return darkResistance; }
    public void setDarkResistance(Integer darkResistance) { this.darkResistance = darkResistance; }

    public Integer getAllElementalResistance() { return allElementalResistance; }
    public void setAllElementalResistance(Integer allElementalResistance) { this.allElementalResistance = allElementalResistance; }

    public Integer getBlindResistance() { return blindResistance; }
    public void setBlindResistance(Integer blindResistance) { this.blindResistance = blindResistance; }

    public Integer getLightningResistance() { return lightningResistance; }
    public void setLightningResistance(Integer lightningResistance) { this.lightningResistance = lightningResistance; }

    public Integer getBurnResistance() { return burnResistance; }
    public void setBurnResistance(Integer burnResistance) { this.burnResistance = burnResistance; }

    public Integer getFreezeResistance() { return freezeResistance; }
    public void setFreezeResistance(Integer freezeResistance) { this.freezeResistance = freezeResistance; }

    public Integer getHoldResistance() { return holdResistance; }
    public void setHoldResistance(Integer holdResistance) { this.holdResistance = holdResistance; }

    public Integer getSleepResistance() { return sleepResistance; }
    public void setSleepResistance(Integer sleepResistance) { this.sleepResistance = sleepResistance; }

    public Integer getBleedingResistance() { return bleedingResistance; }
    public void setBleedingResistance(Integer bleedingResistance) { this.bleedingResistance = bleedingResistance; }

    public Integer getConfuseResistance() { return confuseResistance; }
    public void setConfuseResistance(Integer confuseResistance) { this.confuseResistance = confuseResistance; }

    public Integer getCurseResistance() { return curseResistance; }
    public void setCurseResistance(Integer curseResistance) { this.curseResistance = curseResistance; }

    public Integer getStoneResistance() { return stoneResistance; }
    public void setStoneResistance(Integer stoneResistance) { this.stoneResistance = stoneResistance; }

    public Integer getAllActiveStatusResistance() { return allActiveStatusResistance; }
    public void setAllActiveStatusResistance(Integer allActiveStatusResistance) { this.allActiveStatusResistance = allActiveStatusResistance; }

    public Integer getGrade() { return grade; }
    public void setGrade(Integer grade) { this.grade = grade; }

    public Integer getWeight() { return weight; }
    public void setWeight(Integer weight) { this.weight = weight; }

    public Integer getDurability() { return durability; }
    public void setDurability(Integer durability) { this.durability = durability; }

    public Integer getPrice() { return price; }
    public void setPrice(Integer price) { this.price = price; }

    public Integer getRepairPrice() { return repairPrice; }
    public void setRepairPrice(Integer repairPrice) { this.repairPrice = repairPrice; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public Integer getRoomListMoveSpeedRate() { return roomListMoveSpeedRate; }
    public void setRoomListMoveSpeedRate(Integer roomListMoveSpeedRate) { this.roomListMoveSpeedRate = roomListMoveSpeedRate; }
}
