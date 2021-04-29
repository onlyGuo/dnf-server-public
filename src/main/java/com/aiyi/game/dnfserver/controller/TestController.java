package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.dao.CharacInfoDao;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 10:41
 */
@RestController
@RequestMapping("test")
public class TestController {

    @Resource
    private CharacInfoDao characInfoDao;

    @GetMapping("1")
    public Object testSelect(){
        CharacInfo characInfo = characInfoDao.get(2);
        ChinaseUtil.toSimple(characInfo);
        ChinaseUtil.toTraditional(characInfo);
        ChinaseUtil.toSimple(characInfo);
        return characInfo;
    }

}
