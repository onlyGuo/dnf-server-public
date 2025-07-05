package com.aiyi.game.dnfserver.controller;

import com.aiyi.core.beans.ResultPage;
import com.aiyi.game.dnfserver.conf.NoLogin;
import com.aiyi.game.dnfserver.entity.CharacInfo;
import com.aiyi.game.dnfserver.service.CharacService;
import com.aiyi.game.dnfserver.utils.CharsetUtil;
import com.aiyi.game.dnfserver.utils.ChinaseUtil;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/13 14:33
 */
@RestController
@RequestMapping("api/v1/charac")
public class CharacInfoController {

    @Resource
    private CharacService characService;

    @GetMapping
    public ResultPage<CharacInfo> list(Integer minLev, Integer maxLevel, Integer job,
                                       String name, String account, Integer page, Integer pageSize){
        if (null == page) page = 1;
        if (null == pageSize) pageSize = 10;
        ResultPage<CharacInfo> list = characService.list(minLev, maxLevel, job, name, account, page, pageSize);
        for (CharacInfo info: list.getList()){
            CharsetUtil.latin12utf8(info);
        }
        return list;
    }

    /**
     * 列出某个账号的角色列表
     * @param account
     *      账号
     * @return
     */
    @GetMapping("{account}")
    @NoLogin
    public List<CharacInfo> listByAccount(@PathVariable String account){
        return characService.list(account);
    }

    @PutMapping
    public void update(@RequestBody CharacInfo info){
        characService.update(info);
    }

}
