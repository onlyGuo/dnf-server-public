package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.exception.ValidationException;
import com.aiyi.core.util.DateUtil;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.dao.User;
import com.aiyi.game.dnfserver.service.rmt.RmtService;
import com.aiyi.game.dnfserver.utils.Common;
import com.aiyi.game.dnfserver.utils.HttpUtils;
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
    private RmtService rmtService;

    @PostMapping
    @NoLogin
    public String login(@RequestBody User user){
        user.setClientId(rmtService.getClientId());

        JSONObject json = JSON.parseObject(
                HttpUtils.post(Common.RMT_URL + "api/v1/init/login", user)
        );
        if (json.getInteger("code") != null){
            throw new ValidationException(json.getString("message"));
        }

        String token = UUID.randomUUID().toString().replace("-", "");
        UserTokenCacheUtil.putUserCache(token, user);
        return token;
    }
}
