package com.aiyi.game.dnfserver.service;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.entity.AccountVO;

import java.util.Date;

/**
 * 账号业务操作类
 * @author gsk
 */
public interface AccountService {

    /**
     * 给客户端登录的用户授权
     * @param accountVO
     *          登录用户的实体
     * @return String
     *          登录用户的授权Key
     */
    String loginClient(AccountVO accountVO, boolean ok);

    /**
     * 注册
     * @param accountVO
     */
    void register(AccountVO accountVO);

    /**
     * 查询账号列表
     * @param account
     *      账号
     * @param loginStatus
     *      是否登录
     * @param lastLoginDate
     *      最后登录时间
     * @param page
     *      页码
     * @param pageSize
     *      每页条数
     * @return
     */
    ResultPage<AccountVO> list(String account, Boolean loginStatus,
                               Date lastLoginDate, int page, int pageSize);

    /**
     * chong zhi
     * @param uid
     *      user id
     * @param face
     *      mian zhi
     */
    void recharge(int uid, int face);

    /**
     * 获得用户详情
     * @param uid
     *      用户ID
     * @return
     */
    AccountVO info(int uid);
}
