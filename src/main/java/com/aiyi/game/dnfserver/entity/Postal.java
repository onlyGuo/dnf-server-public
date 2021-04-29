package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;
import com.aiyi.game.dnfserver.utils.MinFieldUtil;
import com.aiyi.game.dnfserver.utils.Simple;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 邮件实体
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 15:51
 */
@TableName(name = "taiwan_cain_2nd.postal")
public class Postal extends PO {

    @ID
    @FieldName(name = "postal_id")
    private long postalId;

    /**
     * 发送时间
     */
    @FieldName(name = "occ_time")
    private Date occTime = new Date();

    /**
     * 发送人ID（角色ID）
     */
    @FieldName(name = "send_charac_no")
    private int sendCharacNo;

    /**
     * 发送人昵称
     */
    @FieldName(name = "send_charac_name")
    @Simple
    private String sendCharacName;

    /**
     * 接收人ID(角色ID)
     */
    @FieldName(name = "receive_charac_no")
    private String receiveCharacNo;

    /**
     * 物品附件ID
     */
    @FieldName(name = "item_id")
    private int itemId;

    /**
     * 物品数量
     */
    @FieldName(name = "add_info")
    private int addInfo = 1;

    /**
     * 物品强化等级
     */
    @FieldName(name = "`upgrade`")
    private int upgrade;

    /**
     * 红字属性类型（智力力量什么的，具体回家分析代码补全）
     */
    @FieldName(name = "amplify_option")
    private int amplifyOption;

    /**
     * 红字属性值
     */
    @FieldName(name = "amplify_value")
    private int amplifyValue;

    /**
     * 金币数量
     */
    private int gold;

    /**
     * 是否删除
     */
    @FieldName(name = "delete_flag")
    private boolean deleteFlag;

    /**
     * 邮件内物品是否是封装（ss禁止封装）
     */
    @FieldName(name = "seal_flag")
    private boolean sealFlag;

    /**
     * 信件ID（但市面上的GM工具是自增的，但实际应该对应letter表，指的是邮件文本内容。）
     * 不知道哪个傻叉干的，不能自增，留空传0就行，表示纯物品邮件， 否则可能导致内容错乱！
     */
    @FieldName(name = "letter_id")
    private int letterId;

    /**
     * 物品锻造等级
     */
    @FieldName(name = "seperate_upgrade")
    private int seperateUpgrade;


    public long getPostalId() {
        return postalId;
    }

    public void setPostalId(long postalId) {
        this.postalId = postalId;
    }

    public Date getOccTime() {
        return occTime;
    }

    public void setOccTime(Date occTime) {
        this.occTime = occTime;
    }

    public int getSendCharacNo() {
        return sendCharacNo;
    }

    public void setSendCharacNo(int sendCharacNo) {
        this.sendCharacNo = sendCharacNo;
    }

    public String getSendCharacName() {
        return sendCharacName;
    }

    public void setSendCharacName(String sendCharacName) {
        this.sendCharacName = sendCharacName;
    }

    public String getReceiveCharacNo() {
        return receiveCharacNo;
    }

    public void setReceiveCharacNo(String receiveCharacNo) {
        this.receiveCharacNo = receiveCharacNo;
    }

    public int getItemId() {
        return itemId;
    }

    public void setItemId(int itemId) {
        this.itemId = itemId;
    }

    public int getAddInfo() {
        return addInfo;
    }

    public void setAddInfo(int addInfo) {
        this.addInfo = addInfo;
    }

    public int getUpgrade() {
        return upgrade;
    }

    public void setUpgrade(int upgrade) {
        this.upgrade = upgrade;
    }

    public int getAmplifyOption() {
        return amplifyOption;
    }

    public void setAmplifyOption(int amplifyOption) {
        this.amplifyOption = amplifyOption;
    }

    public int getAmplifyValue() {
        return amplifyValue;
    }

    public void setAmplifyValue(int amplifyValue) {
        this.amplifyValue = amplifyValue;
    }

    public int getGold() {
        return gold;
    }

    public void setGold(int gold) {
        this.gold = gold;
    }

    public boolean isDeleteFlag() {
        return deleteFlag;
    }

    public void setDeleteFlag(boolean deleteFlag) {
        this.deleteFlag = deleteFlag;
    }

    public boolean isSealFlag() {
        return sealFlag;
    }

    public void setSealFlag(boolean sealFlag) {
        this.sealFlag = sealFlag;
    }

    public int getLetterId() {
        return letterId;
    }

    public void setLetterId(int letterId) {
        this.letterId = letterId;
    }

    public int getSeperateUpgrade() {
        return seperateUpgrade;
    }

    public void setSeperateUpgrade(int seperateUpgrade) {
        this.seperateUpgrade = seperateUpgrade;
    }

    /**
     * insert into
     * taiwan_cain_2nd.postal (
     *     occ_time,           send_charac_name,       receive_charac_no,
     *     amplify_option,     amplify_value,          seperate_upgrade,
     *     seal_flag,          item_id,                add_info,
     *     upgrade,            gold,                   letter_id
     * )values(
     *     NOW(),              '发件人姓名',            '收件人角色ID',
     *     '红字属性',           '可能是增幅等级，给他0',   '锻造等级',
     *     '封装',              '物品代码',             '物品数量',
     *     '强化等级',           '游戏金币',             '信件ID，对应letter表，但这个表是空的'
     * );
     */
}
