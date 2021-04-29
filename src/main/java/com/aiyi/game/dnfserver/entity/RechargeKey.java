package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.annotation.po.TempField;
import com.aiyi.core.beans.PO;

import java.util.Date;

@TableName(name = "dnf_service.recharge_key")
public class RechargeKey extends PO {

    @ID
    private int id;

    /**
     * 卡密
     */
    private String content;

    /**
     * 0 = 点券， 1 = 装备
     */
    private int type;

    /**
     * 面值（type = 0时表示点券数量， type = 1时表示装备编号）
     */
    private int face;

    /**
     * 装备名称(type = 1时有效)
     */
    @FieldName(name = "face_name")
    private String faceName;

    /**
     * 0 = 未使用， 1 = 已使用
     */
    private int status;

    /**
     * 使用账号
     */
    @FieldName(name = "use_account")
    private String useAccount;

    /**
     * 使用账号ID
     */
    @FieldName(name = "use_uid")
    private int useUid;

    /**
     * 创建时间
     */
    @FieldName(name = "create_time")
    private Date createTime = new Date();

    /**
     * 使用时间
     */
    @FieldName(name = "use_time")
    private Date useTime;

    @TempField
    private int count;

    /**
     * 角色（为CDK时有效）
     */
    @TempField
    private int charach;

    /**
     * 验证码(使用卡密时有效)
     */
    @TempField
    private String valicode;

    /**
     * 验证码索引
     */
    @TempField
    private String valiId;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public int getFace() {
        return face;
    }

    public void setFace(int face) {
        this.face = face;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getUseAccount() {
        return useAccount;
    }

    public void setUseAccount(String useAccount) {
        this.useAccount = useAccount;
    }

    public int getUseUid() {
        return useUid;
    }

    public void setUseUid(int useUid) {
        this.useUid = useUid;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public String getFaceName() {
        return faceName;
    }

    public void setFaceName(String faceName) {
        this.faceName = faceName;
    }

    public int getCharach() {
        return charach;
    }

    public void setCharach(int charach) {
        this.charach = charach;
    }

    public String getValicode() {
        return valicode;
    }

    public void setValicode(String valicode) {
        this.valicode = valicode;
    }

    public String getValiId() {
        return valiId;
    }

    public void setValiId(String valiId) {
        this.valiId = valiId;
    }

    public Date getUseTime() {
        return useTime;
    }

    public void setUseTime(Date useTime) {
        this.useTime = useTime;
    }
}
