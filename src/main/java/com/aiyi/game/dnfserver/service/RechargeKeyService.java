package com.aiyi.game.dnfserver.service;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.RechargeKey;

/**
 * 卡密相关管理
 */
public interface RechargeKeyService {

    /**
     * 列出卡密列表
     * @param type
     *      卡密类型
     * @param status
     *      状态
     * @param page
     *      页码
     * @param pageSize
     *      每页条数
     * @return
     */
    ResultPage<RechargeKey> list(Integer type, Integer status, int page,
                                 int pageSize, String content);

    /**
     * 制作卡密
     * @param rechargeKey
     *      卡密信息
     */
    void create(RechargeKey rechargeKey);

    /**
     * 使用卡密
     * @param rechargeKey
     *      卡密信息
     */
    void use(RechargeKey rechargeKey);

}
