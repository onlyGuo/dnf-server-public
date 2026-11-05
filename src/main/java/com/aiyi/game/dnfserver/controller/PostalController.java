package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.conf.GmAllowed;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.security.OwnershipService;
import com.aiyi.game.dnfserver.mail.OnlineMailNotificationService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;
import java.util.Collections;
import java.util.Map;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:42
 */
@RestController
@GmAllowed
@RequestMapping("api/v1/postal")
public class PostalController {

    @Resource
    private PostalService postalService;

    @Resource
    private OwnershipService ownership;

    @Resource
    private OnlineMailNotificationService notifications;

    @GetMapping
    public ResultPage<Postal> list(String account, Date start,
                                   Date end, Integer page,
                                   Integer pageSize){
        if (null == page) page = 1;
        if (null == pageSize) pageSize = 10;
        ResultPage<Postal> list = postalService.list(account, start, end,
                page, pageSize);
        for (Postal postal: list.getList()){
            ChinaseUtil.toSimple(postal);
        }
        return list;
    }

    @PostMapping
    public Map<String, String> sendMail(@RequestBody Postal postal){
        if (postal.getReceiveCharacNo() == null) throw new IllegalArgumentException("收件角色无效");
        try { ownership.requireCharacter(Long.parseLong(postal.getReceiveCharacNo())); }
        catch (NumberFormatException e) { throw new IllegalArgumentException("收件角色无效"); }
        String notificationId = postalService.sendMail(postal);
        return Collections.singletonMap("notification", notifications.await(notificationId, 2500));
    }

}
