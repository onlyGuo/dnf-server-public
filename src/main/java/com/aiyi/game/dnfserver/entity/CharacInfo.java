package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.annotation.po.TempField;
import com.aiyi.core.beans.PO;
import com.aiyi.game.dnfserver.utils.Simple;

import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 角色表
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 10:33
 */
@TableName(name = "taiwan_cain.charac_info")
public class CharacInfo extends PO {

    @FieldName(name = "m_id")
    private int mid;

    @ID
    @FieldName(name = "charac_no")
    private int characNo;

    @FieldName(name = "charac_name")
    @Simple
    private String characName;

    /**
     * 翻译 = 部落,不知啥意思
     */
    private int village = 1;

    /**
     * 职业， 0=鬼剑， 2 = 枪手， 4 = 大叔 其他的慢慢分析
     */
    private int job;

    /**
     * 等级
     */
    private int lev = 1;

    /**
     * 经验
     */
    private int exp;

    /**
     * 翻译： 成长类型，不知是啥，可能是转职类型
     */
    @FieldName(name = "grow_type")
    private int growType;

    /**
     * 血量
     */
    @FieldName(name = "HP")
    private int hp;

    /**
     * 最高血量
     */
    @FieldName(name = "maxHP")
    private int maxHp;

    /**
     * 最高蓝量
     */
    @FieldName(name = "maxMP")
    private int maxMp;

    /**
     * 物攻
     */
    @FieldName(name = "phy_attack")
    private int phyAttack;

    /**
     * 物防
     */
    @FieldName(name = "phy_defense")
    private int phyDefense;

    /**
     * 魔攻
     */
    @FieldName(name = "mag_attack")
    private int magAttack;

    /**
     * 魔防
     */
    @FieldName(name = "mag_defense")
    private int magDefense;

    /**
     * 攻速
     */
    @FieldName(name = "attack_speed")
    private int attackSpeed;

    /**
     * 施放速度
     */
    @FieldName(name = "cast_speed")
    private int castSpeed;

    /**
     * 移速
     */
    @FieldName(name = "move_speed")
    private int moveSpeed;

    /**
     * 硬直
     */
    @FieldName(name = "hit_recovery")
    private int hitRecovery;

    /**
     * 跳跃力
     */
    @FieldName(name = "jump")
    private int jump;

    /**
     * 疲劳
     */
    private int fatigue;

    /**
     * 创建时间
     */
    @FieldName(name = "create_time")
    private Date createTime = new Date();

    @TempField
    private String accountname;

    public int getCharacNo() {
        return characNo;
    }

    public void setCharacNo(int characNo) {
        this.characNo = characNo;
    }

    public String getCharacName() {
        return characName;
    }

    public void setCharacName(String characName) {
        this.characName = characName;
    }

    public int getVillage() {
        return village;
    }

    public void setVillage(int village) {
        this.village = village;
    }

    public int getMid() {
        return mid;
    }

    public void setMid(int mid) {
        this.mid = mid;
    }

    public int getJob() {
        return job;
    }

    public void setJob(int job) {
        this.job = job;
    }

    public int getLev() {
        return lev;
    }

    public void setLev(int lev) {
        this.lev = lev;
    }

    public int getExp() {
        return exp;
    }

    public void setExp(int exp) {
        this.exp = exp;
    }

    public int getGrowType() {
        return growType;
    }

    public void setGrowType(int growType) {
        this.growType = growType;
    }

    public int getHp() {
        return hp;
    }

    public void setHp(int hp) {
        this.hp = hp;
    }

    public int getMaxHp() {
        return maxHp;
    }

    public void setMaxHp(int maxHp) {
        this.maxHp = maxHp;
    }

    public int getMaxMp() {
        return maxMp;
    }

    public void setMaxMp(int maxMp) {
        this.maxMp = maxMp;
    }

    public int getPhyAttack() {
        return phyAttack;
    }

    public void setPhyAttack(int phyAttack) {
        this.phyAttack = phyAttack;
    }

    public int getPhyDefense() {
        return phyDefense;
    }

    public void setPhyDefense(int phyDefense) {
        this.phyDefense = phyDefense;
    }

    public int getMagAttack() {
        return magAttack;
    }

    public void setMagAttack(int magAttack) {
        this.magAttack = magAttack;
    }

    public int getMagDefense() {
        return magDefense;
    }

    public void setMagDefense(int magDefense) {
        this.magDefense = magDefense;
    }

    public int getAttackSpeed() {
        return attackSpeed;
    }

    public void setAttackSpeed(int attackSpeed) {
        this.attackSpeed = attackSpeed;
    }

    public int getCastSpeed() {
        return castSpeed;
    }

    public void setCastSpeed(int castSpeed) {
        this.castSpeed = castSpeed;
    }

    public int getHitRecovery() {
        return hitRecovery;
    }

    public void setHitRecovery(int hitRecovery) {
        this.hitRecovery = hitRecovery;
    }

    public int getJump() {
        return jump;
    }

    public void setJump(int jump) {
        this.jump = jump;
    }

    public int getFatigue() {
        return fatigue;
    }

    public void setFatigue(int fatigue) {
        this.fatigue = fatigue;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getAccountname() {
        return accountname;
    }

    public void setAccountname(String accountname) {
        this.accountname = accountname;
    }

    public int getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(int moveSpeed) {
        this.moveSpeed = moveSpeed;
    }
}
