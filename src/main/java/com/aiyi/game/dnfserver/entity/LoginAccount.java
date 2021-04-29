package com.aiyi.game.dnfserver.entity;

import com.aiyi.core.annotation.po.FieldName;
import com.aiyi.core.annotation.po.ID;
import com.aiyi.core.annotation.po.TableName;
import com.aiyi.core.beans.PO;

import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 在线账号列表实体
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/14 15:05
 */
@TableName(name = "taiwan_login.`login_account_3`")
public class LoginAccount extends PO {

    @ID
    @FieldName(name = "m_id")
    private int mid;

    /**
     * 登录频道号
     */
    @FieldName(name = "m_channel_no")
    private int channelNo;

    /**
     * 登录状态
     */
    @FieldName(name = "login_status")
    private boolean loginStatus;

    /**
     * 最后登录时间
     */
    @FieldName(name = "last_login_date")
    private Date lastLoginDate;

    /**
     * 最后登录IP
     */
    @FieldName(name = "login_ip")
    private String loginIp;

    public int getMid() {
        return mid;
    }

    public void setMid(int mid) {
        this.mid = mid;
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
}
