package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.annotation.po.TempField;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * DNF用户实体
 */
@TableName(name = "d_taiwan.`accounts`")
public class AccountVO extends PO {
    /**
     * 账号ID
     */
    @ID
    @FieldName(name = "UID")
    private Integer uid;
    /**
     * 账号
     */
    private String accountname;
    /**
     * 密码
     */
    private String password;
    /**
     * QQ号
     */
    private String qq;
    /**
     * VIP
     */
    private String vip;

    private boolean admin;

    @FieldName(name = "parent_uid")
    private int parentUid;

    @TempField
    private String valicode;

    @TempField
    private String smsCode;

    /**
     * 登录频道号
     */
    @TempField
    @FieldName(name = "m_channel_no")
    private int channelNo;

    /**
     * 登录状态
     */
    @TempField
    @FieldName(name = "login_status")
    private boolean loginStatus;

    /**
     * 最后登录时间
     */
    @TempField
    @FieldName(name = "last_login_date")
    private Date lastLoginDate;

    /**
     * 最后登录IP
     */
    @TempField
    @FieldName(name = "login_ip")
    private String loginIp;

    @TempField
    private int cera;

    @TempField
    private String validationIndex;

    public Integer getUid() {
        return uid;
    }

    public void setUid(Integer uid) {
        this.uid = uid;
    }

    public String getAccountname() {
        return accountname;
    }

    public void setAccountname(String accountname) {
        this.accountname = accountname;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getQq() {
        return qq;
    }

    public void setQq(String qq) {
        this.qq = qq;
    }

    public String getVip() {
        return vip;
    }

    public void setVip(String vip) {
        this.vip = vip;
    }

    public int getChannelNo() {
        return channelNo;
    }

    public void setChannelNo(int channelNo) {
        this.channelNo = channelNo;
    }

    public boolean isLoginStatus() {
        return loginStatus;
    }

    public void setLoginStatus(boolean loginStatus) {
        this.loginStatus = loginStatus;
    }

    public Date getLastLoginDate() {
        return lastLoginDate;
    }

    public void setLastLoginDate(Date lastLoginDate) {
        this.lastLoginDate = lastLoginDate;
    }

    public String getLoginIp() {
        return loginIp;
    }

    public void setLoginIp(String loginIp) {
        this.loginIp = loginIp;
    }

    public String getValicode() {
        return valicode;
    }

    public void setValicode(String valicode) {
        this.valicode = valicode;
    }

    public String getSmsCode() {
        return smsCode;
    }

    public void setSmsCode(String smsCode) {
        this.smsCode = smsCode;
    }

    public int getCera() {
        return cera;
    }

    public void setCera(int cera) {
        this.cera = cera;
    }

    public String getValidationIndex() {
        return validationIndex;
    }

    public void setValidationIndex(String validationIndex) {
        this.validationIndex = validationIndex;
    }

    public boolean isAdmin() {
        return admin;
    }

    public void setAdmin(boolean admin) {
        this.admin = admin;
    }

    public int getParentUid() {
        return parentUid;
    }

    public void setParentUid(int parentUid) {
        this.parentUid = parentUid;
    }
}
