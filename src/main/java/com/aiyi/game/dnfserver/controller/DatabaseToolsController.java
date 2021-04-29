package com.aiyi.game.dnfserver.controller;

import com.aiyi.game.dnfserver.dao.AccountVODao;
import com.aiyi.game.dnfserver.entity.DataBaseExcute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Copyright (c) 2021 HEBEI CLOUD IOT FACTORY BIGDATA CO.,LTD.
 * Legal liability shall be investigated for unauthorized use
 *
 * @Author: Guo Shengkai
 * @Date: Create in 2021/04/29 14:37
 */
@RestController
@RequestMapping("api/v1/database")
public class DatabaseToolsController {

    @Resource
    private AccountVODao accountVODao;

    /**
     * 通过接口快速操作数据库
     * 在不对外暴露数据库端口时连接数据库执行SQL，以便提升安全系数
     * 此接口需要在管理员登录后方可调用
     * @param excute
     *      执行信息
     * @return
     */
    @PostMapping
    public List<Map<String, Object>> excute(@RequestBody DataBaseExcute excute){
        excute.setScript(
                new String(Base64.getDecoder().decode(excute.getScript()),
                        StandardCharsets.UTF_8)
        );
        if (excute.getType() == 0){
            // 查询
            return accountVODao.listBySql(excute.getScript());
        }else{
            int execute = accountVODao.execute(excute.getScript());
            Map<String, Object> res = new HashMap<>();
            res.put("变更记录数", execute);
            List<Map<String, Object>> list = new ArrayList<>();
            list.add(res);
            return list;
        }
    }


}
