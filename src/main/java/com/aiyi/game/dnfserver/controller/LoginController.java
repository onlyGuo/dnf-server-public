package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.Method;
import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.sql.where.C;
import com.aiyi.core.util.DateUtil;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.AccountDao;
import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.entity.AccountVO;
import com.aiyi.game.dnfserver.utils.Common;
import com.aiyi.game.dnfserver.utils.HttpUtils;
import com.aiyi.game.dnfserver.utils.MD5;
import com.aiyi.game.dnfserver.utils.cache.UserTokenCacheUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.Map;
import java.util.UUID;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/15 9:18
 */
@RestController
@RequestMapping("api/v1/login")
public class LoginController {


    @Resource
    private AccountVODao accountVODao;

    @PostMapping
    @NoLogin
    public String login(@RequestBody User user){
        String password = MD5.getMd5(user.getPassword());
        AccountVO account = accountVODao.get(Method.where(AccountVO::getAccountname, C.EQ, user.getAccount())
                .and(AccountVO::getPassword, C.EQ, password));
        if (null == account){
            throw new ValidationException("账号或密码错误");
        }
        if (account.getAdmin() != 1 && account.getAdmin() != 2){
            throw new ValidationException("只有GM账号才能登录后台管理系统");
        }
        user.setId(account.getUid());
        user.setPassword(null);
        String token = UUID.randomUUID().toString().replace("-", "");
        UserTokenCacheUtil.putUserCache(token, user);
        return token;
    }
}
