package com.aiyi.game.dnfserver.service;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.Postal;

import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * 邮件相关
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:36
 */
public interface PostalService {


    /**
     * 列出邮件列表
     * @param account
     *      收件人账号
     * @param start
     *      开始时间
     * @param end
     *      结束时间
     * @return
     */
    ResultPage<Postal> list(String account, Date start, Date end,
                            Integer page, Integer pageSize);


    /**
     * 发送邮件
     * @param postal
     *      邮件信息
     */
    void sendMail(Postal postal);
}
