package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.Postal;
import com.aiyi.game.dnfserver.service.PostalService;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.Date;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 16:42
 */
@RestController
@RequestMapping("api/v1/postal")
public class PostalController {

    @Resource
    private PostalService postalService;

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
    public void sendMail(@RequestBody Postal postal){
        postalService.sendMail(postal);
    }

}
